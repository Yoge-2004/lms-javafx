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

/**
 * View component for database configuration.
 * Extends VBox to be used within a Tab.
 */
public class DatabaseConfigurationView extends VBox {

    private final DatabaseConfiguration cfg;

    private ComboBox<Engine> engineCombo;
    private TextField sqliteFileField;
    private TextField hostField;
    private TextField portField;
    private TextField dbField;
    private TextField userField;
    private PasswordField passField;
    private CheckBox sslCheck;
    private Spinner<Integer> timeoutSpin;
    private Spinner<Integer> poolSpin;


    private Label statusLabel;
    private Button testBtn;

    public DatabaseConfigurationView(DatabaseConfiguration current) {
        this.cfg = current != null ? current : new DatabaseConfiguration();
        setSpacing(20);
        setPadding(new Insets(24));
        initUI();
    }

    private void initUI() {
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

        engineCombo = new ComboBox<>(FXCollections.observableArrayList(Engine.values()));
        engineCombo.setMaxWidth(Double.MAX_VALUE);
        engineCombo.setStyle(inputStyle());
        engineCombo.setValue(cfg.getEngine());
        engineCombo.setButtonCell(new EngineCell());
        engineCombo.setCellFactory(lv -> new EngineCell());

        // ── SQLite section ────────────────────────────────────────────
        VBox sqliteSection = new VBox(10);
        Label sqliteFileLabel = gridLabel("SQLite File:");
        sqliteFileField = inputTF(cfg.getSqliteFile(), "library.db");
        Button sqliteBrowse = browseFileBtn("Choose SQLite file", sqliteFileField);
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

        hostField = inputTF(cfg.getHost(), "localhost");
        portField = inputTF(cfg.getPort() > 0 ? String.valueOf(cfg.getPort()) : "", "3306");
        AppTheme.makeNumeric(portField);
        dbField   = inputTF(cfg.getDatabase(), "libraryos");
        userField = inputTF(cfg.getUsername(), "db_user");
        passField = new PasswordField();
        passField.setStyle(inputStyle());
        passField.setText(cfg.getPassword());
        passField.setPromptText("Database password");
        sslCheck = new CheckBox("Require SSL/TLS");
        sslCheck.setSelected(cfg.isSslEnabled());

        // ── Password row with show/hide toggle ────────────────────────
        TextField passVisible = new TextField(cfg.getPassword());
        passVisible.setStyle(inputStyle());
        passVisible.setPromptText("Database password");
        passVisible.setVisible(false);
        passVisible.setManaged(false);
        passVisible.textProperty().bindBidirectional(passField.textProperty());
        Button passToggle = new Button();
        passToggle.setGraphic(AppTheme.createIcon(AppTheme.ICON_VISIBILITY, 14));
        passToggle.setStyle("-fx-background-color:transparent; -fx-cursor:hand; -fx-padding:6;");
        AppTheme.installSmartTooltip(passToggle, "Show / hide password");
        passToggle.setOnAction(e -> {
            boolean show = !passVisible.isVisible();
            passVisible.setVisible(show); passVisible.setManaged(show);
            passField.setVisible(!show);  passField.setManaged(!show);
            passToggle.setGraphic(AppTheme.createIcon(
                    show ? AppTheme.ICON_VISIBILITY_OFF : AppTheme.ICON_VISIBILITY, 14));
        });
        StackPane passStack = new StackPane(passField, passVisible);
        HBox passRow = new HBox(4, passStack, passToggle);
        HBox.setHgrow(passStack, Priority.ALWAYS);
        passRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        remoteGrid.addRow(0, gridLabel("Host:"),     hostField);
        remoteGrid.addRow(1, gridLabel("Port:"),     portField);
        remoteGrid.addRow(2, gridLabel("Database:"), dbField);
        remoteGrid.addRow(3, gridLabel("Username:"), userField);
        remoteGrid.addRow(4, gridLabel("Password:"), passRow);
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

        timeoutSpin = new Spinner<>(1, 60, cfg.getConnectionTimeout());
        timeoutSpin.setEditable(true);
        timeoutSpin.getStyleClass().add("themed-spinner");
        timeoutSpin.setMaxWidth(Double.MAX_VALUE);
        AppTheme.fixSpinner(timeoutSpin);

        poolSpin = new Spinner<>(1, 20, cfg.getMaxPoolSize());
        poolSpin.setEditable(true);
        poolSpin.getStyleClass().add("themed-spinner");
        poolSpin.setMaxWidth(Double.MAX_VALUE);
        AppTheme.fixSpinner(poolSpin);

        optGrid.addRow(0, gridLabel("Timeout (s):"),   timeoutSpin);
        optGrid.addRow(1, gridLabel("Pool size:"),     poolSpin);

        // ── Status / test ──────────────────────────────────────────────
        statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setStyle("-fx-font-size: 13px;");
        statusLabel.setVisible(false);

        testBtn = new Button("Test Connection");
        testBtn.setStyle("-fx-background-color: #0D9488; -fx-text-fill: white; " +
                "-fx-font-weight: 600; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8 18;");
        testBtn.setMaxWidth(Double.MAX_VALUE);

        testBtn.setOnAction(evt -> handleTestConnection());

        // ── Visibility logic ───────────────────────────────────────────
        Runnable applyEngineVisibility = () -> {
            Engine e = engineCombo.getValue();
            boolean showSqlite  = e == Engine.SQLITE;
            boolean showRemote  = e != null && e != Engine.NONE && e != Engine.SQLITE;
            boolean showTest    = e != null && e != Engine.NONE;

            sqliteSection.setVisible(showSqlite);  sqliteSection.setManaged(showSqlite);
            remoteSection.setVisible(showRemote);  remoteSection.setManaged(showRemote);
            testBtn.setVisible(showTest);          testBtn.setManaged(showTest);

            if (showRemote && portField.getText().isBlank()) {
                portField.setText(String.valueOf(e.defaultPort()));
            }
        };

        engineCombo.valueProperty().addListener((o, ov, nv) -> applyEngineVisibility.run());
        applyEngineVisibility.run();

        getChildren().addAll(
                title, desc,
                engineLabel, engineCombo,
                sqliteSection, remoteSection,
                sep, optTitle, optGrid,
                testBtn, statusLabel);
    }

    private void handleTestConnection() {
        statusLabel.setVisible(false);
        testBtn.setDisable(true);
        testBtn.setText("Testing…");

        DatabaseConfiguration probe = buildConfigFromUI();

        new Thread(() -> {
            String error = DatabaseConnectionService.testConnection(probe);
            Platform.runLater(() -> {
                testBtn.setDisable(false);
                testBtn.setText("Test Connection");
                statusLabel.setVisible(true);
                if (error == null) {
                    statusLabel.setText("✓ Connection successful!");
                    statusLabel.setStyle("-fx-font-size:13px; -fx-text-fill:#16A34A;");
                } else {
                    statusLabel.setText("✗ " + error);
                    statusLabel.setStyle("-fx-font-size:13px; -fx-text-fill:#DC2626;");
                }
            });
        }, "db-test").start();
    }

    public DatabaseConfiguration buildConfigFromUI() {
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
        return c;
    }

    private TextField inputTF(String val, String prompt) {
        TextField f = new TextField(val != null ? val : "");
        f.setPromptText(prompt);
        f.setStyle(inputStyle());
        return f;
    }

    private Label gridLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:13px; -fx-font-weight:600; -fx-text-fill:" + textPrimary() + ";");
        return l;
    }

    private Button browseFileBtn(String title, TextField target) {
        Button b = new Button("Browse…");
        b.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "#334155" : "#E2E8F0") + "; " +
                "-fx-text-fill:" + (AppTheme.darkMode ? "#F1F5F9" : "#1F2937") + "; " +
                "-fx-background-radius:8px; -fx-cursor:hand; -fx-padding:8 14; -fx-font-weight:600;");
        b.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle(title);
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite database", "*.db", "*.sqlite"));
            java.io.File f2 = fc.showOpenDialog(getScene().getWindow());
            if (f2 != null) target.setText(f2.getAbsolutePath());
        });
        return b;
    }

    private String inputStyle() {
        return "-fx-background-color: " + (AppTheme.darkMode ? "#1E293B" : "#F9FAFB") + "; " +
                "-fx-border-color: " + (AppTheme.darkMode ? "#334155" : "#D1D5DB") + "; " +
                "-fx-border-width: 1.5; -fx-border-radius: 10px; -fx-background-radius: 10px; " +
                "-fx-padding: 9 12; -fx-font-size: 14px; -fx-text-fill: " + textPrimary() + ";";
    }

    private String textPrimary() { return AppTheme.darkMode ? "#F8FAFC" : "#0F172A"; }
    private String textMuted() { return AppTheme.darkMode ? "#94A3B8" : "#64748B"; }

    private static class EngineCell extends ListCell<Engine> {
        @Override
        protected void updateItem(Engine item, boolean empty) {
            super.updateItem(item, empty);
            setText(item == null || empty ? null : item.getDisplayName());
        }
    }
}