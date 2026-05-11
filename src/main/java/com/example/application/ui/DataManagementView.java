package com.example.application.ui;

import com.example.application.ToastDisplay;
import com.example.services.BookService;
import com.example.services.ReportExportService;
import com.example.services.UserService;
import com.example.storage.AppPaths;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * <h1>DataManagementView — Administrative Backup and System Utilities</h1>
 * 
 * <p>The {@code DataManagementView} provides a centralized dashboard for library
 * administrators to monitor system health (via statistics) and perform critical
 * data operations like backups, restores, and cloud synchronization.</p>
 * 
 * <h3>Key Features:</h3>
 * <ul>
 *   <li><b>System Snapshot:</b> Real-time counters for books, users, and circulation states.</li>
 *   <li><b>Local Backups:</b> Timestamped folder snapshots of the local serialized database.</li>
 *   <li><b>Database Sync:</b> One-click restoration from a connected remote database.</li>
 *   <li><b>Migration Engine:</b> Support for encrypted .lms package export/import for multi-PC moves.</li>
 *   <li><b>Report Generation:</b> Quick CSV export for overdue books and active issues.</li>
 * </ul>
 * 
 * @author Yogesh
 * @version 1.5
 */
public class DataManagementView extends ScrollPane {

    /**
     * Constructs a new DataManagementView.
     * 
     * @param owner The parent stage for modal dialogs.
     * @param snapshot Current system data snapshot (counters and config states).
     * @param toastDisplay Reference to the global notification system.
     */
    public DataManagementView(Stage owner, Snapshot snapshot, ToastDisplay toastDisplay) {
        setFitToWidth(true);
        setStyle("-fx-background: transparent; -fx-background-color: transparent;");


        VBox content = new VBox(6);
        content.setPadding(new Insets(12, 16, 12, 16));

        // Header
        Label title = new Label("Data Management");
        title.setStyle("-fx-font-family: 'Plus Jakarta Sans'; -fx-font-size: 22px; " + // Slightly smaller title
                "-fx-font-weight: 700; -fx-text-fill: " + textPrimary() + ";");

        // Statistics cards - 3 columns, more compact
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(12);
        statsGrid.setVgap(8);

        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(33.3);
        statsGrid.getColumnConstraints().addAll(cc, cc, cc);

        statsGrid.add(createStatCard(AppTheme.ICON_BOOK, "#0D9488",
                "Books", String.valueOf(snapshot.totalBooks())), 0, 0);
        statsGrid.add(createStatCard(AppTheme.ICON_LIBRARY, "#3B82F6",
                "Total Copies", String.valueOf(snapshot.totalCopies())), 1, 0);
        statsGrid.add(createStatCard(AppTheme.ICON_CHECK, "#16A34A",
                "Available", String.valueOf(snapshot.availableCopies())), 2, 0);
        statsGrid.add(createStatCard(AppTheme.ICON_SYNC, "#0EA5E9",
                "Issued", String.valueOf(snapshot.issuedCopies())), 0, 1);
        statsGrid.add(createStatCard(AppTheme.ICON_USER, "#8B5CF6",
                "Users", String.valueOf(snapshot.totalUsers())), 1, 1);
        statsGrid.add(createStatCard(AppTheme.ICON_WARNING, "#D97706",
                "Overdue", String.valueOf(snapshot.overdueBooks())), 2, 1);

        // Actions section
        Label actionsLabel = new Label("System Actions");
        actionsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: " + textSecondary() + "; -fx-padding: 4 0 2 0;");

        GridPane actionsGrid = new GridPane();
        actionsGrid.setHgap(10);
        actionsGrid.setVgap(6);
        ColumnConstraints ac = new ColumnConstraints();
        ac.setPercentWidth(50);
        actionsGrid.getColumnConstraints().addAll(ac, ac);

        // Export reports button
        Button exportBtn = createActionButton(AppTheme.ICON_UPLOAD, "#0D9488", "Export Reports",
                "Generate CSV for overdue & issued books", () -> exportReports(snapshot, toastDisplay));
        AppTheme.installSmartTooltip(exportBtn, "Export overdue and issued book reports as CSV files");

        // Backup button
        Button backupBtn = createActionButton(AppTheme.ICON_SAVE, "#3B82F6", "Create Backup",
                "Snapshot local files to a folder", () -> createBackup(owner, toastDisplay));
        AppTheme.installSmartTooltip(backupBtn, "Create a full backup snapshot of all local library files");

        // Restore button
        Button restoreBtn = createActionButton(AppTheme.ICON_SYNC, "#F59E0B", "Restore Backup",
                "Load data from a backup folder", () -> restoreBackup(owner, toastDisplay));
        AppTheme.installSmartTooltip(restoreBtn, "Restore library data from a previously created backup folder");

        // Import from DB button
        Button importDbBtn = createActionButton(AppTheme.ICON_DOWNLOAD, "#6366F1", "Import from Database",
                "Restore local state from cloud snapshot", () -> restoreFromDatabase(toastDisplay));
        AppTheme.installSmartTooltip(importDbBtn, "Pull and restore a snapshot from the connected database");

        // Seed Sample Data button
        Button seedDataBtn = createActionButton(AppTheme.ICON_ADD, "#8B5CF6", "Seed Sample Data",
                "Inject 15 test books & users into the system", () -> seedSampleData(toastDisplay));
        AppTheme.installSmartTooltip(seedDataBtn, "Populate the system with 15 sample books and test users for demonstration");

        // FIX #4: Manual persist button — lets staff force-save all data immediately
        Button saveNowBtn = createActionButton(AppTheme.ICON_SAVE, "#16A34A", "Save Now",
                "Force-write all data to disk immediately", () -> persistAllDataNow(toastDisplay));
        AppTheme.installSmartTooltip(saveNowBtn, "Immediately flush all in-memory data (books, users, borrow records) to permanent storage");

        // --- NEW MIGRATION SECTION ---
        Label migrationLabel = new Label("System Migration");
        migrationLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: " + textSecondary() + "; -fx-padding: 12 0 2 0;");

        HBox migrationBox = new HBox(10);
        Button exportMigrationBtn = createActionButton(AppTheme.ICON_UPLOAD, "#8B5CF6", "Prepare Migration",
                "Export encrypted .lms file for another PC", () -> exportMigration(owner, toastDisplay));
        AppTheme.installSmartTooltip(exportMigrationBtn, "Package all library data into an encrypted .lms file for transfer to another PC");
        Button importMigrationBtn = createActionButton(AppTheme.ICON_DOWNLOAD, "#0D9488", "Import Migration",
                "Load .lms file from another PC", () -> importMigration(owner, toastDisplay));
        AppTheme.installSmartTooltip(importMigrationBtn, "Import an encrypted .lms migration file from another Library OS installation");
        migrationBox.getChildren().addAll(exportMigrationBtn, importMigrationBtn);

        actionsGrid.add(exportBtn, 0, 0);
        actionsGrid.add(backupBtn, 1, 0);
        actionsGrid.add(restoreBtn, 0, 1);
        actionsGrid.add(importDbBtn, 1, 1);
        actionsGrid.add(seedDataBtn, 0, 2);
        actionsGrid.add(saveNowBtn, 1, 2);

        actionsGrid.setAlignment(Pos.TOP_LEFT);


        // Email configuration status
        HBox emailStatus = new HBox(12);
        emailStatus.setAlignment(Pos.CENTER_LEFT);
        emailStatus.setStyle("-fx-background-color: " + (snapshot.emailConfigured() ? surfaceTint("#16A34A") : surfaceTint("#D97706")) + "; " +
                "-fx-background-radius: 8px; -fx-padding: 8 12;"); // Reduced padding

        StackPane emailIcon = createIconBubble(
                snapshot.emailConfigured() ? AppTheme.ICON_CHECK : AppTheme.ICON_WARNING,
                snapshot.emailConfigured() ? "#16A34A" : "#D97706");

        Label emailText = new Label(snapshot.emailConfigured()
                ? "Email service is ready"
                : "Email notifications not configured");
        emailText.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: " + textSecondary() + ";");

        emailStatus.getChildren().addAll(emailIcon, emailText);

        content.getChildren().addAll(title, statsGrid, actionsLabel, actionsGrid, migrationLabel, migrationBox, emailStatus);

        setContent(content);
    }

    private static VBox createStatCard(String iconPath, String accentColor, String label, String value) {
        VBox card = new VBox(6); // Reduced from 8
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12)); // Reduced from 16
        card.setStyle(cardStyle());

        StackPane iconBubble = createIconBubble(iconPath, accentColor);

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-family: 'Plus Jakarta Sans'; -fx-font-size: 22px; " + // Reduced from 24
                "-fx-font-weight: 700; -fx-text-fill: " + textPrimary() + ";");

        Label labelLabel = new Label(label);
        labelLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + textMuted() + ";"); // Reduced from 12

        card.getChildren().addAll(iconBubble, valueLabel, labelLabel);
        return card;
    }

    private static Button createActionButton(String iconPath, String accentColor,
                                             String title, String description, Runnable action) {
        Button btn = new Button();
        btn.setStyle(cardStyle() + "-fx-cursor: hand;");
        btn.setPrefWidth(220);
        btn.setMinWidth(180);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setMinHeight(52); // Reduced from 60
        btn.setPrefHeight(52);
        btn.setMaxHeight(52);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setContentDisplay(ContentDisplay.LEFT);

        VBox textBox = new VBox(0);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: " + textPrimary() + ";"); // Reduced from 14
        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + textMuted() + ";"); // Reduced from 11
        descLabel.setWrapText(true);
        textBox.getChildren().addAll(titleLabel, descLabel);

        HBox content = new HBox(10, createIconBubble(iconPath, accentColor), textBox); // Reduced spacing
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(4, 10, 4, 10)); // Tighter padding
        HBox.setHgrow(textBox, Priority.ALWAYS);

        btn.setGraphic(content);
        btn.setOnAction(e -> action.run());

        String normalStyle = cardStyle() + "-fx-cursor: hand;";
        String hoverStyle  = cardStyle() + "-fx-cursor: hand; " +
                "-fx-border-color: " + accentColor + "66; -fx-border-width: 1.5; " +
                "-fx-effect: dropshadow(gaussian," + accentColor + "55,16,0,0,4);";
        btn.setOnMouseEntered(e -> {
            btn.setStyle(hoverStyle);
            AppTheme.animateScale(btn, 1.02, 120);
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle(normalStyle);
            AppTheme.animateScale(btn, 1.0, 120);
        });
        btn.setOnMousePressed(e  -> AppTheme.animateScale(btn, 0.96, 55));
        btn.setOnMouseReleased(e -> AppTheme.animateScale(btn, btn.isHover() ? 1.02 : 1.0, 80));
        return btn;
    }

    private static void exportReports(Snapshot snapshot, ToastDisplay toastDisplay) {
        try {
            Path overduePath  = ReportExportService.exportOverdueReportCsv(BookService.getAllOverdueBooks());
            Path issuedPath   = ReportExportService.exportIssuedBooksCsv(BookService.getAllActiveIssueRecords());
            Path requestsPath = ReportExportService.exportBorrowRequestsCsv(BookService.getAllBorrowRequests());
            notifySuccess(toastDisplay, "Reports exported to " + snapshot.exportDirectory() +
                    " (" + overduePath.getFileName() + ", " + issuedPath.getFileName() + ", " + requestsPath.getFileName() + ")");
        } catch (Exception e) {
            notifyError(toastDisplay, "Export failed: " + e.getMessage());
        }
    }

    private static void createBackup(Stage owner, ToastDisplay toastDisplay) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupDir = AppPaths.backupDirectory().resolve(ts);
            Files.createDirectories(backupDir);

            // Copy all .ser files
            Path dataDir = AppPaths.resolveDataDirectory();
            if (Files.exists(dataDir)) {
                try (var stream = Files.list(dataDir)) {
                    stream.filter(p -> p.toString().endsWith(".ser"))
                            .forEach(p -> {
                                try { Files.copy(p, backupDir.resolve(p.getFileName()),
                                        StandardCopyOption.REPLACE_EXISTING); }
                                catch (Exception e) {
                                    java.util.logging.Logger.getLogger("DataManagementView")
                                            .log(java.util.logging.Level.WARNING,
                                                    "Backup copy failed for: " + p.getFileName() + " — " + e.getMessage(), e);
                                }
                            });
                }
            }

            // Persist latest in-memory state
            BookService.persistBooksDatabase();
            UserService.persistDatabase();
            notifySuccess(toastDisplay, "Backup created at " + backupDir.toAbsolutePath());
        } catch (Exception e) {
            notifyError(toastDisplay, "Backup failed: " + e.getMessage());
        }
    }

    private static void restoreBackup(Stage owner, ToastDisplay toastDisplay) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Backup Folder — choose any .ser file inside it");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Serialized data", "*.ser"));
        File chosen = fc.showOpenDialog(owner);
        if (chosen == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        AppTheme.applyTheme(confirm.getDialogPane());
        confirm.setTitle("Restore Backup");
        confirm.setHeaderText("Restore from: " + chosen.getParentFile().getName() + "?");
        confirm.setContentText("This will overwrite current data. The app should be restarted after restore.");
        confirm.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            try {
                Path backupFolder = chosen.getParentFile().toPath();
                Path dataDir = AppPaths.resolveDataDirectory();
                Files.createDirectories(dataDir);

                try (var stream = Files.list(backupFolder)) {
                    stream.filter(p -> p.toString().endsWith(".ser"))
                            .forEach(p -> {
                                try { Files.copy(p, dataDir.resolve(p.getFileName()),
                                        StandardCopyOption.REPLACE_EXISTING); }
                                catch (Exception e) {
                                    java.util.logging.Logger.getLogger("DataManagementView")
                                            .log(java.util.logging.Level.WARNING,
                                                    "Restore copy failed for: " + p.getFileName() + " — " + e.getMessage(), e);
                                }
                            });
                }
                notifySuccess(toastDisplay, "Backup restored. Restart Library OS to load the restored data.");
            } catch (Exception e) {
                notifyError(toastDisplay, "Restore failed: " + e.getMessage());
            }
        });
    }

    private static void restoreFromDatabase(ToastDisplay toastDisplay) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        AppTheme.applyTheme(confirm.getDialogPane());
        confirm.setTitle("Import from Database");
        confirm.setHeaderText("Sync with cloud snapshot?");
        confirm.setContentText("This will replace all local data with the latest mirrored state from the database. " +
                "The app should be restarted immediately after sync.");

        confirm.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            try {
                com.example.storage.DataStorage.syncFromDatabase(AppPaths.resolveDataDirectory());
                notifySuccess(toastDisplay, "Sync complete. Restart Library OS to load the restored data.");
            } catch (Exception e) {
                notifyError(toastDisplay, "Sync failed: " + e.getMessage());
            }
        });
    }

    private static void error(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        AppTheme.applyTheme(a.getDialogPane());
        a.setTitle(title); a.setHeaderText(title); a.setContentText(msg);
        a.showAndWait();
    }

    /**
     * Handles the UI workflow for exporting a migration package.
     */
    private static void exportMigration(Stage owner, ToastDisplay toastDisplay) {
        com.example.entities.AppConfiguration cfg = com.example.services.AppConfigurationService.getConfiguration();
        String currentSecret = cfg.getLibraryMasterKey();

        TextInputDialog dialog = new TextInputDialog(currentSecret);
        AppTheme.applyTheme(dialog.getDialogPane());
        dialog.setTitle("Migration Security");
        dialog.setHeaderText("Confirm Administrative Secret");
        dialog.setContentText("Verify your Administrative Secret below. This key will lock the migration file.\n\n" +
                "Hint: Use your primary Admin login password for best results.");

        dialog.showAndWait().ifPresent(password -> {
            if (password.length() < 4) {
                notifyError(toastDisplay, "Secret/Password is too short.");
                return;
            }

            // Sync the master key if they updated it here
            if (!password.equals(currentSecret)) {
                cfg.setLibraryMasterKey(password);
                try {
                    com.example.services.AppConfigurationService.updateConfiguration(cfg);
                    com.example.services.SecurityProvider.setLibraryMasterKey(password);
                } catch (Exception e) {
                    java.util.logging.Logger.getLogger("DataManagementView")
                            .log(java.util.logging.Level.WARNING,
                                    "Failed to persist master-key update before migration export: " + e.getMessage(), e);
                }
            }

            FileChooser fc = new FileChooser();
            fc.setTitle("Save Migration Package");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Library Migration (.lms)", "*.lms"));
            fc.setInitialFileName("Library_Migration_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".lms");
            File file = fc.showSaveDialog(owner);

            if (file != null) {
                try {
                    com.example.services.MigrationService.prepareMigrationPackage(file, password);
                    notifySuccess(toastDisplay, "Migration package created! Locked with your Administrative Secret.");
                } catch (Exception e) {
                    notifyError(toastDisplay, "Migration failed: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Handles the UI workflow for importing a migration package.
     */
    private static void importMigration(Stage owner, ToastDisplay toastDisplay) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Migration Package");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Library Migration (.lms)", "*.lms"));
        File file = fc.showOpenDialog(owner);

        if (file != null) {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Migration Import");
            dialog.setHeaderText("Unlock Migration Package");
            AppTheme.applyTheme(dialog.getDialogPane());

            PasswordField pf = new PasswordField();
            pf.setPromptText("Administrative Secret");
            pf.setPrefWidth(300);
            
            VBox box = new VBox(10, new Label("Enter the Administrative Secret from the source system:"), pf);
            box.setPadding(new Insets(20));
            dialog.getDialogPane().setContent(box);
            
            ButtonType importType = new ButtonType("Import Data", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, importType);
            
            dialog.setResultConverter(bt -> bt == importType ? pf.getText() : null);

            dialog.showAndWait().ifPresent(password -> {
                new Thread(() -> {
                    try {
                        com.example.services.MigrationService.importMigrationPackage(file, password);

                        // The import restores:
                        // 1. All .ser data files to the branch data directory
                        // 2. Library-scoped app config (contains libraryMasterKey, DB creds)
                        // 3. Libraries registry
                        // MigrationService already calls setLibraryMasterKey() and updateConfiguration()
                        // inside importMigrationPackage. We now reload everything in the right order:

                        // Step 1: reload config first so AppPaths resolves the correct branch dir
                        com.example.services.AppConfigurationService.reloadConfiguration();
                        com.example.entities.LibrariesDB.getInstance().forceReload();

                        // Step 2: select the first known library so UsersDB/BooksDB use correct path
                        com.example.entities.AppConfiguration cfg =
                                com.example.services.AppConfigurationService.getConfiguration();
                        String libDisplay = cfg.getCurrentLibraryDisplayName();
                        if (libDisplay == null || libDisplay.isBlank()) {
                            // Try to pick the first known library from the restored registry
                            java.util.List<String> known =
                                    com.example.entities.LibrariesDB.getInstance().getLibraries();
                            if (!known.isEmpty()) libDisplay = known.get(0);
                        }
                        if (libDisplay != null && !libDisplay.isBlank()) {
                            try { com.example.services.AppConfigurationService.selectKnownLibrary(libDisplay); }
                            catch (Exception ignored) {}
                        }

                        // Step 3: reload all stores from the now-correct branch path
                        com.example.entities.UsersDB.getInstance().forceReload();
                        com.example.entities.BooksDB.getInstance().forceReload();
                        com.example.services.BorrowRequestService.forceReload();

                        // Prompt restart on the FX thread
                        javafx.application.Platform.runLater(() -> {
                            Alert restartDlg = new Alert(Alert.AlertType.CONFIRMATION);
                            restartDlg.setTitle("Import Complete");
                            restartDlg.setHeaderText("Migration imported successfully");
                            restartDlg.setContentText(
                                    "Data has been imported and encryption keys have been restored.\n\n" +
                                    "A restart is required to ensure all UI components load from\n" +
                                    "the new data. Restart now?");
                            restartDlg.initOwner(owner);
                            restartDlg.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                            AppTheme.applyTheme(restartDlg.getDialogPane());
                            restartDlg.setOnShowing(ev -> {
                                Button yBtn = (Button) restartDlg.getDialogPane().lookupButton(ButtonType.YES);
                                if (yBtn != null) { yBtn.getStyleClass().add("btn-danger"); yBtn.setStyle(""); }
                            });
                            restartDlg.showAndWait()
                                    .filter(bt -> bt == ButtonType.YES)
                                    .ifPresent(bt -> { javafx.application.Platform.exit(); System.exit(0); });
                        });

                    } catch (Exception e) {
                        String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        javafx.application.Platform.runLater(() ->
                                notifyError(toastDisplay, "Import failed: " + errMsg +
                                        "\n\nVerify your Administrative Secret is correct."));
                    }
                }, "migration-import").start();
            });
        }
    }

    /**
     * FIX #4: Immediately flushes all in-memory data stores to permanent storage.
     * Persists Books, Users, BorrowRequests and PaymentApprovals in one shot.
     * Useful when the admin wants to guarantee data is saved before shutdown or
     * maintenance, without waiting for the next automatic write cycle.
     */
    private static void persistAllDataNow(ToastDisplay toastDisplay) {
        new Thread(() -> {
            StringBuilder errors = new StringBuilder();
            int saved = 0;

            // 1. Books, issue records, borrower details
            try {
                com.example.entities.BooksDB.getInstance().saveAllData();
                saved++;
            } catch (Exception e) {
                errors.append("Books DB: ").append(e.getMessage()).append("\n");
            }

            // 2. Users
            try {
                com.example.services.UserService.persistDatabase();
                saved++;
            } catch (Exception e) {
                errors.append("Users DB: ").append(e.getMessage()).append("\n");
            }

            // 3. Borrow requests
            try {
                com.example.services.BorrowRequestService.persist();
                saved++;
            } catch (Exception e) {
                errors.append("Borrow requests: ").append(e.getMessage()).append("\n");
            }

            // 4. Payment approval requests
            try {
                com.example.services.PaymentApprovalService.persistPublic();
                saved++;
            } catch (Exception e) {
                errors.append("Payment approvals: ").append(e.getMessage()).append("\n");
            }

            final int finalSaved = saved;
            final String errorText = errors.toString();
            javafx.application.Platform.runLater(() -> {
                if (toastDisplay != null) {
                    if (errorText.isEmpty()) {
                        toastDisplay.showSuccess("All data saved to disk (" + finalSaved + " stores flushed).");
                    } else {
                        toastDisplay.showError("Partial save (" + finalSaved + "/4 stores): " + errorText.trim());
                    }
                }
            });
        }, "manual-persist").start();
    }

    private static void seedSampleData(ToastDisplay toastDisplay) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        AppTheme.applyTheme(confirm.getDialogPane());
        confirm.setTitle("Seed Sample Data");
        confirm.setHeaderText("Inject 15 test records?");
        confirm.setContentText("This will add diverse books and a few test users for testing purposes. " +
                "Existing data will not be deleted.");

        confirm.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            try {
                com.example.services.BookService.seedSampleData();
                com.example.services.UserService.seedSampleData();
                notifySuccess(toastDisplay, "Seeding complete! Refresh to see new data.");
            } catch (Exception e) {
                notifyError(toastDisplay, "Seeding failed: " + e.getMessage());
            }
        });
    }

    private static void notifySuccess(ToastDisplay toastDisplay, String message) {
        if (toastDisplay != null) {
            toastDisplay.showSuccess(message);
            return;
        }
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        AppTheme.applyTheme(a.getDialogPane());
        a.setTitle("Completed");
        a.setHeaderText("Completed");
        a.setContentText(message);
        a.showAndWait();
    }

    private static void notifyError(ToastDisplay toastDisplay, String message) {
        if (toastDisplay != null) {
            toastDisplay.showError(message);
            return;
        }
        error("Operation Failed", message);
    }

    private static StackPane createIconBubble(String iconPath, String accentColor) {
        StackPane bubble = new StackPane(AppTheme.createIcon(iconPath, 18));
        bubble.setMinSize(32, 32);
        bubble.setPrefSize(32, 32);
        bubble.setStyle("-fx-background-color:" + surfaceTint(accentColor) + "; -fx-background-radius:10px;");
        return bubble;
    }



    private static String cardStyle() {
        return "-fx-background-color:" + (AppTheme.darkMode ? "#1E293B" : "white") + "; " +
                "-fx-background-radius: 12px; -fx-border-radius: 12px; " +
                "-fx-border-color: " + (AppTheme.darkMode ? "#334155" : "#E2E8F0") + "; -fx-border-width: 1;";
    }

    private static String surfaceTint(String color) {
        return color + "22";
    }

    private static String textPrimary() {
        return AppTheme.darkMode ? "#F8FAFC" : "#0F172A";
    }

    private static String textSecondary() {
        return AppTheme.darkMode ? "#E2E8F0" : "#374151";
    }

    private static String textMuted() {
        return AppTheme.darkMode ? "#94A3B8" : "#64748B";
    }

    public record Snapshot(int totalBooks, int totalCopies, int availableCopies,
                           int issuedCopies, int overdueBooks, int totalUsers,
                           int pendingRequests, double totalFines,
                           String exportDirectory, boolean emailConfigured) {}
}