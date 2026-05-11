package com.example.application.ui;

import com.example.entities.DatabaseConfiguration;
import com.example.entities.DatabaseConfiguration.Engine;
import com.example.services.DatabaseConnectionService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Dialog for configuring optional database persistence.
 * Opened from Settings → Library Configuration → Database tab.
 *
 * <p>This dialog allows users to switch between local file storage (Engine.NONE) 
 * and external databases (SQLite, MySQL, PostgreSQL, etc.). It supports 
 * connection testing and Dual-Write synchronization configuration.</p>
 * 
 * @author Yogesh
 */
public final class DatabaseConfigurationDialog {

    private DatabaseConfigurationDialog() {}

    /**
     * Shows the database configuration dialog and blocks until the user confirms or cancels.
     * 
     * @param owner The parent stage for modal ownership.
     * @param current The current database configuration to populate the fields.
     * @return An Optional containing the new configuration if the user clicked OK, or empty otherwise.
     */
    public static Optional<DatabaseConfiguration> show(Stage owner, DatabaseConfiguration current) {
        Dialog<DatabaseConfiguration> dialog = new Dialog<>();
        dialog.setTitle("Database Configuration");
        dialog.initOwner(owner);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        AppTheme.applyTheme(pane);
        pane.setPrefWidth(560);
        pane.setMinWidth(480);

        dialog.setOnShown(evt -> {
            if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage st) {
                AppTheme.applyWindowIcon(st);
                st.setMinWidth(480);
                st.setMinHeight(500);
                st.sizeToScene();
                st.centerOnScreen();
            }
        });

        DatabaseConfiguration cfg = current != null ? current : new DatabaseConfiguration();

        VBox root = new VBox(20);
        root.setPadding(new Insets(24));

        // ── Title ────────────────────────────────────────────────────
        Label title = new Label("Database Persistence");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: " + textPrimary() + ";");

        Label desc = new Label(
                "Library OS stores data in local files by default. " +
                        "Optionally connect to a database for shared or server-backed persistence. " +
                        "When Dual-Write is enabled, both file and database are kept in sync.");
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 13px; -fx-text-fill: " + textMuted() + ";");

        // ── Engine picker ─────────────────────────────────────────────
        Label engineLabel = gridLabel("Database Engine:");

        ComboBox<Engine> engineCombo = new ComboBox<>(
                FXCollections.observableArrayList(Engine.values()));
        engineCombo.setMaxWidth(Double.MAX_VALUE);
        engineCombo.setStyle(inputStyle());
        engineCombo.setValue(cfg.getEngine());
        engineCombo.setButtonCell(new EngineCell());
        engineCombo.setCellFactory(lv -> new EngineCell());

        // ── SQLite section ────────────────────────────────────────────
        VBox sqliteSection = new VBox(10);
        Label sqliteFileLabel = gridLabel("SQLite File:");
        TextField sqliteFileField = inputTF(cfg.getSqliteFile(), "library.db");
        Button sqliteBrowse = browseFileBtn("Choose SQLite file", sqliteFileField, owner);
        HBox sqliteRow = new HBox(8, sqliteFileField, sqliteBrowse);
        HBox.setHgrow(sqliteFileField, Priority.ALWAYS);
        sqliteSection.getChildren().addAll(sqliteFileLabel, sqliteRow);

        // ── Remote section ────────────────────────────────────────────
        VBox remoteSection = new VBox(12);

        GridPane remoteGrid = new GridPane();
        remoteGrid.setHgap(14);
        remoteGrid.setVgap(12);
        ColumnConstraints c0 = new ColumnConstraints(140);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        remoteGrid.getColumnConstraints().addAll(c0, c1);

        TextField hostField = inputTF(cfg.getHost(), "localhost");
        TextField portField = inputTF(cfg.getPort() > 0 ? String.valueOf(cfg.getPort()) : "", "3306");
        AppTheme.makeNumeric(portField);
        TextField dbField   = inputTF(cfg.getDatabase(), "libraryos");
        TextField userField = inputTF(cfg.getUsername(), "db_user");
        PasswordField passField = new PasswordField();
        passField.setStyle(inputStyle());
        passField.setText(cfg.getPassword());
        passField.setPromptText("Database password");
        CheckBox sslCheck = new CheckBox("Require SSL/TLS");
        sslCheck.setSelected(cfg.isSslEnabled());

        remoteGrid.addRow(0, gridLabel("Host:"),     hostField);
        remoteGrid.addRow(1, gridLabel("Port:"),     portField);
        remoteGrid.addRow(2, gridLabel("Database:"), dbField);
        remoteGrid.addRow(3, gridLabel("Username:"), userField);
        remoteGrid.addRow(4, gridLabel("Password:"), passField);
        remoteGrid.add(sslCheck, 0, 5, 2, 1);

        remoteSection.getChildren().add(remoteGrid);

        // ── Connection options ─────────────────────────────────────────
        Separator sep = new Separator();
        Label optTitle = new Label("Connection Options");
        optTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: " + textPrimary() + ";");

        GridPane optGrid = new GridPane();
        optGrid.setHgap(14);
        optGrid.setVgap(12);
        optGrid.getColumnConstraints().addAll(new ColumnConstraints(140), c1);

        Spinner<Integer> timeoutSpin = new Spinner<>(1, 60, cfg.getConnectionTimeout());
        timeoutSpin.setEditable(true);
        timeoutSpin.getStyleClass().add("themed-spinner");
        timeoutSpin.setMaxWidth(Double.MAX_VALUE);
        AppTheme.fixSpinner(timeoutSpin);

        Spinner<Integer> poolSpin = new Spinner<>(1, 20, cfg.getMaxPoolSize());
        poolSpin.setEditable(true);
        poolSpin.getStyleClass().add("themed-spinner");
        poolSpin.setMaxWidth(Double.MAX_VALUE);
        AppTheme.fixSpinner(poolSpin);

        CheckBox dualWriteCheck = new CheckBox("Dual-Write (sync file + database)");
        dualWriteCheck.setSelected(cfg.isDualWrite());

        optGrid.addRow(0, gridLabel("Timeout (s):"),   timeoutSpin);
        optGrid.addRow(1, gridLabel("Pool size:"),     poolSpin);
        optGrid.add(dualWriteCheck, 0, 2, 2, 1);

        // ── Status / test ──────────────────────────────────────────────
        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setStyle("-fx-font-size: 13px;");
        statusLabel.setVisible(false);

        Button testBtn = new Button("Test Connection");
        testBtn.setStyle("-fx-background-color: #0D9488; -fx-text-fill: white; " +
                "-fx-font-weight: 600; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8 18;");
        testBtn.setMaxWidth(Double.MAX_VALUE);
        AppTheme.installSmartTooltip(testBtn, "Verify connectivity to the configured database");

        // ── Visibility logic ───────────────────────────────────────────
        Runnable applyEngineVisibility = () -> {
            Engine e = engineCombo.getValue();
            boolean showSqlite  = e == Engine.SQLITE;
            boolean showRemote  = e != null && e != Engine.NONE && e != Engine.SQLITE;
            boolean showTest    = e != null && e != Engine.NONE;

            sqliteSection.setVisible(showSqlite);  sqliteSection.setManaged(showSqlite);
            remoteSection.setVisible(showRemote);  remoteSection.setManaged(showRemote);
            testBtn.setVisible(showTest);          testBtn.setManaged(showTest);

            // Pre-fill default port when engine changes
            if (showRemote && portField.getText().isBlank()) {
                portField.setText(String.valueOf(e.defaultPort()));
            }
        };

        engineCombo.valueProperty().addListener((o, ov, nv) -> applyEngineVisibility.run());
        applyEngineVisibility.run();

        testBtn.setOnAction(evt -> {
            statusLabel.setVisible(false);
            testBtn.setDisable(true);
            testBtn.setText("Testing\u2026");

            DatabaseConfiguration probe = buildConfig(engineCombo, sqliteFileField,
                    hostField, portField, dbField, userField, passField,
                    sslCheck, timeoutSpin, poolSpin, dualWriteCheck);

            new Thread(() -> {
                String error = DatabaseConnectionService.testConnection(probe);
                Platform.runLater(() -> {
                    testBtn.setDisable(false);
                    testBtn.setText("Test Connection");
                    statusLabel.setVisible(true);
                    if (error == null) {
                        statusLabel.setText("\u2713 Connection successful!");
                        statusLabel.setStyle("-fx-font-size:13px; -fx-text-fill:#16A34A;");
                    } else {
                        statusLabel.setText("\u2717 " + error);
                        statusLabel.setStyle("-fx-font-size:13px; -fx-text-fill:#DC2626;");
                    }
                });
            }, "db-test").start();
        });

        root.getChildren().addAll(
                title, desc,
                engineLabel, engineCombo,
                sqliteSection, remoteSection,
                sep, optTitle, optGrid,
                testBtn, statusLabel);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background:transparent; -fx-background-color:transparent;");

        pane.setContent(scroll);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
        Button cancelBtn = (Button) pane.lookupButton(ButtonType.CANCEL);
        okBtn.setStyle("-fx-background-color:#0D9488; -fx-text-fill:white; -fx-font-weight:600; " +
                "-fx-font-size:14px; -fx-background-radius:10px; -fx-padding:10 24;");
        if (cancelBtn != null) {
            cancelBtn.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "#334155" : "#E5E7EB") + "; " +
                    "-fx-text-fill:" + (AppTheme.darkMode ? "#F8FAFC" : "#1F2937") + "; " +
                    "-fx-font-weight:600; -fx-font-size:14px; -fx-background-radius:10px; -fx-padding:10 20;");
        }

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            return buildConfig(engineCombo, sqliteFileField,
                    hostField, portField, dbField, userField, passField,
                    sslCheck, timeoutSpin, poolSpin, dualWriteCheck);
        });

        return dialog.showAndWait();
    }

    /**
     * Internal helper to construct a DatabaseConfiguration object from current UI field states.
     * 
     * @return A populated DatabaseConfiguration instance.
     */
    private static DatabaseConfiguration buildConfig(
            ComboBox<Engine> engineCombo, TextField sqliteFileField,
            TextField hostField, TextField portField, TextField dbField,
            TextField userField, PasswordField passField,
            CheckBox sslCheck, Spinner<Integer> timeoutSpin,
            Spinner<Integer> poolSpin, CheckBox dualWriteCheck) {

        DatabaseConfiguration c = new DatabaseConfiguration();
        c.setEngine(engineCombo.getValue());
        c.setSqliteFile(sqliteFileField.getText().trim());
        c.setHost(hostField.getText().trim());

        try { c.setPort(Integer.parseInt(portField.getText().trim())); }
        catch (NumberFormatException ignored) { c.setPort(0); }

        c.setDatabase(dbField.getText().trim());
        c.setUsername(userField.getText().trim());
        c.setPassword(passField.getText());
        c.setSslEnabled(sslCheck.isSelected());
        c.setConnectionTimeout(timeoutSpin.getValue());
        c.setMaxPoolSize(poolSpin.getValue());
        c.setDualWrite(dualWriteCheck.isSelected());
        return c;
    }

    /** Creates a styled TextField with a prompt and default value. */
    private static TextField inputTF(String val, String prompt) {
        TextField f = new TextField(val != null ? val : "");
        f.setPromptText(prompt);
        f.setStyle(inputStyle());
        return f;
    }

    /** Creates a bold labeled intended for grid headers. */
    private static Label gridLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:13px; -fx-font-weight:600; -fx-text-fill:" + textPrimary() + ";");
        return l;
    }

    /** Creates a browse button that opens a FileChooser for SQLite selection. */
    private static Button browseFileBtn(String title, TextField target, Stage owner) {
        Button b = new Button("Browse\u2026");
        b.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "#334155" : "#E2E8F0") + "; " +
                "-fx-text-fill:" + (AppTheme.darkMode ? "#F1F5F9" : "#1F2937") + "; " +
                "-fx-background-radius:8px; -fx-cursor:hand; -fx-padding:8 14; -fx-font-weight:600;");
        AppTheme.installSmartTooltip(b, "Browse for a SQLite database file (.db / .sqlite)");
        b.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle(title);
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite database", "*.db", "*.sqlite"));
            java.io.File f2 = fc.showOpenDialog(owner);
            if (f2 != null) target.setText(f2.getAbsolutePath());
        });
        return b;
    }

    /** Utility for shared input field styling. */
    private static String inputStyle() {
        if (AppTheme.darkMode) {
            return "-fx-background-color:#1E293B; -fx-border-color:#334155; " +
                    "-fx-border-width:1.5; -fx-border-radius:10px; -fx-background-radius:10px; " +
                    "-fx-padding:9 12; -fx-font-size:14px; -fx-text-fill:#E2E8F0;";
        }
        return "-fx-background-color:#F9FAFB; -fx-border-color:#D1D5DB; " +
                "-fx-border-width:1.5; -fx-border-radius:10px; -fx-background-radius:10px; " +
                "-fx-padding:9 12; -fx-font-size:14px;";
    }

    /** Returns primary text color based on active theme. */
    private static String textPrimary() {
        return AppTheme.darkMode ? "#F8FAFC" : "#0F172A";
    }

    /** Returns muted text color based on active theme. */
    private static String textMuted() {
        return AppTheme.darkMode ? "#94A3B8" : "#64748B";
    }

    /** ListCell that shows Engine.displayName instead of enum name for professional presentation. */
    private static class EngineCell extends ListCell<Engine> {
        @Override
        protected void updateItem(Engine item, boolean empty) {
            super.updateItem(item, empty);
            setText(item == null || empty ? null : item.getDisplayName());
        }
    }
}