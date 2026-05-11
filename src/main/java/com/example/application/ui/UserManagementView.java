package com.example.application.ui;

import com.example.application.ToastDisplay;
import com.example.entities.User;
import com.example.entities.UserRole;
import com.example.services.SecurityProvider;
import com.example.services.UserService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <h1>UserManagementView</h1>
 * 
 * <p>A comprehensive administrative console for managing library personnel and
 * members. This view provides a high-fidelity interface for overseeing user accounts,
 * adjusting roles, and processing new registrations.</p>
 * 
 * <h3>Key Administrative Tools:</h3>
 * <ul>
 *   <li><b>Identity Management:</b> Searchable directory of all registered users
 *       linked directly to {@link UserService}.</li>
 *   <li><b>Approval Workflow:</b> Integrated toggles for activating pending
 *       staff and librarian accounts.</li>
 *   <li><b>Role-Based Modification:</b> Secure interfaces for adjusting permissions
 *       without exposing critical system accounts.</li>
 *   <li><b>Audit Readiness:</b> Visual indicators for user status (Active/Inactive)
 *       to ensure library security compliance.</li>
 * </ul>
 * 
 * <h3>Interactive Design:</h3>
 * <ul>
 *   <li><b>Action Diversification:</b> Uses semantic coloring (e.g., Indigo for Edit, 
 *       Red for Delete) to prevent accidental administrative errors.</li>
 *   <li><b>Responsive Data:</b> Employs constrained table resizing and real-time 
 *       search filtering for a snappy management experience.</li>
 *   <li><b>Premium Effects:</b> Utilizes {@link AppTheme} for consistent icons,
 *       buttons, and entrance transitions.</li>
 * </ul>
 * 
 * @author Yogesh
 * @version 3.1
 */
public class UserManagementView extends BorderPane {

    private final String currentUserId;
    private final boolean isAdmin;
    private final ToastDisplay toast;
    private final Runnable onDataChanged;
    private TableView<User> table;
    private TextField searchField;

    /**
     * Initializes the user management view.
     * 
     * @param currentUserId The ID of the currently logged-in administrator.
     * @param toast Reference to the toast notification system for feedback.
     * @param onDataChanged Callback triggered when user data is modified, used to refresh global state.
     */
    public UserManagementView(String currentUserId, ToastDisplay toast, Runnable onDataChanged) {
        this.currentUserId = currentUserId;
        this.isAdmin = UserService.isAdmin(currentUserId);
        this.toast = toast;
        this.onDataChanged = onDataChanged;
        initUI();
    }

    /** Sets up the primary UI structure, including the top action bar and the user table. */
    private void initUI() {
        setStyle("-fx-background-color: " + pageBackground() + ";");
        setPadding(new Insets(0));
        // Ensure no gaps on left/top edges
        BorderPane.setMargin(this, new Insets(0));

        VBox topBar = new VBox(16);
        topBar.setPadding(new Insets(20, 24, 16, 24));
        topBar.setStyle("-fx-background-color:" + pageBackground() + ";" +
                "-fx-border-color: " + (AppTheme.darkMode ? "#1E293B" : "#E2E8F0") + ";" +
                "-fx-border-width: 0 0 1 0;");

        // Title section
        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBadge = new StackPane(AppTheme.createIcon(AppTheme.ICON_USER, 20));
        iconBadge.setMinSize(40, 40);
        iconBadge.setStyle("-fx-background-color: #8B5CF622; -fx-background-radius: 12px;");

        VBox titleTxt = new VBox(2);
        Label title = new Label("User Management");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: " + textPrimary() + ";");
        Label sub = new Label("Manage access control, user roles and pending registrations");
        sub.setStyle("-fx-font-size: 13px; -fx-text-fill: #94A3B8;");
        titleTxt.getChildren().addAll(title, sub);

        Region sp1 = new Region(); HBox.setHgrow(sp1, Priority.ALWAYS);

        Button addBtn = AppTheme.createIconTextButton("Add New User", AppTheme.ICON_ADD, AppTheme.ButtonStyle.PRIMARY);
        addBtn.setOnAction(e -> handleAddUser());

        titleRow.getChildren().addAll(iconBadge, titleTxt, sp1, addBtn);

        // Search bar
        HBox searchRow = new HBox(12);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Search by username, name or email...");
        searchField.setPrefWidth(350);
        searchField.setPrefHeight(40);
        searchField.setStyle(inputStyle());
        searchField.textProperty().addListener((o, old, v) -> reload());

        // Prevent ghost :hover state after theme rebuild — JavaFX keeps the pseudo-state
        // when a new button is constructed at the same screen position as the old one.
        // Requesting focus on the search field clears the hover pseudo-state properly.
        // searchField is captured AFTER it is assigned, so the reference is safe.
        final TextField focusTarget = searchField;
        addBtn.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                javafx.application.Platform.runLater(focusTarget::requestFocus);
            }
        });

        Button refreshBtn = AppTheme.createIconButton(AppTheme.ICON_SYNC, "Refresh users", AppTheme.ButtonStyle.GHOST);
        refreshBtn.setOnAction(e -> reload());

        searchRow.getChildren().addAll(AppTheme.createIcon(AppTheme.ICON_SEARCH, 18), searchField, refreshBtn);

        topBar.getChildren().addAll(titleRow, searchRow);
        setTop(topBar);

        // Table
        table = new TableView<>();
        table.setFixedCellSize(48.0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("table-view");

        // Wrap table with side padding so it doesn't touch the viewport edge
        VBox tableWrapper = new VBox(table);
        tableWrapper.setPadding(new Insets(12, 20, 20, 20));
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(tableWrapper);

        TableColumn<User, String> uCol = col("Username",  u -> u.getUserId(), 130);
        TableColumn<User, String> nCol = col("Full Name", u -> u.getFullName(), 180);
        TableColumn<User, String> rCol = col("Role",      u -> u.getRole().getDisplayName(), 110);

        TableColumn<User, Void> sCol = new TableColumn<>();
        Label sHeader = new Label("Status");
        sHeader.setStyle("-fx-padding:0; -fx-alignment:CENTER;");
        sCol.getStyleClass().add("col-center");
        sHeader.maxWidthProperty().bind(sCol.widthProperty());
        sCol.setGraphic(sHeader);
        sCol.setPrefWidth(140);
        sCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                User u = getTableRow().getItem();
                String txt = u.getUserId().equals(currentUserId) ? "Active (You)" : u.isActive() ? "Active" : "Pending";
                Label chip = new Label(txt);
                chip.getStyleClass().addAll("chip", u.isActive() ? "chip-success" : "chip-warning");
                setGraphic(chip);
                setAlignment(Pos.CENTER);
            }

        });

        TableColumn<User, Void> aCol = new TableColumn<>();
        Label aHeader = new Label("Actions");
        aHeader.setStyle("-fx-padding:0; -fx-alignment:CENTER;");
        aHeader.maxWidthProperty().bind(aCol.widthProperty());
        aCol.setGraphic(aHeader);
        aCol.getStyleClass().add("col-center");
        aCol.setPrefWidth(120);
        aCol.setCellFactory(c -> new TableCell<>() {
            final Button apprBtn = actionBtn(AppTheme.ICON_CHECK, "Approve User", "#16A34A", AppTheme.ButtonStyle.SUCCESS);
            final Button editBtn = actionBtn(AppTheme.ICON_EDIT, "Edit User Profile", "#6366F1", AppTheme.ButtonStyle.INDIGO);
            final Button delBtn  = actionBtn(AppTheme.ICON_DELETE, "Delete User", "#DC2626", AppTheme.ButtonStyle.DANGER);

            {
                apprBtn.setOnAction(e -> approveUser(getTableRow().getItem()));
                editBtn.setOnAction(e -> editUser(getTableRow().getItem()));
                delBtn.setOnAction(e -> deleteUser(getTableRow().getItem()));
            }

            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                User u = getTableRow().getItem();
                HBox box = new HBox(6);
                box.setAlignment(Pos.CENTER);
                if (!u.isActive()) box.getChildren().add(apprBtn);
                box.getChildren().add(editBtn);
                if (!u.getUserId().equals(currentUserId) && (isAdmin || !u.getRole().isAdmin())) box.getChildren().add(delBtn);
                setGraphic(box);
                setAlignment(Pos.CENTER);
            }
        });

        table.getColumns().add(uCol);
        table.getColumns().add(nCol);
        table.getColumns().add(rCol);
        table.getColumns().add(sCol);
        table.getColumns().add(aCol);
        reload();
    }

    /**
     * Filters and updates the displayed user list in the table.
     * 
     * @param users The list of all users retrieved from the service layer.
     */
    public void updateUsers(List<User> users) {
        if (users == null) return;
        String q = searchField.getText().trim().toLowerCase();
        List<User> list = users.stream()
                .filter(u -> q.isEmpty()
                        || (u.getUserId() != null && u.getUserId().toLowerCase().contains(q))
                        || (u.getFullName() != null && u.getFullName().toLowerCase().contains(q))
                        || (u.getEmail() != null && SecurityProvider.decryptUserField(
                                u.getEmail(), u.getUserId(), "", "").toLowerCase().contains(q)))
                .sorted((u1, u2) -> {
                    // Sort inactive (pending) users first to draw admin attention
                    if (u1.isActive() != u2.isActive()) return u1.isActive() ? 1 : -1;
                    return u1.getUserId().compareToIgnoreCase(u2.getUserId());
                })
                .collect(Collectors.toList());
        table.setItems(FXCollections.observableArrayList(list));
    }

    /** Synchronizes the view with the persistence layer. */
    public void reload() {
        updateUsers(UserService.getAllUsers());
        if (onDataChanged != null) onDataChanged.run();
    }

    /** Opens the user registration dialog in administrative mode. */
    private void handleAddUser() {
        RegistrationDialog.show((Stage)getScene().getWindow(), false, true).ifPresent(req -> {
            try {
                if (UserService.userExists(req.username())) { toast.showError("Username taken."); return; }
                UserService.createUser(req.username(), req.password(), req.role());
                User u = UserService.getUserById(req.username());
                // Must unlock with the plain password BEFORE setting email/contact so that
                // writeObject encrypts them with the user-specific key, not the system master key.
                // Without this, plainPasswordForSession is null (transient) on a fresh read,
                // and serialization falls back to the master key → hash values on other machines.
                u.unlockProfile(req.password());
                u.setEmail(req.email());
                u.setContactNumber(req.phoneNumber());
                u.setActive(!req.pendingApproval());
                UserService.updateUser(u);
                reload();
                toast.showSuccess("User created: " + u.getUserId());
            } catch (Exception ex) { toast.showError(ex.getMessage()); }
        });
    }

    /** Activates a pending user account and sends a notification email. */
    private void approveUser(User u) {
        if (u == null) return;
        try {
            u.setActive(true);
            UserService.updateUser(u);

            javafx.application.Platform.runLater(() -> {
                reload();
                table.refresh();
                toast.showSuccess("User approved: " + u.getUserId());
            });

            String approvalEmail = SecurityProvider.decryptUserField(u.getEmail(), u.getUserId(), "", "");
            if (approvalEmail != null && !approvalEmail.isBlank() && approvalEmail.contains("@")) {
                new Thread(() -> {
                    try {
                        com.example.services.ReminderService.sendAccountApprovalEmail(u);
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() ->
                                toast.showError("Failed to send approval email: " + ex.getMessage()));
                    }
                }, "approval-email").start();
            }
        } catch (Exception ex) {
            toast.showError("Approval failed: " + ex.getMessage());
        }
    }

    /** Opens the profile editor for a specific user. */
    private void editUser(User u) {
        if (u == null) return;
        UserAccountDialogs.editUser((Stage)getScene().getWindow(), u, currentUserId, isAdmin, toast);
        reload();
    }

    /** Permanently deletes a user account after administrative confirmation. */
    private void deleteUser(User u) {
        if (u == null) return;

        User actor = UserService.getUserById(currentUserId);
        if (actor != null && actor.getRole() == UserRole.LIBRARIAN && u.isAdmin()) {
            toast.showError("Security Violation: Librarians are not authorized to delete administrator accounts.");
            return;
        }

        Alert conf = new Alert(Alert.AlertType.WARNING, "Delete user \"" + u.getUserId() + "\"? This cannot be undone.", ButtonType.YES, ButtonType.NO);
        conf.setTitle("Confirm Deletion");
        AppTheme.applyTheme(conf.getDialogPane());
        conf.showAndWait().filter(bt -> bt == ButtonType.YES).ifPresent(bt -> {
            try {
                User target = u;
                if (target.isAdmin() && target.isActive()) {
                    long adminCount = UserService.getAllUsers().stream()
                            .filter(user -> user.isAdmin() && user.isActive())
                            .count();
                    if (adminCount <= 1) {
                        toast.showError("Cannot delete the last active administrator.");
                        return;
                    }
                }

                UserService.deleteUser(target.getUserId());
                reload();
                toast.showSuccess("User deleted.");
            } catch (Exception ex) { toast.showError(ex.getMessage()); }
        });
    }

    /** Helper for column generation. */
    private <T> TableColumn<T, String> col(String name, java.util.function.Function<T, String> fn, double w) {
        TableColumn<T, String> c = new TableColumn<>(name);
        c.setPrefWidth(w);
        c.getStyleClass().add("col-left");
        c.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(fn.apply(data.getValue())));
        return c;
    }

    /**
     * Creates a compact, styled action button for use within table cells.
     * 
     * @param icon The SVG icon path.
     * @param tip Tooltip text for accessibility and guidance.
     * @param color HEX color code for the icon/border.
     * @param style The theme button style to apply.
     * @return A styled Button instance.
     */
    private Button actionBtn(String icon, String tip, String color, AppTheme.ButtonStyle style) {
        Button b = AppTheme.createIconButton(icon, tip, style);
        b.setStyle(b.getStyle() + "; -fx-min-width:32px; -fx-min-height:32px;");
        return b;
    }

    private String inputStyle() {
        return "-fx-background-color: " + (AppTheme.darkMode ? "#1E293B" : "#FFFFFF") + "; " +
                "-fx-border-color: " + (AppTheme.darkMode ? "#334155" : "#E2E8F0") + "; " +
                "-fx-border-width: 1.5; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-padding: 8 14;";
    }

    private String pageBackground() { return AppTheme.darkMode ? "#0F172A" : "#F8FAFC"; }
    private String textPrimary() { return AppTheme.darkMode ? "#F1F5F9" : "#1E293B"; }
}