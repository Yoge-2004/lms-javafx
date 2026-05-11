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
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;


/**
 * <h1>UserAccountDialogs</h1>
 *
 * <p>A centralized utility class for generating secure, role-aware account 
 * management interfaces. This class provides standardized dialogs for profile
 * editing, password modification, and account deletion across all user tiers.</p>
 *
 * <h3>Security Model:</h3>
 * <ul>
 *   <li><b>PII Handling:</b> Utilizes {@link SecurityProvider#decrypt} for email
 *       and contact number fields, ensuring sensitive data is only decrypted within 
 *       the authenticated UI context.</li>
 *   <li><b>Integrity:</b> Enforces strict server-side validation through 
 *       {@link UserService} before persisting profile updates.</li>
 *   <li><b>Access Control:</b> Dialogs adapt their interface based on the 
 *       authenticated user's {@link UserRole}.</li>
 * </ul>
 *
 * <h3>Interactive Design:</h3>
 * <ul>
 *   <li><b>Unified Layout:</b> Standardizes on {@link GridPane} for reliable 
 *       multi-environment rendering.</li>
 *   <li><b>Consistent UX:</b> Integrates reusable components like {@code createPasswordBox}
 *       to provide standardized visibility toggles and validation feedback.</li>
 *   <li><b>Motion:</b> Inherits theme animations via {@link AppTheme#applyTheme}.</li>
 * </ul>
 *
 * @author Yogesh
 * @version 3.4
 */
public class UserAccountDialogs {

    // ── Profile editor (available to ALL users) ───────────────────

    public static boolean showProfileEditor(Stage owner, String userId, String password) {
        User user;
        try {
            user = UserService.getUserById(userId);
        } catch (Exception e) {
            return false;
        }

        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Edit Profile");
        dlg.initOwner(owner);
        dlg.setResizable(true);

        DialogPane pane = dlg.getDialogPane();
        AppTheme.applyTheme(pane);
        pane.setPrefWidth(460);
        pane.setPrefHeight(380);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(20));

        TextField firstField = field(user.getFirstName());
        TextField lastField = field(user.getLastName());

        // Decrypt fields using the provided session password
        String decryptedEmail = SecurityProvider.decryptUserField(user.getEmail(), userId, password, user.getSalt());
        String decryptedContact = SecurityProvider.decryptUserField(user.getContactNumber(), userId, password, user.getSalt());

        TextField emailField = field(decryptedEmail);
        TextField contactField = field(decryptedContact);

        grid.add(bold("First Name"), 0, 0);
        grid.add(firstField, 1, 0);
        grid.add(bold("Last Name"), 0, 1);
        grid.add(lastField, 1, 1);
        grid.add(bold("Email"), 0, 2);

        // Email field + OTP verify button side by side
        Button verifyEmailBtn = new Button("Verify");
        verifyEmailBtn.getStyleClass().add("btn-primary");
        verifyEmailBtn.setStyle("-fx-font-size:12px; -fx-padding:6 12; -fx-background-radius:8px;");
        verifyEmailBtn.setDisable(true); // enabled only when email changes

        // Track whether the new email has been OTP-verified
        java.util.concurrent.atomic.AtomicBoolean emailVerified = new java.util.concurrent.atomic.AtomicBoolean(
                decryptedEmail != null && !decryptedEmail.isBlank()); // existing email already trusted
        java.util.concurrent.atomic.AtomicReference<String> pendingOtp = new java.util.concurrent.atomic.AtomicReference<>(null);
        Label err = errorLabel();

        emailField.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean changed = !newVal.trim().equalsIgnoreCase(decryptedEmail == null ? "" : decryptedEmail.trim());
            verifyEmailBtn.setDisable(!changed || newVal.trim().isBlank());
            if (changed) emailVerified.set(false);
        });

        verifyEmailBtn.setOnAction(e -> {
            String newEmail = emailField.getText().trim();
            if (newEmail.isBlank()) return;
            // Generate a 6-digit OTP
            String otp = String.format("%06d", new java.util.Random().nextInt(1_000_000));
            pendingOtp.set(otp);
            emailVerified.set(false);
            // Send OTP email in background
            Thread otpThread = new Thread(() -> {
                try {
                    com.example.services.EmailService.sendEmail(newEmail,
                            "Library OS — Email Verification Code",
                            "Your email verification code is: " + otp +
                                    "\n\nThis code expires in 10 minutes. Do not share it.");
                    javafx.application.Platform.runLater(() -> {
                        // Prompt user to enter code
                        javafx.scene.control.TextInputDialog otpDlg = new javafx.scene.control.TextInputDialog();
                        otpDlg.setTitle("Verify Email");
                        otpDlg.setHeaderText("Enter the 6-digit code sent to " + newEmail);
                        otpDlg.setContentText("Verification code:");
                        AppTheme.applyTheme(otpDlg.getDialogPane());
                        otpDlg.showAndWait().ifPresent(entered -> {
                            if (entered.trim().equals(pendingOtp.get())) {
                                emailVerified.set(true);
                                verifyEmailBtn.setText("✓ Verified");
                                verifyEmailBtn.setDisable(true);
                                verifyEmailBtn.setStyle("-fx-background-color:#16A34A;-fx-text-fill:white;" +
                                        "-fx-font-size:12px;-fx-padding:6 12;-fx-background-radius:8px;");
                            } else {
                                err.setText("Incorrect code. Please try again.");
                                err.setVisible(true);
                            }
                        });
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        err.setText("Could not send verification email: " + ex.getMessage());
                        err.setVisible(true);
                    });
                }
            }, "otp-email-sender");
            otpThread.setDaemon(true);
            otpThread.start();
        });

        HBox emailRow = new HBox(8, emailField, verifyEmailBtn);
        HBox.setHgrow(emailField, Priority.ALWAYS);
        emailRow.setAlignment(Pos.CENTER_LEFT);

        grid.add(emailRow, 1, 2);
        grid.add(bold("Contact"), 0, 3);
        grid.add(contactField, 1, 3);

        grid.add(err, 0, 4, 2, 1);

        ColumnConstraints c0 = new ColumnConstraints(120);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);

        pane.setContent(grid);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        styleOkBtn(pane, "Save Changes");
        styleSecondaryBtn(pane, ButtonType.CANCEL, "Cancel");

        Button saveBtn = (Button) pane.lookupButton(ButtonType.OK);
        if (saveBtn != null) {
            saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                String newEmail = emailField.getText().trim();
                boolean emailChanged = !newEmail.equalsIgnoreCase(decryptedEmail == null ? "" : decryptedEmail.trim());
                if (emailChanged && !newEmail.isBlank() && !emailVerified.get()) {
                    err.setText("Please verify the new email address before saving.");
                    err.setVisible(true);
                    ev.consume();
                }
            });
        }

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return false;
            try {
                user.unlockProfile(password); // restore plainPasswordForSession before setting PII
                user.setFirstName(firstField.getText());
                user.setLastName(lastField.getText());
                user.setEmail(emailField.getText());
                user.setContactNumber(contactField.getText());
                UserService.updateUser(user);
                return true;
            } catch (Exception e) {
                err.setText(e.getMessage());
                err.setVisible(true);
                return false;
            }
        });

        boolean success = dlg.showAndWait().orElse(false);
        if (success) {
            ToastDisplay toast = findToastDisplay(owner);
            if (toast != null) {
                toast.showSuccess("Profile updated successfully!");
            }
        }
        return success;
    }

    // ── Password editor (available to ALL users) ──────────────────

    public static boolean showPasswordEditor(Stage owner, String userId) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Change Password");
        dlg.initOwner(owner);
        dlg.setResizable(true);

        DialogPane pane = dlg.getDialogPane();
        AppTheme.applyTheme(pane);
        pane.setPrefWidth(460);
        pane.setPrefHeight(340);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(20));

        HBox curBox = createPasswordBox("Current password");
        HBox newBox = createPasswordBox("New password (min 4 chars)");
        HBox confBox = createPasswordBox("Re-enter new password");

        PasswordField curField = (PasswordField) ((StackPane) curBox.getChildren().get(0)).getChildren().get(0);
        PasswordField newField = (PasswordField) ((StackPane) newBox.getChildren().get(0)).getChildren().get(0);
        PasswordField confField = (PasswordField) ((StackPane) confBox.getChildren().get(0)).getChildren().get(0);

        Label feedback = new Label();
        feedback.setStyle("-fx-font-size: 11px;");
        Label err = errorLabel();

        newField.textProperty().addListener((o, old, v) -> checkMatch(feedback, v, confField.getText()));
        confField.textProperty().addListener((o, old, v) -> checkMatch(feedback, newField.getText(), v));

        grid.add(bold("Current Password"), 0, 0);
        grid.add(curBox, 1, 0);
        grid.add(bold("New Password"), 0, 1);
        grid.add(newBox, 1, 1);
        grid.add(bold("Confirm"), 0, 2);
        grid.add(confBox, 1, 2);
        grid.add(feedback, 1, 3);
        grid.add(err, 0, 4, 2, 1);

        ColumnConstraints c0 = new ColumnConstraints(140);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);

        pane.setContent(grid);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Button ok = styleOkBtn(pane, "Update Password");
        styleSecondaryBtn(pane, ButtonType.CANCEL, "Cancel");

        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                User u = UserService.getUserById(userId);
                if (!u.checkPassword(curField.getText())) {
                    err.setText("Current password is incorrect");
                    err.setVisible(true);
                    ev.consume();
                    return;
                }
                if (newField.getText().equals(curField.getText())) {
                    err.setText("New password cannot be the same as current password");
                    err.setVisible(true);
                    ev.consume();
                    return;
                }
                if (newField.getText().length() < 4) {
                    err.setText("New password needs 4+ characters");
                    err.setVisible(true);
                    ev.consume();
                    return;
                }
                if (!newField.getText().equals(confField.getText())) {
                    err.setText("New passwords do not match");
                    err.setVisible(true);
                    ev.consume();
                    return;
                }
            } catch (Exception e) {
                err.setText(e.getMessage());
                err.setVisible(true);
                ev.consume();
            }
        });

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK)
                return false;
            try {
                User u = UserService.getUserById(userId);
                String newPass = newField.getText();
                u.setPassword(newPass);
                UserService.updateUser(u);
                com.example.application.LibraryApp.updateSessionPassword(newPass);
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        boolean success = dlg.showAndWait().orElse(false);
        if (success) {
            ToastDisplay toast = findToastDisplay(owner);
            if (toast != null) toast.showSuccess("Password changed successfully!");
        }
        return success;
    }

    // ── User Management (admin/librarian) ────────────────────────

    @SuppressWarnings("unchecked")
    public static void showUserManagement(Stage owner, String currentUserId, ToastDisplay toastDisplay) {
        boolean isAdmin = UserService.isAdmin(currentUserId);

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("User Management");
        dlg.initOwner(owner);
        dlg.setResizable(true);

        DialogPane pane = dlg.getDialogPane();
        AppTheme.applyTheme(pane);
        pane.setPrefWidth(840);
        pane.setPrefHeight(560);

        // Header + Add button
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 12, 0));
        Label heading = new Label("User Management");
        heading.setStyle("-fx-font-size:18px;-fx-font-weight:700;-fx-text-fill:" +
                (AppTheme.darkMode ? "#F8FAFC" : "#0F172A") + ";");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button addBtn = new Button("+ Add User");
        addBtn.setStyle("-fx-background-color:#0D9488;-fx-text-fill:white;" +
                "-fx-font-weight:600;-fx-background-radius:8px;-fx-padding:8 18;-fx-cursor:hand;");
        topBar.getChildren().addAll(heading, sp, addBtn);

        // Table
        TableView<User> table = new TableView<>();
        table.setFixedCellSize(44.0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("table-view");
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPrefHeight(400);

        TableColumn<User, String> uCol = col("Username", u -> u.getUserId(), 130);
        TableColumn<User, String> nCol = col("Name", u -> u.getFullName(), 160);
        TableColumn<User, String> rCol = col("Role", u -> u.getRole().getDisplayName(), 110);

        TableColumn<User, Void> sCol = new TableColumn<>("Status");
        sCol.setPrefWidth(140);
        sCol.setCellFactory(c -> new TableCell<User, Void>() {
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                User u = getTableRow().getItem();
                String txt = u.getUserId().equals(currentUserId) ? "Active (You)"
                        : u.isActive() ? "Active" : "Pending Approval";
                Label chip = new Label(txt);
                chip.getStyleClass().addAll("chip", u.isActive() ? "chip-success" : "chip-warning");
                setGraphic(chip);
                setText(null);
            }
        });

        TableColumn<User, Void> aCol = new TableColumn<>("Actions");
        aCol.setMinWidth(108);
        aCol.setPrefWidth(108);
        aCol.setMaxWidth(108);
        aCol.setCellFactory(c -> new TableCell<User, Void>() {
            final Button apprBtn = actionIconBtn(AppTheme.ICON_CHECK, "Approve account", "#16A34A");
            final Button editBtn = actionIconBtn(AppTheme.ICON_EDIT, "Edit user", "#3B82F6");
            final Button delBtn = actionIconBtn(AppTheme.ICON_DELETE, "Delete user", "#DC2626");
            {
                apprBtn.setOnAction(e -> {
                    User u = getTableRow().getItem();
                    if (u == null)
                        return;
                    u.setActive(true);
                    try {
                        UserService.updateUser(u);
                        reload(table);
                        notifySuccess(toastDisplay, "User approved: " + u.getUserId());
                    } catch (Exception ex) {
                        notifyError(toastDisplay, ex.getMessage());
                    }
                });
                editBtn.setOnAction(e -> {
                    User u = getTableRow().getItem();
                    if (u == null)
                        return;
                    editUser(owner, u, currentUserId, isAdmin, toastDisplay);
                    reload(table);
                });
                delBtn.setOnAction(e -> {
                    User u = getTableRow().getItem();
                    if (u == null || u.getUserId().equals(currentUserId))
                        return;
                    if (!isAdmin && u.getRole().isAdmin()) {
                        notifyError(toastDisplay, "Only administrators can delete admin accounts.");
                        return;
                    }
                    Alert conf = new Alert(Alert.AlertType.WARNING,
                            "Delete \"" + u.getUserId() + "\"?",
                            new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE),
                            ButtonType.CANCEL);
                    conf.setTitle("Delete User");
                    AppTheme.applyTheme(conf.getDialogPane());
                    conf.showAndWait()
                            .filter(bt -> bt.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                            .ifPresent(bt -> {
                                try {
                                    UserService.deleteUser(u.getUserId());
                                    reload(table);
                                    notifySuccess(toastDisplay, "User deleted: " + u.getUserId());
                                } catch (Exception ex) {
                                    notifyError(toastDisplay, ex.getMessage());
                                }
                            });
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                User u = getTableRow().getItem();
                HBox box = new HBox(2);
                if (!u.isActive())
                    box.getChildren().add(apprBtn);
                box.getChildren().add(editBtn);
                if (!u.getUserId().equals(currentUserId)
                        && (isAdmin || !u.getRole().isAdmin()))
                    box.getChildren().add(delBtn);
                box.setAlignment(Pos.CENTER);
                setGraphic(box);
            }
        });

        table.getColumns().addAll(uCol, nCol, rCol, sCol, aCol);
        reload(table);

        addBtn.setOnAction(e -> {
            RegistrationDialog.show(owner, false, true).ifPresent(req -> {
                try {
                    if (UserService.userExists(req.username())) {
                        notifyError(toastDisplay, "Username already taken.");
                        return;
                    }
                    if (UserService.emailExists(req.email())) {
                        notifyError(toastDisplay, "Email address already in use.");
                        return;
                    }
                    UserService.createUser(req.username(), req.password(), req.role());
                    User created = UserService.getUserById(req.username());
                    created.unlockProfile(req.password());
                    created.setEmail(req.email());
                    created.setContactNumber(req.phoneNumber());
                    created.setActive(!req.pendingApproval());
                    UserService.updateUser(created);
                    reload(table);
                    notifySuccess(toastDisplay, "User created: " + req.username());
                } catch (Exception ex) {
                    notifyError(toastDisplay, ex.getMessage());
                }
            });
        });

        VBox content = new VBox(topBar, table);
        content.setPadding(new Insets(10, 20, 20, 20)); // Reduced top padding
        pane.setContent(content);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        styleSecondaryBtn(pane, ButtonType.CLOSE, "Close");
        dlg.showAndWait();
    }

    // ── Edit user (admin/librarian) ───────────────────────────────

    public static void editUser(Stage owner, User user, String currentUserId, boolean isAdmin,
                                ToastDisplay toastDisplay) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Edit: " + user.getUserId());
        dlg.initOwner(owner);
        dlg.setResizable(true);

        DialogPane pane = dlg.getDialogPane();
        AppTheme.applyTheme(pane);
        pane.setPrefWidth(460);
        pane.setPrefHeight(420);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(20));

        TextField firstField = field(user.getFirstName());
        TextField lastField = field(user.getLastName());

        // Decrypt PII with master-key fallback — admin/librarian doesn't know the user's
        // password so we pass empty strings which triggers the master-key fallback path.
        TextField emailField   = field(SecurityProvider.decryptUserField(user.getEmail(),          user.getUserId(), "", ""));
        TextField contactField = field(SecurityProvider.decryptUserField(user.getContactNumber(), user.getUserId(), "", ""));
        boolean isSelf = user.getUserId().equals(currentUserId);

        ComboBox<UserRole> roleBox = new ComboBox<>();
        roleBox.getItems().addAll(isAdmin
                ? new UserRole[] { UserRole.USER, UserRole.LIBRARIAN, UserRole.ADMIN }
                : new UserRole[] { UserRole.USER, UserRole.LIBRARIAN });
        roleBox.setValue(user.getRole());
        roleBox.setMaxWidth(Double.MAX_VALUE);

        CheckBox activeCheck = new CheckBox("Account is active");
        activeCheck.setSelected(user.isActive());
        activeCheck.setStyle("-fx-text-fill:" + (AppTheme.darkMode ? "#E2E8F0" : "#374151") + ";");

        Label err = errorLabel();

        int row = 0;
        grid.add(bold("First Name"), 0, row);
        grid.add(firstField, 1, row++);
        grid.add(bold("Last Name"), 0, row);
        grid.add(lastField, 1, row++);
        grid.add(bold("Email"), 0, row);
        grid.add(emailField, 1, row++);
        grid.add(bold("Contact"), 0, row);
        grid.add(contactField, 1, row++);
        if (!isSelf) {
            grid.add(bold("Role"), 0, row);
            grid.add(roleBox, 1, row++);
            grid.add(activeCheck, 0, row, 2, 1);
            row++;
        }
        grid.add(err, 0, row, 2, 1);

        ColumnConstraints c0 = new ColumnConstraints(120);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);

        pane.setContent(grid);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Button okBtn = styleOkBtn(pane, "Save");
        styleSecondaryBtn(pane, ButtonType.CANCEL, "Cancel");

        // Prevent dialog from closing if validation fails
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                user.setFirstName(firstField.getText());
                user.setLastName(lastField.getText());
                user.setEmail(emailField.getText());
                user.setContactNumber(contactField.getText());
                if (!isSelf) {
                    user.setRole(roleBox.getValue());
                    user.setActive(activeCheck.isSelected());
                }
                UserService.updateUser(user);
                notifySuccess(toastDisplay, "User \"" + user.getUserId() + "\" updated successfully.");
            } catch (Exception e) {
                err.setText(e.getMessage());
                err.setVisible(true);
                ev.consume(); // keep dialog open
            }
        });

        dlg.setResultConverter(bt -> bt == ButtonType.OK);
        dlg.showAndWait();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static void reload(TableView<User> t) {
        List<User> list = UserService.getAllUsers().stream()
                .sorted((u1, u2) -> {
                    if (u1.isActive() != u2.isActive()) return u1.isActive() ? 1 : -1;
                    return u1.getUserId().compareToIgnoreCase(u2.getUserId());
                })
                .collect(java.util.stream.Collectors.toList());
        t.setItems(FXCollections.observableArrayList(list));
    }

    private static TableColumn<User, String> col(String name,
                                                 java.util.function.Function<User, String> fn, double w) {
        TableColumn<User, String> c = new TableColumn<>(name);
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(fn.apply(d.getValue())));
        c.setPrefWidth(w);
        return c;
    }

    private static Button actionIconBtn(String iconPath, String tooltip, String color) {
        Button b = new Button();
        var icon = AppTheme.createIcon(iconPath, 14);
        icon.setStyle("-fx-fill:white;");
        b.setGraphic(icon);
        AppTheme.installSmartTooltip(b, tooltip);
        b.setStyle("-fx-background-color:" + color + "; -fx-text-fill:white; -fx-cursor:hand;" +
                "-fx-background-radius:8px; -fx-padding:5; -fx-min-width:26px; -fx-pref-width:26px; " +
                "-fx-max-width:26px; -fx-min-height:26px; -fx-pref-height:26px; -fx-max-height:26px;");
        return b;
    }

    private static Label bold(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size:13px;-fx-font-weight:600;-fx-text-fill:"
                + textPrimary() + ";");
        return l;
    }

    private static String textPrimary() {
        return AppTheme.darkMode ? "#F8FAFC" : "#0F172A";
    }

    private static TextField field(String val) {
        TextField f = new TextField(val != null ? val : "");
        f.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "#1E293B" : "#F9FAFB") +
                ";-fx-border-color:" + (AppTheme.darkMode ? "#334155" : "#D1D5DB") + ";" +
                "-fx-border-width:1.5;-fx-border-radius:8px;-fx-background-radius:8px;" +
                "-fx-padding:9 12;-fx-font-size:14px;-fx-text-fill:" +
                (AppTheme.darkMode ? "#E2E8F0" : "#111827") + ";");
        return f;
    }

    private static PasswordField passField(String prompt) {
        PasswordField f = new PasswordField();
        f.setPromptText(prompt);
        f.setStyle("-fx-background-color:transparent; -fx-border-color:transparent; " +
                "-fx-padding:9 12;-fx-font-size:14px;-fx-text-fill:" +
                (AppTheme.darkMode ? "#E2E8F0" : "#111827") + ";");
        return f;
    }

    private static HBox createPasswordBox(String prompt) {
        PasswordField pf = passField(prompt);
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setManaged(false);
        tf.setVisible(false);
        tf.textProperty().bindBidirectional(pf.textProperty());
        tf.setStyle(pf.getStyle());

        Button toggle = new Button();
        toggle.getStyleClass().add("btn-ghost");
        toggle.setGraphic(AppTheme.createIcon(AppTheme.ICON_VISIBILITY, 16));
        toggle.setStyle("-fx-background-color:transparent; -fx-cursor:hand; -fx-padding:0 12; -fx-border-color:transparent; -fx-border-width:0;");

        toggle.setOnAction(e -> {
            boolean show = !tf.isVisible();
            tf.setVisible(show);
            tf.setManaged(show);
            pf.setVisible(!show);
            pf.setManaged(!show);
            toggle.setGraphic(AppTheme.createIcon(show ? AppTheme.ICON_VISIBILITY_OFF : AppTheme.ICON_VISIBILITY, 16));
        });

        StackPane stack = new StackPane(pf, tf);
        HBox box = new HBox(stack, toggle);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "#1E293B" : "#F9FAFB") +
                ";-fx-border-color:" + (AppTheme.darkMode ? "#334155" : "#D1D5DB") + ";" +
                "-fx-border-width:1.5;-fx-border-radius:8px;-fx-background-radius:8px;");
        HBox.setHgrow(stack, Priority.ALWAYS);
        return box;
    }

    private static Label errorLabel() {
        Label l = new Label();
        l.setStyle("-fx-text-fill:#DC2626;-fx-font-size:12px;");
        l.setVisible(false);
        l.managedProperty().bind(l.visibleProperty());
        return l;
    }

    private static void checkMatch(Label lbl, String pass, String confirm) {
        if (confirm == null || confirm.isEmpty()) { lbl.setText(""); return; }
        if (pass.equals(confirm)) {
            lbl.setText("Passwords match");
            lbl.setStyle("-fx-font-size:11px; -fx-text-fill:#16A34A;");
        } else {
            lbl.setText("Passwords do not match");
            lbl.setStyle("-fx-font-size:11px; -fx-text-fill:#DC2626;");
        }
    }

    private static Button styleOkBtn(DialogPane pane, String text) {
        Button ok = (Button) pane.lookupButton(ButtonType.OK);
        if (ok != null) {
            ok.setText(text);
            ok.setStyle("-fx-background-color:#0D9488;-fx-text-fill:white;" +
                    "-fx-font-weight:600;-fx-font-size:14px;" +
                    "-fx-background-radius:8px;-fx-padding:9 22;");
        }
        return ok;
    }

    private static Button styleSecondaryBtn(DialogPane pane, ButtonType buttonType, String text) {
        Button button = (Button) pane.lookupButton(buttonType);
        if (button != null) {
            button.setText(text);
            button.getStyleClass().add("btn-secondary");
            button.setStyle("");
        }
        return button;
    }

    private static void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        AppTheme.applyTheme(alert.getDialogPane());
        alert.showAndWait();
    }

    private static void notifySuccess(ToastDisplay toastDisplay, String message) {
        if (toastDisplay != null) {
            toastDisplay.showSuccess(message);
        }
    }

    private static void notifyError(ToastDisplay toastDisplay, String message) {
        if (toastDisplay != null) {
            toastDisplay.showError(message);
            return;
        }
        showAlert(message);
    }
    public static void showDeleteAccount(Stage owner, String userId, Runnable onDeleted, ToastDisplay toast) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Delete Account");
        dlg.initOwner(owner);

        DialogPane pane = dlg.getDialogPane();
        AppTheme.applyTheme(pane);
        pane.setPrefWidth(440);
        pane.setPrefHeight(340);

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.CENTER);

        var icon = AppTheme.createIcon(AppTheme.ICON_WARNING, 48);
        icon.setStyle("-fx-fill: #DC2626;");

        Label warning = new Label("Confirm Account Deletion");
        warning.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: " + textPrimary() + ";");

        Label desc = new Label("Deleting your account is permanent. All borrow history and personal data will be wiped. Please enter your password to confirm.");
        desc.setWrapText(true);
        desc.setStyle("-fx-text-alignment: center; -fx-text-fill: " + (AppTheme.darkMode ? "#94A3B8" : "#64748B") + ";");

        HBox passBox = createPasswordBox("Current password");
        PasswordField passField = (PasswordField) ((StackPane) passBox.getChildren().get(0)).getChildren().get(0);
        Label err = errorLabel();

        root.getChildren().addAll(icon, warning, desc, passBox, err);
        pane.setContent(root);

        pane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        // applyTheme must be called AFTER button types are added so its
        // internal loop can find the nodes and apply gradient styles to both.
        AppTheme.applyTheme(pane);

        // Override OK button to danger (red) — applyTheme assigned btn-primary (teal)
        Button delBtn = (Button) pane.lookupButton(ButtonType.OK);
        if (delBtn != null) {
            delBtn.setText("Permanently Delete");
            delBtn.getStyleClass().remove("btn-primary");
            delBtn.getStyleClass().add("btn-danger");
            delBtn.setStyle(""); // clear any inline style so CSS :hover/:pressed work
        }

        // Update Cancel button text and enforce secondary styling
        Button keepBtn = (Button) pane.lookupButton(ButtonType.CANCEL);
        if (keepBtn != null) {
            keepBtn.setText("Keep My Account");
            keepBtn.getStyleClass().add("btn-secondary");
            keepBtn.setStyle("");
        }

        if (delBtn != null) delBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                UserService.deleteOwnAccount(userId, passField.getText());
                notifySuccess(toast, "Account deleted. Goodbye!");
                onDeleted.run();
                dlg.setResult(true);
                dlg.close();
            } catch (Exception e) {
                err.setText(e.getMessage());
                err.setVisible(true);
                ev.consume();
            }
        });

        dlg.showAndWait();
    }

    private static ToastDisplay findToastDisplay(Stage owner) {
        if (owner == null) return null;
        Object data = owner.getUserData();
        if (data instanceof ToastDisplay) return (ToastDisplay) data;
        if (owner.getScene() != null) {
            data = owner.getScene().getUserData();
            if (data instanceof ToastDisplay) return (ToastDisplay) data;
        }
        return null;
    }
}
