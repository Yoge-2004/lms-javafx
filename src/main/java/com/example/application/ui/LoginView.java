package com.example.application.ui;

import com.example.application.ToastDisplay;
import com.example.entities.AppConfiguration;
import com.example.entities.User;
import com.example.services.AppConfigurationService;
import com.example.services.ReminderService;
import com.example.services.SecurityProvider;
import com.example.services.UserService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Orientation;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.*;
import javafx.scene.Node;
import javafx.collections.FXCollections;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Modern, visually stunning login view with smooth animations,
 * intuitive UX, and professional design.
 */
public class LoginView extends StackPane {

    private final BiConsumer<String, String> onLoginSuccess;
    private final Runnable onRegisterRequested;
    private final ToastDisplay toastDisplay;
    private final StringProperty passwordProperty = new SimpleStringProperty("");

    private TextField usernameField;
    private PasswordField passwordField;
    private TextField visiblePasswordField;
    private Button togglePasswordBtn;
    private Label errorLabel;
    private Button loginButton;
    private ProgressIndicator loadingIndicator;
    private VBox loginForm;
    private ComboBox<String> libraryCombo;
    private final List<String> availableLibraries = new ArrayList<>();
    /** True while the entrance animation is running — suppresses early dropdown opens. */
    private boolean animating = false;

    private Label brandLabel;
    private Label headline;
    private Label welcomeLabel;
    private Label signInLabel;

    /**
     * Constructs a new LoginView.
     *
     * @param onLoginSuccess Callback triggered with the userId upon successful authentication.
     * @param onRegisterRequested Callback triggered when the "Create Account" link is clicked.
     * @param toastDisplay Reference to the global notification system.
     */
    public LoginView(BiConsumer<String, String> onLoginSuccess, Runnable onRegisterRequested, ToastDisplay toastDisplay) {
        this.onLoginSuccess = onLoginSuccess;
        this.onRegisterRequested = onRegisterRequested;
        this.toastDisplay = toastDisplay;

        initializeUI();
        setupAnimations();
    }

    /**
     * Assembles the multi-layered layout including the hero section and the interactive 
     * login card within a scrollable container.
     */
    private void initializeUI() {
        setStyle("-fx-background-color: " + loginBackground() + ";");
        setPadding(new Insets(40));

        FlowPane mainLayout = new FlowPane(Orientation.HORIZONTAL, 48, 32);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setMaxWidth(1200);
        mainLayout.setPrefWrapLength(980);

        VBox heroSection  = createHeroSection();
        VBox loginCard    = createLoginCard();

        mainLayout.getChildren().addAll(heroSection, loginCard);

        ScrollPane scrollPane = new ScrollPane(mainLayout);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        getChildren().add(scrollPane);

        // ── Entrance animations ───────────────────────────────────────────
        // Hero: fade + slide from left
        animating = true;
        heroSection.setOpacity(0);
        heroSection.setTranslateX(-40);
        FadeTransition heroFade = new FadeTransition(Duration.millis(600), heroSection);
        heroFade.setToValue(1);
        TranslateTransition heroSlide = new TranslateTransition(Duration.millis(600), heroSection);
        heroSlide.setToX(0);
        heroSlide.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(heroFade, heroSlide).play();

        // Login card: fade + slide from right (slight delay)
        // Block mouse input during the animation so the user cannot accidentally
        // trigger the library/category dropdowns mid-slide.
        loginCard.setMouseTransparent(true);
        loginCard.setOpacity(0);
        loginCard.setTranslateX(40);
        FadeTransition cardFade = new FadeTransition(Duration.millis(600), loginCard);
        cardFade.setToValue(1);
        cardFade.setDelay(Duration.millis(120));
        TranslateTransition cardSlide = new TranslateTransition(Duration.millis(600), loginCard);
        cardSlide.setToX(0);
        cardSlide.setDelay(Duration.millis(120));
        cardSlide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition cardAnim = new ParallelTransition(cardFade, cardSlide);
        cardAnim.setOnFinished(ev -> {
            // Animation done — re-enable interaction and allow dropdowns to open
            loginCard.setMouseTransparent(false);
            animating = false;
        });
        cardAnim.play();
    }

    private VBox createHeroSection() {
        VBox hero = new VBox(32);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setMaxWidth(500);
        hero.setPadding(new Insets(40));

        // Logo/Brand
        brandLabel = new Label("LIBRARY OS");
        brandLabel.setStyle("-fx-font-family: 'Plus Jakarta Sans'; -fx-font-size: 18px; " +
                "-fx-font-weight: 800; -fx-text-fill: #14B8A6; -fx-letter-spacing: 0.2em;");
        brandLabel.setWrapText(true);
        brandLabel.setMaxWidth(500);
        brandLabel.setMinHeight(Region.USE_PREF_SIZE);
        brandLabel.setTextOverrun(OverrunStyle.CLIP);

        // Main headline
        headline = new Label("Manage Your Library\nwith Intelligence");
        headline.setStyle("-fx-font-family: 'Plus Jakarta Sans'; -fx-font-size: 48px; " +
                "-fx-font-weight: 800; -fx-text-fill: white; -fx-line-spacing: 8px;");
        headline.setWrapText(true);
        headline.setMaxWidth(480);
        headline.setMinHeight(Region.USE_PREF_SIZE);

        // Subtitle
        Label subtitle = new Label("A modern, comprehensive solution for library circulation, " +
                "catalog management, and user administration.");
        subtitle.setStyle("-fx-font-size: 18px; -fx-text-fill: #94A3B8; -fx-line-spacing: 4px;");
        subtitle.setWrapText(true);

        // Feature list
        VBox features = new VBox(16);
        features.getChildren().addAll(
                createFeatureItem(AppTheme.ICON_CHECK, "Streamlined book circulation"),
                createFeatureItem(AppTheme.ICON_CHECK, "Real-time analytics dashboard"),
                createFeatureItem(AppTheme.ICON_CHECK, "Automated overdue notifications"),
                createFeatureItem(AppTheme.ICON_CHECK, "Multi-role user management")
        );

        hero.getChildren().addAll(brandLabel, headline, subtitle, features);
        return hero;
    }

    private HBox createFeatureItem(String iconPath, String text) {
        HBox item = new HBox(12);
        item.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBadge = new StackPane(AppTheme.createIcon(iconPath, 16));
        iconBadge.setMinSize(28, 28);
        iconBadge.setPrefSize(28, 28);
        iconBadge.setMaxSize(28, 28);
        iconBadge.setStyle("-fx-background-color: rgba(20, 184, 166, 0.15); -fx-background-radius: 14px;");

        Label label = new Label(text);
        label.setStyle("-fx-font-size: 15px; -fx-text-fill: #CBD5E1;");

        item.getChildren().addAll(iconBadge, label);
        return item;
    }

    private VBox createLoginCard() {
        loginForm = new VBox(24);
        loginForm.setAlignment(Pos.TOP_CENTER);
        loginForm.setPadding(new Insets(48));
        loginForm.setPrefWidth(420);
        loginForm.setMinWidth(420);
        loginForm.setMaxWidth(420);
        loginForm.setStyle(loginCardStyle());

        // Card shadow
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#000000", 0.2));
        shadow.setRadius(30);
        shadow.setOffsetY(10);
        loginForm.setEffect(shadow);

        // Card header
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);

        welcomeLabel = new Label("Welcome back");
        welcomeLabel.setStyle("-fx-font-family: 'Plus Jakarta Sans'; -fx-font-size: 28px; " +
                "-fx-font-weight: 700; -fx-text-fill: " + primaryText() + ";");
        welcomeLabel.setWrapText(true);
        welcomeLabel.setMaxWidth(340);
        welcomeLabel.setAlignment(Pos.CENTER);
        welcomeLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        signInLabel = new Label("Sign in to your account");
        signInLabel.setStyle("-fx-font-size: 15px; -fx-text-fill: " + mutedText() + ";");
        signInLabel.setAlignment(Pos.CENTER);
        signInLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        header.getChildren().addAll(welcomeLabel, signInLabel);

        // Form fields
        VBox formFields = new VBox(20);
        formFields.setFillWidth(true);

        VBox libraryBox = createLibraryBox();

        // Username field
        VBox usernameBox = new VBox(6);
        Label usernameLabel = new Label("Username");
        usernameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: " + labelText() + ";");

        usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        usernameField.setStyle(getInputStyle());
        usernameField.setPrefHeight(48);
        HBox.setHgrow(usernameField, Priority.ALWAYS);

        usernameBox.getChildren().addAll(usernameLabel, usernameField);

        // Password field with toggle
        VBox passwordBox = new VBox(6);
        Label passwordLabel = new Label("Password");
        passwordLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: " + labelText() + ";");

        HBox passwordContainer = createPasswordContainer();

        passwordBox.getChildren().addAll(passwordLabel, passwordContainer);

        // Error label
        errorLabel = new Label();
        errorLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #DC2626;");
        errorLabel.setVisible(false);
        errorLabel.setWrapText(true);

        formFields.getChildren().addAll(libraryBox, usernameBox, passwordBox, errorLabel);

        // Login button
        loginButton = new Button("Sign In");
        loginButton.setStyle(signInButtonStyle("#0D9488"));
        loginButton.setPrefHeight(52);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(e -> handleLogin());
        installSignInButtonMotion(loginButton);

        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        loadingIndicator.setMaxSize(24, 24);

        StackPane buttonStack = new StackPane(loginButton, loadingIndicator);
        StackPane.setAlignment(loadingIndicator, Pos.CENTER);
        HBox.setHgrow(buttonStack, Priority.ALWAYS);

        HBox buttonContainer = new HBox(buttonStack);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.setMaxWidth(Double.MAX_VALUE);

        // Register link
        HBox registerBox = new HBox(6);
        registerBox.setAlignment(Pos.CENTER);

        Label noAccountLabel = new Label("Don't have an account?");
        noAccountLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + mutedText() + ";");

        Hyperlink registerLink = new Hyperlink("Create one");
        registerLink.setStyle("-fx-font-size: 14px; -fx-text-fill: #0D9488; -fx-font-weight: 600;");
        registerLink.setOnAction(e -> {
            // CRITICAL FIX: Select the library the user has chosen BEFORE calling
            // the registration callback. This ensures UsersDB.forceReload() runs
            // (via selectKnownLibrary) so hasRegisteredUsers() reads from the correct
            // branch-scoped path, not the blank-config empty directory.
            String selectedLib = resolveSelectedLibrary();
            if (selectedLib != null && !selectedLib.isBlank()) {
                new Thread(() -> {
                    try {
                        AppConfigurationService.selectKnownLibrary(selectedLib);
                    } catch (Exception ex) {
                        // non-fatal: registration dialog will still open
                    }
                    javafx.application.Platform.runLater(() -> {
                        if (onRegisterRequested != null) onRegisterRequested.run();
                    });
                }, "register-lib-select").start();
            } else {
                if (onRegisterRequested != null) onRegisterRequested.run();
            }
        });

        registerBox.getChildren().addAll(noAccountLabel, registerLink);

        // Forgot password link
        Hyperlink forgotPassLink = new Hyperlink("Forgot password?");
        forgotPassLink.setStyle("-fx-font-size: 12px; -fx-text-fill: " + mutedText() + ";");
        forgotPassLink.setOnAction(e -> showForgotPasswordDialog());

        VBox footerBox = new VBox(8, registerBox, forgotPassLink);
        footerBox.setAlignment(Pos.CENTER);

        // Advance to username field when a library is confirmed via dropdown selection or Enter.
        libraryCombo.setOnAction(e -> {
            if (libraryCombo.getValue() != null) usernameField.requestFocus();
        });
        libraryCombo.getEditor().setOnAction(e -> {
            String selectedLibrary = resolveSelectedLibrary();
            if (selectedLibrary != null) {
                selectLibrary(selectedLibrary);
                usernameField.requestFocus();
            }
        });
        usernameField.setOnAction(e -> passwordField.requestFocus());
        passwordField.setOnAction(e -> handleLogin());
        visiblePasswordField.setOnAction(e -> handleLogin());

        // Hide the library dropdown when focus moves to other fields
        usernameField.focusedProperty().addListener((obs, old, focused) -> {
            if (focused && libraryCombo != null) libraryCombo.hide();
        });
        passwordField.focusedProperty().addListener((obs, old, focused) -> {
            if (focused && libraryCombo != null) libraryCombo.hide();
        });
        visiblePasswordField.focusedProperty().addListener((obs, old, focused) -> {
            if (focused && libraryCombo != null) libraryCombo.hide();
        });

        loginForm.getChildren().addAll(header, formFields, buttonContainer, footerBox);

        return loginForm;
    }

    private HBox createPasswordContainer() {
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setStyle("-fx-background-color:transparent; -fx-border-color:transparent; " +
                "-fx-font-size:15px; -fx-text-fill:" + fieldText() + "; -fx-prompt-text-fill:" + promptText() + "; " +
                "-fx-padding:10 14; -fx-font-family:'Noto Sans','Liberation Sans','DejaVu Sans',sans-serif;");
        passwordField.setPrefHeight(48);

        visiblePasswordField = new TextField();
        visiblePasswordField.setPromptText("Enter your password");
        visiblePasswordField.setStyle("-fx-background-color:transparent; -fx-border-color:transparent; " +
                "-fx-font-size:15px; -fx-text-fill:" + fieldText() + "; -fx-prompt-text-fill:" + promptText() + "; " +
                "-fx-padding:10 14; -fx-font-family:'Noto Sans','Liberation Sans','DejaVu Sans',sans-serif;");
        visiblePasswordField.setPrefHeight(48);
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setManaged(false);

        // Bind fields
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        passwordProperty.bind(passwordField.textProperty());

        // Toggle button
        togglePasswordBtn = new Button();
        togglePasswordBtn.setPrefHeight(48);
        togglePasswordBtn.setPrefWidth(48);
        togglePasswordBtn.setGraphic(AppTheme.createIcon(AppTheme.ICON_VISIBILITY, 16));
        togglePasswordBtn.getStyleClass().addAll("app-button", "btn-ghost", "password-toggle", "auth-password-toggle");
        togglePasswordBtn.setFocusTraversable(false);
        AppTheme.installSmartTooltip(togglePasswordBtn, "Show / hide password");

        togglePasswordBtn.setOnAction(e -> togglePasswordVisibility());

        StackPane fieldStack = new StackPane(passwordField, visiblePasswordField);
        HBox.setHgrow(fieldStack, Priority.ALWAYS);

        HBox container = new HBox(fieldStack, togglePasswordBtn);
        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("input-with-icon");
        return container;
    }

    private VBox createLibraryBox() {
        AppConfiguration config = AppConfigurationService.getConfiguration();
        availableLibraries.clear();
        availableLibraries.addAll(com.example.entities.LibrariesDB.getInstance().getLibraries());
        availableLibraries.sort(String.CASE_INSENSITIVE_ORDER);

        VBox libraryBox = new VBox(6);
        libraryBox.setFillWidth(true);
        Label libraryLabel = new Label("Library");
        libraryLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 600; -fx-text-fill: " + labelText() + ";");

        String configuredLibrary = config.getCurrentLibraryDisplayName();
        String initialLibrary = availableLibraries.stream()
                .filter(value -> value.equalsIgnoreCase(configuredLibrary))
                .findFirst()
                .orElse(availableLibraries.isEmpty() ? "" : availableLibraries.get(0));

        javafx.collections.ObservableList<String> masterLibraries =
                FXCollections.observableArrayList(availableLibraries);
        javafx.collections.transformation.FilteredList<String> filteredLibraries =
                new javafx.collections.transformation.FilteredList<>(masterLibraries, s -> true);

        libraryCombo = new ComboBox<>(filteredLibraries);
        libraryCombo.setEditable(true);
        libraryCombo.setPromptText("Select your library");
        libraryCombo.setMaxWidth(Double.MAX_VALUE);
        libraryCombo.setVisibleRowCount(Math.min(6, Math.max(1, availableLibraries.size())));
        libraryCombo.setStyle(getInputStyle());

        if (!initialLibrary.isBlank()) {
            libraryCombo.setValue(initialLibrary);
            Platform.runLater(() -> updateBrandingForLibrary(initialLibrary));
        }

        // Filter dropdown items as the user types — FilteredList only changes the
        // predicate so the editor text and caret position are never touched.
        libraryCombo.getEditor().textProperty().addListener((obs, old, text) -> {
            String lower = (text == null ? "" : text).trim().toLowerCase();
            filteredLibraries.setPredicate(s -> lower.isEmpty() || s.toLowerCase().contains(lower));
            // Never open the dropdown while the entrance animation is still running —
            // it causes the popup to appear mis-positioned over the username field.
            Platform.runLater(() -> {
                if (!animating
                        && !libraryCombo.isShowing()
                        && !filteredLibraries.isEmpty()
                        && libraryCombo.getScene() != null
                        && libraryCombo.getScene().getWindow() != null
                        && libraryCombo.getScene().getWindow().isShowing()) {
                    libraryCombo.show();
                }
            });
        });

        // On selection, reset the filter predicate, update tooltip and branding
        libraryCombo.valueProperty().addListener((obs, old, selected) -> {
            if (selected != null && !selected.isBlank()) {
                filteredLibraries.setPredicate(s -> true);
                AppTheme.installSmartTooltip(libraryCombo, selected);
                updateBrandingForLibrary(selected);
            }
        });

        libraryBox.getChildren().addAll(libraryLabel, libraryCombo);
        return libraryBox;
    }

    private String getInputStyle() {
        return "-fx-background-color: " + fieldSurface() + "; -fx-border-color: " + fieldBorder() + "; " +
                "-fx-border-width: 1.5; -fx-border-radius: 12px; " +
                "-fx-background-radius: 12px; -fx-font-size: 15px; " +
                "-fx-text-fill: " + fieldText() + "; -fx-prompt-text-fill: " + promptText() + "; " +
                "-fx-padding: 10 14;";
    }

    private void togglePasswordVisibility() {
        boolean isHidden = passwordField.isVisible();
        passwordField.setVisible(!isHidden);
        passwordField.setManaged(!isHidden);
        visiblePasswordField.setVisible(isHidden);
        visiblePasswordField.setManaged(isHidden);
        togglePasswordBtn.setGraphic(AppTheme.createIcon(
                isHidden ? AppTheme.ICON_VISIBILITY_OFF : AppTheme.ICON_VISIBILITY, 16));
    }

    private void handleLogin() {
        String selectedLibrary = resolveSelectedLibrary();
        String username = usernameField.getText().trim();
        String password = passwordProperty.getValue();

        // Validation
        if (selectedLibrary == null) {
            showError("Select a library from the list before signing in");
            shakeForm();
            return;
        }

        if (username.isEmpty()) {
            showError("Please enter your username");
            shakeForm();
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter your password");
            shakeForm();
            return;
        }

        // Show loading state
        setLoading(true);

        // BUG FIX: The original anonymous thread was not a daemon thread. Non-daemon threads
        // prevent the JVM from shutting down — if the user closes the window while a login
        // request is in progress, the process would hang until the thread finishes.
        // Fix: name the thread and mark it as a daemon so it does not block JVM exit.
        Thread loginThread = new Thread(() -> {
            try {
                AppConfigurationService.selectKnownLibrary(selectedLibrary);
                boolean success = UserService.login(username, password);

                Platform.runLater(() -> {
                    setLoading(false);
                    if (success) {
                        // BUG FIX: getUserById() throws UserException when not found — it never
                        // returns null. The previous "loggedUser != null" check was dead code.
                        // Worse, a surprise exception inside Platform.runLater() would propagate
                        // to JavaFX's uncaught-exception handler and silently abort this callback,
                        // leaving the UI stuck in the logged-in state with no feedback.
                        // Replace with a proper try-catch so errors surface correctly.
                        com.example.entities.User loggedUser;
                        try {
                            loggedUser = com.example.services.UserService.getUserById(username);
                        } catch (Exception e) {
                            showError("Login error: " + e.getMessage());
                            shakeForm();
                            return;
                        }
                        boolean isRemoteDb = com.example.services.AppConfigurationService.getConfiguration()
                                .getDatabaseConfiguration().getEngine().isRemote();

                        if (loggedUser.getRole() == com.example.entities.UserRole.USER && !isRemoteDb) {
                            AppTheme.showSecurityEnforcementError();
                            return;
                        }

                        showSuccessAnimation();
                        if (onLoginSuccess != null) {
                            onLoginSuccess.accept(username, password);
                        }
                    } else {
                        showError("Invalid username or password");
                        shakeForm();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setLoading(false);
                    showError("Login failed: " + ex.getMessage());
                    shakeForm();
                });
            }
        }, "forgot-password-email");
        loginThread.setDaemon(true);
        loginThread.start();
    }

    private void setLoading(boolean loading) {
        loginButton.setVisible(!loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        visiblePasswordField.setDisable(loading);
        togglePasswordBtn.setDisable(loading);
        if (libraryCombo != null) {
            libraryCombo.setDisable(loading);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #DC2626; -fx-padding: 8 0 0 0; -fx-wrap-text: true;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(Double.MAX_VALUE);
    }


    private String resolveSelectedLibrary() {
        if (libraryCombo == null) return null;
        // Prefer a confirmed selection; fall back to what is typed in the editor
        String value = libraryCombo.getValue() != null ? libraryCombo.getValue().trim() : "";
        if (value.isEmpty() && libraryCombo.getEditor() != null) {
            value = libraryCombo.getEditor().getText() != null
                    ? libraryCombo.getEditor().getText().trim() : "";
        }
        if (value.isEmpty()) return null;
        final String typed = value;
        return availableLibraries.stream()
                .filter(v -> v.equalsIgnoreCase(typed))
                .findFirst()
                .orElse(null);
    }


    private void selectLibrary(String library) {
        libraryCombo.setValue(library);
        libraryCombo.hide();
    }

    private void updateBrandingForLibrary(String libraryDisplayName) {
        if (libraryDisplayName == null || libraryDisplayName.isBlank()) return;

        brandLabel.setText(libraryDisplayName.toUpperCase());
        AppTheme.installSmartTooltip(brandLabel, libraryDisplayName);

        welcomeLabel.setText("Welcome to " + libraryDisplayName);
        int nameLen = libraryDisplayName.length();
        int fontSize = nameLen <= 18 ? 28 : nameLen <= 32 ? 22 : nameLen <= 48 ? 18 : 15;
        welcomeLabel.setStyle("-fx-font-family: 'Plus Jakarta Sans'; -fx-font-size: " + fontSize + "px; " +
                "-fx-font-weight: 700; -fx-text-fill: " + primaryText() + ";");
        welcomeLabel.setWrapText(true);
        welcomeLabel.setMaxWidth(340);
        welcomeLabel.setAlignment(Pos.CENTER);
        welcomeLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        welcomeLabel.setMinHeight(Region.USE_PREF_SIZE);
    }

    private String signInButtonStyle(String color) {
        return "-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-font-size: 16px; -fx-font-weight: 700; -fx-background-radius: 12px; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(13,148,136,0.24), 10, 0, 0, 3);";
    }

    private void installSignInButtonMotion(Button button) {
        button.setOnMouseEntered(e -> {
            button.setStyle(signInButtonStyle("#0F766E"));
            ScaleTransition st = new ScaleTransition(Duration.millis(140), button);
            st.setToX(1.02);
            st.setToY(1.02);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        button.setOnMouseExited(e -> {
            button.setStyle(signInButtonStyle("#0D9488"));
            ScaleTransition st = new ScaleTransition(Duration.millis(140), button);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        button.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(80), button);
            st.setToX(0.98);
            st.setToY(0.98);
            st.play();
        });
        button.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
            st.setToX(button.isHover() ? 1.02 : 1.0);
            st.setToY(button.isHover() ? 1.02 : 1.0);
            st.play();
        });
    }

    private void shakeForm() {
        // FIXED: every child of SequentialTransition must be a distinct object.
        // Re-using `left` and `right` caused:
        //   IllegalArgumentException: Attempting to add a duplicate to the list of children
        TranslateTransition t1 = new TranslateTransition(Duration.millis(50), loginForm); t1.setToX(-8);
        TranslateTransition t2 = new TranslateTransition(Duration.millis(50), loginForm); t2.setToX(8);
        TranslateTransition t3 = new TranslateTransition(Duration.millis(50), loginForm); t3.setToX(-8);
        TranslateTransition t4 = new TranslateTransition(Duration.millis(50), loginForm); t4.setToX(8);
        TranslateTransition t5 = new TranslateTransition(Duration.millis(50), loginForm); t5.setToX(0);
        new SequentialTransition(t1, t2, t3, t4, t5).play();
    }

    private void showSuccessAnimation() {
        FadeTransition fade = new FadeTransition(Duration.millis(300), loginForm);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            loginForm.setVisible(false);
        });
        fade.play();
    }

    /**
     * Coordinates a layered entrance animation sequence.
     * 1. Hero text segments slide up sequentially.
     * 2. The Login Card fades and slides into its terminal position.
     * 3. Form fields (input boxes, labels) stagger in for a polished UX.
     */
    private void setupAnimations() {
        Platform.runLater(() -> {
            // Hero section entrance (Title, Features, etc.)
            VBox hero = (VBox) ((FlowPane) ((ScrollPane) getChildren().get(0)).getContent()).getChildren().get(0);
            double delay = 100;
            for (Node n : hero.getChildren()) {
                AppTheme.slideUp(n, delay, 40, 600);
                delay += 120;
            }

            // Authentication Card initialization
            loginForm.setOpacity(0);
            loginForm.setTranslateY(50);

            FadeTransition fade = new FadeTransition(Duration.millis(800), loginForm);
            fade.setToValue(1);

            TranslateTransition slide = new TranslateTransition(Duration.millis(800), loginForm);
            slide.setToY(0);
            slide.setInterpolator(AppTheme.EASE_OUT_QUART);

            ParallelTransition cardEntrance = new ParallelTransition(fade, slide);
            cardEntrance.setDelay(Duration.millis(300));
            cardEntrance.play();

            // Form element staggering for premium arrival feel
            double fieldDelay = 600;
            for (Node child : loginForm.getChildren()) {
                if (child instanceof VBox && ((VBox) child).getChildren().size() > 2) {
                    VBox fields = (VBox) child;
                    for (Node f : fields.getChildren()) {
                        AppTheme.slideUp(f, fieldDelay, 20, 500);
                        fieldDelay += 100;
                    }
                } else {
                    AppTheme.slideUp(child, fieldDelay, 20, 500);
                    fieldDelay += 100;
                }
            }
        });
    }

    /**
     * Displays a secure password reset prompt.
     *
     * <p>Users provide their username, and the system verifies account existence
     * before triggering an asynchronous email dispatch containing a temporary password.</p>
     */
    private void showForgotPasswordDialog() {
        // Resolve the library the user has chosen in the login combo BEFORE opening
        // the dialog. Without this, UserService.userExists() reads from whatever
        // UsersDB path was last loaded (possibly an empty bootstrap path), causing
        // every username lookup to return false.
        String selectedLibrary = resolveSelectedLibrary();
        if (selectedLibrary != null && !selectedLibrary.isBlank()) {
            try {
                AppConfigurationService.selectKnownLibrary(selectedLibrary);
            } catch (Exception ignored) {
                // Non-fatal: dialog will still open; user lookup may fail gracefully
            }
        }

        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("Forgot Password");
        dlg.setHeaderText("Reset Your Password");

        DialogPane pane = dlg.getDialogPane();
        AppTheme.applyTheme(pane);
        pane.setPrefWidth(500);
        pane.setMinWidth(460);

        // Use the selected library display name, falling back to configured value
        String displayLibrary = selectedLibrary != null && !selectedLibrary.isBlank()
                ? selectedLibrary
                : AppConfigurationService.getConfiguration().getLibraryName()
                        + " - " + AppConfigurationService.getConfiguration().getBranchName();

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        Label info = new Label("Enter your username and Library OS will email a temporary password to the address saved on your account.");
        info.setWrapText(true);
        info.setStyle("-fx-font-size: 13px; -fx-text-fill: " + mutedText() + ";");

        TextField usernameResetField = new TextField();
        usernameResetField.setPromptText("Enter your username");
        usernameResetField.setStyle(getInputStyle());
        usernameResetField.setPrefHeight(40);

        Label statusLabel = new Label();
        statusLabel.setVisible(false);
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #DC2626; -fx-padding: 8 0 0 0;");

        Label branchLabel = new Label("Library Branch: " + displayLibrary);
        branchLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0D9488; -fx-font-size: 14px;");

        content.getChildren().addAll(branchLabel, info, new Label("Username:"), usernameResetField, statusLabel);
        pane.setContent(content);

        ButtonType sendType = new ButtonType("Send Email", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, sendType);
        Button okBtn = (Button) pane.lookupButton(sendType);

        // Validation logic before closing the dialog
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String username = usernameResetField.getText().trim();
            if (username.isEmpty()) {
                statusLabel.setText("Enter your username first.");
                statusLabel.setVisible(true);
                event.consume();
                return;
            }
            if (!UserService.userExists(username)) {
                statusLabel.setText("No account was found for that username.");
                statusLabel.setVisible(true);
                event.consume();
                return;
            }
            statusLabel.setVisible(false);
        });

        dlg.setResultConverter(bt -> bt == sendType ? usernameResetField.getText().trim() : null);
        // Capture the resolved library for use in the async dispatch thread
        final String libraryForDispatch = selectedLibrary;
        dlg.showAndWait().ifPresent(username -> dispatchForgotPasswordEmail(username, libraryForDispatch));
    }

    /**
     * Dispatches the forgot-password email on a background thread.
     *
     * @param username         the account username to reset
     * @param selectedLibrary  the library selected in the login combo (may be null);
     *                         used to ensure the correct branch's UsersDB is active
     *                         before the async lookup so the right user record is found.
     */
    private void dispatchForgotPasswordEmail(String username, String selectedLibrary) {
        if (toastDisplay != null) {
            toastDisplay.showInfo("Sending temporary password email…");
        }
        Thread emailThread = new Thread(() -> {
            try {
                // Re-select the library inside the thread: AppConfigurationService may have
                // been switched by another concurrent operation between dialog close and here.
                if (selectedLibrary != null && !selectedLibrary.isBlank()) {
                    AppConfigurationService.selectKnownLibrary(selectedLibrary);
                }

                User user = UserService.getUserById(username);
                // Decrypt with master-key fallback (no session password available here).
                // Pass the real salt so the derivation is correct if user-key is ever tried.
                String targetEmail = SecurityProvider.decryptUserField(
                        user.getEmail(), username, null, user.getSalt());
                if (targetEmail == null || targetEmail.isBlank()) {
                    throw new IllegalStateException("This account does not have an email address on file.");
                }

                String originalPassword = user.getPassword();
                String temporaryPassword = buildTemporaryPassword();

                user.setPassword(temporaryPassword);
                UserService.updateUser(user);
                UserService.persistDatabase();

                try {
                    ReminderService.sendTemporaryPassword(user, temporaryPassword);
                } catch (Exception mailError) {
                    user.setPassword(originalPassword);
                    UserService.updateUser(user);
                    UserService.persistDatabase();
                    throw mailError;
                }

                Platform.runLater(() -> {
                    if (toastDisplay != null) {
                        toastDisplay.showSuccess("Temporary password emailed to " + targetEmail + ".");
                    } else {
                        showError("Temporary password emailed to " + targetEmail + ".");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    String message = ReminderService.toUserMessage(ex);
                    if (toastDisplay != null) {
                        toastDisplay.showError("Forgot password failed: " + message);
                    } else {
                        showError("Forgot password failed: " + message);
                    }
                });
            }
        }, "forgot-password-email");
        emailThread.setDaemon(true);
        emailThread.start();
    }

    private String buildTemporaryPassword() {
        String seed = java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return "LIB" + seed.substring(0, 8);
    }

    private String loginBackground() {
        if (AppTheme.darkMode) {
            return "linear-gradient(from 0% 0% to 100% 100%, #020617, #0F172A 50%, #134E4A)";
        }
        return "linear-gradient(from 0% 0% to 100% 100%, #0F172A, #1E293B 50%, #134E4A)";
    }

    private String loginCardStyle() {
        return "-fx-background-color: " + cardSurface() + "; -fx-background-radius: 24px; " +
                "-fx-border-radius: 24px; -fx-border-color: " + cardBorder() + "; -fx-border-width: 1;";
    }

    private String cardSurface() {
        return AppTheme.darkMode ? "#0F172A" : "white";
    }

    private String cardBorder() {
        return AppTheme.darkMode ? "#1E293B" : "#E2E8F0";
    }

    private String primaryText() {
        return AppTheme.darkMode ? "#F8FAFC" : "#0F172A";
    }

    private String labelText() {
        return AppTheme.darkMode ? "#CBD5E1" : "#374151";
    }

    private String mutedText() {
        return AppTheme.darkMode ? "#94A3B8" : "#64748B";
    }

    private String fieldSurface() {
        return AppTheme.darkMode ? "#1E293B" : "#F9FAFB";
    }

    private String fieldBorder() {
        return AppTheme.darkMode ? "#334155" : "#D1D5DB";
    }

    private String fieldText() {
        return AppTheme.darkMode ? "#F8FAFC" : "#111827";
    }

    private String promptText() {
        return AppTheme.darkMode ? "#64748B" : "#9CA3AF";
    }
}
