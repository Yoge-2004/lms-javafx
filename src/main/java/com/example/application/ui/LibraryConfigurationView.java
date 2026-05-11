package com.example.application.ui;

import com.example.entities.AppConfiguration;
import com.example.services.AppConfigurationService;
import com.example.services.BookService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.Node;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LibraryConfigurationView extends BorderPane {

    private final AppConfiguration config;
    private final Consumer<String> onSave;
    private static int lastSelectedTabIndex = 0;

    private Spinner<Integer> maxBorrowSpin;
    private Spinner<Integer> loanDaysSpin;
    private Spinner<Integer> renewalSpin;
    private Spinner<Double> fineSpin;
    private TextField currSymbolField;
    private TextField currCodeField;
    private TextField libNameField;
    private TextField branchNameField;

    private TextField smtpHostField;

    private ComboBox<Integer> smtpPortCombo;
    private TextField smtpUserField;
    private PasswordField smtpPassField;
    private TextField fromField;
    private CheckBox authCheck;
    private CheckBox tlsCheck;

    private TextField dataDirField;
    private TextField exportDirField;
    private CheckBox dualWriteCheck;
    // storageFormatCombo removed — it did nothing useful (backup always uses .ser)
    private DatabaseConfigurationView dbConfigView;

    public LibraryConfigurationView(AppConfiguration config, Consumer<String> onSave) {
        this.config = config;
        this.onSave = onSave;
        initUI();
    }

    private void initUI() {
        setStyle("-fx-background-color: " + pageBackground() + ";");

        VBox header = new VBox(8);
        header.setPadding(new Insets(28, 28, 20, 28));
        header.setStyle("-fx-background-color: #0F172A;");
        Label title = new Label("Library Configuration");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: white;");
        Label sub = new Label("Global system settings, borrowing rules, and email configuration");
        sub.setStyle("-fx-font-size: 13px; -fx-text-fill: #94A3B8;");
        header.getChildren().addAll(title, sub);
        setTop(header);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("settings-tab-pane");

        dbConfigView = new DatabaseConfigurationView(config.getDatabaseConfiguration());

        tabs.getTabs().addAll(
                new Tab("Rules & Currency", buildRulesPanel()),
                new Tab("Email / SMTP", buildEmailPanel()),
                new Tab("Storage & Data", buildStoragePanel()),
                new Tab("Database", dbConfigView)
        );

        // Assign icons to tabs
        tabs.getTabs().get(0).setGraphic(AppTheme.createIcon(AppTheme.ICON_EDIT, 16));
        tabs.getTabs().get(1).setGraphic(AppTheme.createIcon(AppTheme.ICON_MAIL, 16));
        tabs.getTabs().get(2).setGraphic(AppTheme.createIcon(AppTheme.ICON_SYNC, 16));
        tabs.getTabs().get(3).setGraphic(AppTheme.createIcon(AppTheme.ICON_SETTINGS, 16));

        tabs.getSelectionModel().select(Math.min(lastSelectedTabIndex, tabs.getTabs().size() - 1));
        tabs.getSelectionModel().selectedIndexProperty().addListener((obs, old, val) -> {
            lastSelectedTabIndex = val.intValue();
        });

        VBox content = new VBox(20, tabs);
        content.setPadding(new Insets(20));

        Button saveBtn = new Button("Save Configuration");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setPrefHeight(40);
        saveBtn.setOnAction(e -> handleSave());

        VBox footer = new VBox(saveBtn);
        footer.setPadding(new Insets(20));
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox main = new VBox(tabs, footer);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(main);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent; -fx-background-color:transparent;");
        setCenter(scroll);
    }

    private VBox buildRulesPanel() {
        VBox p = panelContainer();
        p.setAlignment(Pos.TOP_CENTER);

        GridPane g = grid();
        g.setAlignment(Pos.CENTER);

        maxBorrowSpin = spinner(1, 100, BookService.getMaxBorrowLimit());
        loanDaysSpin = spinner(1, 365, BookService.getLoanPeriodDays());
        renewalSpin = spinner(0, 10, 2);
        fineSpin = doubleSpinner(0.0, 1000.0, BookService.getFinePerDay());

        currSymbolField = textField(config.getCurrencySymbol());
        currCodeField = textField(config.getCurrencyCode());

        libNameField = textField(config.getLibraryName());
        branchNameField = textField(config.getBranchName());

        g.addRow(0, label("Library Name:"), libNameField);
        g.addRow(1, label("Branch Name:"), branchNameField);
        g.addRow(2, label("Max Books per User:"), maxBorrowSpin);
        g.addRow(3, label("Loan Period (days):"), loanDaysSpin);
        g.addRow(4, label("Max Renewals:"), renewalSpin);
        g.addRow(5, label("Fine per Day:"), fineSpin);
        g.addRow(6, label("Currency Symbol:"), currSymbolField);
        g.addRow(7, label("Currency Code:"), currCodeField);

        AppTheme.fixSpinner(maxBorrowSpin);
        AppTheme.fixSpinner(loanDaysSpin);
        AppTheme.fixSpinner(renewalSpin);
        AppTheme.fixDoubleSpinner(fineSpin);

        p.getChildren().addAll(sectionHeader("IDENTITY & RULES"), g);
        return p;
    }


    private VBox buildEmailPanel() {
        VBox p = panelContainer();
        GridPane g = grid();

        smtpHostField = textField(config.getSmtpHost());
        smtpPortCombo = new ComboBox<>(FXCollections.observableArrayList(25, 465, 587, 2525));
        smtpPortCombo.setValue(config.getSmtpPort());
        smtpPortCombo.setEditable(true);
        AppTheme.makeNumeric(smtpPortCombo.getEditor());

        smtpUserField = textField(config.getSmtpUsername());
        smtpPassField = new PasswordField();
        smtpPassField.setText(config.getSmtpPassword());
        smtpPassField.setStyle(inputStyle());

        // ── SMTP password row with show/hide toggle ───────────────────
        TextField smtpPassVisible = new TextField(config.getSmtpPassword());
        smtpPassVisible.setStyle(inputStyle());
        smtpPassVisible.setVisible(false);
        smtpPassVisible.setManaged(false);
        smtpPassVisible.textProperty().bindBidirectional(smtpPassField.textProperty());
        Button smtpPassToggle = new Button();
        smtpPassToggle.setGraphic(AppTheme.createIcon(AppTheme.ICON_VISIBILITY, 14));
        smtpPassToggle.setStyle("-fx-background-color:transparent; -fx-cursor:hand; -fx-padding:6;");
        AppTheme.installSmartTooltip(smtpPassToggle, "Show / hide password");
        smtpPassToggle.setOnAction(e -> {
            boolean show = !smtpPassVisible.isVisible();
            smtpPassVisible.setVisible(show);  smtpPassVisible.setManaged(show);
            smtpPassField.setVisible(!show);   smtpPassField.setManaged(!show);
            smtpPassToggle.setGraphic(AppTheme.createIcon(
                    show ? AppTheme.ICON_VISIBILITY_OFF : AppTheme.ICON_VISIBILITY, 14));
        });
        StackPane smtpPassStack = new StackPane(smtpPassField, smtpPassVisible);
        HBox smtpPassRow = new HBox(4, smtpPassStack, smtpPassToggle);
        HBox.setHgrow(smtpPassStack, Priority.ALWAYS);
        smtpPassRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        fromField = textField(config.getFromAddress());
        authCheck = new CheckBox("Enable SMTP Authentication");
        authCheck.setSelected(config.isSmtpAuth());
        tlsCheck = new CheckBox("Enable STARTTLS");
        tlsCheck.setSelected(config.isStartTlsEnabled());

        g.addRow(0, label("SMTP Host:"), smtpHostField);
        g.addRow(1, label("SMTP Port:"), smtpPortCombo);
        g.addRow(2, label("Username:"), smtpUserField);
        g.addRow(3, label("Password:"), smtpPassRow);
        g.addRow(4, label("From Address:"), fromField);
        g.add(authCheck, 0, 5, 2, 1);
        g.add(tlsCheck, 0, 6, 2, 1);

        p.getChildren().addAll(sectionHeader("SMTP CONFIGURATION"), g);
        return p;
    }

    private VBox buildStoragePanel() {
        VBox p = panelContainer();
        GridPane g = grid();

        dataDirField = textField(config.getDataDirectory());
        Button browseDataBtn = new Button("Browse...");
        browseDataBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File f = dc.showDialog(getScene().getWindow());
            if (f != null) dataDirField.setText(f.getAbsolutePath());
        });
        HBox dataDirBox = new HBox(8, dataDirField, browseDataBtn);
        HBox.setHgrow(dataDirField, Priority.ALWAYS);

        exportDirField = textField(config.getExportDirectory());
        Button browseExportBtn = new Button("Browse...");
        browseExportBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File f = dc.showDialog(getScene().getWindow());
            if (f != null) exportDirField.setText(f.getAbsolutePath());
        });
        HBox exportDirBox = new HBox(8, exportDirField, browseExportBtn);
        HBox.setHgrow(exportDirField, Priority.ALWAYS);

        // FIX: Storage format combo removed — it did nothing. The app always uses
        // .ser (Java serialization) files for backup/import. The combo was misleading.

        dualWriteCheck = new CheckBox("Enable Dual-Write (Synchronize local files and database)");
        dualWriteCheck.setSelected(config.getDatabaseConfiguration().isDualWrite());
        dualWriteCheck.setStyle("-fx-font-size: 14px; -fx-text-fill: " + textPrimary() + ";");

        g.addRow(0, label("Data Directory:"), dataDirBox);
        g.addRow(1, label("Export Directory:"), exportDirBox);
        g.add(dualWriteCheck, 0, 2, 2, 1);

        Label dualWriteNote = new Label("When enabled, all changes are written to both the database and local .ser files for maximum redundancy. Backups are always created as .ser files.");
        dualWriteNote.setWrapText(true);
        dualWriteNote.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B; -fx-font-style: italic;");
        g.add(dualWriteNote, 0, 3, 2, 1);

        p.getChildren().addAll(sectionHeader("STORAGE SETTINGS"), g);
        return p;
    }

    private void handleSave() {
        // ── Inline validation before saving ──────────────────────────────────
        List<String> errors = new ArrayList<>();
        String libName = libNameField.getText().trim();
        if (libName.isEmpty()) errors.add("Library name is required.");

        // Email: if any SMTP field is filled, host and from-address must be present
        String smtpHost = smtpHostField.getText().trim();
        String fromAddr = fromField.getText().trim();
        boolean anyEmailFilled = !smtpHost.isEmpty() || !fromAddr.isEmpty()
                || !smtpUserField.getText().trim().isEmpty();
        if (anyEmailFilled) {
            if (smtpHost.isEmpty()) errors.add("SMTP Host is required when configuring email.");
            if (fromAddr.isEmpty()) errors.add("From Address is required when configuring email.");
        }

        // DB config validation is delegated to dbConfigView (if it provides one)
        com.example.entities.DatabaseConfiguration proposedDb = dbConfigView.buildConfigFromUI();
        if (proposedDb != null && proposedDb.getEngine() != com.example.entities.DatabaseConfiguration.Engine.NONE) {
            if (!proposedDb.isConfigured()) {
                errors.add("Database host and database name are required.");
            }
        }

        if (!errors.isEmpty()) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("Configuration Incomplete");
            alert.setHeaderText("Please fix the following before saving:");
            alert.setContentText(String.join("\n", errors));
            com.example.application.ui.AppTheme.applyTheme(alert.getDialogPane());
            alert.showAndWait();
            return;
        }

        try {
            String oldBranchId = config.getBranchId();
            config.setLibraryName(libName);
            config.setBranchName(branchNameField.getText().trim());
            config.setSmtpHost(smtpHostField.getText());
            config.setSmtpPort(smtpPortCombo.getValue());
            config.setSmtpUsername(smtpUserField.getText());
            config.setSmtpPassword(smtpPassField.getText());
            config.setFromAddress(fromField.getText());
            config.setSmtpAuth(authCheck.isSelected());
            config.setStartTlsEnabled(tlsCheck.isSelected());
            config.setCurrencySymbol(currSymbolField.getText());
            config.setCurrencyCode(currCodeField.getText());
            config.setDataDirectory(dataDirField.getText());
            config.setExportDirectory(exportDirField.getText());

            // Sync dual-write setting between tabs
            config.getDatabaseConfiguration().setDualWrite(dualWriteCheck.isSelected());
            config.setDatabaseConfiguration(dbConfigView.buildConfigFromUI());

            AppConfigurationService.updateConfiguration(config);

            // FIX: Update LibrariesDB so the login screen dropdown reflects edited names.
            // BUG: The previous code used addLibrary() which only removes entries whose
            // display-name matches the NEW name. If the library or branch name changed,
            // the old entry was never removed, causing stale duplicates in the dropdown.
            // Fix: explicitly remove the old entry by its stable branchId first, then add
            // the entry with the new names.
            com.example.entities.LibrariesDB.getInstance().removeLibraryById(oldBranchId);
            com.example.entities.LibrariesDB.getInstance().addLibrary(
                    config.getLibraryName(), config.getBranchName(), oldBranchId,
                    config.getDataDirectory(), config.getExportDirectory());

            BookService.updateLibraryConfiguration(
                    maxBorrowSpin.getValue(),
                    loanDaysSpin.getValue(),
                    fineSpin.getValue()
            );

            if (onSave != null) onSave.accept("Configuration saved successfully.");
        } catch (Exception e) {
            // Error handling handled by caller or via toast
        }
    }

    // --- UI Helpers ---
    private VBox panelContainer() {
        VBox v = new VBox(20);
        v.setPadding(new Insets(24));
        return v;
    }

    private GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(20); g.setVgap(14);
        ColumnConstraints c1 = new ColumnConstraints(160);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
        return g;
    }

    private Label sectionHeader(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #14B8A6; -fx-letter-spacing: 1px;");
        return l;
    }

    private Label label(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: " + textPrimary() + ";");
        return l;
    }

    private TextField textField(String v) {
        TextField f = new TextField(v != null ? v : "");
        f.setStyle(inputStyle());
        return f;
    }

    private Spinner<Integer> spinner(int min, int max, int val) {
        Spinner<Integer> s = new Spinner<>(min, max, val);
        s.setEditable(true);
        s.setMaxWidth(Double.MAX_VALUE);
        s.getStyleClass().add("themed-spinner");
        return s;
    }

    private Spinner<Double> doubleSpinner(double min, double max, double val) {
        Spinner<Double> s = new Spinner<>(min, max, val, 0.5);
        s.setEditable(true);
        s.setMaxWidth(Double.MAX_VALUE);
        s.getStyleClass().add("themed-spinner");
        return s;
    }

    private String inputStyle() {
        return "-fx-background-color: " + (AppTheme.darkMode ? "#1E293B" : "#FFFFFF") + "; " +
                "-fx-border-color: " + (AppTheme.darkMode ? "#334155" : "#E2E8F0") + "; " +
                "-fx-border-width: 1.5; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 8 12;";
    }

    private String pageBackground() { return AppTheme.darkMode ? "#0F172A" : "#F8FAFC"; }
    private String textPrimary() { return AppTheme.darkMode ? "#F1F5F9" : "#1E293B"; }

    public void setSelectedTab(int index) {
        if (getCenter() instanceof ScrollPane sp && sp.getContent() instanceof VBox vb) {
            for (Node n : vb.getChildren()) {
                if (n instanceof TabPane tp) {
                    if (index >= 0 && index < tp.getTabs().size()) {
                        tp.getSelectionModel().select(index);
                    }
                    break;
                }
            }
        }
    }
}
