package com.example.application.ui;

import com.example.application.ToastDisplay;
import com.example.entities.Book;
import com.example.exceptions.BooksException;
import com.example.services.AppConfigurationService;
import com.example.services.BookService;
import javafx.application.Platform;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/**
 * <h1>CatalogView</h1>
 *
 * <p>A high-fidelity, grid-based interface for browsing and managing the library's
 * book collection. This view provides intuitive filtering, real-time search, and
 * role-aware management tools (staff only).</p>
 *
 * <h3>Key Capabilities:</h3>
 * <ul>
 *   <li><b>Dynamic Grid Layout:</b> Automatically calculates and adapts card 
 *       dimensions for optimal screen utilization across desktop and mobile-style 
 *       dimensions.</li>
 *   <li><b>Intelligent Filtering:</b> Supports multi-dimensional filtering by 
 *       category and text search, powered by {@link BookService}.</li>
 *   <li><b>Interactive Book Cards:</b> Every book is represented as a visual card
 *       featuring deterministic category-based gradients, availability indicators,
 *       and smooth lift animations.</li>
 *   <li><b>Resource Management:</b> (Staff) provides direct CRUD interfaces via 
 *       {@link BookDialog} to maintain the catalog integrity.</li>
 * </ul>
 *
 * <h3>Aesthetic Features:</h3>
 * <ul>
 *   <li><b>Deterministic Gradients:</b> Uses consistent color palettes derived
 *       from category names to ensure visual harmony.</li>
 *   <li><b>Staggered Entrances:</b> New grid items glide into view with controlled
 *       delays to prevent visual jarring during filtering.</li>
 *   <li><b>Hover feedback:</b> Real-time scaling and shadow elevation on 
 *       individual cards to signal interactivity.</li>
 * </ul>
 *
 * @author Yogesh
 * @version 4.5
 */
public class CatalogView extends BorderPane {
    private static final String ALL_CATEGORIES = "All Categories";
    private static final String ADD_CATEGORY = "Add New Category...";

    private final ObservableList<Book> booksList;
    private final boolean isStaff;
    private final String currentUser;
    private final Runnable onRefresh;
    private final ToastDisplay toastDisplay;

    private TextField searchField;
    private ComboBox<String> categoryFilter;
    private FlowPane booksGrid;
    private Label resultCountLabel;
    private FilteredList<Book> filteredBooks;
    private double bookCardWidth = 280;

    /**
     * Constructs a new CatalogView.
     *
     * @param booksList The observable list of books to display (backed by BookService).
     * @param isStaff Whether the current user has administrative permissions.
     * @param currentUser The ID of the currently logged-in user.
     * @param onRefresh Callback to trigger a data refresh from the parent controller.
     * @param toastDisplay Reference to the global notification system.
     */
    public CatalogView(ObservableList<Book> booksList, boolean isStaff, String currentUser,
                       Runnable onRefresh, ToastDisplay toastDisplay) {
        this.booksList = booksList;
        this.isStaff = isStaff;
        this.currentUser = currentUser;
        this.onRefresh = onRefresh;
        this.toastDisplay = toastDisplay;

        initializeUI();
        setupDataBinding();
    }

    /** Sets up the primary visual structure, including scroll panes and responsive listeners. */
    private void initializeUI() {
        setStyle("-fx-background-color: " + pageBackground() + ";");
        setPadding(new Insets(0));

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: " + pageBackground() + ";");

        // Header section
        VBox header = createHeader();

        // Filter bar
        FlowPane filterBar = createFilterBar();

        // Results count indicator
        resultCountLabel = new Label("Showing all books");
        resultCountLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748B;");

        // Books grid container
        booksGrid = createBooksGrid();

        content.getChildren().addAll(header, filterBar, resultCountLabel, booksGrid);
        scrollPane.setContent(content);
        setCenter(scrollPane);

        // Listen for width changes to adjust card sizing dynamically
        widthProperty().addListener((obs, oldValue, newValue) -> updateResponsiveLayout());
        Platform.runLater(this::updateResponsiveLayout);
    }

    private VBox createHeader() {
        VBox header = new VBox(8);

        Label title = new Label("Book Catalog");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Browse, search, and manage your library collection");
        subtitle.getStyleClass().add("page-subtitle");

        header.getChildren().addAll(title, subtitle);
        return header;
    }

    private FlowPane createFilterBar() {
        FlowPane bar = new FlowPane(Orientation.HORIZONTAL, 12, 12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("filter-bar");
        bar.setPadding(new Insets(12, 16, 12, 16));

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search books by title, author, or ISBN...");
        searchField.getStyleClass().add("search-field");
        searchField.setMinWidth(240);
        searchField.setPrefWidth(320);
        searchField.textProperty().addListener((obs, old, newVal) -> applyFilters());

        // Category filter
        categoryFilter = new ComboBox<>();
        refreshCategoryFilter();
        categoryFilter.setValue(ALL_CATEGORIES);
        categoryFilter.setPrefWidth(220);
        categoryFilter.setMinWidth(200);
        categoryFilter.valueProperty().addListener((obs, old, newVal) -> {
            if (ADD_CATEGORY.equals(newVal)) {
                BookDialog.showCategoryDialog(getScene() != null ? getScene().getWindow() : null, "")
                        .ifPresentOrElse(this::rememberAndSelectCategory, () -> categoryFilter.setValue(ALL_CATEGORIES));
            } else {
                applyFilters();
            }
        });

        // Add book button (staff only)
        if (isStaff) {
            Button addBookBtn = AppTheme.createIconTextButton("Add Book", AppTheme.ICON_ADD, AppTheme.ButtonStyle.PRIMARY);
            addBookBtn.setOnAction(e -> showAddBookDialog());
            addBookBtn.setMinHeight(40);
            bar.getChildren().addAll(searchField, categoryFilter, addBookBtn);
        } else {
            bar.getChildren().addAll(searchField, categoryFilter);
        }

        return bar;
    }

    private FlowPane createBooksGrid() {
        FlowPane grid = new FlowPane(javafx.geometry.Orientation.HORIZONTAL);
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setAlignment(Pos.TOP_LEFT);
        grid.setPrefWrapLength(960);
        return grid;
    }

    private void setupDataBinding() {
        filteredBooks = new FilteredList<>(booksList, b -> true);
        updateBooksGrid();

        booksList.addListener((javafx.collections.ListChangeListener<Book>) c -> {
            Platform.runLater(() -> {
                refreshCategoryFilter();
                updateBooksGrid();
                updateResponsiveLayout();
            });
        });
    }

    /** Rebuild category dropdown from live book data + "Add category..." option. */
    /** Rebuilds the category dropdown from live book data plus the "Add new..." option. */
    private void refreshCategoryFilter() {
        String current = categoryFilter.getValue();
        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        List<String> bookCategories = new ArrayList<>(AppConfigurationService.getAvailableBookCategories(booksList));
        bookCategories.sort(String::compareToIgnoreCase);
        categories.addAll(bookCategories);
        categories.add(ADD_CATEGORY);
        categoryFilter.getItems().setAll(categories);
        categoryFilter.setValue(current != null && categories.contains(current) ? current : ALL_CATEGORIES);
    }

    /** Applies text-search and category predicates to the book list. */
    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase().trim();
        String category = categoryFilter.getValue();
        // Ignore the sentinel value
        if (category == null || ADD_CATEGORY.equals(category)) return;

        filteredBooks.setPredicate(book -> {
            boolean matchesSearch = searchText.isEmpty() ||
                    book.getTitle().toLowerCase().contains(searchText) ||
                    book.getAuthor().toLowerCase().contains(searchText) ||
                    book.getIsbn().toLowerCase().contains(searchText);

            boolean matchesCategory = ALL_CATEGORIES.equals(category) ||
                    book.getCategory().equalsIgnoreCase(category);

            return matchesSearch && matchesCategory;
        });

        updateBooksGrid();
        updateResultCount();
    }

    /** Updates the result count label based on the current filter state. */
    private void updateResultCount() {
        int count = filteredBooks.size();
        String searchText = searchField.getText().trim();

        if (searchText.isEmpty()) {
            resultCountLabel.setText("Showing " + count + " book" + (count != 1 ? "s" : ""));
        } else {
            resultCountLabel.setText("Found " + count + " result" + (count != 1 ? "s" : "") +
                    " for \"" + searchText + "\"");
        }
    }

    /** Completely rebuilds the grid children with cards for the current filtered list. */
    private void updateBooksGrid() {
        booksGrid.getChildren().clear();

        for (Book book : filteredBooks) {
            VBox card = createBookCard(book);
            booksGrid.getChildren().add(card);
        }

        if (filteredBooks.isEmpty()) {
            VBox emptyState = createEmptyState();
            emptyState.prefWidthProperty().bind(booksGrid.widthProperty());
            booksGrid.getChildren().add(emptyState);
        }

        if (!booksGrid.getChildren().isEmpty()) {
            // Apply entrance transitions to the first set of visible cards
            AppTheme.staggeredEntrance(booksGrid.getChildren().stream().limit(8).toList(), 20, 35);
        }
    }

    /**
     * Generates a rich, interactive VBox representing a single book.
     *
     * <p>The card design features a deterministic header gradient based on category, 
     * responsive hover transitions, and role-based action buttons.</p>
     *
     * @param book The book entity to visualize.
     * @return A fully styled VBox instance.
     */
    private VBox createBookCard(Book book) {
        VBox card = new VBox(0);
        card.getStyleClass().add("book-card");
        card.setPrefWidth(bookCardWidth);
        card.setMaxWidth(bookCardWidth);

        // Header with per-category gradient — deterministic, readable in both themes
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(24));
        header.setStyle("-fx-background-color: " + gradientForCategory(book.getCategory()) + "; " +
                "-fx-background-radius: 12 12 0 0;");

        StackPane iconLabel = new StackPane(AppTheme.createIcon(AppTheme.ICON_BOOK, 26));
        iconLabel.setPrefSize(72, 72);
        iconLabel.setMaxSize(72, 72);
        iconLabel.setStyle("-fx-background-color: rgba(255,255,255,0.16); -fx-background-radius: 36px;");

        Label categoryChip = new Label(book.getCategory());
        categoryChip.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-background-radius: 20px; " +
                "-fx-padding: 4 12; -fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: white;");

        header.getChildren().addAll(iconLabel, categoryChip);

        // Body — Details section
        VBox body = new VBox(8);
        body.setPadding(new Insets(20));
        body.setStyle("-fx-background-color: " + cardSurface() + ";");

        Label titleLabel = new Label(book.getTitle());
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 700; -fx-text-fill: " + textPrimary() + ";");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(bookCardWidth - 40);

        Label authorLabel = new Label("by " + book.getAuthor());
        authorLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + textMuted() + ";");

        Label isbnLabel = new Label("ISBN: " + book.getFormattedIsbn());
        isbnLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + textSoft() + ";");

        body.getChildren().addAll(titleLabel, authorLabel, isbnLabel);

        // Footer — Status and Quantity
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 20, 16, 20));
        footer.setStyle("-fx-background-color: " + cardSurface() + "; -fx-background-radius: 0 0 12 12; " +
                "-fx-border-color: " + dividerColor() + "; -fx-border-width: 1 0 0 0;");

        boolean isAvailable = book.getQuantity() > 0;
        Label availabilityLabel = new Label(isAvailable ? "Available" : "Out of Stock");
        availabilityLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: " +
                (isAvailable ? "#16A34A" : "#DC2626") + ";");

        Label quantityLabel = new Label(book.getQuantity() + " copies");
        quantityLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + textSoft() + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        footer.getChildren().addAll(availabilityLabel, spacer, quantityLabel);

        // Action block (Management for staff, Requests for users)
        if (isStaff) {
            HBox actions = new HBox(8);
            actions.setPadding(new Insets(0, 20, 16, 20));
            actions.setAlignment(Pos.CENTER_LEFT);
            actions.setStyle("-fx-background-color: " + cardSurface() + ";");

            Button editBtn = AppTheme.createIconButton(AppTheme.ICON_EDIT, "Edit", AppTheme.ButtonStyle.GHOST);
            editBtn.getStyleClass().add("action-edit");
            editBtn.setOnAction(e -> showEditBookDialog(book));

            Button deleteBtn = AppTheme.createIconButton(AppTheme.ICON_DELETE, "Delete Book", AppTheme.ButtonStyle.DANGER);
            deleteBtn.getStyleClass().add("action-delete");
            deleteBtn.setOnAction(e -> showDeleteBookConfirmation(book));

            actions.getChildren().addAll(editBtn, deleteBtn);
            card.getChildren().addAll(header, body, footer, actions);
        } else {
            HBox actions = new HBox();
            actions.setPadding(new Insets(0, 20, 16, 20));
            actions.setAlignment(Pos.CENTER);
            actions.setStyle("-fx-background-color: " + cardSurface() + ";");

            Button requestBtn = AppTheme.createButton("Request Book", AppTheme.ButtonStyle.PRIMARY);
            requestBtn.setMaxWidth(Double.MAX_VALUE);
            requestBtn.setDisable(!isAvailable);
            requestBtn.setOnAction(e -> requestBook(book));

            actions.getChildren().add(requestBtn);
            card.getChildren().addAll(header, body, footer, actions);
        }

        // Apply interactive lift effects
        String hoverShadow = AppTheme.darkMode
                ? "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.65), 32, 0.1, 0, 10);"
                : "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.28), 28, 0.06, 0, 10);";
        String hoverBorder = AppTheme.darkMode
                ? "-fx-border-color: #14B8A6; -fx-border-width: 1;"
                : "-fx-border-color: #CBD5E1; -fx-border-width: 1;";
        String restShadow = AppTheme.darkMode
                ? "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 4);"
                : "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.06), 10, 0, 0, 2);";
        String restBorder = AppTheme.darkMode
                ? "-fx-border-color: #334155; -fx-border-width: 1;"
                : "-fx-border-color: #E2E8F0; -fx-border-width: 1;";

        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: " + cardSurface() + "; -fx-background-radius: 12px; " +
                    hoverBorder + hoverShadow);
            AppTheme.animateScale(card, 1.016, 130);
        });
        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: " + cardSurface() + "; -fx-background-radius: 12px; " +
                    restBorder + restShadow);
            AppTheme.animateScale(card, 1.0, 130);
        });

        return card;
    }

    private VBox createEmptyState() {
        VBox empty = new VBox(16);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(60));
        empty.setPrefWidth(600);

        StackPane icon = new StackPane(AppTheme.createIcon(AppTheme.ICON_LIBRARY, 36));
        icon.setPrefSize(52, 52);
        icon.setMaxSize(52, 52);
        icon.setStyle("-fx-background-color: " + (AppTheme.darkMode ? "#0F172A" : "#E2E8F0") + "; -fx-background-radius: 16px;");

        Label title = new Label("No books found");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 600; -fx-text-fill: " + textPrimary() + ";");

        Label desc = new Label("Try adjusting your search or filters to find what you're looking for.");
        desc.setStyle("-fx-font-size: 14px; -fx-text-fill: " + textMuted() + ";");
        desc.setWrapText(true);
        desc.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        empty.getChildren().addAll(icon, title, desc);
        return empty;
    }

    private void showAddBookDialog() {
        BookDialog.showAddDialog(getScene().getWindow()).ifPresent(bookData -> {
            try {
                Book newBook = new Book(
                        bookData.isbn(),
                        bookData.title(),
                        bookData.author(),
                        bookData.category(),
                        bookData.quantity()
                );
                BookService.addBook(newBook);
                booksList.setAll(BookService.getAllBooks());
                rememberCategory(bookData.category(), false, null);

                if (toastDisplay != null) {
                    toastDisplay.showSuccess("Book added successfully!");
                }
            } catch (Exception e) {
                if (toastDisplay != null) {
                    toastDisplay.showError("Failed to add book: " + e.getMessage());
                }
            }
        });
    }

    private void showEditBookDialog(Book book) {
        BookDialog.showEditDialog(getScene().getWindow(), book).ifPresent(bookData -> {
            try {
                book.setTitle(bookData.title());
                book.setAuthor(bookData.author());
                book.setCategory(bookData.category());
                book.setQuantity(bookData.quantity());

                BookService.updateBook(book);
                rememberCategory(bookData.category(), false, null);

                if (onRefresh != null) {
                    onRefresh.run();
                }

                if (toastDisplay != null) {
                    toastDisplay.showSuccess("Book updated successfully!");
                }
            } catch (Exception e) {
                if (toastDisplay != null) {
                    toastDisplay.showError("Failed to update book: " + e.getMessage());
                }
            }
        });
    }

    private void showDeleteBookConfirmation(Book book) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Delete Book");
        alert.setHeaderText("Delete \"" + book.getTitle() + "\"?");
        alert.setContentText("This action cannot be undone. The book will be permanently removed from the catalog.");
        alert.initOwner(getScene().getWindow());
        ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(deleteType, ButtonType.CANCEL);
        AppTheme.applyTheme(alert.getDialogPane());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == deleteType) {
            try {
                BookService.deleteBook(book.getIsbn());

                if (onRefresh != null) {
                    onRefresh.run();
                }

                if (toastDisplay != null) {
                    toastDisplay.showSuccess("Book deleted successfully!");
                }
            } catch (BooksException e) {
                if (toastDisplay != null) {
                    toastDisplay.showError("Failed to delete book: " + e.getMessage());
                }
            }
        }
    }

    private void requestBook(Book book) {
        try {
            BookService.requestBookForUser(book.getIsbn(), currentUser, 1);

            if (toastDisplay != null) {
                toastDisplay.showSuccess("Book request submitted!");
            }
            if (onRefresh != null) {
                onRefresh.run();
            }
        } catch (Exception e) {
            if (toastDisplay != null) {
                toastDisplay.showError("Failed to request book: " + e.getMessage());
            }
        }
    }

    private String pageBackground() {
        return AppTheme.darkMode ? "#0F172A" : "#F1F5F9";
    }

    /** Deterministic gradient per category — same name → same colour across runs. */
    private static final String[] CATEGORY_GRADIENTS = {
            "linear-gradient(from 0% 0% to 100% 100%, #0F766E, #14B8A6)", // teal
            "linear-gradient(from 0% 0% to 100% 100%, #1D4ED8, #60A5FA)", // blue
            "linear-gradient(from 0% 0% to 100% 100%, #7C3AED, #A78BFA)", // violet
            "linear-gradient(from 0% 0% to 100% 100%, #B45309, #F59E0B)", // amber
            "linear-gradient(from 0% 0% to 100% 100%, #0E7490, #38BDF8)", // cyan
            "linear-gradient(from 0% 0% to 100% 100%, #065F46, #34D399)", // emerald
            "linear-gradient(from 0% 0% to 100% 100%, #9D174D, #F472B6)", // pink
            "linear-gradient(from 0% 0% to 100% 100%, #1E3A5F, #3B82F6)", // navy
            "linear-gradient(from 0% 0% to 100% 100%, #7F1D1D, #F87171)", // red
            "linear-gradient(from 0% 0% to 100% 100%, #4C1D95, #8B5CF6)", // purple
            "linear-gradient(from 0% 0% to 100% 100%, #134E4A, #2DD4BF)", // dark-teal
            "linear-gradient(from 0% 0% to 100% 100%, #3B1F00, #FB923C)", // orange
            "linear-gradient(from 0% 0% to 100% 100%, #0F766E, #5EEAD4)", // aqua
            "linear-gradient(from 0% 0% to 100% 100%, #1E40AF, #93C5FD)", // sky
            "linear-gradient(from 0% 0% to 100% 100%, #831843, #FDA4AF)", // rose
            "linear-gradient(from 0% 0% to 100% 100%, #365314, #BEF264)", // lime
            "linear-gradient(from 0% 0% to 100% 100%, #5B21B6, #C4B5FD)", // lavender
            "linear-gradient(from 0% 0% to 100% 100%, #9A3412, #FDBA74)", // tangerine
    };

    private static String gradientForCategory(String category) {
        if (category == null || category.isBlank()) {
            return CATEGORY_GRADIENTS[0];
        }
        // BUG FIX: Math.abs(Integer.MIN_VALUE) overflows and returns Integer.MIN_VALUE
        // (still negative), so "% CATEGORY_GRADIENTS.length" yields a negative index
        // and throws ArrayIndexOutOfBoundsException for certain category names.
        // Math.floorMod always produces a non-negative result, making the index safe.
        int index = Math.floorMod(category.trim().toLowerCase().hashCode(), CATEGORY_GRADIENTS.length);
        return CATEGORY_GRADIENTS[index];
    }

    private String cardSurface() {        return AppTheme.darkMode ? "#1E293B" : "white";
    }

    private String dividerColor() {
        return AppTheme.darkMode ? "#334155" : "#F1F5F9";
    }

    private String textPrimary() {
        return AppTheme.darkMode ? "#F8FAFC" : "#1E293B";
    }

    private String textMuted() {
        return AppTheme.darkMode ? "#CBD5E1" : "#64748B";
    }

    private String textSoft() {
        return AppTheme.darkMode ? "#94A3B8" : "#94A3B8";
    }

    private void rememberAndSelectCategory(String category) {
        rememberCategory(category, true, "Category added.");
    }

    private void rememberCategory(String category, boolean selectFilter, String successMessage) {
        if (category == null || category.isBlank()) {
            return;
        }
        try {
            AppConfigurationService.rememberBookCategory(category);
            refreshCategoryFilter();
            if (selectFilter) {
                categoryFilter.setValue(category.trim());
                applyFilters();
            }
            if (successMessage != null && toastDisplay != null) {
                toastDisplay.showSuccess(successMessage);
            }
        } catch (Exception e) {
            if (toastDisplay != null) {
                toastDisplay.showError("Failed to save category: " + e.getMessage());
            }
        }
    }

    private void updateResponsiveLayout() {
        double availableWidth = Math.max(320, getWidth() - 112);
        int columns;
        if (availableWidth >= 1240) {
            columns = 4;
        } else if (availableWidth >= 920) {
            columns = 3;
        } else if (availableWidth >= 620) {
            columns = 2;
        } else {
            columns = 1;
        }

        double computedWidth = (availableWidth - ((columns - 1) * 16)) / columns;
        double targetWidth = Math.max(240, Math.min(320, computedWidth));
        booksGrid.setPrefWrapLength(Math.max(targetWidth, availableWidth));

        if (Math.abs(bookCardWidth - targetWidth) > 1) {
            bookCardWidth = targetWidth;
            if (filteredBooks != null) {
                updateBooksGrid();
            }
        }
    }

    public void refresh() {
        refreshCategoryFilter();
        applyFilters();
        updateBooksGrid();
        updateResponsiveLayout();
    }
}

/**
 * Dialog for adding/editing books.
 */
class BookDialog {

    public static Optional<BookData> showAddDialog(javafx.stage.Window owner) {
        return showDialog(owner, "Add New Book", null);
    }

    public static Optional<BookData> showEditDialog(javafx.stage.Window owner, Book book) {
        return showDialog(owner, "Edit Book", book);
    }

    private static Optional<BookData> showDialog(javafx.stage.Window owner, String title, Book existingBook) {
        Dialog<BookData> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.initOwner(owner);

        // Dialog pane styling – respect current dark / light theme
        DialogPane dialogPane = dialog.getDialogPane();
        AppTheme.applyTheme(dialogPane);   // adds stylesheet + dark-mode class when needed

        // Form fields
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField isbnField = new TextField();
        isbnField.setPromptText("Enter ISBN");
        isbnField.setDisable(existingBook != null);

        TextField titleField = new TextField();
        titleField.setPromptText("Enter book title");

        TextField authorField = new TextField();
        authorField.setPromptText("Enter author name");

        ComboBox<String> categoryField = new ComboBox<>();
        categoryField.setEditable(true);
        categoryField.getItems().addAll(AppConfigurationService.getAvailableBookCategories(BookService.getAllBooks()));
        categoryField.setPromptText("Select or type a category");
        categoryField.setMaxWidth(Double.MAX_VALUE);

        Button addCategoryButton = AppTheme.createIconButton(AppTheme.ICON_ADD, "Add category", AppTheme.ButtonStyle.GHOST);
        addCategoryButton.setOnAction(e -> showCategoryDialog(owner, categoryField.getEditor().getText()).ifPresent(category -> {
            try {
                AppConfigurationService.rememberBookCategory(category);
                categoryField.getItems().setAll(AppConfigurationService.getAvailableBookCategories(BookService.getAllBooks()));
                if (!categoryField.getItems().contains(category)) {
                    categoryField.getItems().add(category);
                }
                categoryField.setValue(category);
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to save category: " + ex.getMessage(), ButtonType.OK);
                AppTheme.applyTheme(alert.getDialogPane());
                alert.showAndWait();
            }
        }));
        HBox categoryRow = new HBox(8, categoryField, addCategoryButton);
        HBox.setHgrow(categoryField, Priority.ALWAYS);
        categoryRow.setAlignment(Pos.CENTER_LEFT);
        addCategoryButton.setMinWidth(36);
        addCategoryButton.setPrefWidth(36);
        addCategoryButton.setMinHeight(36);
        addCategoryButton.setPrefHeight(36);
        addCategoryButton.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "#334155" : "#E2E8F0") +
                ";-fx-background-radius:8px;-fx-cursor:hand;-fx-padding:0;-fx-border-width:0;");

        Spinner<Integer> quantityField = new Spinner<>(1, 999_999, 1);
        quantityField.setEditable(true);
        quantityField.getStyleClass().add("themed-spinner");
        quantityField.setMaxWidth(Double.MAX_VALUE);
        AppTheme.fixSpinner(quantityField);

        // Pre-fill if editing
        if (existingBook != null) {
            isbnField.setText(existingBook.getIsbn());
            titleField.setText(existingBook.getTitle());
            authorField.setText(existingBook.getAuthor());
            categoryField.setValue(existingBook.getCategory());
            quantityField.getValueFactory().setValue(existingBook.getQuantity());
        }

        grid.addRow(0, new Label("ISBN:"), isbnField);
        grid.addRow(1, new Label("Title:"), titleField);
        grid.addRow(2, new Label("Author:"), authorField);
        grid.addRow(3, new Label("Category:"), categoryRow);
        grid.addRow(4, new Label("Quantity:"), quantityField);

        Label errorLabel = new Label();
        errorLabel.setVisible(false);
        errorLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#DC2626;");
        grid.add(errorLabel, 0, 5, 2, 1);

        dialogPane.setContent(grid);

        // Buttons
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL, saveButtonType);
        Button saveButton = (Button) dialogPane.lookupButton(saveButtonType);
        Button cancelButton = (Button) dialogPane.lookupButton(ButtonType.CANCEL);
        // FIX: Style both buttons so cancel doesn't look plain/unstyled
        saveButton.getStyleClass().add("btn-primary");
        if (cancelButton != null) {
            cancelButton.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "#334155" : "#E5E7EB") +
                    "; -fx-text-fill:" + (AppTheme.darkMode ? "#F8FAFC" : "#1F2937") +
                    "; -fx-font-weight:600; -fx-background-radius:8px; -fx-padding:8 18;");
        }
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                resolveBookData(isbnField, titleField, authorField, categoryField, quantityField);
                errorLabel.setVisible(false);
            } catch (Exception ex) {
                errorLabel.setText(ex.getMessage());
                errorLabel.setVisible(true);
                event.consume();
            }
        });

        // Result converter
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return resolveBookData(isbnField, titleField, authorField, categoryField, quantityField);
            }
            return null;
        });

        return dialog.showAndWait();
    }

    static Optional<String> showCategoryDialog(javafx.stage.Window owner, String initialValue) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("New Category");
        if (owner != null) {
            dialog.initOwner(owner);
        }

        DialogPane dialogPane = dialog.getDialogPane();
        AppTheme.applyTheme(dialogPane);
        dialogPane.setPrefWidth(420);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Label heading = new Label("Add a Category");
        heading.setStyle("-fx-font-size:18px; -fx-font-weight:700; -fx-text-fill:" +
                (AppTheme.darkMode ? "#F8FAFC" : "#0F172A") + ";");

        Label copy = new Label("Create a reusable category for catalog filters and new books.");
        copy.setStyle("-fx-font-size:13px; -fx-text-fill:" + (AppTheme.darkMode ? "#94A3B8" : "#64748B") + ";");
        copy.setWrapText(true);

        TextField categoryField = new TextField(initialValue == null ? "" : initialValue.trim());
        categoryField.setPromptText("e.g. Children");

        Label errorLabel = new Label();
        errorLabel.setVisible(false);
        errorLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#DC2626;");

        content.getChildren().addAll(heading, copy, categoryField, errorLabel);
        dialogPane.setContent(content);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL, saveType);

        Button saveButton = (Button) dialogPane.lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (categoryField.getText() == null || categoryField.getText().isBlank()) {
                errorLabel.setText("Category name is required.");
                errorLabel.setVisible(true);
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == saveType ? categoryField.getText().trim() : null);
        return dialog.showAndWait();
    }

    private static BookData resolveBookData(TextField isbnField, TextField titleField, TextField authorField,
                                            ComboBox<String> categoryField, Spinner<Integer> quantityField) {
        String categoryValue = categoryField.getEditor().getText() != null
                && !categoryField.getEditor().getText().isBlank()
                ? categoryField.getEditor().getText().trim()
                : categoryField.getValue();
        Book preview = new Book(
                isbnField.getText().trim(),
                titleField.getText().trim(),
                authorField.getText().trim(),
                categoryValue,
                quantityField.getValue()
        );
        return new BookData(
                preview.getIsbn(),
                preview.getTitle(),
                preview.getAuthor(),
                preview.getCategory(),
                quantityField.getValue()
        );
    }

    public record BookData(String isbn, String title, String author, String category, int quantity) {}
}