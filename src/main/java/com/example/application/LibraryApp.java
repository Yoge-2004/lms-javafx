package com.example.application;

import com.example.application.ui.*;
import com.example.entities.*;
import com.example.entities.BooksDB.IssueRecord;
import com.example.services.*;
import com.example.storage.AppPaths;
import com.example.storage.DataStorage;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.*;

/**
 * Main application class for Library OS.
 *
 * <p><b>Architecture</b>: {@link LibrariesDB} is the <em>only</em> globally shared
 * singleton.  It is loaded once at startup so the login-screen library dropdown is
 * always populated, even before any user has logged in.
 *
 * <p>All other singletons — {@link AppConfiguration}, {@link BooksDB},
 * {@link UsersDB}, borrow-requests, etc. — are <em>per-library</em> and are loaded
 * lazily only after the user has selected a library and authenticated.
 */
public class LibraryApp extends Application implements ToastDisplay {

    private static final Logger LOG = Logger.getLogger(LibraryApp.class.getName());
    private static final String DESKTOP_APP_ID = "com.example.application.LibraryApp";

    static {
        configureDesktopIdentity();
    }

    public static void configureDesktopIdentity() {
        // ── WM_CLASS / glass name (X11 + Wayland) ──────────────────────────
        // glass.gtk.name sets the WM_CLASS instance name; must match
        // StartupWMClass in the .desktop file for GNOME/KDE to link the
        // window to the correct taskbar entry and icon.
        System.setProperty("glass.gtk.name", DESKTOP_APP_ID);
        System.setProperty("glass.gtk.application.name", DESKTOP_APP_ID);
        // Newer JavaFX glass property (JavaFX 21+)
        System.setProperty("com.sun.javafx.wm.class", DESKTOP_APP_ID);
        System.setProperty("glass.wm.class", DESKTOP_APP_ID);

        // ── Human-readable app name shown in task managers ─────────────────
        System.setProperty("javafx.application.name", "Library OS");
        System.setProperty("com.sun.javafx.application.name", "Library OS");

        // ── AWT app name (macOS dock label + some Linux taskbar integrations)
        System.setProperty("apple.awt.application.name", "Library OS");

        // ── Wayland/GTK: XDG app-id so the compositor maps us to the correct icon.
        // These MUST be set before the JavaFX toolkit initializes.
        System.setProperty("javafx.glass.gtk.id", DESKTOP_APP_ID);

        // ── X11: appClassName for window manager identification
        System.setProperty("sun.awt.X11.XToolkit.appClassName", DESKTOP_APP_ID);

        // ── Wayland/GNOME Dev Mode Taskbar Icon Hack ─────────────────────────
        setupLinuxDevDesktopEntry();
    }

    private static void setupLinuxDevDesktopEntry() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("nix") && !os.contains("nux") && !os.contains("aix")) return;

        // Skip dev-mode shortcut if running from the official installation path
        String path = LibraryApp.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path.contains("/opt/libraryos")) {
            LOG.info("Running from /opt/libraryos - skipping development shortcut setup.");
            return;
        }


        try {
            String userHome = System.getProperty("user.home");
            java.io.File appDir = new java.io.File(userHome, ".local/share/applications");
            java.io.File iconDir = new java.io.File(userHome, ".local/share/icons/hicolor/256x256/apps");
            if (!appDir.exists()) appDir.mkdirs();
            if (!iconDir.exists()) iconDir.mkdirs();

            // Name must match the app_id / StartupWMClass for Wayland
            String iconName = DESKTOP_APP_ID;
            java.io.File iconFile = new java.io.File(iconDir, iconName + ".png");

            // Write icon
            try (java.io.InputStream is = LibraryApp.class.getResourceAsStream("/icon-256.png")) {
                if (is != null) {
                    java.nio.file.Files.copy(is, iconFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }

            java.io.File desktopFile = new java.io.File(appDir, DESKTOP_APP_ID + ".desktop");

            // Write desktop file using ABSOLUTE path for the icon to ensure immediate visibility
            // in Zorin/GNOME without waiting for icon cache updates.
            String desktopContent = "[Desktop Entry]\n" +
                    "Version=1.0\n" +
                    "Type=Application\n" +
                    "Name=Library OS (Dev)\n" +
                    "GenericName=Library Management System\n" +
                    "Comment=Development mode launcher for Library OS\n" +
                    "Icon=" + iconFile.getAbsolutePath() + "\n" +
                    "Exec=/usr/bin/env true\n" +
                    "Terminal=false\n" +
                    "NoDisplay=false\n" +
                    "Categories=Office;Education;Utility;\n" +
                    "StartupNotify=true\n" +
                    "StartupWMClass=" + DESKTOP_APP_ID + "\n";
            java.nio.file.Files.writeString(desktopFile.toPath(), desktopContent);
            
            // Force GNOME/Zorin to refresh its applications and icons cache immediately
            try {
                Runtime.getRuntime().exec(new String[]{"gtk-update-icon-cache", "-f", "-t", new java.io.File(userHome, ".local/share/icons/hicolor").getAbsolutePath()});
                Runtime.getRuntime().exec(new String[]{"update-desktop-database", appDir.getAbsolutePath()});
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    // Shell
    private Stage     primaryStage;
    private StackPane rootStack;
    private StackPane contentArea;
    private VBox      sidebar;
    private Label     statusLabel;
    private Label     footerLibraryLabel;
    private ProgressIndicator loadingIndicator;
    private Label     userNameLabel;
    private Label     userRoleLabel;
    private Label     headerTitleLabel;
    private Button    activeNavBtn;
    private Button    backBtn;
    private HBox      activeToast      = null;
    private SequentialTransition activeToastAnim = null;

    // Session state
    private static LibraryApp instance;
    private String   currentUser         = null;
    private String   currentUserPassword = null;
    private UserRole currentUserRole     = UserRole.USER;

    public static void updateSessionPassword(String newPassword) {
        if (instance != null) instance.currentUserPassword = newPassword;
    }

    private Timeline autoRefreshTimer;
    private final Stack<Region> navigationHistory = new Stack<>();

    // Per-session observable lists
    private final ObservableList<Book>                booksList    = FXCollections.observableArrayList();
    private final ObservableList<IssueRecord>         issuesList   = FXCollections.observableArrayList();
    private final ObservableList<BorrowRequest>       requestsList = FXCollections.observableArrayList();
    private final ObservableList<BooksDB.InvoiceData> historyList  = FXCollections.observableArrayList();

    // Cached per-session view instances
    private AnalyticsDashboard analyticsDashboard;
    private CatalogView        catalogView;
    private CirculationView    circulationView;

    private enum SetupAccess { STAFF, USER }

    // =========================================================================
    // Application lifecycle
    // =========================================================================

    // =========================================================================
    // Tarball first-run integration (Arch / generic Linux .tar.xz)
    // =========================================================================

    /**
     * On the very first launch from a {@code .tar.xz} extraction, runs
     * {@code postextract.sh} on a background thread so the app icon and
     * {@code .desktop} entry are registered in the user's
     * {@code ~/.local/share} directories — regardless of whether the user
     * opened the binary directly from a file manager or via the terminal
     * wrapper script.
     *
     * <p>The method is a no-op on Windows/macOS, when running from the
     * official {@code /opt/libraryos} installation, or when running from
     * a development class-path (Maven / IDE).
     */
    private static void runTarballIntegrationIfNeeded() {
        // ── 1. Linux only ─────────────────────────────────────────────────────
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("nix") && !os.contains("nux") && !os.contains("aix")) return;

        // ── 2. Resolve where *this* binary lives ──────────────────────────────
        String codePath = LibraryApp.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();

        // Skip: official deb/rpm installation at /opt/libraryos
        if (codePath.contains("/opt/libraryos")) return;

        // Skip: running straight from Maven target/ or an IDE class-path
        if (codePath.endsWith(".class") || codePath.contains("/target/classes")) return;

        // ── 3. Check the integration marker ───────────────────────────────────
        Path marker = Paths.get(System.getProperty("user.home"),
                ".local", "share", "libraryos", ".integrated");
        if (Files.exists(marker)) return;   // already integrated — nothing to do

        // ── 4. Locate postextract.sh relative to the running binary ───────────
        // jpackage app-image layout:  <root>/lib/app/library-os-full.jar
        //                             <root>/bin/LibraryOS
        //                             <root>/postextract.sh   ← our script
        // codePath points at the JAR, so go up three levels to reach <root>.
        Path jarPath     = Paths.get(codePath);         // …/lib/app/library-os-full.jar
        Path installRoot = jarPath.getParent()           // …/lib/app
                                  .getParent()           // …/lib
                                  .getParent();          // <root>

        Path script = installRoot.resolve("postextract.sh");
        if (!Files.exists(script)) {
            Logger.getLogger(LibraryApp.class.getName())
                  .warning("[tarball] postextract.sh not found at " + script
                           + " — skipping desktop integration.");
            return;
        }

        // ── 5. Run postextract.sh on a daemon thread (non-blocking) ───────────
        Logger.getLogger(LibraryApp.class.getName())
              .info("[tarball] First run detected — running desktop integration: " + script);

        Thread integrationThread = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "sh", script.toAbsolutePath().toString(),
                        installRoot.toAbsolutePath().toString());
                pb.redirectErrorStream(true);   // merge stderr → stdout
                Process proc = pb.start();

                // Drain output so the process doesn't block on a full pipe
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(proc.getInputStream()))) {
                    Logger logger = Logger.getLogger(LibraryApp.class.getName());
                    reader.lines().forEach(line -> logger.info("[postextract] " + line));
                }

                int exit = proc.waitFor();
                if (exit != 0) {
                    Logger.getLogger(LibraryApp.class.getName())
                          .warning("[tarball] postextract.sh exited with code " + exit);
                } else {
                    // Write marker so we never run integration again
                    Files.createDirectories(marker.getParent());
                    Files.writeString(marker,
                            "integrated on " + java.time.Instant.now() + "\n");
                    Logger.getLogger(LibraryApp.class.getName())
                          .info("[tarball] Desktop integration complete.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Logger.getLogger(LibraryApp.class.getName())
                      .log(Level.WARNING, "[tarball] Desktop integration failed", e);
            }
        }, "libraryos-tarball-integration");
        integrationThread.setDaemon(true);  // must not prevent JVM exit
        integrationThread.start();
    }

    // =========================================================================
    // Application lifecycle
    // =========================================================================

    @Override
    public void start(Stage stage) {
        runTarballIntegrationIfNeeded();   // no-op on deb/rpm/windows; first-run only
        configureDesktopIdentity();
        instance = this;
        LoggingConfigurator.configure();
        this.primaryStage = stage;
        stage.setUserData(this);
        stage.setTitle("Library OS");

        javafx.geometry.Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double w = screen.getWidth() * 0.95, h = screen.getHeight() * 0.95;
        stage.setMinWidth(Math.min(900, w));
        stage.setMinHeight(Math.min(600, h));

        rootStack = new StackPane();
        stage.setScene(AppTheme.createScene(rootStack, w, h));
        AppTheme.applyWindowIcon(stage);
        Platform.runLater(() -> AppTheme.runLater(() -> AppTheme.applyWindowIcon(stage), 1500));

        // Step 1: Bootstrap global state (LibrariesDB only — no per-library data yet)
        bootstrapGlobalState();

        // Step 2: Apply theme and show login or first-run wizard
        AppConfiguration cfg = AppConfigurationService.getConfiguration();
        AppTheme.darkMode = cfg.isDarkMode();
        if (cfg.isDarkMode()) applyDarkMode(true);

        if (cfg.isInitialSetupDone()) {
            showLoginScreen();
        } else {
            rootStack.getChildren().setAll(new StackPane());
        }

        stage.show();
        AppTheme.applyWindowIcon(stage);
        stage.centerOnScreen();
        stage.setOnCloseRequest(e -> shutdown());
        if (!cfg.isInitialSetupDone()) Platform.runLater(this::showSetupWizard);
    }

    /**
     * Bootstraps only global state needed before login.
     * Only {@link LibrariesDB} is loaded here. Everything else is per-library
     * and is loaded after the user selects a branch and authenticates.
     */
    private void bootstrapGlobalState() {
        AppConfiguration cfg = AppConfigurationService.getConfiguration();
        SecurityProvider.setLibraryMasterKey(cfg.getLibraryMasterKey());

        DatabaseConfiguration dbCfg = cfg.getDatabaseConfiguration();
        if (dbCfg != null && dbCfg.isConfigured()) {
            boolean connected = DatabaseConnectionService.connect(dbCfg);
            if (connected) {
                LOG.info("DB connected at startup. Pulling LibrariesDB snapshot...");
                LibrariesDB.getInstance().forceReload();
                LibrariesDB.getInstance().save();
                AppConfigurationService.refreshSetupStateFromLibraries();
            } else {
                LOG.warning("DB configured but unreachable — using local LibrariesDB.");
                String loc = dbCfg.getEngine() == DatabaseConfiguration.Engine.SQLITE
                        ? dbCfg.getSqliteFile()
                        : dbCfg.getHost() + ":" + dbCfg.getPort();
                Platform.runLater(() -> showDatabaseConnectionError(dbCfg.getEngine().getDisplayName(), loc));
            }
        } else {
            DatabaseConnectionService.disconnect();
        }

        // Always register the locally-configured library so the dropdown is never empty
        // on first launch or when the DB is unreachable.
        if (cfg.isInitialSetupDone() && isConfiguredLibraryIdentity(cfg)) {
            LibrariesDB.getInstance().addLibrary(
                    cfg.getLibraryName(), cfg.getBranchName(), cfg.getBranchId(),
                    cfg.getDataDirectory(), cfg.getExportDirectory());
            AppConfigurationService.refreshSetupStateFromLibraries();
        }
    }

    private boolean isConfiguredLibraryIdentity(AppConfiguration cfg) {
        return cfg != null
                && cfg.getLibraryName() != null
                && !cfg.getLibraryName().isBlank()
                && !"Select Library".equalsIgnoreCase(cfg.getLibraryName());
    }

    private void showDatabaseConnectionError(String engineName, String location) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Database Connection Failed");
        alert.setHeaderText("Could not connect to " + engineName);
        alert.setContentText(
                "Library OS could not reach the configured database:\n\n"
                        + "  Engine : " + engineName + "\n"
                        + "  Address: " + location + "\n\n"
                        + "The application will run in local file-storage mode.\n\n"
                        + "To fix this, go to Settings → Library Configuration → Database.");
        alert.initOwner(primaryStage);
        if (alert.getDialogPane() != null) AppTheme.applyTheme(alert.getDialogPane());
        alert.show();
    }

    // =========================================================================
    // First-run Setup Wizard
    // =========================================================================

    private void showSetupWizard() {
        Optional<SetupAccess> access = promptInitialSetupAccess();
        if (access.isEmpty()) { Platform.exit(); return; }

        AppConfiguration cfg = AppConfigurationService.getConfiguration();
        if (access.get() == SetupAccess.USER) {
            if (!cfg.getDatabaseConfiguration().getEngine().isRemote()) {
                AppTheme.showSecurityEnforcementError(); Platform.exit(); return;
            }
            cfg.markSetupDone();
            try {
                AppConfigurationService.updateConfiguration(cfg);
                showLoginScreen();
                showInfo("Setup skipped. Staff can configure Library OS later from Settings.");
            } catch (IOException ex) { showError("Could not save configuration: " + ex.getMessage()); }
            return;
        }

        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Library OS - Staff Setup");
        dlg.initOwner(primaryStage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        DialogPane dp = dlg.getDialogPane();
        dp.setPrefWidth(540);

        VBox hero = new VBox(10);
        hero.setAlignment(Pos.CENTER); hero.setPadding(new Insets(32, 32, 24, 32));
        hero.setStyle("-fx-background-color:#0F172A;");
        Label libLbl = new Label(cfg.getLibraryName());
        libLbl.setStyle("-fx-font-size:24px; -fx-font-weight:800; -fx-text-fill:white;");
        Label brLbl = new Label(cfg.getBranchName());
        brLbl.setStyle("-fx-font-size:14px; -fx-font-weight:500; -fx-text-fill:#94A3B8;");
        hero.getChildren().addAll(libLbl, brLbl);

        VBox form = new VBox(14);
        form.setPadding(new Insets(24, 32, 16, 32));
        TextField libNameF  = wField(cfg.getLibraryName(),   "e.g. City Public Library");
        TextField branchF   = wField(cfg.getBranchName(),    "e.g. Main Branch");
        TextField dataDirF  = wField(cfg.getDataDirectory(),  "data");
        TextField exportF   = wField(cfg.getExportDirectory(), "exports");
        final DatabaseConfiguration[] dbH = { cfg.getDatabaseConfiguration() != null ? cfg.getDatabaseConfiguration() : new DatabaseConfiguration() };

        HBox dataRow   = new HBox(8, dataDirF, browseBtn(dataDirF, "Choose data folder"));   HBox.setHgrow(dataDirF, Priority.ALWAYS);
        HBox exportRow = new HBox(8, exportF,  browseBtn(exportF,  "Choose export folder")); HBox.setHgrow(exportF, Priority.ALWAYS);
        form.getChildren().addAll(wRow("Library Name", libNameF), wRow("Branch Name", branchF),
                wRow("Data Folder", dataRow), wRow("Export Folder", exportRow));

        PasswordField masterKeyF = new PasswordField();
        masterKeyF.setPromptText("Enter a strong administrative password");
        masterKeyF.setText(cfg.getLibraryMasterKey());
        masterKeyF.setStyle("-fx-background-color:#F9FAFB; -fx-border-color:#D1D5DB; -fx-border-width:1.5; -fx-border-radius:10px; -fx-background-radius:10px; -fx-padding:10 14; -fx-font-size:14px;");
        Label secHint = new Label("This password protects your data. Keep it safe — you need it to restore data on another machine.");
        secHint.setStyle("-fx-font-size:11px; -fx-text-fill:#B91C1C; -fx-font-weight:600;"); secHint.setWrapText(true);
        form.getChildren().add(new VBox(6, new Label("Administrative Secret"), masterKeyF, secHint));

        Label dbStatus = new Label("Database setup is optional. File storage used by default.");
        dbStatus.setWrapText(true); dbStatus.setStyle("-fx-font-size:12px; -fx-text-fill:#64748B;");
        Runnable refreshDbSt = () -> {
            DatabaseConfiguration db = dbH[0];
            if (db == null || !db.isConfigured()) { dbStatus.setText("Database setup is optional. File storage used by default."); return; }
            String loc = db.getEngine() == DatabaseConfiguration.Engine.SQLITE ? db.getSqliteFile() : db.getHost() + ":" + db.getPort() + " / " + db.getDatabase();
            dbStatus.setText("Configured: " + db.getEngine().getDisplayName() + " — " + loc);
        };
        Button dbCfgBtn = AppTheme.createIconTextButton("Optional Database Setup", AppTheme.ICON_SAVE, AppTheme.ButtonStyle.OUTLINE);
        dbCfgBtn.setOnAction(e -> DatabaseConfigurationDialog.show(primaryStage, dbH[0]).ifPresent(u -> { dbH[0] = u; refreshDbSt.run(); }));
        form.getChildren().add(wRow("Database", new VBox(8, dbCfgBtn, dbStatus)));

        Label importHint = new Label("Optionally restore data from a previous .lms migration package.");
        importHint.setStyle("-fx-font-size:12px; -fx-text-fill:#64748B;"); importHint.setWrapText(true);
        Button importPkgBtn = AppTheme.createIconTextButton("Import Migration Package (.lms)", AppTheme.ICON_DOWNLOAD, AppTheme.ButtonStyle.OUTLINE);
        Button importDbBtn  = AppTheme.createIconTextButton("Import from Database", AppTheme.ICON_SYNC, AppTheme.ButtonStyle.OUTLINE);
        importPkgBtn.setMaxWidth(Double.MAX_VALUE); importDbBtn.setMaxWidth(Double.MAX_VALUE);
        Label importSt = new Label(); importSt.setWrapText(true); importSt.setStyle("-fx-font-size:12px;");

        importPkgBtn.setOnAction(ev -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Select Migration Package");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Library Migration (.lms)", "*.lms"));
            java.io.File file = fc.showOpenDialog(primaryStage);
            if (file == null) return;
            Dialog<String> pd = new Dialog<>(); pd.setTitle("Migration Import"); pd.setHeaderText("Unlock Migration Package");
            AppTheme.applyTheme(pd.getDialogPane());
            PasswordField pf = new PasswordField(); pf.setPromptText("Administrative Secret"); pf.setPrefWidth(300);
            VBox box = new VBox(10, new Label("Enter the Administrative Secret used on the old PC:"), pf); box.setPadding(new Insets(20));
            pd.getDialogPane().setContent(box);
            ButtonType ib = new ButtonType("Import", ButtonBar.ButtonData.OK_DONE);
            pd.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ib);
            pd.setResultConverter(bt -> bt == ib ? pf.getText() : null);
            pd.showAndWait().ifPresent(pw -> {
                try {
                    MigrationService.importMigrationPackage(file, pw);
                    // FIX #25: The import only writes .ser files to disk. All singleton
                    // DBs (UsersDB, BooksDB, LibrariesDB, BorrowRequests) were already
                    // loaded into memory in their empty initial state before the wizard
                    // appeared. Force-reload every singleton so the rest of the setup
                    // wizard (and the subsequent login screen) use the imported data.
                    UsersDB.getInstance().forceReload();
                    BooksDB.getInstance().forceReload();
                    LibrariesDB.getInstance().forceReload();
                    BorrowRequestService.forceReload();
                    // Reload library config in case the package included a config file,
                    // then update the master key from it so encryption is consistent.
                    AppConfigurationService.reloadConfiguration();
                    AppConfiguration importedCfg = AppConfigurationService.getConfiguration();
                    if (importedCfg.getLibraryMasterKey() != null
                            && !importedCfg.getLibraryMasterKey().isBlank()) {
                        SecurityProvider.setLibraryMasterKey(importedCfg.getLibraryMasterKey());
                    }
                    // Populate form fields with values from imported config where possible
                    if (importedCfg.getLibraryName() != null && !importedCfg.getLibraryName().isBlank())
                        libNameF.setText(importedCfg.getLibraryName());
                    if (importedCfg.getBranchName() != null && !importedCfg.getBranchName().isBlank())
                        branchF.setText(importedCfg.getBranchName());
                    importSt.setText("✓ Data restored and loaded. Review fields above then click Save & Continue.");
                    importSt.setStyle("-fx-font-size:12px; -fx-text-fill:#16A34A;");
                } catch (Exception ex) {
                    importSt.setText("✗ Import failed: " + ex.getMessage());
                    importSt.setStyle("-fx-font-size:12px; -fx-text-fill:#DC2626;");
                }
            });
        });

        importDbBtn.setOnAction(ev -> {
            DatabaseConfiguration db = dbH[0];
            if (db == null || !db.isConfigured()) { importSt.setText("⚠ Configure a database connection above first."); importSt.setStyle("-fx-font-size:12px; -fx-text-fill:#D97706;"); AppTheme.shake(importDbBtn); return; }
            if (!DatabaseConnectionService.connect(db)) { importSt.setText("✗ Could not connect. Check settings."); importSt.setStyle("-fx-font-size:12px; -fx-text-fill:#DC2626;"); AppTheme.shake(importDbBtn); return; }
            try {
                DataStorage.syncFromDatabase(AppPaths.resolveDataDirectory());
                AppConfigurationService.reloadConfiguration();
                UsersDB.getInstance().forceReload(); BooksDB.getInstance().forceReload();
                LibrariesDB.getInstance().forceReload(); BorrowRequestService.forceReload();
                importSt.setText("✓ Synced from " + db.getEngine().getDisplayName() + ". Click Save & Continue."); importSt.setStyle("-fx-font-size:12px; -fx-text-fill:#16A34A;");
            } catch (Exception ex) { importSt.setText("✗ Sync failed: " + ex.getMessage()); importSt.setStyle("-fx-font-size:12px; -fx-text-fill:#DC2626;"); }
        });
        form.getChildren().add(wRow("Restore / Import", new VBox(8, importHint, importPkgBtn, importDbBtn, importSt)));

        ScrollPane scroll = new ScrollPane(new VBox(0, hero, form));
        scroll.setFitToWidth(true); scroll.setPrefHeight(650); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        dp.setContent(scroll);

        ButtonType doneBt = new ButtonType("Save & Continue", ButtonBar.ButtonData.OK_DONE);
        dp.getButtonTypes().add(doneBt); AppTheme.applyTheme(dp);
        Button ok = (Button) dp.lookupButton(doneBt);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            if (libNameF.getText().trim().isEmpty()) { showError("Library name is required."); evt.consume(); return; }
            if (branchF.getText().trim().isEmpty())  { showError("Branch name is required.");  evt.consume(); }
        });
        dlg.setResultConverter(bt -> bt == doneBt);

        if (dlg.showAndWait().orElse(false)) {
            cfg.setLibraryName(libNameF.getText()); cfg.setBranchName(branchF.getText());
            cfg.setDataDirectory(dataDirF.getText()); cfg.setExportDirectory(exportF.getText());
            cfg.setLibraryMasterKey(masterKeyF.getText()); cfg.setDatabaseConfiguration(dbH[0]);
            cfg.markSetupDone();
            try {
                AppConfigurationService.updateConfiguration(cfg);
                LibrariesDB.getInstance().addLibrary(cfg.getLibraryName(), cfg.getBranchName(), cfg.getBranchId(),
                        cfg.getDataDirectory(), cfg.getExportDirectory());
                showLoginScreen();
                showConfigurationSavedToast(new DatabaseConfiguration(), cfg.getDatabaseConfiguration());
            } catch (IOException ex) { showError("Could not save configuration: " + ex.getMessage()); }
        } else { Platform.exit(); }
    }

    private TextField wField(String val, String prompt) {
        TextField f = new TextField(val); f.setPromptText(prompt);
        f.setStyle("-fx-background-color:#F9FAFB; -fx-border-color:#D1D5DB; -fx-border-width:1.5; -fx-border-radius:10px; -fx-background-radius:10px; -fx-padding:10 14; -fx-font-size:14px;");
        return f;
    }
    private Button browseBtn(TextField target, String title) {
        Button b = new Button("Browse\u2026");
        b.setStyle("-fx-background-color:#E2E8F0; -fx-background-radius:8px; -fx-border-radius:8px; -fx-cursor:hand; -fx-padding:8 14; -fx-font-weight:600;");
        b.setOnAction(e -> { DirectoryChooser dc = new DirectoryChooser(); dc.setTitle(title); java.io.File dir = dc.showDialog(primaryStage); if (dir != null) target.setText(dir.getAbsolutePath()); });
        return b;
    }
    private VBox wRow(String lbl, javafx.scene.Node field) { Label l = new Label(lbl); l.setStyle("-fx-font-size:13px; -fx-font-weight:600; -fx-text-fill:#374151;"); return new VBox(6, l, field); }

    private Optional<SetupAccess> promptInitialSetupAccess() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Library OS Setup"); alert.setHeaderText("Who is setting up this library?");
        alert.setContentText("Librarians and administrators configure library settings now. Regular users can skip to sign in.");
        alert.initOwner(primaryStage); AppTheme.applyTheme(alert.getDialogPane());
        ButtonType staffType = new ButtonType("Librarian / Admin", ButtonBar.ButtonData.OK_DONE);
        ButtonType userType  = new ButtonType("User",              ButtonBar.ButtonData.OTHER);
        alert.getButtonTypes().setAll(staffType, userType, ButtonType.CANCEL);
        return alert.showAndWait().flatMap(r -> { if (r == staffType) return Optional.of(SetupAccess.STAFF); if (r == userType) return Optional.of(SetupAccess.USER); return Optional.empty(); });
    }

    // =========================================================================
    // Login / Session
    // =========================================================================

    private void showLoginScreen() {
        stopAutoRefresh();
        // Clear all per-library state
        analyticsDashboard = null; catalogView = null; circulationView = null;
        currentUser = null; currentUserPassword = null;
        booksList.clear(); issuesList.clear(); requestsList.clear(); historyList.clear();
        navigationHistory.clear();

        LoginView lv = new LoginView((user, pass) -> { this.currentUserPassword = pass; handleLoginSuccess(user); }, this::showRegistrationDialog, this);
        lv.setOpacity(0);
        rootStack.getChildren().setAll(lv);
        FadeTransition ft = new FadeTransition(Duration.millis(350), lv); ft.setToValue(1); ft.play();
    }

    private void showRegistrationDialog() {
        // CRITICAL FIX: We must select the current library BEFORE calling
        // hasRegisteredUsers(), because UsersDB.getInstance() resolves its file path
        // from AppConfigurationService.getConfiguration(). At this point the
        // configuration may still be the blank bootstrap config (no branch selected),
        // which points to an empty directory. Selecting the library first forces
        // UsersDB to reload from the correct branch-scoped path.
        String currentLibrary = AppConfigurationService.getConfiguration().getCurrentLibraryDisplayName();
        if (currentLibrary != null && !currentLibrary.isBlank()) {
            try {
                AppConfigurationService.selectKnownLibrary(currentLibrary);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Could not pre-select library before registration check", e);
            }
        }
        RegistrationDialog.show(primaryStage, !UserService.hasRegisteredUsers(), true).ifPresent(req -> {
            try {
                if (req.libraryName() != null) AppConfigurationService.selectKnownLibrary(req.libraryName());
                if (UserService.userExists(req.username())) { showError("Username \"" + req.username() + "\" is already taken."); return; }
                UserService.createUser(req.username(), req.password(), req.role());
                User created = UserService.getUserById(req.username());
                created.unlockProfile(req.password());
                created.setEmail(req.email()); created.setContactNumber(req.phoneNumber()); created.setActive(!req.pendingApproval());
                UserService.updateUser(created); UserService.persistDatabase();
                showSuccess(req.pendingApproval() ? "Registration submitted for approval." : "Account created! Please sign in.");
            } catch (Exception ex) { LOG.log(Level.SEVERE, "Registration failed", ex); showError("Registration failed: " + ex.getMessage()); }
        });
    }

    /**
     * Called after credentials are verified. Loads all per-library data for the session.
     * Architecture: only here (post-login) do we load BooksDB, UsersDB, etc.
     */
    private void handleLoginSuccess(String username) {
        this.currentUser = username;
        User user = UserService.getUserById(username);
        this.currentUserRole = user != null ? user.getRole() : UserRole.USER;

        if (user != null && user.isDarkMode() != AppTheme.darkMode) applyDarkMode(user.isDarkMode());

        // Load all per-library singletons now that we know the active branch
        loadPerLibraryData();

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("content-area");
        sidebar     = buildSidebar();
        contentArea = new StackPane(); contentArea.getStyleClass().add("content-area");
        layout.setLeft(sidebar); layout.setTop(buildHeader()); layout.setCenter(contentArea); layout.setBottom(buildStatusBar());
        rootStack.getChildren().setAll(layout);
        primaryStage.setMaximized(true);

        startAutoRefresh();
        Platform.runLater(() -> { navigateToDashboard(); showSuccess("Signed in as " + currentUser + "."); });
    }

    /**
     * Loads / refreshes all per-library singletons.
     * Separated from handleLoginSuccess so it can be reused by refreshAllData().
     */
    private void loadPerLibraryData() {
        AppConfiguration cfg = AppConfigurationService.getConfiguration();
        SecurityProvider.setLibraryMasterKey(cfg.getLibraryMasterKey());

        if (DatabaseConnectionService.isConnected()) {
            try { DataStorage.syncFromDatabase(AppPaths.resolveDataDirectory(cfg)); }
            catch (Exception e) { LOG.log(Level.WARNING, "Could not sync per-library data from DB", e); }
        }

        UsersDB.getInstance().forceReload();
        BooksDB.getInstance().forceReload();
        BorrowRequestService.forceReload();

        if (currentUser != null && currentUserPassword != null) {
            User u = UserService.getUserById(currentUser);
            if (u != null) u.unlockProfile(currentUserPassword);
        }

        Platform.runLater(this::updateObservableLists);
    }

    private void updateObservableLists() {
        booksList.setAll(BookService.getAllBooks());
        issuesList.setAll(BookService.getAllIssueRecords());
        requestsList.setAll(BookService.getAllBorrowRequests());
        historyList.setAll(BooksDB.getInstance().getInvoiceHistory());
        if (analyticsDashboard != null) analyticsDashboard.refresh();
        if (catalogView        != null) catalogView.refresh();
        if (circulationView    != null) circulationView.refresh();
    }

    // =========================================================================
    // Shell — Sidebar
    // =========================================================================

    private VBox buildSidebar() {
        VBox sb = new VBox(4);
        sb.setPrefWidth(248); sb.setMinWidth(248); sb.setMaxWidth(248);
        sb.getStyleClass().add("sidebar");

        HBox logoBox = new HBox(4); logoBox.setPadding(new Insets(0, 0, 28, 10));
        Label lib = new Label("LIBRARY"); lib.getStyleClass().add("sidebar-logo");
        Label os  = new Label("OS");      os.getStyleClass().addAll("sidebar-logo", "sidebar-logo-accent");
        logoBox.getChildren().addAll(lib, os);

        Label navHdr = new Label("NAVIGATION"); navHdr.getStyleClass().addAll("sidebar-section-label", "nav-section-navigation");
        Button dash = navBtn("Dashboard",   AppTheme.ICON_DASHBOARD, true, this::navigateToDashboard);
        dash.getStyleClass().add("nav-dashboard");
        Button cat  = navBtn("Catalog",     AppTheme.ICON_LIBRARY,   true, this::navigateToCatalog);
        cat.getStyleClass().add("nav-catalog");
        Button circ = navBtn("Circulation", AppTheme.ICON_SYNC,      true, this::navigateToCirculation);
        circ.getStyleClass().add("nav-circulation");
        VBox nav = new VBox(4, navHdr, dash, cat, circ);

        VBox mgmt = new VBox(4);
        if (currentUserRole.isStaff()) {
            Label mgmtHdr = new Label("MANAGEMENT"); mgmtHdr.getStyleClass().addAll("sidebar-section-label", "nav-section-management");
            Button users = navBtn("Users",    AppTheme.ICON_USER,     true, this::showUserManagement);
            users.getStyleClass().add("nav-users");
            Button sett  = navBtn("Settings", AppTheme.ICON_SETTINGS, true, this::showSettings);
            sett.getStyleClass().add("nav-settings");
            mgmt.getChildren().addAll(mgmtHdr, users, sett);
        }

        Label accountHdr = new Label("ACCOUNT"); accountHdr.getStyleClass().addAll("sidebar-section-label", "nav-section-account");
        Button profileBtn = navBtn("Edit Profile",    AppTheme.ICON_USER,   false, () -> { if (UserAccountDialogs.showProfileEditor(primaryStage, currentUser, currentUserPassword)) refreshSessionUI(); });
        profileBtn.getStyleClass().add("nav-profile");
        Button passBtn    = navBtn("Change Password", AppTheme.ICON_LOCK,   false, () -> UserAccountDialogs.showPasswordEditor(primaryStage, currentUser));
        passBtn.getStyleClass().add("nav-password");
        Button delBtn     = navBtn("Delete Account",  AppTheme.ICON_DELETE, false, () -> UserAccountDialogs.showDeleteAccount(primaryStage, currentUser, this::showLoginScreen, this));
        delBtn.getStyleClass().add("nav-delete-account");
        VBox accountSection = new VBox(4, accountHdr, profileBtn, passBtn, delBtn);

        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox warningBanner = buildConfigWarning();

        User u = UserService.getUserById(currentUser);
        userNameLabel = new Label(u != null ? u.getFullName() : currentUser); userNameLabel.getStyleClass().add("sidebar-profile-name");
        userRoleLabel = new Label(currentUserRole.getDisplayName()); userRoleLabel.getStyleClass().add("sidebar-profile-role");
        VBox profile = new VBox(3, userNameLabel, userRoleLabel); profile.getStyleClass().add("sidebar-profile");

        sb.getChildren().addAll(logoBox, nav, mgmt, accountSection, spacer);
        if (warningBanner != null) sb.getChildren().add(warningBanner);
        sb.getChildren().add(profile);
        return sb;
    }

    private VBox buildConfigWarning() {
        if (!currentUserRole.isStaff()) return null;
        AppConfiguration cfg = AppConfigurationService.getConfiguration();
        boolean emailOk = cfg.isEmailConfigured();
        // Check both whether DB is configured (fields non-blank) AND actually connected.
        // isConnected() can stay true after fields are cleared until next restart, so
        // we also gate on isConfigured() to catch "fields were cleared" immediately.
        boolean dbOk = cfg.getDatabaseConfiguration().isConfigured()
                && DatabaseConnectionService.isConnected();
        if (emailOk && dbOk) return null;
        VBox banner = new VBox(4); banner.setPadding(new Insets(10, 8, 10, 8));
        banner.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "rgba(180,83,9,0.22)" : "#FEF3C7") + "; -fx-background-radius:10px; -fx-border-color:" + (AppTheme.darkMode ? "#92400E" : "#FCD34D") + "; -fx-border-width:1; -fx-border-radius:10px; -fx-cursor:hand;");
        Label hdr = new Label("⚠  Configuration Incomplete");
        hdr.setStyle("-fx-font-size:11px; -fx-font-weight:700; -fx-text-fill:" + (AppTheme.darkMode ? "#FCD34D" : "#92400E") + ";"); hdr.setWrapText(true); banner.getChildren().add(hdr);
        if (!emailOk) { Label r = new Label("· Email/SMTP not configured"); r.setStyle("-fx-font-size:10px; -fx-text-fill:" + (AppTheme.darkMode ? "#FDE68A" : "#B45309") + ";"); r.setWrapText(true); banner.getChildren().add(r); }
        if (!dbOk)    { Label r = new Label("· Database not connected");     r.setStyle("-fx-font-size:10px; -fx-text-fill:" + (AppTheme.darkMode ? "#FDE68A" : "#B45309") + ";"); r.setWrapText(true); banner.getChildren().add(r); }
        banner.setOnMouseClicked(e -> showLibraryConfig());
        AppTheme.installSmartTooltip(banner, "Click to open Library Configuration");
        return banner;
    }

    private Button navBtn(String text, String iconPath, boolean activatesNav, Runnable action) {
        Button b = new Button(text); AppTheme.installSmartTooltip(b, text);
        if (iconPath != null && !iconPath.isBlank()) b.setGraphic(AppTheme.createIcon(iconPath, 18));
        b.getStyleClass().add("sidebar-btn");
        b.setOnAction(e -> { if (activatesNav) setActiveNav(b); action.run(); });
        return b;
    }
    private void setActiveNav(Button btn) {
        if (activeNavBtn != null) activeNavBtn.getStyleClass().remove("active");
        activeNavBtn = btn; btn.getStyleClass().add("active");
    }

    // =========================================================================
    // Shell — Header
    // =========================================================================

    private HBox buildHeader() {
        HBox h = new HBox(10); h.setPadding(new Insets(14, 20, 14, 20));
        h.getStyleClass().add("app-header"); h.setAlignment(Pos.CENTER_LEFT);

        headerTitleLabel = new Label("Dashboard"); headerTitleLabel.getStyleClass().add("header-title");

        backBtn = AppTheme.createIconButton(AppTheme.ICON_ARROW_BACK, "Go Back", AppTheme.ButtonStyle.GHOST);
        backBtn.setVisible(false); backBtn.setManaged(false); backBtn.setOnAction(e -> navigateBack());

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = AppTheme.createIconButton(AppTheme.ICON_REFRESH, "Refresh", AppTheme.ButtonStyle.GHOST);
        refreshBtn.setOnAction(e -> {
            String t = headerTitleLabel.getText(); headerTitleLabel.setText("Refreshing\u2026");
            refreshAllData(true);
            PauseTransition p = new PauseTransition(Duration.millis(900)); p.setOnFinished(ev -> headerTitleLabel.setText(t)); p.play();
        });

        AppConfiguration cfg = AppConfigurationService.getConfiguration();
        Button themeBtn = AppTheme.createIconButton(cfg.isDarkMode() ? AppTheme.ICON_MOON : AppTheme.ICON_SUN,
                cfg.isDarkMode() ? "Dark theme enabled" : "Light theme enabled", AppTheme.ButtonStyle.GHOST);
        themeBtn.getStyleClass().add("theme-toggle-btn");
        themeBtn.setOnAction(e -> {
            cfg.toggleDarkMode(); applyDarkMode(cfg.isDarkMode());
            if (currentUser != null) {
                User u = UserService.getUserById(currentUser);
                if (u != null) { u.setDarkMode(cfg.isDarkMode()); try { UserService.updateUser(u); UserService.persistDatabase(); } catch (Exception ex) { LOG.warning(ex.getMessage()); } }
            }
            themeBtn.setGraphic(AppTheme.createIcon(cfg.isDarkMode() ? AppTheme.ICON_MOON : AppTheme.ICON_SUN, 18));
            AppTheme.installSmartTooltip(themeBtn, cfg.isDarkMode() ? "Dark theme enabled" : "Light theme enabled");
            try { AppConfigurationService.updateConfiguration(cfg); }
            catch (IOException ioEx) { LOG.log(Level.SEVERE, "Failed to persist app configuration after theme change", ioEx); }
        });

        Button logoutBtn = AppTheme.createIconButton(AppTheme.ICON_LOGOUT, "Sign out", AppTheme.ButtonStyle.GHOST);
        logoutBtn.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Sign out of Library OS?", ButtonType.YES, ButtonType.NO);
            a.setTitle("Sign Out"); a.initOwner(primaryStage);
            // setOnShowing guarantees button nodes exist before lookup
            a.setOnShowing(ev -> {
                AppTheme.applyTheme(a.getDialogPane());
                Button yesBtn = (Button) a.getDialogPane().lookupButton(ButtonType.YES);
                if (yesBtn != null) {
                    yesBtn.getStyleClass().remove("btn-primary");
                    yesBtn.getStyleClass().add("btn-danger");
                    yesBtn.setStyle("");
                }
                Button noBtn = (Button) a.getDialogPane().lookupButton(ButtonType.NO);
                if (noBtn != null) {
                    noBtn.getStyleClass().add("btn-outline");
                    noBtn.setStyle("");
                }
            });
            a.showAndWait().filter(bt -> bt == ButtonType.YES).ifPresent(bt -> { currentUserPassword = null; showLoginScreen(); });
        });

        h.getChildren().addAll(backBtn, headerTitleLabel, spacer, refreshBtn, themeBtn, logoutBtn);
        return h;
    }

    // =========================================================================
    // Shell — Status bar
    // =========================================================================

    private HBox buildStatusBar() {
        HBox bar = new HBox(8); bar.setPadding(new Insets(8, 20, 8, 20));
        bar.getStyleClass().add("status-bar"); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinHeight(36); bar.setPrefHeight(36); bar.setMaxHeight(36);
        loadingIndicator = new ProgressIndicator(); loadingIndicator.setMaxSize(14, 14); loadingIndicator.setVisible(false);
        statusLabel = new Label("Ready"); statusLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#64748B;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        AppConfiguration cfg = AppConfigurationService.getConfiguration();
        footerLibraryLabel = new Label(cfg.getLibraryName() + " \u00B7 " + cfg.getBranchName());
        footerLibraryLabel.setStyle("-fx-font-size:11px; -fx-text-fill:#94A3B8;");
        Label ver = new Label("Library OS v3.4"); ver.setStyle("-fx-font-size:11px; -fx-text-fill:#94A3B8;");
        bar.getChildren().addAll(loadingIndicator, statusLabel, sp, footerLibraryLabel, new Label("  |  "), ver);
        return bar;
    }

    private void refreshLibraryFooter() {
        if (footerLibraryLabel == null) return;
        AppConfiguration cfg = AppConfigurationService.getConfiguration();
        footerLibraryLabel.setText(cfg.getLibraryName() + " \u00B7 " + cfg.getBranchName());
    }

    // =========================================================================
    // Navigation
    // =========================================================================

    private void navigateToDashboard() {
        if (analyticsDashboard == null) {
            analyticsDashboard = new AnalyticsDashboard(currentUser, currentUserRole.isStaff(), this);
            // FIX: args were swapped in prior version — correct order is (circulation, catalog, userMgmt)
            analyticsDashboard.setNavigationCallbacks((java.util.function.IntConsumer) this::navigateToCirculation, this::navigateToCatalog, this::showUserManagement);
        }
        showView(analyticsDashboard);
    }

    private void navigateToCatalog() {
        if (catalogView == null) catalogView = new CatalogView(booksList, currentUserRole.isStaff(), currentUser, this::refreshAllData, this);
        showView(catalogView);
    }

    private void navigateToCirculation()             { navigateToCirculation(-1); }
    private void navigateToCirculation(int tabIndex) {
        if (circulationView == null) circulationView = new CirculationView(issuesList, requestsList, historyList, currentUserRole.isStaff(), currentUser, this::refreshAllData, this, this::refreshAllData);
        if (tabIndex >= 0) circulationView.selectTab(tabIndex);
        showView(circulationView);
    }

    private void showView(Region view) {
        if (view == analyticsDashboard || view == catalogView || view == circulationView) navigationHistory.clear();
        internalShowView(view, true);
    }

    private void navigateBack() { if (!navigationHistory.isEmpty()) internalShowView(navigationHistory.pop(), false); }

    private void internalShowView(Region view, boolean addToHistory) {
        Region current = contentArea.getChildren().isEmpty() ? null : (Region) contentArea.getChildren().get(0);
        if (current == view) return;
        if (addToHistory && current != null) navigationHistory.push(current);
        AppTheme.crossfadeViews(current, view, contentArea);
        updateNavigationState(view);
    }

    private void updateNavigationState(Region view) {
        backBtn.setVisible(!navigationHistory.isEmpty()); backBtn.setManaged(!navigationHistory.isEmpty());
        String title = "Dashboard";
        if      (view instanceof CatalogView)              title = "Catalog";
        else if (view instanceof CirculationView)           title = "Circulation";
        else if (view instanceof UserManagementView)        title = "User Management";
        else if (view instanceof SettingsView)              title = "Settings";
        else if (view instanceof LibraryConfigurationView)  title = "Library Configuration";
        else if (view instanceof DataManagementView)        title = "Data Management";
        if (headerTitleLabel != null) headerTitleLabel.setText(title);
        if (view instanceof AnalyticsDashboard d) Platform.runLater(d::refreshLayout);
    }

    // =========================================================================
    // Settings / sub-views
    // =========================================================================

    private void showUserManagement() { showView(new UserManagementView(currentUser, this, this::refreshAllData)); }

    private void showSettings() {
        showView(new SettingsView(currentUserRole, new SettingsView.Actions() {
            @Override public void openProfile()               { if (UserAccountDialogs.showProfileEditor(primaryStage, currentUser, currentUserPassword)) refreshSessionUI(); }
            @Override public void openPassword()              { UserAccountDialogs.showPasswordEditor(primaryStage, currentUser); }
            @Override public void openUserManagement()        { showUserManagement(); }
            @Override public void openLibraryConfiguration()  { showLibraryConfig(); }
            @Override public void openDataManagement()        { showDataManagement(); }
            @Override public void openAnalytics()             { navigateToDashboard(); }
            @Override public void deleteAccount()             { UserAccountDialogs.showDeleteAccount(primaryStage, currentUser, LibraryApp.this::showLoginScreen, LibraryApp.this); }
        }));
    }

    private void showLibraryConfig() { showLibraryConfig(-1); }
    private void showLibraryConfig(int tabIndex) {
        try {
            AppConfiguration cfg = AppConfigurationService.getConfiguration();
            LibraryConfigurationView view = new LibraryConfigurationView(cfg, msg -> {
                showSuccess(msg);
                // Re-register the (possibly renamed) library in LibrariesDB
                AppConfiguration refreshed = AppConfigurationService.getConfiguration();
                LibrariesDB.getInstance().addLibrary(refreshed.getLibraryName(), refreshed.getBranchName(), refreshed.getBranchId(),
                        refreshed.getDataDirectory(), refreshed.getExportDirectory());
                refreshLibraryFooter();
                refreshAllData();
                // FIX: Rebuild the sidebar immediately so the "Configuration Incomplete"
                // warning banner disappears the moment DB/email config is saved, without
                // requiring the user to restart the app.
                Platform.runLater(() -> {
                    Node mainLayout = rootStack.getChildren().get(0);
                    if (mainLayout instanceof BorderPane shell) {
                        sidebar = buildSidebar();
                        shell.setLeft(sidebar);
                    }
                });
            });
            if (tabIndex >= 0) view.setSelectedTab(tabIndex);
            showView(view);
        } catch (Exception ex) { LOG.log(Level.SEVERE, "Could not load library configuration", ex); showError("Could not load configuration."); }
    }

    private void showDataManagement() {
        try {
            Map<String, Object> s = BookService.getLibraryStatistics();
            AppConfiguration cfg = AppConfigurationService.getConfiguration();
            showView(new DataManagementView(primaryStage, new DataManagementView.Snapshot(
                    n(s, "totalBooks"), n(s, "totalCopies"), n(s, "availableCopies"),
                    n(s, "issuedCopies"), n(s, "overdueBooks"), UserService.getAllUsers().size(),
                    n(s, "pendingRequests"), ((Number) s.getOrDefault("totalFines", 0.0)).doubleValue(),
                    cfg.getExportDirectory(), cfg.isEmailConfigured()), this));
        } catch (Exception ex) { showError("Data management error: " + ex.getMessage()); }
    }

    private static int n(Map<String, Object> m, String k) { return ((Number) m.getOrDefault(k, 0)).intValue(); }

    public void refreshSessionUI() {
        if (currentUser == null) return;
        User u = UserService.getUserById(currentUser);
        if (u != null) { if (userNameLabel != null) userNameLabel.setText(u.getFullName()); if (userRoleLabel != null) userRoleLabel.setText(u.getRole().getDisplayName()); }
        refreshLibraryFooter();
    }

    // =========================================================================
    // Data refresh
    // =========================================================================

    public void refreshAllData()               { refreshAllData(false); }
    public void refreshAllData(boolean manual) {
        if (currentUser == null) return;
        if (loadingIndicator != null) loadingIndicator.setVisible(true);
        if (statusLabel != null) statusLabel.setText("Syncing...");
        CompletableFuture.runAsync(() -> { try { loadPerLibraryData(); } catch (Exception e) { LOG.log(Level.SEVERE, "Refresh failed", e); } })
                .thenRun(() -> Platform.runLater(() -> {
                    if (loadingIndicator != null) loadingIndicator.setVisible(false);
                    if (statusLabel != null) statusLabel.setText("Last sync: " + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    if (manual) showSuccess("Data refreshed successfully.");
                }));
    }

    private void startAutoRefresh() {
        autoRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(60), e -> refreshAllData(false)));
        autoRefreshTimer.setCycleCount(Timeline.INDEFINITE); autoRefreshTimer.play();
    }
    private void stopAutoRefresh() { if (autoRefreshTimer != null) autoRefreshTimer.stop(); }

    // =========================================================================
    // Toast notifications
    // =========================================================================

    @Override public void showSuccess(String m) { toast(m, "toast-success", "\u2713"); }
    @Override public void showError  (String m) { toast(m, "toast-error",   "\u2715"); }
    @Override public void showInfo   (String m) { toast(m, "toast-info",    "\u2139"); }
    @Override public void showWarning(String m) { toast(m, "toast-warning", "\u26A0"); }

    private void toast(String message, String style, String icon) {
        Platform.runLater(() -> {
            if (activeToast != null) { if (activeToastAnim != null) activeToastAnim.stop(); rootStack.getChildren().remove(activeToast); activeToast = null; activeToastAnim = null; }
            HBox t = new HBox(8); t.setAlignment(Pos.CENTER_LEFT); t.getStyleClass().addAll("toast-notification", style); t.setMaxWidth(420); t.setMaxHeight(Region.USE_PREF_SIZE);
            Label ico = new Label(icon); ico.setStyle("-fx-font-size:13px; -fx-min-width:16px; -fx-max-width:16px; -fx-alignment:center; -fx-font-weight:700;");
            Label msg = new Label(message); msg.setStyle("-fx-font-size:13px; -fx-font-weight:500;"); msg.setWrapText(false); msg.setEllipsisString("\u2026");
            t.getChildren().addAll(ico, msg);
            StackPane.setAlignment(t, Pos.TOP_CENTER); StackPane.setMargin(t, new Insets(84, 0, 0, 0));
            rootStack.getChildren().add(t); t.setOpacity(0); t.setTranslateY(-15); activeToast = t;
            FadeTransition fi = new FadeTransition(Duration.millis(180), t); fi.setToValue(1);
            TranslateTransition si = new TranslateTransition(Duration.millis(180), t); si.setToY(0);
            PauseTransition pa = new PauseTransition(Duration.seconds(3.0));
            FadeTransition fo = new FadeTransition(Duration.millis(200), t); fo.setToValue(0);
            fo.setOnFinished(e -> { rootStack.getChildren().remove(t); if (activeToast == t) { activeToast = null; activeToastAnim = null; } });
            SequentialTransition seq = new SequentialTransition(new ParallelTransition(fi, si), pa, fo); activeToastAnim = seq; seq.play();
        });
    }

    // =========================================================================
    // Dark mode / Theme
    // =========================================================================

    private void applyDarkMode(boolean dark) {
        AppTheme.darkMode = dark;
        Scene s = primaryStage != null ? primaryStage.getScene() : null;
        if (s == null) return;
        AppTheme.animateThemeChange(s.getRoot(), () -> {
            if (dark) { if (!s.getRoot().getStyleClass().contains("dark-mode")) s.getRoot().getStyleClass().add("dark-mode"); }
            else        s.getRoot().getStyleClass().remove("dark-mode");
            rebuildShell(); rebuildCurrentViewForTheme();
        });
    }

    private void rebuildShell() {
        if (rootStack.getChildren().isEmpty()) return;
        Node mainLayout = rootStack.getChildren().get(0);
        if (!(mainLayout instanceof BorderPane shell)) return;
        sidebar = buildSidebar(); shell.setLeft(sidebar); shell.setTop(buildHeader()); shell.setBottom(buildStatusBar());
        updateActiveSidebarState();
    }

    private void updateActiveSidebarState() {
        if (contentArea == null || contentArea.getChildren().isEmpty()) return;
        Node current = contentArea.getChildren().get(0);
        List<Button> all = new ArrayList<>(); findAllButtons(sidebar, all);
        for (Button btn : all) {
            String t = btn.getText();
            boolean active = (current instanceof AnalyticsDashboard && "Dashboard".equals(t)) ||
                    (current instanceof CatalogView && "Catalog".equals(t)) ||
                    (current instanceof CirculationView && "Circulation".equals(t)) ||
                    (current instanceof UserManagementView && "Users".equals(t)) ||
                    (current instanceof SettingsView && "Settings".equals(t));
            if (active) { if (!btn.getStyleClass().contains("active")) btn.getStyleClass().add("active"); activeNavBtn = btn; }
            else btn.getStyleClass().remove("active");
        }
    }

    private void findAllButtons(Parent root, List<Button> buttons) {
        for (Node n : root.getChildrenUnmodifiable()) {
            if (n instanceof Button b && b.getStyleClass().contains("sidebar-btn")) buttons.add(b);
            else if (n instanceof Parent p) findAllButtons(p, buttons);
        }
    }

    /**
     * Rebuilds the current view after a theme change.
     *
     * FIX (compile error): The previous version had orphaned/dangling statements after the
     * closing brace of this method that made the file syntactically invalid.
     *
     * FIX (back-button): rebuildCurrentViewForTheme called showView() which pushed entries
     * onto navigationHistory, making the back-button appear after a theme toggle.
     * Both are fixed by clearing history before and after the rebuild.
     */
    private void rebuildCurrentViewForTheme() {
        if (contentArea == null || contentArea.getChildren().isEmpty()) return;
        Node current = contentArea.getChildren().get(0);
        int selectedTabIndex = findTabPaneIndex(current);

        // Clear history before rebuild so new calls to internalShowView(view, true)
        // don't push anything, and the back-button stays hidden.
        navigationHistory.clear();
        analyticsDashboard = null; catalogView = null; circulationView = null;

        if      (current instanceof AnalyticsDashboard)    navigateToDashboard();
        else if (current instanceof CatalogView)           navigateToCatalog();
        else if (current instanceof CirculationView)       navigateToCirculation(selectedTabIndex);
        else if (current instanceof UserManagementView)    showUserManagement();
        else if (current instanceof SettingsView)          showSettings();
        else if (current instanceof LibraryConfigurationView) showLibraryConfig(selectedTabIndex);
        else if (current instanceof DataManagementView)    showDataManagement();
        else                                               navigateToDashboard();

        // Clear again after rebuild — internalShowView(view, true) may have pushed the
        // old instance when it detected a different object reference after clearing caches.
        navigationHistory.clear();
        if (backBtn != null) { backBtn.setVisible(false); backBtn.setManaged(false); }
    }

    private int findTabPaneIndex(Node root) {
        if (root instanceof TabPane tp) return tp.getSelectionModel().getSelectedIndex();
        if (root instanceof Parent p) { for (Node n : p.getChildrenUnmodifiable()) { int i = findTabPaneIndex(n); if (i != -1) return i; } }
        return -1;
    }

    public static void applyDialogTheme(DialogPane pane) {
        AppTheme.applyTheme(pane);
        if (AppConfigurationService.getConfiguration().isDarkMode()) { if (!pane.getStyleClass().contains("dark-mode")) pane.getStyleClass().add("dark-mode"); }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    private void shutdown() {
        stopAutoRefresh(); DatabaseConnectionService.disconnect();
        try { UserService.persistDatabase(); }
        catch (Exception e) { LOG.log(Level.SEVERE, "User database failed to persist on shutdown — data may be lost", e); }
        try { BookService.persistBooksDatabase(); }
        catch (Exception e) { LOG.log(Level.SEVERE, "Books database failed to persist on shutdown — data may be lost", e); }
    }

    private void showConfigurationSavedToast(DatabaseConfiguration previous, DatabaseConfiguration current) {
        if (previous == null) previous = new DatabaseConfiguration();
        if (current  == null) current  = new DatabaseConfiguration();
        if (!current.isConfigured()) { DatabaseConnectionService.disconnect(); showSuccess(previous.isConfigured() ? "Configuration saved. Database sync disabled." : "Configuration saved."); return; }
        boolean changed = !current.equals(previous);
        if (DatabaseConnectionService.connect(current)) showSuccess(changed ? "Configuration saved. " + current.getEngine().getDisplayName() + " connected." : "Configuration saved.");
        else showInfo(changed ? "Configuration saved, but database unreachable. File storage active." : "Configuration saved. Database unavailable, file storage active.");
    }

    public static void main(String[] args) {
        configureDesktopIdentity();
        System.setProperty("glass.gtk.disable.glib.idle", "true");
        launch(args);
    }
}
