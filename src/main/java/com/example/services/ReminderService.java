package com.example.services;

import com.example.entities.AppConfiguration;
import com.example.entities.BooksDB.IssueRecord;
import com.example.entities.User;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.activation.DataHandler;
import jakarta.mail.util.ByteArrayDataSource;

import java.io.InputStream;
import java.net.ConnectException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code ReminderService} automates the process of notifying users about 
 * overdue books and upcoming deadlines.
 *
 * <p>It utilizes JavaMail to send formatted emails and logs all notification 
 * attempts to prevent duplicate reminders.</p>
 */
public final class ReminderService {
    private static final Logger LOGGER = Logger.getLogger(ReminderService.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;


    private ReminderService() {
    }

    public static ReminderDispatchResult sendOverdueReminders(List<IssueRecord> overdueBooks) throws MessagingException {
        AppConfiguration config = AppConfigurationService.getConfiguration();
        validateConfiguration(config);

        Map<String, List<IssueRecord>> recordsByUser = new LinkedHashMap<>();
        for (IssueRecord record : overdueBooks) {
            if (record.getRemainingFine() > 0) {
                recordsByUser.computeIfAbsent(record.getUserId(), key -> new ArrayList<>()).add(record);
            }
        }

        ReminderDispatchResult result = new ReminderDispatchResult();
        for (Map.Entry<String, List<IssueRecord>> entry : recordsByUser.entrySet()) {
            // BUG FIX: UserService.getUserById() throws UserException when the user is not
            // found — it never returns null. The previous "if (user == null)" was dead code.
            // If a userId in the overdue records no longer exists in the DB, the uncaught
            // UserException propagated out of this method, aborting ALL remaining reminders.
            // We now catch the exception and gracefully skip that user.
            User user;
            try {
                user = UserService.getUserById(entry.getKey());
            } catch (Exception e) {
                result.incrementSkippedNoEmail();
                continue;
            }
            if (user.getEmail() == null || SecurityProvider.decryptUserField(user.getEmail(), user.getUserId(), "", "").isBlank()) {
                result.incrementSkippedNoEmail();
                continue;
            }
            try {
                String targetEmail = SecurityProvider.decryptUserField(user.getEmail(), user.getUserId(), "", "");
                if (targetEmail != null && !targetEmail.isBlank()) {
                    sendOverdueReminder(user, entry.getValue());
                    result.incrementSent();
                } else {
                    result.incrementSkippedNoEmail();
                }
            } catch (MessagingException ex) {
                LOGGER.log(Level.WARNING, "Failed to send reminder to " + user.getUserId(), ex);
                result.addFailure(user.getUserId(), ex.getMessage());
            }
        }
        return result;
    }

    public static void sendOverdueReminder(User user, List<IssueRecord> records) throws MessagingException {
        if (user == null) {
            throw new MessagingException("User information is required to send a reminder.");
        }
        if (records == null || records.isEmpty()) {
            throw new MessagingException("No overdue items were provided for the reminder.");
        }
        if (user.getEmail() == null || SecurityProvider.decryptUserField(user.getEmail(), user.getUserId(), "", "").isBlank()) {
            throw new MessagingException("The selected user does not have an email address on file.");
        }

        AppConfiguration config = AppConfigurationService.getConfiguration();
        validateConfiguration(config);
        Session session = createSession(config);
        String targetEmail = SecurityProvider.decryptUserField(user.getEmail(), user.getUserId(), "", "");
        sendMessage(session, config, targetEmail, "Library overdue reminder",
                buildOverdueBody(config, user, records),
                buildOverdueHtmlBody(config, user, records), null, null);
    }

    public static void sendTemporaryPassword(User user, String temporaryPassword) throws MessagingException {
        if (user == null) {
            throw new MessagingException("User information is required to send a password reset email.");
        }
        if (temporaryPassword == null || temporaryPassword.isBlank()) {
            throw new MessagingException("Temporary password cannot be blank.");
        }
        if (user.getEmail() == null || SecurityProvider.decryptUserField(user.getEmail(), user.getUserId(), "", "").isBlank()) {
            throw new MessagingException("The selected user does not have an email address on file.");
        }

        AppConfiguration config = AppConfigurationService.getConfiguration();
        validateConfiguration(config);
        Session session = createSession(config);
        String targetEmail = SecurityProvider.decryptUserField(user.getEmail(), user.getUserId(), "", "");
        sendMessage(session, config, targetEmail, "Library OS password reset",
                buildTemporaryPasswordBody(config, user, temporaryPassword),
                buildTemporaryPasswordHtmlBody(config, user, temporaryPassword), null, null);
    }

    public static void sendPaymentInvoice(User user, IssueRecord record, double amount, String invoiceId, byte[] attachment, String fileName) throws MessagingException {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new MessagingException("Valid user email is required.");
        }

        AppConfiguration config = AppConfigurationService.getConfiguration();
        validateConfiguration(config);
        Session session = createSession(config);

        String subject = "Invoice " + invoiceId + " - Fine Payment Receipt";
        String plain = "Fine Payment Receipt\n\nInvoice: " + invoiceId + "\nAmount: " + amount + "\nBook: " + record.getBookTitle();
        String html = buildPaymentInvoiceHtmlBody(config, user, record, amount, invoiceId);

        sendMessage(session, config, SecurityProvider.decryptUserField(user.getEmail(), user.getUserId(), "", ""), subject, plain, html, attachment, fileName);
    }

    /**
     * FIX #19: Sends a styled HTML verification code email that is visually consistent
     * with other transactional emails (forgot-password, overdue reminders, receipts).
     * Previously RegistrationDialog sent a bare plain-text email via EmailService.sendEmail().
     *
     * @param toAddress the recipient email address
     * @param otp       the 6-digit verification code to embed in the email
     * @throws MessagingException if the email cannot be sent
     */
    /**
     * Sends a premium styled HTML verification code email consistent with other
     * Library OS transactional emails. Digit boxes use table-cell layout (not
     * CSS spans) so they render correctly in Gmail, Outlook, Yahoo, and Apple Mail.
     */
    public static void sendVerificationCode(String toAddress, String otp) throws MessagingException {
        if (toAddress == null || toAddress.isBlank()) {
            throw new MessagingException("Recipient email address is required.");
        }
        AppConfiguration config = AppConfigurationService.getConfiguration();
        validateConfiguration(config);
        Session session = createSession(config);

        String subject = "Your Library OS verification code";
        String plain = "Your Library OS email verification code is: " + otp
                + "\n\nThis code is valid for this session only."
                + " Do not share it with anyone.\n\n"
                + config.getCurrentLibraryDisplayName();

        // Build digit boxes using table cells (renders in Gmail/Outlook/Apple Mail)
        StringBuilder digitCells = new StringBuilder();
        for (char c : otp.toCharArray()) {
            digitCells.append(
                "<td align=\"center\" valign=\"middle\" style=\"width:52px;height:62px;"
                + "background-color:#0F172A;border-radius:12px;margin:0 4px;"
                + "font-size:30px;font-weight:800;color:#14B8A6;"
                + "font-family:Segoe UI,Arial,sans-serif;border:2px solid #134E4A;\">"
            ).append(c).append("</td>"
                + "<td style=\"width:8px;\"></td>");
        }
        // Remove trailing spacer
        String cells = digitCells.toString();
        if (cells.endsWith("<td style=\"width:8px;\"></td>")) {
            cells = cells.substring(0, cells.lastIndexOf("<td style=\"width:8px;\"></td>"));
        }

        String body =
            "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">"
            + "<tr><td style=\"padding:8px 0 24px;text-align:center;\">"
            + "<p style=\"margin:0;font-size:16px;color:#334155;line-height:1.6;\">"
            + "Enter the 6-digit code below to verify your email address and complete registration.</p>"
            + "</td></tr>"
            + "<tr><td align=\"center\" style=\"padding:0 0 28px;\">"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">"
            + "<tr>" + cells + "</tr>"
            + "</table>"
            + "</td></tr>"
            + "<tr><td style=\"padding:0 0 20px;text-align:center;\">"
            + "<p style=\"margin:0;font-size:13px;color:#94A3B8;\">"
            + "This code is valid for this session only. Do not share it with anyone.</p>"
            + "</td></tr>"
            + "<tr><td style=\"padding:20px 0 0;border-top:1px solid #F1F5F9;\">"
            + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">"
            + "<tr>"
            + "<td style=\"width:36px;vertical-align:top;padding-right:12px;\">"
            + "<div style=\"width:32px;height:32px;background:linear-gradient(135deg,#0F172A,#134E4A);"
            + "border-radius:8px;text-align:center;line-height:32px;"
            + "font-size:16px;color:#14B8A6;font-weight:bold;\">&#x26A0;</div>"
            + "</td>"
            + "<td style=\"vertical-align:top;\">"
            + "<p style=\"margin:0;font-size:13px;color:#64748B;line-height:1.5;\">"
            + "<strong style=\"color:#475569;\">Didn't request this?</strong><br>"
            + "If you did not try to register with Library OS, you can safely ignore this email. "
            + "Your account has not been created.</p>"
            + "</td>"
            + "</tr></table>"
            + "</td></tr>"
            + "</table>";

        String html = buildEmailShell(config, "Email Verification", "Confirm your email address", "", body);
        sendMessage(session, config, toAddress, subject, plain, html, null, null);
    }

    public static void sendAccountApprovalEmail(User user) throws MessagingException {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new MessagingException("User information and valid email are required.");
        }

        AppConfiguration config = AppConfigurationService.getConfiguration();
        validateConfiguration(config);
        Session session = createSession(config);

        String subject = "Your Library OS account has been approved!";
        String plain = "Hello " + user.getFullName() + ",\n\n" +
                "Great news! Your account at " + config.getCurrentLibraryDisplayName() + " has been approved by the administrator.\n" +
                "You can now sign in using your username and password.\n\n" +
                "Happy reading!";
        String html = buildAccountApprovalHtmlBody(config, user);

        sendMessage(session, config, SecurityProvider.decryptUserField(user.getEmail(), user.getUserId(), "", ""), subject, plain, html, null, null);
    }

    /**
     * FIXED: Added null checks for SMTP credentials to prevent NullPointerException
     * in PasswordAuthentication constructor.
     */
    private static Session createSession(AppConfiguration config) {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", config.getSmtpHost());
        properties.put("mail.smtp.port", String.valueOf(config.getSmtpPort()));
        properties.put("mail.smtp.auth", String.valueOf(config.isSmtpAuth()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(config.isStartTlsEnabled()));
        properties.put("mail.smtp.connectiontimeout", "15000");
        properties.put("mail.smtp.timeout", "15000");
        properties.put("mail.smtp.writetimeout", "15000");

        if (config.isSmtpAuth()) {
            // FIXED: Null-safe credential retrieval
            final String username = config.getSmtpUsername() != null ? config.getSmtpUsername() : "";
            final String password = config.getSmtpPassword() != null ? config.getSmtpPassword() : "";

            // Only create authenticator if username is not empty
            if (!username.isEmpty()) {
                return Session.getInstance(properties, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });
            } else {
                LOGGER.log(Level.WARNING, "SMTP auth enabled but username is null/empty");
            }
        }
        return Session.getInstance(properties);
    }

    private static void sendMessage(Session session, AppConfiguration config, String toAddress,
                                    String subject, String plainBody, String htmlBody, byte[] attachment, String attachmentName) throws MessagingException {
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(config.getFromAddress()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
        message.setSubject(subject);
        message.setHeader("X-Mailer", "Library OS");

        MimeMultipart mixed = new MimeMultipart("mixed");

        MimeBodyPart contentPart = new MimeBodyPart();
        MimeMultipart alternative = new MimeMultipart("alternative");

        MimeBodyPart plainPart = new MimeBodyPart();
        plainPart.setText(plainBody, StandardCharsets.UTF_8.name());
        alternative.addBodyPart(plainPart);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
        alternative.addBodyPart(htmlPart);

        contentPart.setContent(alternative);
        mixed.addBodyPart(contentPart);

        if (attachment != null && attachment.length > 0) {
            MimeBodyPart attachPart = new MimeBodyPart();
            attachPart.setDataHandler(new DataHandler(new ByteArrayDataSource(attachment, "application/octet-stream")));
            attachPart.setFileName(attachmentName);
            mixed.addBodyPart(attachPart);
        }

        message.setContent(mixed);
        try {
            Transport.send(message);
        } catch (MessagingException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof java.net.ConnectException
                    || cause instanceof java.net.UnknownHostException
                    || cause instanceof java.net.NoRouteToHostException
                    || cause instanceof java.net.SocketException) {
                throw new MessagingException(
                        "No network connectivity — check your internet connection and try again.", ex);
            }
            if (cause instanceof java.net.SocketTimeoutException) {
                throw new MessagingException(
                        "Connection to mail server timed out — check your SMTP settings.", ex);
            }
            throw ex;
        }
    }

    private static String buildOverdueBody(AppConfiguration config, User user, List<IssueRecord> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("Hello ").append(user.getFullName()).append(",\n\n");
        builder.append("The following library items are overdue:\n\n");
        double totalFine = 0.0;
        for (IssueRecord record : records) {
            double fine = record.getRemainingFine();
            totalFine += fine;
            builder.append("- ").append(record.getBookTitle())
                    .append(" | Due: ").append(record.getDueDate().format(DATE_FORMATTER))
                    .append(" | Days overdue: ").append(record.getDaysOverdue())
                    .append(" | Fine: ").append(config.formatAmount(fine))
                    .append('\n');
        }

        builder.append("\nTotal outstanding fine: ").append(config.formatAmount(totalFine)).append('\n');
        builder.append("Please return the item(s) or contact the library administrator.\n");
        return builder.toString();
    }

    private static String buildTemporaryPasswordBody(AppConfiguration config, User user, String temporaryPassword) {
        String libraryName = config.getCurrentLibraryDisplayName();
        return "Hello " + user.getFullName() + ",\n\n" +
                "A password reset was requested for your Library OS account at " + libraryName + ".\n\n" +
                "Temporary password: " + temporaryPassword + "\n\n" +
                "Sign in with this password and change it immediately from Settings > Change Password.\n" +
                "If you did not request this change, contact the library administrator.\n";
    }

    private static String buildOverdueHtmlBody(AppConfiguration config, User user, List<IssueRecord> records) {
        StringBuilder items = new StringBuilder();
        double totalFine = 0.0;
        for (IssueRecord record : records) {
            double fine = record.getRemainingFine();
            totalFine += fine;
            items.append("""
                    <tr class="stack-row">
                      <td class="stack-cell" data-label="Item" style="padding:12px 14px;border-bottom:1px solid #E2E8F0;font-weight:600;color:#0F172A;">%s</td>
                      <td class="stack-cell" data-label="Due Date" style="padding:12px 14px;border-bottom:1px solid #E2E8F0;color:#475569;">%s</td>
                      <td class="stack-cell" data-label="Overdue" style="padding:12px 14px;border-bottom:1px solid #E2E8F0;color:#B91C1C;">%d day(s)</td>
                      <td class="stack-cell" data-label="Fine" style="padding:12px 14px;border-bottom:1px solid #E2E8F0;color:#0F766E;font-weight:600;">%s</td>
                    </tr>
                    """.formatted(
                    escapeHtml(record.getBookTitle()),
                    escapeHtml(record.getDueDate().format(DATE_FORMATTER)),
                    record.getDaysOverdue(),
                    escapeHtml(config.formatAmount(fine))));
        }

        String body = """
                <p style="margin:0 0 16px 0;color:#334155;font-size:15px;line-height:1.6;">Hello %s,</p>
                <p style="margin:0 0 20px 0;color:#334155;font-size:15px;line-height:1.6;">
                  The following library items are overdue. Please return them or contact the library team if you need help.
                </p>
                <div style="border:1px solid #E2E8F0;border-radius:16px;overflow:hidden;background:#FFFFFF;">
                  <table role="presentation" class="stack-table" style="width:100%%;border-collapse:collapse;table-layout:fixed;">
                    <thead>
                      <tr style="background:#F8FAFC;">
                        <th class="stack-head" style="text-align:left;padding:12px 14px;color:#64748B;font-size:12px;">Item</th>
                        <th class="stack-head" style="text-align:left;padding:12px 14px;color:#64748B;font-size:12px;">Due Date</th>
                        <th class="stack-head" style="text-align:left;padding:12px 14px;color:#64748B;font-size:12px;">Overdue</th>
                        <th class="stack-head" style="text-align:left;padding:12px 14px;color:#64748B;font-size:12px;">Fine</th>
                      </tr>
                    </thead>
                    <tbody>%s</tbody>
                  </table>
                </div>
                <div style="margin-top:20px;padding:16px 18px;background:#ECFDF5;border:1px solid #A7F3D0;border-radius:14px;">
                  <div style="font-size:13px;color:#065F46;text-transform:uppercase;font-weight:700;letter-spacing:0.04em;">Outstanding Fine</div>
                  <div style="margin-top:4px;font-size:24px;font-weight:800;color:#0F766E;">%s</div>
                </div>
                """.formatted(
                escapeHtml(user.getFullName()),
                items,
                escapeHtml(config.formatAmount(totalFine)));

        // Icon: Book/Library
        String icon = """
                <div style="display:inline-block;width:56px;height:56px;background:linear-gradient(135deg,#0F172A,#134E4A 58%,#14B8A6);border-radius:16px;position:relative;box-shadow:0 4px 10px rgba(15,23,42,0.3);vertical-align:middle;">
                  <div style="position:absolute;left:14px;top:10px;width:28px;height:34px;background:#FFFFFF;border-radius:4px;box-shadow:0 1px 3px rgba(0,0,0,0.2);">
                    <div style="position:absolute;left:0;top:0;width:9px;height:100%;background:#CCFBF1;border-radius:4px 0 0 4px;"></div>
                    <div style="position:absolute;left:11px;top:6px;width:12px;height:2px;background:#0F766E;opacity:0.8;"></div>
                    <div style="position:absolute;left:11px;top:12px;width:12px;height:2px;background:#0F766E;opacity:0.8;"></div>
                    <div style="position:absolute;left:11px;top:18px;width:8px;height:2px;background:#0F766E;opacity:0.8;"></div>
                  </div>
                </div>
                """;

        return buildEmailShell(config, "Overdue Reminder",
                "Please return the items below as soon as possible.", icon, body);
    }

    private static String buildTemporaryPasswordHtmlBody(AppConfiguration config, User user, String temporaryPassword) {
        String body = """
                <p style="margin:0 0 16px 0;color:#334155;font-size:15px;line-height:1.6;">Hello %s,</p>
                <p style="margin:0 0 20px 0;color:#334155;font-size:15px;line-height:1.6;">
                  A password reset was requested for your Library OS account at %s.
                </p>
                <div style="padding:18px 20px;border-radius:16px;background:#0F172A;">
                  <div style="font-size:12px;color:#94A3B8;text-transform:uppercase;font-weight:700;letter-spacing:0.08em;">Temporary Password</div>
                  <div style="margin-top:8px;font-size:28px;font-weight:800;color:#F8FAFC;letter-spacing:0.08em;">%s</div>
                </div>
                <p style="margin:20px 0 0 0;color:#475569;font-size:14px;line-height:1.6;">
                  Sign in with this password and change it immediately from <strong>Settings &gt; Change Password</strong>.
                </p>
                """.formatted(
                escapeHtml(user.getFullName()),
                escapeHtml(config.getCurrentLibraryDisplayName()),
                escapeHtml(temporaryPassword));

        // Icon: Lock
        String icon = """
                <div style="display:inline-block;width:56px;height:56px;background:linear-gradient(135deg,#6366F1,#4F46E5);border-radius:16px;position:relative;box-shadow:0 4px 10px rgba(99,102,241,0.3);vertical-align:middle;">
                  <div style="position:absolute;left:18px;top:24px;width:20px;height:16px;background:#FFFFFF;border-radius:4px;"></div>
                  <div style="position:absolute;left:22px;top:14px;width:12px;height:14px;border:3px solid #FFFFFF;border-bottom:none;border-radius:6px 6px 0 0;"></div>
                </div>
                """;

        return buildEmailShell(config, "Password Reset",
                "Use the temporary password below to sign in and update your credentials.", icon, body);
    }

    private static String buildEmailShell(AppConfiguration config, String heading, String subtitle, String iconBlock, String body) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <style>
                    @media only screen and (max-width: 640px) {
                      .shell-body { padding: 12px !important; }
                      .shell-card { border-radius: 18px !important; }
                      .shell-hero { padding: 22px 18px !important; }
                      .shell-content { padding: 22px 18px !important; }
                      .shell-hero-row { display: block !important; }
                      .shell-hero-copy { margin-top: 14px !important; }
                      .stack-head { display: none !important; }
                      .stack-table,
                      .stack-table tbody,
                      .stack-row,
                      .stack-cell { display: block !important; width: 100% !important; }
                      .stack-row { border-bottom: 1px solid #E2E8F0 !important; padding: 6px 0 !important; }
                      .stack-cell {
                        box-sizing: border-box !important;
                        border-bottom: none !important;
                        padding: 6px 14px 6px 120px !important;
                        position: relative !important;
                        word-break: break-word !important;
                      }
                      .stack-cell:before {
                        content: attr(data-label) !important;
                        position: absolute !important;
                        left: 14px !important;
                        top: 6px !important;
                        width: 92px !important;
                        color: #64748B !important;
                        font-size: 11px !important;
                        font-weight: 700 !important;
                        text-transform: uppercase !important;
                      }
                    }
                  </style>
                </head>
                <body class="shell-body" style="margin:0;padding:24px;background:#E2E8F0;font-family:Segoe UI,Arial,sans-serif;">
                  <div class="shell-card" style="max-width:680px;margin:0 auto;background:#FFFFFF;border-radius:24px;overflow:hidden;box-shadow:0 18px 40px rgba(15,23,42,0.12);">
                    <div class="shell-hero" style="padding:32px;background:linear-gradient(135deg,#0F172A,#134E4A 58%,#14B8A6);">
                      <table role="presentation" border="0" cellpadding="0" cellspacing="0" style="width:100%;">
                        <tr>
                          <td style="vertical-align:middle;padding-right:24px;width:56px;">
                            <!-- Premium CSS-based Logo (Cross-Client Compatible) -->
                            <table role="presentation" border="0" cellpadding="0" cellspacing="0" style="width:56px;height:56px;background-color:#0F172A;border-radius:14px;overflow:hidden;box-shadow:0 4px 10px rgba(0,0,0,0.3);">
                              <tr>
                                <td align="center" valign="middle" style="padding:0;">
                                  <table role="presentation" border="0" cellpadding="0" cellspacing="0" style="width:28px;height:36px;background-color:#FFFFFF;border-radius:4px;overflow:hidden;">
                                    <tr>
                                      <td style="width:9px;background-color:#CCFBF1;border-right:1px solid #F1F5F9;">&nbsp;</td>
                                      <td valign="middle" style="padding:4px 3px;">
                                        <div style="width:12px;height:2px;background-color:#0F766E;margin-bottom:5px;font-size:1px;line-height:1px;">&nbsp;</div>
                                        <div style="width:12px;height:2px;background-color:#0F766E;margin-bottom:5px;font-size:1px;line-height:1px;">&nbsp;</div>
                                        <div style="width:8px;height:2px;background-color:#0F766E;font-size:1px;line-height:1px;">&nbsp;</div>
                                      </td>
                                    </tr>
                                  </table>
                                </td>
                              </tr>
                            </table>
                          </td>
                          <td style="vertical-align:middle;text-align:left;">
                            <div style="font-size:12px;font-weight:700;letter-spacing:0.15em;text-transform:uppercase;color:#99F6E4;margin-bottom:6px;">Library OS</div>
                            <div style="font-size:28px;font-weight:800;color:#F8FAFC;line-height:1.1;margin:0;">{{HEADING}}</div>
                            <div style="margin-top:6px;font-size:14px;color:#CCFBF1;line-height:1.4;opacity:0.85;">{{SUBTITLE}}</div>
                          </td>
                        </tr>
                      </table>
                    </div>
                    <div class="shell-content" style="padding:28px 32px;">
                      <div style="margin-bottom:20px;font-size:14px;color:#64748B;">{{LIBRARY}}</div>
                      {{BODY}}
                      <p style="margin:24px 0 0 0;padding-top:20px;border-top:1px solid #F1F5F9;color:#94A3B8;font-size:12px;text-align:center;">
                        This is an automated message from Library OS. Please do not reply.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """
                .replace("{{HEADING}}", escapeHtml(heading))
                .replace("{{SUBTITLE}}", escapeHtml(subtitle))
                .replace("{{LIBRARY}}", escapeHtml(config.getCurrentLibraryDisplayName()))
                .replace("{{BODY}}", body);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }



    private static void validateConfiguration(AppConfiguration config) throws MessagingException {
        if (config == null || !config.isEmailConfigured()) {
            throw new MessagingException("Email is not configured. Update Library Configuration first.");
        }
    }

    public static String toUserMessage(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnknownHostException) {
                return hasUsableNetworkConnection()
                        ? "Could not resolve the email server host. Check the SMTP host value."
                        : "No network connectivity detected. Connect to the internet and try again.";
            }
            if (current instanceof ConnectException || current instanceof SocketTimeoutException) {
                return hasUsableNetworkConnection()
                        ? "Could not connect to the email server. Check the SMTP host, port, and firewall settings."
                        : "No network connectivity detected. Connect to the internet and try again.";
            }
            if (current instanceof MessagingException messagingException && messagingException.getNextException() != null) {
                String nested = toUserMessage(messagingException.getNextException());
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
            current = current.getCause();
        }

        String message = error != null ? error.getMessage() : null;
        if (message == null || message.isBlank()) {
            return "Email sending failed.";
        }
        if (!hasUsableNetworkConnection() && (
                message.contains("Couldn't connect")
                        || message.contains("Could not connect")
                        || message.contains("Connection timed out")
                        || message.contains("Unknown host"))) {
            return "No network connectivity detected. Connect to the internet and try again.";
        }
        return message;
    }

    private static boolean hasUsableNetworkConnection() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return false;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback() && !networkInterface.isVirtual()) {
                    return true;
                }
            }
        } catch (SocketException ex) {
            LOGGER.log(Level.FINE, "Could not inspect network interfaces", ex);
        }
        return false;
    }

    public static final class ReminderDispatchResult {
        private int sentCount;
        private int skippedNoEmailCount;
        private final Map<String, String> failures = new LinkedHashMap<>();

        public int getSentCount() {
            return sentCount;
        }

        public int getSkippedNoEmailCount() {
            return skippedNoEmailCount;
        }

        public Map<String, String> getFailures() {
            return Map.copyOf(failures);
        }

        void incrementSent() {
            sentCount++;
        }

        void incrementSkippedNoEmail() {
            skippedNoEmailCount++;
        }

        void addFailure(String userId, String reason) {
            failures.put(userId, reason);
        }
    }
    private static String buildPaymentInvoiceHtmlBody(AppConfiguration config, User user, IssueRecord record, double amount, String invoiceId) {
        String template = loadHtmlTemplate("invoice-template.html");
        String body;
        if (template == null) {
            body = "<h1>Invoice " + invoiceId + "</h1><p>Amount: " + amount + "</p>";
        } else {
            // Extract content between <div class="content"> and the last </div> before </body>
            int start = template.indexOf("<div class=\"content\">");
            int end = template.lastIndexOf("</div>");
            if (start != -1 && end != -1) {
                body = template.substring(start, end + 6);
            } else {
                body = template;
            }

            body = body
                    .replace("{{LIBRARY_NAME}}", config.getLibraryName())
                    .replace("{{INVOICE_ID}}", invoiceId)
                    .replace("{{DATE}}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm")))
                    .replace("{{USER_NAME}}", user.getDisplayName())
                    .replace("{{USER_ID}}", user.getUserId())
                    .replace("{{BOOK_TITLE}}", record.getBookTitle())
                    .replace("{{ISBN}}", record.getIsbn())
                    .replace("{{DAYS}}", String.valueOf(record.getDaysOverdue()))
                    .replace("{{AMOUNT}}", AppConfigurationService.getConfiguration().formatAmount(amount))
                    .replace("{{YEAR}}", String.valueOf(java.time.Year.now().getValue()));
        }

        // Icon: Invoice
        String icon = """
                <div style="display:inline-block;width:56px;height:56px;background:linear-gradient(135deg,#0D9488,#0F766E);border-radius:16px;position:relative;box-shadow:0 4px 10px rgba(13,148,136,0.3);vertical-align:middle;">
                  <div style="position:absolute;left:15px;top:12px;width:26px;height:32px;background:#FFFFFF;border-radius:2px;">
                    <div style="position:absolute;left:4px;top:6px;width:18px;height:2px;background:#E2E8F0;"></div>
                    <div style="position:absolute;left:4px;top:12px;width:18px;height:2px;background:#E2E8F0;"></div>
                    <div style="position:absolute;left:4px;top:18px;width:12px;height:2px;background:#0D9488;"></div>
                  </div>
                </div>
                """;

        return buildEmailShell(config, "Payment Receipt", "Thank you for your payment.", icon, body);
    }

    private static String buildAccountApprovalHtmlBody(AppConfiguration config, User user) {
        String body = """
                <p style="margin:0 0 16px 0;color:#334155;font-size:15px;line-height:1.6;">Hello %s,</p>
                <p style="margin:0 0 20px 0;color:#334155;font-size:15px;line-height:1.6;">
                  Great news! Your account at <strong>%s</strong> has been approved by the administrator.
                </p>
                <p style="margin:0 0 20px 0;color:#334155;font-size:15px;line-height:1.6;">
                  You can now sign in using your username (<strong>%s</strong>) and the password you chose during registration.
                </p>
                <div style="margin:24px 0;text-align:center;">
                   <div style="display:inline-block;padding:14px 28px;background:#0D9488;color:#FFFFFF;border-radius:12px;font-weight:700;text-decoration:none;">Sign In Now</div>
                </div>
                """.formatted(
                escapeHtml(user.getFullName()),
                escapeHtml(config.getCurrentLibraryDisplayName()),
                escapeHtml(user.getUserId()));

        // Icon: Checkmark/Success
        String icon = """
                <div style="display:inline-block;width:56px;height:56px;background:linear-gradient(135deg,#22C55E,#16A34A);border-radius:16px;position:relative;box-shadow:0 4px 10px rgba(34,197,94,0.3);vertical-align:middle;">
                  <div style="position:absolute;left:16px;top:16px;width:24px;height:24px;background:white;border-radius:50%%;">
                     <div style="position:absolute;left:8px;top:11px;width:8px;height:4px;border-left:3px solid #16A34A;border-bottom:3px solid #16A34A;transform:rotate(-45deg);"></div>
                  </div>
                </div>
                """;

        return buildEmailShell(config, "Account Approved",
                "Your registration request has been accepted.", icon, body);
    }

    private static String loadHtmlTemplate(String name) {
        try (InputStream is = ReminderService.class.getResourceAsStream("/templates/" + name)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}