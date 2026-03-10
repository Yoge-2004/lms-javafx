package com.example.application;

import com.example.entities.Book;
import com.example.entities.BooksDB.IssueRecord;
import com.example.entities.User;
import com.example.exceptions.BooksException;
import com.example.exceptions.UserException;
import com.example.exceptions.ValidationException;
import com.example.services.BookService;
import com.example.services.UserService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.scene.Node;
import javafx.stage.Modality;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced JavaFX application for library management with improved UI/UX,
 * error handling, performance optimizations, and comprehensive settings module.
 */
public class LibraryApp extends Application {

    // Application constants
    private static final String APP_TITLE = "Library Management System";
    private static final String APP_VERSION = "1.0";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    // UI State
    private String currentUser;
    private Stage primaryStage;

    // Data collections
    private final ObservableList<Book> booksList = FXCollections.observableArrayList();
    private FilteredList<Book> filteredBooks;
    private final ObservableList<IssueRecord> issueRecordsList = FXCollections.observableArrayList();
    private FilteredList<IssueRecord> filteredIssueRecords;

    // UI Components
    private TextField searchField;
    private ListView<Book> booksListView;
    private ListView<IssueRecord> issueRecordsListView;
    private Label statusLabel;
    private Label statisticsLabel;
    private ProgressIndicator loadingIndicator;

    // UI Style constants
    private static final String BUTTON_STYLE_PRIMARY =
            "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; " +
                    "-fx-cursor: hand; -fx-padding: 8 16; -fx-background-radius: 4;";
    private static final String BUTTON_STYLE_SUCCESS =
            "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; " +
                    "-fx-cursor: hand; -fx-padding: 8 16; -fx-background-radius: 4;";
    private static final String BUTTON_STYLE_DANGER =
            "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; " +
                    "-fx-cursor: hand; -fx-padding: 8 16; -fx-background-radius: 4;";
    private static final String BUTTON_STYLE_WARNING =
            "-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold; " +
                    "-fx-cursor: hand; -fx-padding: 8 16; -fx-background-radius: 4;";

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        primaryStage.setTitle(APP_TITLE + " v" + APP_VERSION);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        showLoginScreen();
        primaryStage.show();

        // Handle application close
        primaryStage.setOnCloseRequest(event -> handleApplicationClose());
    }

    /**
     * Displays the login screen with enhanced UI and validation.
     */
    private void showLoginScreen() {
        VBox loginPane = createLoginPane();
        Scene loginScene = new Scene(loginPane, 450, 380);

        primaryStage.setScene(loginScene);
        primaryStage.centerOnScreen();
    }

    /**
     * Creates the login pane with improved styling and functionality.
     */
    private VBox createLoginPane() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #f4f6f8;");

        // Header section
        VBox headerSection = new VBox(5);
        headerSection.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("📚 " + APP_TITLE);
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #333;");

        Label subtitleLabel = new Label("Librarian Portal - Version " + APP_VERSION);
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        headerSection.getChildren().addAll(titleLabel, subtitleLabel);

        // Login form section
        VBox formSection = new VBox(12);
        formSection.setAlignment(Pos.CENTER);
        formSection.setMaxWidth(320);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        usernameField.setMaxWidth(300);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setMaxWidth(300);

        Label messageLabel = new Label();
        messageLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(300);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button loginButton = new Button("Login");
        loginButton.setStyle(BUTTON_STYLE_PRIMARY);
        loginButton.setDefaultButton(true);

        Button registerButton = new Button("Register New User");
        registerButton.setStyle(BUTTON_STYLE_SUCCESS);

        buttonBox.getChildren().addAll(loginButton, registerButton);

        // Add loading indicator
        ProgressIndicator loginProgress = new ProgressIndicator();
        loginProgress.setVisible(false);
        loginProgress.setMaxSize(30, 30);

        formSection.getChildren().addAll(
                usernameField, passwordField, buttonBox, messageLabel, loginProgress
        );

        // Event handlers
        loginButton.setOnAction(e -> handleLogin(usernameField.getText(), passwordField.getText(),
                messageLabel, loginProgress));

        registerButton.setOnAction(e -> handleRegistration(usernameField.getText(), passwordField.getText(),
                messageLabel, loginProgress));

        // Enter key support
        passwordField.setOnAction(e -> loginButton.fire());
        usernameField.setOnAction(e -> passwordField.requestFocus());

        root.getChildren().addAll(headerSection, formSection);

        // Focus on username field
        Platform.runLater(() -> usernameField.requestFocus());

        return root;
    }

    /**
     * Handles user login with enhanced validation and error handling.
     */
    private void handleLogin(String username, String password, Label messageLabel,
                             ProgressIndicator progressIndicator) {

        clearMessage(messageLabel);

        if (username == null || username.trim().isEmpty()) {
            showErrorMessage(messageLabel, "Please enter your username.");
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            showErrorMessage(messageLabel, "Please enter your password.");
            return;
        }

        // Show loading state
        progressIndicator.setVisible(true);
        messageLabel.setText("Authenticating...");
        messageLabel.setStyle("-fx-text-fill: blue; -fx-font-size: 12px;");

        // Perform login asynchronously
        CompletableFuture.supplyAsync(() -> {
            try {
                return UserService.login(username.trim(), password);
            } catch (Exception e) {
                System.err.println("Login failed for user: " + username + " - " + e.getMessage());
                return false;
            }
        }).thenAcceptAsync(success -> {
            Platform.runLater(() -> {
                progressIndicator.setVisible(false);

                if (success) {
                    currentUser = username.trim();
                    showSuccessMessage(messageLabel, "Login successful! Loading dashboard...");

                    // Small delay to show success message
                    Timeline timeline = new Timeline(new KeyFrame(Duration.millis(1000), e -> showMainDashboard()));
                    timeline.play();
                } else {
                    showErrorMessage(messageLabel, "Invalid username or password. Please try again.");
                }
            });
        });
    }

    /**
     * Handles user registration with validation.
     */
    private void handleRegistration(String username, String password, Label messageLabel,
                                    ProgressIndicator progressIndicator) {

        clearMessage(messageLabel);

        if (username == null || username.trim().isEmpty()) {
            showErrorMessage(messageLabel, "Please enter a username.");
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            showErrorMessage(messageLabel, "Please enter a password.");
            return;
        }

        // Show loading state
        progressIndicator.setVisible(true);
        messageLabel.setText("Creating account...");
        messageLabel.setStyle("-fx-text-fill: blue; -fx-font-size: 12px;");

        // Perform registration asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                UserService.createUser(username.trim(), password);
                UserService.persistDatabase();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    showErrorMessage(messageLabel, "Registration failed: " + e.getMessage());
                });
                return;
            }

            Platform.runLater(() -> {
                progressIndicator.setVisible(false);
                showSuccessMessage(messageLabel, "Account created successfully! You can now login.");
            });
        });
    }

    /**
     * Shows the main dashboard with enhanced layout and functionality.
     */
    private void showMainDashboard() {
        BorderPane mainPane = new BorderPane();
        mainPane.setStyle("-fx-background-color: white;");

        // Create main sections
        mainPane.setTop(createHeaderSection());
        mainPane.setLeft(createBooksSection());
        mainPane.setCenter(createIssueRecordsSection());
        mainPane.setBottom(createStatusBar());

        Scene mainScene = new Scene(mainPane, 1600, 900);

        primaryStage.setScene(mainScene);
        primaryStage.setMaximized(true);

        // Load initial data
        refreshAllData();

        // Auto-refresh every 30 seconds
        Timeline autoRefresh = new Timeline(new KeyFrame(Duration.seconds(30), e -> refreshAllData()));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
    }

    /**
     * Creates the header section with user info and quick stats.
     */
    private HBox createHeaderSection() {
        HBox header = new HBox(20);
        header.setPadding(new Insets(15, 20, 15, 20));
        header.setStyle("-fx-background-color: #2c3e50;");
        header.setAlignment(Pos.CENTER_LEFT);

        // User info section
        VBox userInfo = new VBox(2);
        Label welcomeLabel = new Label("Welcome, " + currentUser);
        welcomeLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        Label timeLabel = new Label("Logged in at " + LocalDate.now().format(DATE_FORMATTER));
        timeLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 12px;");

        userInfo.getChildren().addAll(welcomeLabel, timeLabel);

        // Statistics section
        statisticsLabel = new Label("Loading statistics...");
        statisticsLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        // Action buttons section
        HBox actionButtons = new HBox(10);

        Button refreshButton = createStyledButton("🔄 Refresh", BUTTON_STYLE_PRIMARY);
        refreshButton.setOnAction(e -> refreshAllData());

        Button settingsButton = createStyledButton("⚙️ Settings", BUTTON_STYLE_PRIMARY);
        settingsButton.setOnAction(e -> showSettingsDialog());

        Button logoutButton = createStyledButton("🚪 Logout", BUTTON_STYLE_DANGER);
        logoutButton.setOnAction(e -> handleLogout());

        actionButtons.getChildren().addAll(refreshButton, settingsButton, logoutButton);

        // Spacer to push action buttons to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(userInfo, spacer, statisticsLabel, actionButtons);

        return header;
    }

    /**
     * Creates the books management section.
     */
    private VBox createBooksSection() {
        VBox booksSection = new VBox(10);
        booksSection.setPadding(new Insets(15));
        booksSection.setPrefWidth(700);
        booksSection.setStyle("-fx-background-color: #f9fafb; -fx-border-radius: 10; -fx-background-radius: 10;");

        Label sectionTitle = new Label("📚 Books Management");
        sectionTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #333;");

        // Toolbar
        HBox toolbar = new HBox(10);
        Button btnAdd = createStyledButton("➕ Add Book", BUTTON_STYLE_SUCCESS);
        Button btnUpdate = createStyledButton("✏️ Update Book", BUTTON_STYLE_PRIMARY);
        Button btnDelete = createStyledButton("🗑️ Delete Book", BUTTON_STYLE_DANGER);
        Button btnRefresh = createStyledButton("🔄 Refresh", BUTTON_STYLE_PRIMARY);

        btnAdd.setOnAction(e -> handleAddBook());
        btnUpdate.setOnAction(e -> handleUpdateBook());
        btnDelete.setOnAction(e -> handleDeleteBook());
        btnRefresh.setOnAction(e -> refreshAllData());

        toolbar.getChildren().addAll(btnAdd, btnUpdate, btnDelete, btnRefresh);

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search books by title, author, category, ISBN...");
        searchField.setMaxWidth(Double.MAX_VALUE);

        // Books list view
        booksListView = new ListView<>();
        filteredBooks = new FilteredList<>(booksList, b -> true);
        booksListView.setItems(filteredBooks);

        setupBooksListView();
        setupSearchFilter();

        // Issue button
        Button btnIssue = createStyledButton("📖 Issue Book", BUTTON_STYLE_SUCCESS);
        btnIssue.setOnAction(e -> handleIssueBook());

        booksSection.getChildren().addAll(sectionTitle, toolbar, searchField, booksListView, btnIssue);

        return booksSection;
    }

    /**
     * Creates the issue records section.
     */
    private VBox createIssueRecordsSection() {
        VBox issueSection = new VBox(10);
        issueSection.setPadding(new Insets(15));
        issueSection.setPrefWidth(660);
        issueSection.setStyle("-fx-background-color: #f9fafb; -fx-border-radius: 10; -fx-background-radius: 10;");

        Label sectionTitle = new Label("📋 Issued Books");
        sectionTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #333;");

        // Issue records list view
        issueRecordsListView = new ListView<>();
        filteredIssueRecords = new FilteredList<>(issueRecordsList, r -> true);
        issueRecordsListView.setItems(filteredIssueRecords);

        setupIssueRecordsListView();

        // Action buttons
        HBox buttons = new HBox(15);
        Button btnReturn = createStyledButton("↩️ Return Book", BUTTON_STYLE_DANGER);
        Button btnViewUser = createStyledButton("👤 View User", BUTTON_STYLE_PRIMARY);
        Button btnOverdueReport = createStyledButton("⚠️ Overdue Report", BUTTON_STYLE_WARNING);
        Button btnSendReminders = createStyledButton("📧 Send Reminders", "#8e44ad");

        btnReturn.setOnAction(e -> handleReturnBook());
        btnViewUser.setOnAction(e -> handleViewUserDetails());
        btnOverdueReport.setOnAction(e -> showOverdueReport());
        btnSendReminders.setOnAction(e -> sendReminders());

        buttons.getChildren().addAll(btnReturn, btnViewUser, btnOverdueReport, btnSendReminders);
        buttons.setAlignment(Pos.CENTER);

        issueSection.getChildren().addAll(sectionTitle, issueRecordsListView, buttons);

        return issueSection;
    }

    /**
     * Creates the status bar.
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5, 15, 5, 15));
        statusBar.setStyle("-fx-background-color: #ecf0f1; -fx-border-color: #bdc3c7; -fx-border-width: 1 0 0 0;");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #333;");

        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setMaxSize(16, 16);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label versionLabel = new Label("Version " + APP_VERSION);
        versionLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        statusBar.getChildren().addAll(statusLabel, loadingIndicator, spacer, versionLabel);

        return statusBar;
    }

    /**
     * Sets up the books list view with custom cell factory.
     */
    private void setupBooksListView() {
        booksListView.setCellFactory(lv -> new ListCell<Book>() {
            @Override
            protected void updateItem(Book book, boolean empty) {
                super.updateItem(book, empty);
                if (empty || book == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                int available = book.getQuantity();
                String icon = (available > 0) ? "✅" : "❌";

                setText(String.format("📘 %s\n   by %s | %s\n   Available: %d %s",
                        book.getTitle(), book.getAuthor(), book.getCategory(),
                        available, icon));

                setStyle(available > 0 ? "-fx-background-color: #e6fae6;" : "-fx-background-color: #ffe6e6;");
            }
        });
    }

    /**
     * Sets up the issue records list view with custom cell factory.
     */
    private void setupIssueRecordsListView() {
        issueRecordsListView.setCellFactory(lv -> new ListCell<IssueRecord>() {
            @Override
            protected void updateItem(IssueRecord record, boolean empty) {
                super.updateItem(record, empty);
                if (empty || record == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                String userInfo;
                try {
                    User user = UserService.getUserById(record.getUserId());
                    userInfo = user.getUserId() + (user.getEmail() != null ? " (" + user.getEmail() + ")" : "");
                } catch (Exception e) {
                    userInfo = record.getUserId();
                }

                String status = record.getDaysOverdue() > 0 ? "⚠️ OVERDUE (" + record.getDaysOverdue() + " days)" : "✅ On Time";
                String fineInfo = record.getDaysOverdue() > 0 ? String.format(", Fine $%.2f", record.calculateFine()) : "";

                setText(String.format("📚 %s\nUser: %s\nQty: %d | Issued: %s | Due: %s\nStatus: %s%s",
                        record.getBookTitle(), userInfo, record.getQuantity(),
                        record.getIssueDate(), record.getDueDate(), status, fineInfo));

                setStyle(record.getDaysOverdue() > 0 ? "-fx-background-color: #ffdddd;" : "-fx-background-color: #ddffdd;");
            }
        });
    }

    /**
     * Sets up search filtering for books.
     */
    private void setupSearchFilter() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = (newVal == null) ? "" : newVal.toLowerCase();
            filteredBooks.setPredicate(book -> {
                if (filter.isEmpty()) return true;
                return book.getTitle().toLowerCase().contains(filter)
                        || book.getAuthor().toLowerCase().contains(filter)
                        || book.getCategory().toLowerCase().contains(filter)
                        || book.getIsbn().toLowerCase().contains(filter);
            });
        });
    }

    /**
     * Refreshes all data asynchronously with loading indicators.
     */
    private void refreshAllData() {
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(true);
            statusLabel.setText("Loading data...");
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("books", BookService.getAllBooks());
                data.put("issueRecords", BookService.getAllActiveIssueRecords());
                data.put("statistics", BookService.getLibraryStatistics());
                return data;
            } catch (Exception e) {
                System.err.println("Failed to refresh data: " + e.getMessage());
                return new HashMap<String, Object>();
            }
        }).thenAcceptAsync(data -> {
            Platform.runLater(() -> {
                updateUIWithData(data);
                if (loadingIndicator != null) {
                    loadingIndicator.setVisible(false);
                    statusLabel.setText("Ready");
                }
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void updateUIWithData(Map<String, Object> data) {
        try {
            // Update books list
            List<Book> books = (List<Book>) data.get("books");
            if (books != null) {
                booksList.setAll(books);
            }

            // Update issue records list
            List<IssueRecord> records = (List<IssueRecord>) data.get("issueRecords");
            if (records != null) {
                issueRecordsList.setAll(records);
            }

            // Update statistics
            Map<String, Object> stats = (Map<String, Object>) data.get("statistics");
            if (stats != null) {
                updateStatisticsDisplay(stats);
            }

        } catch (Exception e) {
            System.err.println("Failed to update UI with data: " + e.getMessage());
            showErrorAlert("Failed to update display data: " + e.getMessage());
        }
    }

    private void updateStatisticsDisplay(Map<String, Object> stats) {
        try {
            String statsText = String.format(
                    "📚 Books: %d | 📖 Available: %d | 📋 Issued: %d | ⚠️ Overdue: %d | 💰 Fines: $%.2f",
                    stats.get("totalBooks"),
                    stats.get("availableCopies"),
                    stats.get("issuedCopies"),
                    stats.get("overdueBooks"),
                    stats.get("totalFines")
            );
            statisticsLabel.setText(statsText);
        } catch (Exception e) {
            statisticsLabel.setText("Statistics unavailable");
            System.err.println("Failed to format statistics: " + e.getMessage());
        }
    }

    // Event handlers for book management
    private void handleAddBook() {
        Optional<Book> result = showBookDialog("Add New Book", null);
        result.ifPresent(book -> {
            try {
                BookService.addBook(book);
                showSuccessAlert("Book added successfully.");
                refreshAllData();
            } catch (Exception e) {
                showErrorAlert("Failed to add book: " + e.getMessage());
            }
        });
    }

    private void handleUpdateBook() {
        Book selected = booksListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showErrorAlert("Please select a book to update.");
            return;
        }

        Optional<Book> result = showBookDialog("Update Book", selected);
        result.ifPresent(book -> {
            try {
                BookService.updateBook(book);
                showSuccessAlert("Book updated successfully.");
                refreshAllData();
            } catch (Exception e) {
                showErrorAlert("Failed to update book: " + e.getMessage());
            }
        });
    }

    private void handleDeleteBook() {
        Book selected = booksListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showErrorAlert("Please select a book to delete.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete this book?\n" + selected.getTitle(),
                ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    BookService.deleteBook(selected.getIsbn());
                    showSuccessAlert("Book deleted successfully.");
                    refreshAllData();
                } catch (Exception e) {
                    showErrorAlert("Failed to delete book: " + e.getMessage());
                }
            }
        });
    }

    private void handleIssueBook() {
        Book selected = booksListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showErrorAlert("Please select a book to issue.");
            return;
        }

        if (selected.getQuantity() <= 0) {
            showErrorAlert("This book is out of stock.");
            return;
        }

        showUserSelectionDialog("Select User to Issue Book")
                .ifPresent(user -> {
                    showQuantityDialog("Issue Copies", "How many copies to issue?", selected.getQuantity(), 1)
                            .ifPresent(quantity -> {
                                try {
                                    BookService.issueBookToUser(selected.getIsbn(), user.getUserId(), quantity);
                                    showSuccessAlert("Book issued successfully to " + user.getUserId());
                                    refreshAllData();
                                } catch (Exception e) {
                                    showErrorAlert("Failed to issue book: " + e.getMessage());
                                }
                            });
                });
    }

    private void handleReturnBook() {
        IssueRecord selected = issueRecordsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showErrorAlert("Please select an issued book to return.");
            return;
        }

        showQuantityDialog("Return Copies", "How many copies to return?", selected.getQuantity(), selected.getQuantity())
                .ifPresent(quantity -> {
                    try {
                        BookService.returnBookFromUser(selected.getIsbn(), selected.getUserId(), quantity);
                        showSuccessAlert("Book returned successfully.");
                        refreshAllData();
                    } catch (Exception e) {
                        showErrorAlert("Failed to return book: " + e.getMessage());
                    }
                });
    }

    private void handleViewUserDetails() {
        IssueRecord selected = issueRecordsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showErrorAlert("Please select an issued book to view user details.");
            return;
        }

        try {
            User user = UserService.getUserById(selected.getUserId());
            String details = String.format(
                    "User Details:\n\n" +
                            "Username: %s\n" +
                            "Email: %s\n" +
                            "Contact: %s\n" +
                            "Books Borrowed: %d\n" +
                            "Outstanding Fines: $%.2f",
                    user.getUserId(),
                    user.getEmail() != null ? user.getEmail() : "Not provided",
                    user.getContactNumber() != null ? user.getContactNumber() : "Not provided",
                    BookService.getUserTotalBorrowedBooks(user.getUserId()),
                    BookService.getUserTotalFine(user.getUserId())
            );

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("User Details");
            alert.setHeaderText(null);
            alert.setContentText(details);
            alert.showAndWait();
        } catch (Exception e) {
            showErrorAlert("Failed to load user details: " + e.getMessage());
        }
    }

    private void showOverdueReport() {
        List<IssueRecord> overdueBooks = BookService.getAllOverdueBooks();
        if (overdueBooks.isEmpty()) {
            showInfoAlert("No overdue books found.");
            return;
        }

        StringBuilder report = new StringBuilder("Overdue Books Report:\n\n");
        double totalFines = 0;

        for (IssueRecord record : overdueBooks) {
            double fine = record.calculateFine();
            totalFines += fine;
            report.append(String.format(
                    "Book: %s\nUser: %s\nDue Date: %s\nDays Overdue: %d\nFine: $%.2f\n\n",
                    record.getBookTitle(),
                    record.getUserId(),
                    record.getDueDate(),
                    record.getDaysOverdue(),
                    fine
            ));
        }

        report.append(String.format("Total Outstanding Fines: $%.2f", totalFines));

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Overdue Books Report");
        alert.setHeaderText("Found " + overdueBooks.size() + " overdue books");
        alert.setContentText(report.toString());
        alert.getDialogPane().setPrefSize(500, 400);
        alert.showAndWait();
    }

    private void sendReminders() {
        List<IssueRecord> overdueBooks = BookService.getAllOverdueBooks();
        if (overdueBooks.isEmpty()) {
            showInfoAlert("No overdue books. No reminders to send.");
            return;
        }

        Set<String> uniqueUsers = overdueBooks.stream()
                .map(IssueRecord::getUserId)
                .collect(Collectors.toSet());

        showInfoAlert("Simulated email reminders sent to " + uniqueUsers.size() + " users with overdue books.");
    }

    // Dialog methods
    private Optional<Book> showBookDialog(String title, Book existingBook) {
        Dialog<Book> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField isbnField = new TextField();
        TextField titleField = new TextField();
        TextField authorField = new TextField();
        TextField categoryField = new TextField();
        TextField quantityField = new TextField();

        if (existingBook != null) {
            isbnField.setText(existingBook.getIsbn());
            isbnField.setDisable(true);
            titleField.setText(existingBook.getTitle());
            authorField.setText(existingBook.getAuthor());
            categoryField.setText(existingBook.getCategory());
            quantityField.setText(String.valueOf(existingBook.getQuantity()));
        } else {
            isbnField.setPromptText("ISBN");
            titleField.setPromptText("Title");
            authorField.setPromptText("Author");
            categoryField.setPromptText("Category");
            quantityField.setPromptText("Quantity");
        }

        grid.addRow(0, new Label("ISBN:"), isbnField);
        grid.addRow(1, new Label("Title:"), titleField);
        grid.addRow(2, new Label("Author:"), authorField);
        grid.addRow(3, new Label("Category:"), categoryField);
        grid.addRow(4, new Label("Quantity:"), quantityField);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                try {
                    if (isbnField.getText().trim().isEmpty() ||
                            titleField.getText().trim().isEmpty() ||
                            authorField.getText().trim().isEmpty() ||
                            categoryField.getText().trim().isEmpty() ||
                            quantityField.getText().trim().isEmpty()) {
                        showErrorAlert("All fields are required.");
                        return null;
                    }

                    int quantity = Integer.parseInt(quantityField.getText().trim());
                    if (quantity < 0) {
                        showErrorAlert("Quantity cannot be negative.");
                        return null;
                    }

                    if (existingBook != null) {
                        existingBook.setTitle(titleField.getText().trim());
                        existingBook.setAuthor(authorField.getText().trim());
                        existingBook.setCategory(categoryField.getText().trim());
                        existingBook.setQuantity(quantity);
                        return existingBook;
                    } else {
                        return new Book(
                                isbnField.getText().trim(),
                                titleField.getText().trim(),
                                authorField.getText().trim(),
                                categoryField.getText().trim(),
                                quantity
                        );
                    }
                } catch (NumberFormatException e) {
                    showErrorAlert("Please enter a valid number for quantity.");
                    return null;
                } catch (Exception e) {
                    showErrorAlert("Error creating book: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private Optional<User> showUserSelectionDialog(String title) {
        List<User> allUsers = UserService.getAllUsers();
        if (allUsers.isEmpty()) {
            showErrorAlert("No users found. Please register users first.");
            return Optional.empty();
        }

        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResizable(true);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        TextField searchFieldUser = new TextField();
        searchFieldUser.setPromptText("Search users by name or ID...");

        ListView<User> userListView = new ListView<>();
        ObservableList<User> userObservableList = FXCollections.observableArrayList(allUsers);
        FilteredList<User> filteredUsers = new FilteredList<>(userObservableList, user -> true);
        userListView.setItems(filteredUsers);
        userListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        userListView.setPrefHeight(200);

        // Set up the list cell factory to display user information
        userListView.setCellFactory(lv -> new ListCell<User>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                } else {
                    String displayText = user.getUserId();
                    if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                        displayText += " (" + user.getEmail() + ")";
                    }
                    if (user.getContactNumber() != null && !user.getContactNumber().trim().isEmpty()) {
                        displayText += " - " + user.getContactNumber();
                    }
                    setText(displayText);
                }
            }
        });

        // Set up search filtering
        searchFieldUser.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal == null ? "" : newVal.toLowerCase().trim();
            filteredUsers.setPredicate(user -> {
                if (filter.isEmpty()) return true;
                return user.getUserId().toLowerCase().contains(filter) ||
                        (user.getEmail() != null && user.getEmail().toLowerCase().contains(filter)) ||
                        (user.getContactNumber() != null && user.getContactNumber().toLowerCase().contains(filter));
            });
        });

        content.getChildren().addAll(
                new Label("Search and select a user:"),
                searchFieldUser,
                userListView
        );

        dialog.getDialogPane().setContent(content);

        // Set result converter
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                return userListView.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private Optional<Integer> showQuantityDialog(String title, String content, int max, int defaultValue) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(defaultValue));
        dialog.setTitle(title);
        dialog.setHeaderText(content);
        dialog.setContentText("Quantity (1-" + max + "):");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                int quantity = Integer.parseInt(result.get());
                if (quantity >= 1 && quantity <= max) {
                    return Optional.of(quantity);
                } else {
                    showErrorAlert("Quantity must be between 1 and " + max);
                }
            } catch (NumberFormatException e) {
                showErrorAlert("Please enter a valid number.");
            }
        }
        return Optional.empty();
    }

    // === FIXED SETTINGS MODULE START ===

    /**
     * Shows the main settings dialog with different options.
     */
    private void showSettingsDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Library Management System Settings");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.setResizable(true);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(400);

        // User Profile Section
        VBox profileSection = new VBox(10);
        Label profileTitle = new Label("User Profile");
        profileTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50;");

        Button editProfileButton = createStyledButton("✏️ Edit Profile", BUTTON_STYLE_PRIMARY);
        editProfileButton.setPrefWidth(200);
        editProfileButton.setOnAction(e -> {
            dialog.close();
            showUserSettingsStage();
        });

        Button changePasswordButton = createStyledButton("🔒 Change Password", BUTTON_STYLE_WARNING);
        changePasswordButton.setPrefWidth(200);
        changePasswordButton.setOnAction(e -> {
            dialog.close();
            showChangePasswordStage();
        });

        profileSection.getChildren().addAll(profileTitle, editProfileButton, changePasswordButton);

        // System Settings Section
        VBox systemSection = new VBox(10);
        Label systemTitle = new Label("System Settings");
        systemTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50;");

        Button libraryConfigButton = createStyledButton("📚 Library Configuration", BUTTON_STYLE_PRIMARY);
        libraryConfigButton.setPrefWidth(200);
        libraryConfigButton.setOnAction(e -> {
            dialog.close();
            showLibraryConfigDialog();
        });

        Button dataManagementButton = createStyledButton("💾 Data Management", BUTTON_STYLE_PRIMARY);
        dataManagementButton.setPrefWidth(200);
        dataManagementButton.setOnAction(e -> {
            dialog.close();
            showDataManagementDialog();
        });

        systemSection.getChildren().addAll(systemTitle, libraryConfigButton, dataManagementButton);

        // About Section
        VBox aboutSection = new VBox(10);
        Label aboutTitle = new Label("About");
        aboutTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50;");

        Label versionLabel = new Label("Library Management System v" + APP_VERSION);
        versionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        Label copyrightLabel = new Label("© 2025 Library Management System");
        copyrightLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        aboutSection.getChildren().addAll(aboutTitle, versionLabel, copyrightLabel);

        content.getChildren().addAll(
                profileSection,
                new Separator(),
                systemSection,
                new Separator(),
                aboutSection
        );

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /**
     * FIXED: Shows the user settings/profile dialog for updating user details.
     * This version ensures all text fields are properly editable.
     */
    private boolean showUserSettingsStage() {
        try {
            User currentUserObj = UserService.getUserById(currentUser);

            Stage stage = new Stage();
            stage.setTitle("User Settings - " + currentUser);
            stage.initModality(Modality.APPLICATION_MODAL);

            GridPane grid = new GridPane();
            grid.setHgap(15);
            grid.setVgap(15);
            grid.setPadding(new Insets(20));

            Label userIdLabel = new Label("Username:");
            TextField userIdField = new TextField(currentUserObj.getUserId());
            userIdField.setDisable(true);
            userIdField.setStyle("-fx-background-color: #f8f9fa; -fx-opacity: 1;");

            Label firstNameLabel = new Label("First Name:");
            TextField firstNameField = new TextField(Optional.ofNullable(currentUserObj.getFirstName()).orElse(""));
            firstNameField.setPromptText("Enter your first name");

            Label lastNameLabel = new Label("Last Name:");
            TextField lastNameField = new TextField(Optional.ofNullable(currentUserObj.getLastName()).orElse(""));
            lastNameField.setPromptText("Enter your last name");

            Label emailLabel = new Label("Email Address:");
            TextField emailField = new TextField(Optional.ofNullable(currentUserObj.getEmail()).orElse(""));
            emailField.setPromptText("Enter your email address");

            Label contactLabel = new Label("Contact Number:");
            TextField contactField = new TextField(Optional.ofNullable(currentUserObj.getContactNumber()).orElse(""));
            contactField.setPromptText("Enter your contact number");

            grid.addRow(0, userIdLabel, userIdField);
            grid.addRow(1, firstNameLabel, firstNameField);
            grid.addRow(2, lastNameLabel, lastNameField);
            grid.addRow(3, emailLabel, emailField);
            grid.addRow(4, contactLabel, contactField);

            Label validationLabel = new Label();
            validationLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
            validationLabel.setWrapText(true);
            validationLabel.setMaxWidth(400);

            Button btnOk = new Button("OK");
            Button btnCancel = new Button("Cancel");

            HBox buttons = new HBox(10, btnOk, btnCancel);
            buttons.setPadding(new Insets(10));
            buttons.setAlignment(Pos.CENTER_RIGHT);

            VBox vbox = new VBox(10, grid, validationLabel, buttons);
            vbox.setPadding(new Insets(10));

            Scene scene = new Scene(vbox);
            stage.setScene(scene);

            final boolean[] updated = {false};

            btnOk.setOnAction(event -> {
                String emailText = emailField.getText().trim();
                String contactText = contactField.getText().trim();

                if (!emailText.isEmpty() && !User.isValidEmail(emailText)) {
                    validationLabel.setText("❌ Invalid email format.");
                    return;
                }
                if (!contactText.isEmpty() && !User.isValidContactNumber(contactText)) {
                    validationLabel.setText("❌ Invalid contact number format.");
                    return;
                }

                currentUserObj.setFirstName(firstNameField.getText().trim().isEmpty() ? null : firstNameField.getText().trim());
                currentUserObj.setLastName(lastNameField.getText().trim().isEmpty() ? null : lastNameField.getText().trim());
                currentUserObj.setEmail(emailText.isEmpty() ? null : emailText);
                currentUserObj.setContactNumber(contactText.isEmpty() ? null : contactText);

                try {
                    UserService.updateUser(currentUserObj);
                    UserService.persistDatabase();
                    updated[0] = true;
                    stage.close();
                } catch (Exception ex) {
                    validationLabel.setText("❌ Failed to update profile: " + ex.getMessage());
                }
            });

            btnCancel.setOnAction(event -> stage.close());

            stage.show();

            if (updated[0]) {
                showSuccessAlert("Profile updated successfully!");
                refreshHeaderInfo();
            }

            return updated[0];

        } catch (Exception e) {
            showErrorAlert("Failed to load user settings: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates the user profile with the provided information.
     */
    private boolean updateUserProfile(User user, String email, String contact,
                                      String firstName, String lastName,
                                      String currentPassword, String newPassword,
                                      String confirmPassword, Label validationLabel) {
        try {
            // Clear previous validation messages
            validationLabel.setText("");
            validationLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px; -fx-font-weight: bold;");

            // Validate email format if provided
            if (email != null && !email.trim().isEmpty()) {
                if (!User.isValidEmail(email.trim())) {
                    validationLabel.setText("❌ Invalid email format. Please enter a valid email address.");
                    return false;
                }
            }

            // Validate contact number if provided
            if (contact != null && !contact.trim().isEmpty()) {
                if (!User.isValidContactNumber(contact.trim())) {
                    validationLabel.setText("❌ Invalid contact number format. Please enter a valid phone number.");
                    return false;
                }
            }

            // Validate password change if requested
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                // Check current password first
                if (currentPassword == null || currentPassword.trim().isEmpty()) {
                    validationLabel.setText("❌ Current password is required to change password.");
                    return false;
                }

                if (!UserService.login(user.getUserId(), currentPassword)) {
                    validationLabel.setText("❌ Current password is incorrect.");
                    return false;
                }

                // Validate new password
                if (!User.isValidPassword(newPassword)) {
                    validationLabel.setText("❌ New password must be at least 4 characters long.");
                    return false;
                }

                // Check password confirmation
                if (!newPassword.equals(confirmPassword)) {
                    validationLabel.setText("❌ New passwords do not match.");
                    return false;
                }
            }

            // Update user information
            user.setEmail(email != null && !email.trim().isEmpty() ? email.trim() : null);
            user.setContactNumber(contact != null && !contact.trim().isEmpty() ? contact.trim() : null);
            user.setFirstName(firstName != null && !firstName.trim().isEmpty() ? firstName.trim() : null);
            user.setLastName(lastName != null && !lastName.trim().isEmpty() ? lastName.trim() : null);

            // Update password if requested
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                user.setPassword(newPassword);
            }

            // Update last login time
            user.updateLastLogin();

            // Save changes
            UserService.updateUser(user);
            UserService.persistDatabase();

            // Show success message
            validationLabel.setText("✅ Profile updated successfully!");
            validationLabel.setStyle("-fx-text-fill: green; -fx-font-size: 12px; -fx-font-weight: bold;");

            return true;

        } catch (Exception e) {
            validationLabel.setText("❌ Update failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Shows a dedicated password change dialog.
     */
    private boolean showChangePasswordStage() {
        try {
            Stage stage = new Stage();
            stage.setTitle("Change Password");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);

            VBox content = new VBox(20);
            content.setPadding(new Insets(25));

            GridPane grid = new GridPane();
            grid.setHgap(15);
            grid.setVgap(15);

            PasswordField currentPasswordField = new PasswordField();
            currentPasswordField.setPromptText("Enter current password");
            currentPasswordField.setPrefWidth(300);

            PasswordField newPasswordField = new PasswordField();
            newPasswordField.setPromptText("Enter new password (min 4 characters)");
            newPasswordField.setPrefWidth(300);

            PasswordField confirmPasswordField = new PasswordField();
            confirmPasswordField.setPromptText("Confirm new password");
            confirmPasswordField.setPrefWidth(300);

            Label messageLabel = new Label();
            messageLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
            messageLabel.setWrapText(true);
            messageLabel.setPrefWidth(400);

            // Password strength indicator
            Label strengthLabel = new Label();
            strengthLabel.setStyle("-fx-font-size: 11px;");

            grid.add(new Label("Current Password:"), 0, 0);
            grid.add(currentPasswordField, 1, 0);

            grid.add(new Label("New Password:"), 0, 1);
            grid.add(newPasswordField, 1, 1);

            grid.add(new Label("Confirm Password:"), 0, 2);
            grid.add(confirmPasswordField, 1, 2);

            grid.add(strengthLabel, 1, 3);

            content.getChildren().addAll(grid, messageLabel);

            // Buttons
            Button btnOk = new Button("OK");
            Button btnCancel = new Button("Cancel");
            HBox buttons = new HBox(10, btnOk, btnCancel);
            buttons.setAlignment(Pos.CENTER_RIGHT);

            content.getChildren().add(buttons);

            Scene scene = new Scene(content);
            stage.setScene(scene);

            final boolean[] passwordChanged = {false};

            // Password strength checker
            newPasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null || newVal.isEmpty()) {
                    strengthLabel.setText("");
                    return;
                }

                String strength = getPasswordStrength(newVal);
                strengthLabel.setText("Password strength: " + strength);

                if ("Weak".equals(strength)) {
                    strengthLabel.setStyle("-fx-text-fill: red; -fx-font-size: 11px;");
                } else if ("Medium".equals(strength)) {
                    strengthLabel.setStyle("-fx-text-fill: orange; -fx-font-size: 11px;");
                } else {
                    strengthLabel.setStyle("-fx-text-fill: green; -fx-font-size: 11px;");
                }
            });

            btnOk.setOnAction(event -> {
                // Call your existing password change logic passing messageLabel for errors
                boolean success = changeUserPassword(
                        currentPasswordField.getText(),
                        newPasswordField.getText(),
                        confirmPasswordField.getText(),
                        messageLabel);

                if (success) {
                    passwordChanged[0] = true;
                    stage.close();
                }
                // else keep window open for corrections
            });

            btnCancel.setOnAction(event -> stage.close());

            // Set initial focus
            stage.setOnShown(event -> currentPasswordField.requestFocus());

            stage.show();

            if (passwordChanged[0]) {
                showSuccessAlert("Password changed successfully!");
            }
            return passwordChanged[0];

        } catch (Exception e) {
            showErrorAlert("Failed to open Change Password window: " + e.getMessage());
            return false;
        }
    }

    /**
     * Changes the user's password with validation.
     */
    private boolean changeUserPassword(String currentPassword, String newPassword,
                                       String confirmPassword, Label messageLabel) {
        try {
            messageLabel.setText("");
            messageLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");

            // Validate inputs
            if (currentPassword == null || currentPassword.trim().isEmpty()) {
                messageLabel.setText("Current password is required.");
                return false;
            }

            if (newPassword == null || newPassword.trim().isEmpty()) {
                messageLabel.setText("New password is required.");
                return false;
            }

            if (!newPassword.equals(confirmPassword)) {
                messageLabel.setText("New passwords do not match.");
                return false;
            }

            // Validate current password
            if (!UserService.login(currentUser, currentPassword)) {
                messageLabel.setText("Current password is incorrect.");
                return false;
            }

            // Validate new password
            if (!User.isValidPassword(newPassword)) {
                messageLabel.setText("New password must be at least 4 characters long.");
                return false;
            }

            // Check if new password is different from current
            if (newPassword.equals(currentPassword)) {
                messageLabel.setText("New password must be different from current password.");
                return false;
            }

            // Update password
            User user = UserService.getUserById(currentUser);
            user.setPassword(newPassword);
            user.updateLastLogin();
            UserService.updateUser(user);
            UserService.persistDatabase();

            messageLabel.setText("Password changed successfully!");
            messageLabel.setStyle("-fx-text-fill: green; -fx-font-size: 12px;");

            return true;

        } catch (Exception e) {
            messageLabel.setText("Password change failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets password strength indicator.
     */
    private String getPasswordStrength(String password) {
        if (password == null || password.length() < 4) return "Weak";

        int score = 0;

        // Length
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;

        // Character types
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[!@#$%^&*()_+=\\-\\[\\]{};':\"\\\\|,./<>\\/?].*")) score++;

        if (score <= 2) return "Weak";
        if (score <= 4) return "Medium";
        return "Strong";
    }

    /**
     * Shows library configuration dialog for system settings.
     */
    private void showLibraryConfigDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Library Configuration");
        dialog.setHeaderText("System Configuration Settings");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK);
        dialog.setResizable(true);

        VBox content = new VBox(20);
        content.setPadding(new Insets(25));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);

        // Current settings (read-only display)
        Label currentSettingsLabel = new Label("Current Library Settings");
        currentSettingsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");

        TextField maxBorrowField = new TextField("5");  // Default values since BookService methods may not exist
        maxBorrowField.setDisable(true);
        maxBorrowField.setStyle("-fx-background-color: #f5f5f5;");

        TextField loanPeriodField = new TextField("14");
        loanPeriodField.setDisable(true);
        loanPeriodField.setStyle("-fx-background-color: #f5f5f5;");

        TextField finePerDayField = new TextField("$2.00");
        finePerDayField.setDisable(true);
        finePerDayField.setStyle("-fx-background-color: #f5f5f5;");

        int row = 0;
        grid.add(currentSettingsLabel, 0, row++, 2, 1);

        grid.add(new Label("Maximum Books Per User:"), 0, row);
        grid.add(maxBorrowField, 1, row++);

        grid.add(new Label("Loan Period (days):"), 0, row);
        grid.add(loanPeriodField, 1, row++);

        grid.add(new Label("Fine Per Day:"), 0, row);
        grid.add(finePerDayField, 1, row++);

        // Future configuration options
        Label futureLabel = new Label("Advanced Configuration");
        futureLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");

        Label infoLabel = new Label("Advanced configuration features including custom loan periods,\ndynamic fine calculations, and user group management\nwill be available in future versions.");
        infoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        infoLabel.setWrapText(true);

        content.getChildren().addAll(grid, new Separator(), futureLabel, infoLabel);

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /**
     * Shows data management options with FIXED backup button.
     */
    private void showDataManagementDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Data Management");
        dialog.setHeaderText("Manage your library data");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.setResizable(true);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(350);

        // Backup Section
        VBox backupSection = new VBox(10);
        Label backupTitle = new Label("Database Backup");
        backupTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");

        Button backupButton = createStyledButton("💾 Create Backup", BUTTON_STYLE_SUCCESS);
        backupButton.setPrefWidth(200);
        // FIXED BACKUP BUTTON HANDLER
        backupButton.setOnAction(e -> {
            // Show loading state
            if (loadingIndicator != null) {
                loadingIndicator.setVisible(true);
                statusLabel.setText("Creating backup...");
            }

            // Run backup operation in background thread
            CompletableFuture.supplyAsync(() -> {
                try {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    boolean success = true;

                    success &= com.example.storage.DataStorage.createBackup("data/users_db.ser");
                    success &= com.example.storage.DataStorage.createBackup("data/books_db.ser");
                    success &= com.example.storage.DataStorage.createBackup("data/issued_books.ser");
                    success &= com.example.storage.DataStorage.createBackup("data/borrower_details.ser");
                    success &= com.example.storage.DataStorage.createBackup("data/issue_records.ser");

                    return Map.of("success", success, "timestamp", timestamp);
                } catch (Exception ex) {
                    return Map.of("success", false, "error", ex.getMessage());
                }
            }).thenAcceptAsync(result -> {
                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    if (loadingIndicator != null) {
                        loadingIndicator.setVisible(false);
                        statusLabel.setText("Ready");
                    }

                    Boolean success = (Boolean) result.get("success");
                    if (success) {
                        String timestamp = (String) result.get("timestamp");
                        showSuccessAlert("Database backup created successfully!\n\nBackup files created with timestamp: " + timestamp);
                    } else {
                        String error = (String) result.get("error");
                        if (error != null) {
                            showErrorAlert("Backup failed: " + error);
                        } else {
                            showErrorAlert("Some backup operations failed. Check the console for details.");
                        }
                    }
                });
            });
        });

        backupSection.getChildren().addAll(backupTitle, backupButton);

        // Statistics Section
        VBox statsSection = new VBox(10);
        Label statsTitle = new Label("Library Statistics");
        statsTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");

        Button statisticsButton = createStyledButton("📊 View Statistics", BUTTON_STYLE_PRIMARY);
        statisticsButton.setPrefWidth(200);
        statisticsButton.setOnAction(this::showBasicStatisticsStage);

        statsSection.getChildren().addAll(statsTitle, statisticsButton);

        // Export Section
        VBox exportSection = new VBox(10);
        Label exportTitle = new Label("Data Export");
        exportTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");

        Button exportButton = createStyledButton("📤 Export Data", BUTTON_STYLE_PRIMARY);
        exportButton.setPrefWidth(200);
        exportButton.setOnAction(e -> {
            showInfoAlert("Data export features including CSV export,\nreport generation, and data analytics\nwill be available in future versions.");
        });

        exportSection.getChildren().addAll(exportTitle, exportButton);

        content.getChildren().addAll(
                backupSection,
                new Separator(),
                statsSection,
                new Separator(),
                exportSection
        );

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /**
     * Shows basic statistics information.
     */

    private void showBasicStatisticsStage(ActionEvent actionEvent) {
        try {
            int totalUsers = UserService.getUserCount();
            int totalBooks = booksList.size();

            String statsText = String.format(
                    "Library Statistics:\n\n" +
                            "📚 Total Books: %d\n" +
                            "👥 Total Users: %d\n" +
                            "📋 Active Issues: %d\n\n" +
                            "System Version: %s\n" +
                            "Generated: %s",
                    totalBooks,
                    totalUsers,
                    issueRecordsList.size(),
                    APP_VERSION,
                    LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            );

            // Close the invoking dialog
            Stage currentStage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
            currentStage.close();

            // Create new Stage for statistics
            Stage stage = new Stage();
            stage.setTitle("Library Statistics");
            stage.setResizable(false);

            // Create UI content
            VBox vbox = new VBox();
            vbox.setSpacing(15);
            vbox.setPadding(new Insets(20));

            Label headerLabel = new Label("Current Library Statistics");
            headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

            TextArea statsArea = new TextArea(statsText);
            statsArea.setEditable(false);
            statsArea.setWrapText(true);
            statsArea.setPrefWidth(400);
            statsArea.setPrefHeight(200);
            statsArea.setFocusTraversable(false);

            Button closeBtn = new Button("Close");
            closeBtn.setOnAction(e -> stage.close());
            HBox buttonBox = new HBox(closeBtn);
            buttonBox.setAlignment(Pos.CENTER_RIGHT);

            vbox.getChildren().addAll(headerLabel, statsArea, buttonBox);

            Scene scene = new Scene(vbox);
            stage.setScene(scene);

            vbox.applyCss();
            vbox.layout();

            stage.setResizable(true);
            stage.setOnShown(event -> statsArea.requestFocus());
            Platform.runLater(() -> stage.show());


        } catch (Exception e) {
            showErrorAlert("Failed to generate statistics: " + e.getMessage());
        }
    }

    /**
     * Refreshes the header information to show updated user details.
     */
    private void refreshHeaderInfo() {
        try {
            User updatedUser = UserService.getUserById(currentUser);

            // Find the welcome label in the header and update it
            BorderPane mainPane = (BorderPane) primaryStage.getScene().getRoot();
            HBox header = (HBox) mainPane.getTop();
            VBox userInfo = (VBox) header.getChildren().get(0);
            Label welcomeLabel = (Label) userInfo.getChildren().get(0);

            String displayName = updatedUser.getFullName();
            if (!displayName.equals(currentUser)) {
                welcomeLabel.setText("Welcome, " + displayName + " (" + currentUser + ")");
            } else {
                welcomeLabel.setText("Welcome, " + currentUser);
            }

        } catch (Exception e) {
            System.err.println("Failed to refresh header info: " + e.getMessage());
        }
    }

    // === FIXED SETTINGS MODULE END ===

    // Utility methods for UI components
    private Button createStyledButton(String text, String style) {
        Button button = new Button(text);
        button.setStyle(style);
        return button;
    }

    private void showErrorMessage(Label messageLabel, String message) {
        messageLabel.setText(message);
        messageLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
    }

    private void showSuccessMessage(Label messageLabel, String message) {
        messageLabel.setText(message);
        messageLabel.setStyle("-fx-text-fill: green; -fx-font-size: 12px;");
    }

    private void clearMessage(Label messageLabel) {
        messageLabel.setText("");
    }

    // FIXED ALERT METHODS - These now properly handle rendering issues
    /**
     * FIXED: Shows error alert with proper threading and sizing.
     */
    private void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("An Error Occurred");
            alert.setContentText(message);

            // Ensure proper sizing and visibility
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
            alert.getDialogPane().setPrefWidth(450);
            alert.setResizable(true);

            // Force refresh of the dialog pane
            alert.getDialogPane().autosize();

            alert.showAndWait();
        });
    }

    /**
     * FIXED: Shows success alert with proper threading and sizing.
     */
    private void showSuccessAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText("Operation Completed Successfully");
            alert.setContentText(message);

            // Ensure proper sizing and visibility
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
            alert.getDialogPane().setPrefWidth(450);
            alert.setResizable(true);

            // Force refresh of the dialog pane
            alert.getDialogPane().autosize();

            alert.showAndWait();
        });
    }

    /**
     * FIXED: Shows info alert with proper threading and sizing.
     */
    private void showInfoAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(null);
            alert.setContentText(message);

            // Ensure proper sizing and visibility
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
            alert.getDialogPane().setPrefWidth(450);
            alert.setResizable(true);

            // Force refresh of the dialog pane
            alert.getDialogPane().autosize();

            alert.showAndWait();
        });
    }

    private void handleLogout() {
        currentUser = null;
        showLoginScreen();
    }

    /**
     * Handles application shutdown with proper cleanup.
     */
    private void handleApplicationClose() {
        try {
            UserService.persistDatabase();
            System.out.println("Application closed gracefully by user: " + currentUser);
        } catch (Exception e) {
            System.err.println("Error during application shutdown: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
