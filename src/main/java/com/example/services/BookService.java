package com.example.services;

import com.example.entities.Book;
import com.example.entities.BooksDB;
import com.example.entities.BooksDB.IssueRecord;
import com.example.entities.User;
import com.example.entities.UsersDB;
import com.example.exceptions.BooksException;
import com.example.exceptions.UserException;
import com.example.exceptions.ValidationException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Comprehensive service layer for book management operations with enhanced validation,
 * error handling, and business logic.
 */
public final class BookService {

    private static final Logger LOGGER = Logger.getLogger(BookService.class.getName());

    private static final BooksDB booksDB = BooksDB.getInstance();
    private static final UsersDB usersDB = UsersDB.getInstance();

    // Business rule constants
    private static final int MIN_TITLE_LENGTH = 1;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MIN_AUTHOR_LENGTH = 1;
    private static final int MAX_AUTHOR_LENGTH = 100;
    private static final int MIN_CATEGORY_LENGTH = 1;
    private static final int MAX_CATEGORY_LENGTH = 50;
    private static final int MIN_ISBN_LENGTH = 10;
    private static final int MAX_ISBN_LENGTH = 17;

    // Private constructor to prevent instantiation
    private BookService() {
        throw new UnsupportedOperationException("Service class cannot be instantiated");
    }

    // --- Book Management Operations ---

    /**
     * Adds a new book to the library with validation.
     *
     * @param book the book to add
     * @throws BooksException if validation fails or book addition fails
     */
    public static void addBook(Book book) throws BooksException {
        if (book == null) {
            throw new BooksException("Book cannot be null");
        }

        validateBookData(book);

        try {
            booksDB.addBook(book);
            LOGGER.log(Level.INFO, "Book added successfully: {0}", book.getTitle());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to add book: " + book.getTitle(), e);
            if (e instanceof BooksException) {
                throw e;
            }
            throw new BooksException("Failed to add book: " + e.getMessage(), e);
        }
    }

    /**
     * Creates and adds a new book with the provided parameters.
     *
     * @param isbn the ISBN
     * @param title the title
     * @param author the author
     * @param category the category
     * @param quantity the initial quantity
     * @throws BooksException if validation fails or book creation fails
     */
    public static void addBook(String isbn, String title, String author, String category, int quantity)
            throws BooksException {

        validateBookParameters(isbn, title, author, category, quantity);

        Book book = new Book(isbn.trim(), title.trim(), author.trim(), category.trim(), quantity);
        addBook(book);
    }

    /**
     * Updates an existing book's information.
     *
     * @param book the book with updated information
     * @throws BooksException if book doesn't exist or update fails
     */
    public static void updateBook(Book book) throws BooksException {
        if (book == null) {
            throw new BooksException("Book cannot be null");
        }

        validateBookData(book);

        try {
            booksDB.modifyBook(book);
            LOGGER.log(Level.INFO, "Book updated successfully: {0}", book.getTitle());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update book: " + book.getTitle(), e);
            if (e instanceof BooksException) {
                throw e;
            }
            throw new BooksException("Failed to update book: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a book by ISBN.
     *
     * @param isbn the ISBN of the book to delete
     * @throws BooksException if book is currently issued or deletion fails
     */
    public static void deleteBook(String isbn) throws BooksException {
        validateIsbn(isbn);

        try {
            booksDB.removeBook(isbn.trim());
            LOGGER.log(Level.INFO, "Book deleted successfully: {0}", isbn);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete book: " + isbn, e);
            if (e instanceof BooksException) {
                throw e;
            }
            throw new BooksException("Failed to delete book: " + e.getMessage(), e);
        }
    }

    /**
     * Searches for books by query string matching title, author, or category.
     *
     * @param query the search query
     * @return list of matching books
     */
    public static List<Book> searchBooks(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return getAllBooks();
            }

            List<Book> results = booksDB.searchBooks(query.trim());
            LOGGER.log(Level.FINE, "Search query '{0}' returned {1} results",
                    new Object[]{query, results.size()});
            return results;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Search operation failed for query: " + query, e);
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves a book by its ISBN.
     *
     * @param isbn the ISBN to search for
     * @return the book or null if not found
     * @throws BooksException if ISBN is invalid
     */
    public static Book getBookByIsbn(String isbn) throws BooksException {
        validateIsbn(isbn);

        try {
            return booksDB.getBook(isbn.trim());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve book: " + isbn, e);
            throw new BooksException("Failed to retrieve book: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all books in the library.
     *
     * @return immutable list of all books
     */
    public static List<Book> getAllBooks() {
        try {
            return List.copyOf(booksDB.getBooks());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve all books", e);
            return Collections.emptyList();
        }
    }

    // --- Issue and Return Operations ---

    /**
     * Issues books to a user with specified quantity and comprehensive validation.
     *
     * @param isbn the book ISBN
     * @param userId the user ID
     * @param quantity the number of copies to issue
     * @throws BooksException if book is not available or validation fails
     * @throws UserException if user is not found or validation fails
     */
    public static void issueBookToUser(String isbn, String userId, int quantity)
            throws BooksException, UserException {

        validateIssueParameters(isbn, userId, quantity);

        Book book = getBookByIsbn(isbn);
        if (book == null) {
            throw new BooksException("Book not found: " + isbn);
        }

        User user = usersDB.getUser(userId.trim());
        if (user == null) {
            throw new UserException("User not found: " + userId);
        }

        if (book.getQuantity() < quantity) {
            throw new BooksException("Insufficient copies available. Available: " + book.getQuantity());
        }

        if (!canUserBorrowMoreBooks(userId, quantity)) {
            int currentBorrowed = getUserTotalBorrowedBooks(userId);
            int maxAllowed = getMaxBorrowLimit();
            throw new BooksException(String.format("Borrowing limit exceeded. Current: %d, Requested: %d, Max: %d",
                    currentBorrowed, quantity, maxAllowed));
        }

        try {
            booksDB.issueBook(isbn.trim(), user, quantity);
            LOGGER.log(Level.INFO, "Issued {0} copies of book {1} to user {2}",
                    new Object[]{quantity, isbn, userId});
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to issue book", e);
            if (e instanceof BooksException) {
                throw e;
            }
            throw new BooksException("Failed to issue book: " + e.getMessage(), e);
        }
    }

    /**
     * Issues one copy of a book to a user (convenience method).
     *
     * @param isbn the book ISBN
     * @param userId the user ID
     * @throws BooksException if book is not available or validation fails
     * @throws UserException if user is not found
     */
    public static void issueBookToUser(String isbn, String userId) throws BooksException, UserException {
        issueBookToUser(isbn, userId, 1);
    }

    /**
     * Returns specified quantity of a book from a user.
     *
     * @param isbn the book ISBN
     * @param userId the user ID
     * @param quantity the number of copies to return
     * @throws BooksException if validation fails or return operation fails
     * @throws UserException if user is not found
     */
    public static void returnBookFromUser(String isbn, String userId, int quantity)
            throws BooksException, UserException {

        validateReturnParameters(isbn, userId, quantity);

        User user = usersDB.getUser(userId.trim());
        if (user == null) {
            throw new UserException("User not found: " + userId);
        }

        try {
            booksDB.returnBook(isbn.trim(), user, quantity);
            LOGGER.log(Level.INFO, "Returned {0} copies of book {1} from user {2}",
                    new Object[]{quantity, isbn, userId});
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to return book", e);
            if (e instanceof BooksException) {
                throw e;
            }
            throw new BooksException("Failed to return book: " + e.getMessage(), e);
        }
    }

    /**
     * Returns all copies of a book from a user.
     *
     * @param isbn the book ISBN
     * @param userId the user ID
     * @throws BooksException if validation fails or return operation fails
     * @throws UserException if user is not found
     */
    public static void returnAllCopiesFromUser(String isbn, String userId)
            throws BooksException, UserException {

        validateIsbn(isbn);
        validateUserId(userId);

        Map<String, Integer> borrowerDetails = getBorrowerDetailsForBook(isbn);
        Integer borrowedCount = borrowerDetails.get(userId.trim());

        if (borrowedCount == null || borrowedCount <= 0) {
            throw new BooksException("User has not borrowed any copies of this book");
        }

        returnBookFromUser(isbn, userId, borrowedCount);
    }

    // --- Query and Statistics Methods ---

    /**
     * Retrieves all active (unreturned) issue records.
     *
     * @return list of active issue records
     */
    public static List<IssueRecord> getAllActiveIssueRecords() {
        try {
            return booksDB.getAllActiveIssueRecords();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to retrieve active issue records", e);
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves active issue records for a specific user.
     *
     * @param userId the user ID
     * @return list of user's active issue records
     */
    public static List<IssueRecord> getUserActiveIssueRecords(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return booksDB.getUserActiveIssueRecords(userId.trim());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to retrieve user issue records: " + userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Calculates total fine amount for a user.
     *
     * @param userId the user ID
     * @return total fine amount
     */
    public static double getUserTotalFine(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0.0;
        }

        try {
            return booksDB.calculateUserFine(userId.trim());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to calculate user fine: " + userId, e);
            return 0.0;
        }
    }

    /**
     * Retrieves overdue issue records for a user.
     *
     * @param userId the user ID
     * @return list of overdue issue records
     */
    public static List<IssueRecord> getUserOverdueBooks(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return booksDB.getUserOverdueIssueRecords(userId.trim());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to retrieve overdue books for user: " + userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves all overdue issue records across all users.
     *
     * @return list of all overdue issue records
     */
    public static List<IssueRecord> getAllOverdueBooks() {
        try {
            return booksDB.getAllOverdueIssueRecords();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to retrieve all overdue books", e);
            return Collections.emptyList();
        }
    }

    /**
     * Gets total number of books currently borrowed by a user.
     *
     * @param userId the user ID
     * @return number of borrowed books
     */
    public static int getUserTotalBorrowedBooks(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }

        try {
            return booksDB.getUserBorrowedCount(userId.trim());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get borrowed count for user: " + userId, e);
            return 0;
        }
    }

    /**
     * Checks if a user can borrow more books without exceeding the maximum limit.
     *
     * @param userId the user ID
     * @param requestedQuantity the number of books to borrow
     * @return true if user can borrow the requested quantity
     */
    public static boolean canUserBorrowMoreBooks(String userId, int requestedQuantity) {
        if (userId == null || userId.trim().isEmpty() || requestedQuantity <= 0) {
            return false;
        }

        try {
            int currentlyBorrowed = getUserTotalBorrowedBooks(userId);
            int maxAllowed = getMaxBorrowLimit();
            return (currentlyBorrowed + requestedQuantity) <= maxAllowed;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking borrow eligibility for user: " + userId, e);
            return false;
        }
    }

    // --- Borrower and Availability Information ---

    /**
     * Gets borrower details for a specific book.
     *
     * @param isbn the book ISBN
     * @return map of user IDs to quantities borrowed
     */
    public static Map<String, Integer> getBorrowerDetailsForBook(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            return booksDB.getBorrowerDetailsForBook(isbn.trim());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get borrower details for book: " + isbn, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Gets all borrower details across all books.
     *
     * @return nested map of ISBN to (user ID to quantity)
     */
    public static Map<String, Map<String, Integer>> getAllBorrowerDetails() {
        try {
            return booksDB.getAllBorrowerDetails();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get all borrower details", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Gets total number of copies currently issued for a book.
     *
     * @param isbn the book ISBN
     * @return total issued quantity
     */
    public static int getTotalIssuedQuantityForBook(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return 0;
        }

        try {
            return booksDB.getTotalIssued(isbn.trim());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get issued quantity for book: " + isbn, e);
            return 0;
        }
    }

    /**
     * Gets current available quantity for a book.
     *
     * @param isbn the book ISBN
     * @return available quantity
     */
    public static int getAvailableQuantityForBook(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return 0;
        }

        try {
            return booksDB.getAvailableQuantity(isbn.trim());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get available quantity for book: " + isbn, e);
            return 0;
        }
    }

    /**
     * Gets original total quantity (available + issued) for a book.
     *
     * @param isbn the book ISBN
     * @return original total quantity
     */
    public static int getOriginalQuantityForBook(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return 0;
        }

        try {
            return booksDB.getOriginalQuantity(isbn.trim());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get original quantity for book: " + isbn, e);
            return 0;
        }
    }

    /**
     * Checks if a book is available for issuing.
     *
     * @param isbn the book ISBN
     * @return true if book is available
     */
    public static boolean isBookAvailableForIssue(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return false;
        }

        try {
            Book book = booksDB.getBook(isbn.trim());
            return book != null && book.getQuantity() > 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check availability for book: " + isbn, e);
            return false;
        }
    }

    // --- Enhanced Information and Statistics ---

    /**
     * Gets detailed information about a book including availability status.
     *
     * @param isbn the book ISBN
     * @return formatted string with book details and availability
     */
    public static String getBookDetailsWithAvailability(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return "Invalid ISBN provided";
        }

        try {
            Book book = booksDB.getBook(isbn.trim());
            if (book == null) {
                return "Book not found";
            }

            int available = getAvailableQuantityForBook(isbn);
            int issued = getTotalIssuedQuantityForBook(isbn);
            int total = getOriginalQuantityForBook(isbn);

            StringBuilder details = new StringBuilder();
            details.append("📘 ").append(book.getTitle()).append("\n");
            details.append("Author: ").append(book.getAuthor()).append("\n");
            details.append("Category: ").append(book.getCategory()).append("\n");
            details.append("ISBN: ").append(book.getIsbn()).append("\n");
            details.append("Available: ").append(available).append("/").append(total);
            details.append(" (Issued: ").append(issued).append(")");

            if (book.getIssuedTo() != null && !book.getIssuedTo().trim().isEmpty()) {
                details.append("\nIssued to: ").append(book.getIssuedTo());
            }

            return details.toString();

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get book details for: " + isbn, e);
            return "Error retrieving book details";
        }
    }

    /**
     * Gets comprehensive library statistics.
     *
     * @return map containing various library statistics
     */
    public static Map<String, Object> getLibraryStatistics() {
        try {
            List<Book> books = booksDB.getBooks();

            int totalUniqueBooks = books.size();
            int totalCopies = books.stream()
                    .mapToInt(book -> getOriginalQuantityForBook(book.getIsbn()))
                    .sum();

            int availableCopies = books.stream()
                    .mapToInt(Book::getQuantity)
                    .sum();

            int issuedCopies = totalCopies - availableCopies;

            List<IssueRecord> overdueRecords = getAllOverdueBooks();
            int overdueBooks = overdueRecords.size();

            double totalFines = overdueRecords.stream()
                    .mapToDouble(IssueRecord::calculateFine)
                    .sum();

            double utilizationRate = totalCopies == 0 ? 0.0 : (issuedCopies * 100.0 / totalCopies);

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalBooks", totalUniqueBooks);
            statistics.put("totalCopies", totalCopies);
            statistics.put("availableCopies", availableCopies);
            statistics.put("issuedCopies", issuedCopies);
            statistics.put("overdueBooks", overdueBooks);
            statistics.put("totalFines", totalFines);
            statistics.put("utilizationRate", utilizationRate);
            statistics.put("generatedAt", LocalDate.now());

            return Collections.unmodifiableMap(statistics);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate library statistics", e);
            return Collections.emptyMap();
        }
    }

    // --- Configuration Methods ---

    /**
     * Gets the maximum number of books a user can borrow.
     *
     * @return maximum borrow limit
     */
    public static int getMaxBorrowLimit() {
        try {
            return booksDB.getMaxBorrow();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get max borrow limit", e);
            return 5; // Default fallback
        }
    }

    /**
     * Gets the default loan period in days.
     *
     * @return loan period in days
     */
    public static int getLoanPeriodDays() {
        try {
            return booksDB.getLoanDays();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get loan period", e);
            return 14; // Default fallback
        }
    }

    /**
     * Gets the fine amount per day for overdue books.
     *
     * @return fine per day
     */
    public static double getFinePerDay() {
        try {
            return booksDB.getFinePerDay();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get fine per day", e);
            return 2.0; // Default fallback
        }
    }

    // --- Persistence Operations ---

    /**
     * Forces persistence of the books database.
     *
     * @throws IOException if persistence fails
     */
    public static void persistBooksDatabase() throws IOException {
        try {
            // The BooksDB automatically persists on changes, but this ensures consistency
            booksDB.forcePersist();
            LOGGER.log(Level.INFO, "Books database persisted successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to persist books database", e);
            throw new IOException("Failed to persist books database: " + e.getMessage(), e);
        }
    }

    // --- Private Validation Methods ---

    private static void validateBookData(Book book) throws BooksException {
        if (book.getIsbn() == null || book.getIsbn().trim().isEmpty()) {
            throw new BooksException("ISBN cannot be empty");
        }

        if (book.getTitle() == null || book.getTitle().trim().isEmpty()) {
            throw new BooksException("Title cannot be empty");
        }

        if (book.getAuthor() == null || book.getAuthor().trim().isEmpty()) {
            throw new BooksException("Author cannot be empty");
        }

        if (book.getCategory() == null || book.getCategory().trim().isEmpty()) {
            throw new BooksException("Category cannot be empty");
        }

        if (book.getQuantity() < 0) {
            throw new BooksException("Quantity cannot be negative");
        }

        validateBookParameters(book.getIsbn(), book.getTitle(), book.getAuthor(),
                book.getCategory(), book.getQuantity());
    }

    private static void validateBookParameters(String isbn, String title, String author,
                                               String category, int quantity) throws BooksException {

        validateIsbn(isbn);

        if (title == null || title.trim().length() < MIN_TITLE_LENGTH ||
                title.trim().length() > MAX_TITLE_LENGTH) {
            throw new BooksException("Title must be between " + MIN_TITLE_LENGTH +
                    " and " + MAX_TITLE_LENGTH + " characters");
        }

        if (author == null || author.trim().length() < MIN_AUTHOR_LENGTH ||
                author.trim().length() > MAX_AUTHOR_LENGTH) {
            throw new BooksException("Author must be between " + MIN_AUTHOR_LENGTH +
                    " and " + MAX_AUTHOR_LENGTH + " characters");
        }

        if (category == null || category.trim().length() < MIN_CATEGORY_LENGTH ||
                category.trim().length() > MAX_CATEGORY_LENGTH) {
            throw new BooksException("Category must be between " + MIN_CATEGORY_LENGTH +
                    " and " + MAX_CATEGORY_LENGTH + " characters");
        }

        if (quantity < 0) {
            throw new BooksException("Quantity cannot be negative");
        }
    }

    private static void validateIsbn(String isbn) throws BooksException {
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new BooksException("ISBN cannot be empty");
        }

        String cleanIsbn = isbn.trim().replaceAll("[^0-9X]", "");
        if (cleanIsbn.length() < MIN_ISBN_LENGTH || cleanIsbn.length() > MAX_ISBN_LENGTH) {
            throw new BooksException("ISBN must be between " + MIN_ISBN_LENGTH +
                    " and " + MAX_ISBN_LENGTH + " characters");
        }
    }

    private static void validateUserId(String userId) throws ValidationException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("User ID cannot be empty");
        }
    }

    private static void validateIssueParameters(String isbn, String userId, int quantity)
            throws BooksException, ValidationException {
        validateIsbn(isbn);
        validateUserId(userId);

        if (quantity <= 0) {
            throw new BooksException("Quantity must be positive");
        }

        if (quantity > getMaxBorrowLimit()) {
            throw new BooksException("Cannot issue more than " + getMaxBorrowLimit() +
                    " books at once");
        }
    }

    private static void validateReturnParameters(String isbn, String userId, int quantity)
            throws BooksException, ValidationException {
        validateIsbn(isbn);
        validateUserId(userId);

        if (quantity <= 0) {
            throw new BooksException("Quantity must be positive");
        }
    }

    // --- Legacy Method Names for Backward Compatibility ---

    /**
     * @deprecated Use {@link #getAllBooks()} instead
     */
    @Deprecated
    public static List<Book> getAllBooksList() {
        return getAllBooks();
    }

    /**
     * @deprecated Use {@link #getBookByIsbn(String)} instead
     */
    @Deprecated
    public static Book getBookByISBN(String isbn) {
        try {
            return getBookByIsbn(isbn);
        } catch (BooksException e) {
            LOGGER.log(Level.WARNING, "Legacy method call failed", e);
            return null;
        }
    }

    /**
     * @deprecated Use {@link #getUserTotalFine(String)} instead
     */
    @Deprecated
    public static double getUserFine(String userId) {
        return getUserTotalFine(userId);
    }

    /**
     * @deprecated Use {@link #getUserTotalBorrowedBooks(String)} instead
     */
    @Deprecated
    public static int getUserBorrowed(String userId) {
        return getUserTotalBorrowedBooks(userId);
    }

    /**
     * @deprecated Use {@link #getAllOverdueBooks()} instead
     */
    @Deprecated
    public static List<IssueRecord> getOverdueBooks() {
        return getAllOverdueBooks();
    }

    /**
     * @deprecated Use {@link #isBookAvailableForIssue(String)} instead
     */
    @Deprecated
    public static boolean isBookAvailable(String isbn) {
        return isBookAvailableForIssue(isbn);
    }

    /**
     * @deprecated Use {@link #canUserBorrowMoreBooks(String, int)} instead
     */
    @Deprecated
    public static boolean canUserBorrowMore(String userId, int quantity) {
        return canUserBorrowMoreBooks(userId, quantity);
    }

    /**
     * @deprecated Use {@link #issueBookToUser(String, String, int)} instead
     */
    @Deprecated
    public static void issueBook(String isbn, String userId, int quantity)
            throws BooksException, UserException {
        issueBookToUser(isbn, userId, quantity);
    }

    /**
     * @deprecated Use {@link #returnBookFromUser(String, String, int)} instead
     */
    @Deprecated
    public static void returnBook(String isbn, String userId, int quantity)
            throws BooksException, UserException {
        returnBookFromUser(isbn, userId, quantity);
    }

    /**
     * @deprecated Use {@link #getBorrowerDetailsForBook(String)} instead
     */
    @Deprecated
    public static Map<String, Integer> getBorrowerDetails(String isbn) {
        return getBorrowerDetailsForBook(isbn);
    }

    /**
     * @deprecated Use {@link #getAvailableQuantityForBook(String)} instead
     */
    @Deprecated
    public static int getAvailableQuantity(String isbn) {
        return getAvailableQuantityForBook(isbn);
    }

    /**
     * @deprecated Use {@link #getOriginalQuantityForBook(String)} instead
     */
    @Deprecated
    public static int getOriginalQuantity(String isbn) {
        return getOriginalQuantityForBook(isbn);
    }

    /**
     * @deprecated Use {@link #getLoanPeriodDays()} instead
     */
    @Deprecated
    public static int getLoanDays() {
        return getLoanPeriodDays();
    }

    /**
     * @deprecated Use {@link #persistBooksDatabase()} instead
     */
    @Deprecated
    public static void persistBooks() throws IOException {
        persistBooksDatabase();
    }
}