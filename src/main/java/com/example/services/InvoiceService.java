package com.example.services;

import com.example.application.ToastDisplay;
import com.example.application.ui.AppTheme;
import com.example.entities.AppConfiguration;
import com.example.entities.BooksDB;
import com.example.entities.BooksDB.IssueRecord;
import com.example.entities.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * The {@code InvoiceService} handles the generation of invoices and receipt
 * management for library fines and fees.
 *
 * <p>It provides the logic to calculate overdue fees and records payment history
 * for financial auditing.</p>
 */
public final class InvoiceService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void generateAndHandleInvoice(User user, IssueRecord record, double amount, ToastDisplay toast) {
        generateAndHandleInvoice(user, record, amount, toast, null);
    }

    public static void generateAndHandleInvoice(User user, IssueRecord record, double amount, ToastDisplay toast, Runnable onComplete) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Process Fine Payment");

        DialogPane dp = dlg.getDialogPane();
        AppTheme.applyTheme(dp);
        dp.setPrefWidth(460);

        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);

        // Header - neutral, not "success" until payment is actually made
        StackPane iconBadge = new StackPane(AppTheme.createIcon(AppTheme.ICON_CARD, 26));
        iconBadge.setPrefSize(56, 56);
        iconBadge.setMaxSize(56, 56);
        iconBadge.setStyle("-fx-background-color: rgba(99,102,241,0.15); -fx-background-radius: 28px;");

        Label title = new Label("Process Fine Payment");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill:" +
                (AppTheme.darkMode ? "#E0E7FF" : "#3730A3") + ";");

        Separator sep1 = new Separator();

        // Fine summary
        VBox details = new VBox(6);
        details.setAlignment(Pos.CENTER_LEFT);
        details.setPadding(new Insets(0, 8, 0, 8));
        double totalFine      = record.calculateFine();
        double alreadyPaidAmt = record.getPaidAmount();
        double remaining      = Math.max(0, totalFine - alreadyPaidAmt);
        Label descLbl     = new Label("Total fine:        " + AppTheme.formatCurrency(totalFine));
        Label paidLbl     = new Label("Already paid:   " + AppTheme.formatCurrency(alreadyPaidAmt));
        Label balanceLbl  = new Label("Balance due:    " + AppTheme.formatCurrency(remaining));
        descLbl.setStyle("-fx-text-fill: #64748B; -fx-font-size:13px;");
        paidLbl.setStyle("-fx-text-fill: #64748B; -fx-font-size:13px;");
        balanceLbl.setStyle("-fx-font-weight: 700; -fx-font-size:14px; -fx-text-fill:" +
                (AppTheme.darkMode ? "#F8FAFC" : "#0F172A") + ";");
        details.getChildren().addAll(descLbl, paidLbl, balanceLbl);

        Separator sep2 = new Separator();

        // Amount input
        Label payLbl = new Label("Enter payment amount:");
        payLbl.setStyle("-fx-font-weight: 600; -fx-font-size:13px;");
        TextField amountField = new TextField(String.format("%.2f", remaining));
        amountField.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-alignment: CENTER; -fx-padding: 10;" +
                "-fx-border-radius:10px; -fx-background-radius:10px; -fx-border-color:" +
                (AppTheme.darkMode ? "#4F46E5" : "#6366F1") + "; -fx-border-width:2;");
        amountField.textProperty().addListener((obs, old, newValue) -> {
            if (!newValue.matches("\\d*(\\.\\d*)?")) amountField.setText(old);
        });

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill:#DC2626; -fx-font-size:12px;");
        errorLbl.setVisible(false);

        // Action choice radio buttons
        Label actionLbl = new Label("After payment:");
        actionLbl.setStyle("-fx-font-weight: 600; -fx-font-size:13px;");
        javafx.scene.control.ToggleGroup actionGroup = new javafx.scene.control.ToggleGroup();
        RadioButton rbDone   = new RadioButton("Record payment only");
        RadioButton rbPrint  = new RadioButton("Print receipt");
        RadioButton rbEmail  = new RadioButton("Send email receipt");
        RadioButton rbBoth   = new RadioButton("Print & email receipt");
        for (RadioButton rb : new RadioButton[]{rbDone, rbPrint, rbEmail, rbBoth}) {
            rb.setToggleGroup(actionGroup);
            rb.setStyle("-fx-font-size:13px;");
        }
        rbDone.setSelected(true);
        VBox actionBox = new VBox(6, rbDone, rbPrint, rbEmail, rbBoth);

        root.getChildren().addAll(iconBadge, title, sep1, details, sep2, payLbl, amountField, errorLbl, actionLbl, actionBox);
        dp.setContent(root);

        ButtonType confirmType = new ButtonType("Confirm Payment", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType  = new ButtonType("Cancel",          ButtonBar.ButtonData.CANCEL_CLOSE);
        dp.getButtonTypes().addAll(cancelType, confirmType);

        Button confirmBtn = (Button) dp.lookupButton(confirmType);
        Button cancelBtn  = (Button) dp.lookupButton(cancelType);
        confirmBtn.getStyleClass().add("btn-primary");
        if (cancelBtn != null) {
            cancelBtn.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "#334155" : "#E5E7EB") +
                    "; -fx-text-fill:" + (AppTheme.darkMode ? "#F8FAFC" : "#1F2937") +
                    "; -fx-font-weight:600; -fx-background-radius:8px; -fx-padding:9 18;");
        }

        // Prevent closing without valid amount
        confirmBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            String text = amountField.getText().trim();
            if (text.isEmpty()) {
                errorLbl.setText("Please enter a payment amount.");
                errorLbl.setVisible(true);
                AppTheme.flashError(amountField);
                ev.consume();
                return;
            }
            double val;
            try { val = Double.parseDouble(text); }
            catch (Exception e) {
                errorLbl.setText("Invalid amount — please enter a number.");
                errorLbl.setVisible(true);
                ev.consume();
                return;
            }
            if (val <= 0) {
                errorLbl.setText("Amount must be greater than zero.");
                errorLbl.setVisible(true);
                AppTheme.flashError(amountField);
                ev.consume();
                return;
            }
            double max = record.calculateFine() - record.getPaidAmount();
            if (val > max + 0.05) {
                errorLbl.setText("Amount (" + AppTheme.formatCurrency(val) + ") exceeds balance (" + AppTheme.formatCurrency(max) + ").");
                errorLbl.setVisible(true);
                AppTheme.flashError(amountField);
                ev.consume();
            }
        });

        // BUG FIX: The original setResultConverter always returned null, so showAndWait()
        // returned Optional.empty() regardless of how the dialog was closed — by Confirm,
        // Cancel, or the window X button.  The code after showAndWait() then checked only
        // whether the amount field was non-empty, which it was whenever the user had typed
        // anything before clicking Cancel or X.  Result: payment was recorded even when
        // the user explicitly cancelled.
        // Fix: use an AtomicBoolean flag that is set to true ONLY when the Confirm button's
        // action filter passes validation and the button fires.  Every other close path
        // leaves it false, so the payment block below is skipped.
        java.util.concurrent.atomic.AtomicBoolean confirmed = new java.util.concurrent.atomic.AtomicBoolean(false);
        confirmBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            // This filter runs AFTER the validation filter above.  If we reach here,
            // validation passed (the earlier filter would have consumed the event otherwise).
            confirmed.set(true);
        });

        dlg.setResultConverter(bt -> null);
        dlg.showAndWait();

        // Only process payment if the user explicitly clicked Confirm (not Cancel / X)
        if (!confirmed.get()) return;

        // Re-parse the amount (the validation filter above already verified it's valid)
        double finalAmount;
        try { finalAmount = Double.parseDouble(amountField.getText().trim()); }
        catch (Exception e) { return; }
        if (finalAmount <= 0) return;

        // --- Process payment ---
        record.setPaidAmount(record.getPaidAmount() + finalAmount);
        double currentAccruedFine = record.calculateFine();
        if (record.getPaidAmount() >= currentAccruedFine - 0.01) {
            if (!record.isReturned()) {
                record.setDueDate(java.time.LocalDate.now());
                record.setPaidAmount(0);
                record.setFineAmount(0);
                record.setFinePaid(false);
            } else {
                record.setFineAmount(currentAccruedFine);
                record.setFinePaid(true);
            }
        }
        BooksDB.getInstance().saveAllData();

        String invoiceId = "INV-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BooksDB.getInstance().addInvoiceRecord(
                new BooksDB.InvoiceData(invoiceId, user.getUserId(), record.getIsbn(), record.getBookTitle(), finalAmount));

        if (toast != null) toast.showSuccess("Payment of " + AppTheme.formatCurrency(finalAmount) + " recorded. Invoice: " + invoiceId);

        // --- Handle receipt action based on radio selection ---
        boolean doPrint = rbPrint.isSelected() || rbBoth.isSelected();
        boolean doEmail = rbEmail.isSelected() || rbBoth.isSelected();
        if (doPrint) printInvoice(user, record, finalAmount, invoiceId);
        if (doEmail) emailInvoice(user, record, finalAmount, invoiceId, toast);

        if (onComplete != null) onComplete.run();
    }

    public static void processInvoiceActions(User user, IssueRecord record, double amount, String invoiceId, ToastDisplay toast) {
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("Receipt Options");
        DialogPane dp = dlg.getDialogPane();
        AppTheme.applyTheme(dp);
        dp.setMinWidth(480);
        dp.setPrefWidth(480);
        // Remove default button bar entirely — we draw our own styled buttons
        dp.getButtonTypes().setAll(ButtonType.CLOSE);
        // Hide the default Close button (we handle closing ourselves)
        Button defaultClose = (Button) dp.lookupButton(ButtonType.CLOSE);
        if (defaultClose != null) defaultClose.setVisible(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.CENTER);

        // ── Success badge ──────────────────────────────────────────────────
        StackPane iconBadge = new StackPane(AppTheme.createIcon(AppTheme.ICON_CHECK, 22));
        iconBadge.setPrefSize(48, 48); iconBadge.setMaxSize(48, 48);
        iconBadge.setStyle("-fx-background-color:rgba(22,163,74,0.15); -fx-background-radius:24px;");
        Label successLbl = new Label("Payment Confirmed");
        successLbl.setStyle("-fx-font-size:18px; -fx-font-weight:800; -fx-text-fill:#16A34A;");
        Label msg = new Label("Payment of " + AppTheme.formatCurrency(amount) + " has been recorded.");
        msg.setStyle("-fx-font-size:13px; -fx-text-fill:" + (AppTheme.darkMode ? "#94A3B8" : "#64748B") + ";");
        Label idLbl = new Label("Invoice: " + invoiceId);
        idLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#94A3B8; -fx-padding:0 0 8 0;");

        Label choiceLbl = new Label("What would you like to do with the receipt?");
        choiceLbl.setStyle("-fx-font-size:13px; -fx-font-weight:600; -fx-text-fill:" +
                (AppTheme.darkMode ? "#E2E8F0" : "#1E293B") + ";");

        // ── Action buttons — fully styled, not in ButtonBar ────────────────
        final boolean[] acted = {false};

        Button printBtn  = AppTheme.createIconTextButton("Print Receipt",  AppTheme.ICON_PRINT,  AppTheme.ButtonStyle.PRIMARY);
        Button emailBtn  = AppTheme.createIconTextButton("Send Email",      AppTheme.ICON_MAIL,   AppTheme.ButtonStyle.PRIMARY);
        Button bothBtn   = AppTheme.createIconTextButton("Print & Email",   AppTheme.ICON_PRINT,  AppTheme.ButtonStyle.SUCCESS);
        Button doneBtn   = AppTheme.createIconTextButton("Done",            AppTheme.ICON_CHECK,  AppTheme.ButtonStyle.SECONDARY);

        for (Button b : new Button[]{printBtn, emailBtn, bothBtn, doneBtn}) {
            b.setMinWidth(130); b.setPrefHeight(40);
        }

        printBtn.setOnAction(e -> { acted[0] = true; dlg.close(); printInvoice(user, record, amount, invoiceId); });
        emailBtn.setOnAction(e -> { acted[0] = true; dlg.close(); emailInvoice(user, record, amount, invoiceId, toast); });
        bothBtn.setOnAction(e  -> { acted[0] = true; dlg.close(); printInvoice(user, record, amount, invoiceId); emailInvoice(user, record, amount, invoiceId, toast); });
        doneBtn.setOnAction(e  -> dlg.close());

        HBox actionRow = new HBox(10, printBtn, emailBtn, bothBtn);
        actionRow.setAlignment(Pos.CENTER);

        root.getChildren().addAll(iconBadge, successLbl, msg, idLbl, new Separator(),
                choiceLbl, actionRow, doneBtn);
        dp.setContent(root);

        dlg.showAndWait();
    }

    private static void printInvoice(User user, IssueRecord record, double amount, String invoiceId) {
        VBox printable = createInvoiceNode(user, record, amount, invoiceId);

        Stage printStage = new Stage();
        printStage.initModality(Modality.APPLICATION_MODAL);
        printStage.setTitle("Print Preview - " + invoiceId);

        ScrollPane scroll = new ScrollPane(printable);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");

        // ── Check printer availability up-front so we can show the right buttons ──
        // On Wayland / Hyprland (CachyOS etc.) JavaFX's PrinterJob uses CUPS.
        // If CUPS has no printers configured (not even cups-pdf), createPrinterJob()
        // returns null and showPrintDialog() is never reached — causing a silent no-op.
        // We detect this early and always offer "Save as PNG" as a fallback.
        boolean hasPrinters = !javafx.print.Printer.getAllPrinters().isEmpty();

        Button printBtn = new Button(hasPrinters ? "Print Now" : "No Printers — try Save as PNG");
        printBtn.getStyleClass().add("btn-primary");
        printBtn.setDisable(!hasPrinters);

        Button saveBtn = new Button("Save as PNG");
        saveBtn.getStyleClass().add("btn-secondary");

        Label printerWarning = new Label();
        if (!hasPrinters) {
            printerWarning.setText(
                "No printers detected via CUPS.\n" +
                "On Wayland/Hyprland install cups-pdf for a virtual PDF printer:\n" +
                "  sudo pacman -S cups cups-pdf && sudo systemctl enable --now cups\n" +
                "Or use \"Save as PNG\" to export the receipt.");
            printerWarning.setStyle(
                "-fx-text-fill: #DC2626; -fx-font-size: 12px; -fx-wrap-text: true;");
            printerWarning.setWrapText(true);
            printerWarning.setMaxWidth(460);
        }

        printBtn.setOnAction(e -> {
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job == null) {
                // Printer list changed between open and click — show actionable error.
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("No Printers Found");
                alert.setHeaderText("No printers detected");
                alert.setContentText(
                    "JavaFX could not find any printers via CUPS.\n\n" +
                    "On CachyOS / Arch with Hyprland:\n" +
                    "  1. Install CUPS:  sudo pacman -S cups cups-pdf\n" +
                    "  2. Enable CUPS:   sudo systemctl enable --now cups\n" +
                    "  3. Restart the app and try again.\n\n" +
                    "Alternatively, use the \"Save as PNG\" button to export the receipt.");
                AppTheme.applyTheme(alert.getDialogPane());
                alert.showAndWait();
                return;
            }
            boolean proceed = job.showPrintDialog(printStage);
            if (proceed) {
                boolean success = job.printPage(printable);
                if (success) {
                    job.endJob();
                    printStage.close();
                } else {
                    job.cancelJob();
                }
            } else {
                job.cancelJob();
            }
        });

        saveBtn.setOnAction(e -> {
            // Force layout so snapshot captures all content at full size
            new Scene(printable);
            printable.applyCss();
            printable.layout();

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.WHITE);
            WritableImage image = printable.snapshot(params, null);

            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Save Receipt as PNG");
            chooser.setInitialFileName("Invoice_" + invoiceId + ".png");
            chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PNG Image", "*.png"));
            java.io.File file = chooser.showSaveDialog(printStage);
            if (file != null) {
                try {
                    ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                } catch (Exception ex) {
                    Alert err = new Alert(Alert.AlertType.ERROR,
                        "Could not save file:\n" + ex.getMessage());
                    AppTheme.applyTheme(err.getDialogPane());
                    err.showAndWait();
                }
            }
        });

        HBox btnRow = new HBox(10, printBtn, saveBtn);
        btnRow.setAlignment(Pos.CENTER);

        VBox layout = new VBox(14, scroll, printerWarning, btnRow);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: white;");

        Scene scene = new Scene(layout, 520, 780);
        AppTheme.applyTheme(scene);
        printStage.setScene(scene);
        printStage.show();
    }

    private static VBox createInvoiceNode(User user, IssueRecord record, double amount, String invoiceId) {
        VBox v = new VBox(15);
        v.setPadding(new Insets(40));
        v.setStyle("-fx-background-color: white; -fx-text-fill: black;");
        v.setMinWidth(450);
        v.setMaxWidth(450);

        AppConfiguration cfg = AppConfigurationService.getConfiguration();

        Label libName = new Label(cfg.getLibraryName().toUpperCase());
        libName.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");

        Label receiptTitle = new Label("FINE PAYMENT RECEIPT");
        receiptTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #64748B;");

        Separator sep1 = new Separator();

        GridPane info = new GridPane();
        info.setHgap(20); info.setVgap(8);
        info.add(boldLabel("Invoice ID:"), 0, 0); info.add(new Label(invoiceId), 1, 0);
        info.add(boldLabel("Date:"),       0, 1); info.add(new Label(LocalDateTime.now().format(FMT)), 1, 1);
        info.add(boldLabel("Member:"),     0, 2); info.add(new Label(user.getDisplayName() + " (" + user.getUserId() + ")"), 1, 2);

        Separator sep2 = new Separator();

        VBox items = new VBox(10);
        items.getChildren().add(boldLabel("Details:"));
        items.getChildren().add(new Label("Book: " + record.getBookTitle()));
        items.getChildren().add(new Label("ISBN: " + record.getIsbn()));
        items.getChildren().add(new Label("Overdue Days: " + record.getDaysOverdue()));

        Separator sep3 = new Separator();

        HBox totalBox = new HBox();
        totalBox.setAlignment(Pos.CENTER_RIGHT);
        Label totalLbl = new Label("AMOUNT PAID: ");
        totalLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 16px;");
        Label amtLbl = new Label(AppTheme.formatCurrency(amount));
        amtLbl.setStyle("-fx-font-weight: 800; -fx-font-size: 20px; -fx-text-fill: #0D9488;");
        totalBox.getChildren().addAll(totalLbl, amtLbl);

        Label footer = new Label("Thank you for using Library OS.\nPlease keep this receipt for your records.");
        footer.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        footer.setPadding(new Insets(20, 0, 0, 0));
        footer.setAlignment(Pos.CENTER);
        footer.setWrapText(true);

        v.getChildren().addAll(libName, receiptTitle, sep1, info, sep2, items, sep3, totalBox, footer);

        // Ensure all labels in printable node are black
        v.lookupAll(".label").forEach(n -> {
            if (n instanceof Label l) l.setTextFill(javafx.scene.paint.Color.BLACK);
        });
        amtLbl.setTextFill(javafx.scene.paint.Color.web("#0D9488"));

        return v;
    }

    private static Label boldLabel(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight: 700; -fx-text-fill: black;");
        return l;
    }

    public static void emailInvoice(User user, IssueRecord record, double amount, String invoiceId, ToastDisplay toast) {
        if (toast != null) toast.showInfo("Sending invoice to " + user.getEmail() + "...");

        // Generate the invoice attachment (PNG snapshot)
        VBox node = createInvoiceNode(user, record, amount, invoiceId);
        new Scene(node); // Force layout
        WritableImage image = node.snapshot(new SnapshotParameters(), null);

        byte[] attachmentData;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", bos);
            attachmentData = bos.toByteArray();
        } catch (Exception e) {
            attachmentData = null;
        }

        final byte[] finalAttachment = attachmentData;
        new Thread(() -> {
            try {
                ReminderService.sendPaymentInvoice(user, record, amount, invoiceId, finalAttachment, "Invoice_" + invoiceId + ".png");
                Platform.runLater(() -> {
                    if (toast != null) toast.showSuccess("Invoice emailed successfully.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (toast != null) toast.showError("Failed to email invoice: " + e.getMessage());
                });
            }
        }, "invoice-email").start();
    }
}
