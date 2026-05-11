package com.example.services;

import com.example.entities.AppConfiguration;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Low-level SMTP email-sending utility for Library OS.
 *
 * <p>This class provides a single public entry point — {@link #sendEmail} —
 * that composes and transmits a plain-text email message using the SMTP
 * credentials stored in the current {@link AppConfiguration}.  All
 * higher-level email workflows (overdue reminders, OTP verification codes,
 * temporary passwords, payment receipts) delegate through this class.</p>
 *
 * <p>Configuration is read lazily at call-time from
 * {@link AppConfigurationService#getConfiguration()}, so changes made via the
 * Library Configuration dialog take effect on the next send without requiring
 * a restart.</p>
 *
 * <p>This is a non-instantiable utility class; all members are {@code static}.</p>
 *
 * @see ReminderService
 * @see InvoiceService
 */
public final class EmailService {

    /** Logger for SMTP diagnostics and delivery failures. */
    private static final Logger LOGGER = Logger.getLogger(EmailService.class.getName());

    /**
     * Private constructor — prevents instantiation of this utility class.
     */
    private EmailService() {
    }

    /**
     * Sends a plain-text email to the given recipient using the library's
     * configured SMTP server.
     *
     * <p>The method reads SMTP host, port, TLS, and credential settings from
     * {@link AppConfigurationService#getConfiguration()} at the time of the
     * call.  If email is not yet configured (i.e.
     * {@link AppConfiguration#isEmailConfigured()} returns {@code false}), a
     * {@link MessagingException} is thrown immediately without attempting a
     * network connection.</p>
     *
     * <p>Delivery failures are both logged at {@code WARNING} level and
     * re-thrown so that the caller can surface them to the user.</p>
     *
     * @param toAddress the RFC-5321 email address of the recipient;
     *                  must not be {@code null} or blank
     * @param subject   the email subject line; {@code null} is treated as an
     *                  empty string
     * @param body      the plain-text email body; {@code null} is treated as
     *                  an empty string
     * @throws MessagingException if email is not configured, the address is
     *                            invalid, or the SMTP transport fails
     */
    public static void sendEmail(String toAddress, String subject, String body) throws MessagingException {
        AppConfiguration config = AppConfigurationService.getConfiguration();
        if (config == null || !config.isEmailConfigured()) {
            throw new MessagingException("Email is not configured. Update Library Configuration first.");
        }
        if (toAddress == null || toAddress.isBlank()) {
            throw new MessagingException("Recipient email address is required.");
        }

        // Build the message using the library's sender address
        MimeMessage message = new MimeMessage(createSession(config));
        message.setFrom(new InternetAddress(config.getFromAddress()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
        message.setSubject(subject == null ? "" : subject);
        message.setText(body == null ? "" : body, StandardCharsets.UTF_8.name());
        message.setHeader("X-Mailer", "Library OS");

        try {
            Transport.send(message);
        } catch (MessagingException e) {
            LOGGER.log(Level.WARNING, "Failed to send email to " + toAddress, e);
            throw e;
        }
    }

    /**
     * Constructs a Jakarta Mail {@link Session} from the library's SMTP
     * configuration.
     *
     * <p>If SMTP authentication is disabled or the configured username is
     * blank, an anonymous (no-auth) session is returned.  Otherwise an
     * {@link Authenticator} is supplied using the stored SMTP credentials.</p>
     *
     * <p>Connection, read, and write timeouts are each fixed at 15 seconds to
     * prevent the UI thread from blocking indefinitely on a slow mail server.</p>
     *
     * @param config the current library configuration; must not be {@code null}
     *               and must satisfy {@link AppConfiguration#isEmailConfigured()}
     * @return a fully configured {@link Session} ready for message delivery
     */
    private static Session createSession(AppConfiguration config) {
        // Base SMTP properties used for both authenticated and anonymous sessions
        Properties properties = new Properties();
        properties.put("mail.smtp.host", config.getSmtpHost());
        properties.put("mail.smtp.port", String.valueOf(config.getSmtpPort()));
        properties.put("mail.smtp.auth", String.valueOf(config.isSmtpAuth()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(config.isStartTlsEnabled()));
        properties.put("mail.smtp.connectiontimeout", "15000");
        properties.put("mail.smtp.timeout", "15000");
        properties.put("mail.smtp.writetimeout", "15000");

        // Return anonymous session when auth is disabled
        if (!config.isSmtpAuth()) {
            return Session.getInstance(properties);
        }

        String username = config.getSmtpUsername() == null ? "" : config.getSmtpUsername();
        String password = config.getSmtpPassword() == null ? "" : config.getSmtpPassword();

        // Fall back to no-auth if username was not set
        if (username.isBlank()) {
            return Session.getInstance(properties);
        }

        // Authenticated session — credentials are provided via Authenticator
        return Session.getInstance(properties, new Authenticator() {
            /**
             * Returns the SMTP username/password pair stored in the library
             * configuration.
             *
             * @return a {@link PasswordAuthentication} built from the
             *         configured SMTP credentials
             */
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }
}
