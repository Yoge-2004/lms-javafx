package com.example.entities;

import com.example.exceptions.BooksException;
import com.example.storage.DataStorage;

import java.io.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Comprehensive thread-safe singleton database for book management with enhanced
 * functionality, validation, and automatic persistence.
 */
public final class BooksDB implements Serializable {
    private static final long serialVersionUID = 2L; // Incremented for version tracking

    private static final Logger LOGGER = Logger.getLogger(BooksDB.class.getName());

    // File paths for different data types
    private static final String BOOKS_DB_FILE = "data/books_db.ser";
    private static final String ISSUED_BOOKS_FILE = "data/issued_books.ser";
    private static final String BORROWER_DETAILS_FILE = "data/borrower_details.ser";
    private static final String ISSUE_RECORDS_FILE = "data/issue_records.ser";

    // Business rule constants
    public static final int DEFAULT_LOAN_DAYS = 14;
    public static final int MAX_BORROW_LIMIT = 5;
    public static final double FINE_PER_DAY = 2.0;
    public static final int MAX_RENEWAL_COUNT = 2;

    private static volatile BooksDB instance;
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Core data structures
    private final Map<String, Book> books;
    private final Map<String, List<String>> issuedBooks; // userId -> list of ISBNs
    private final Map<String, Map<String, Integer>> borrowerDetails; // ISBN -> (userId -> quantity)
    private final Map<String, List<IssueRecord>> issueRecords; // userId -> list of IssueRecords

    // Configuration
    private transient boolean autoSave = true;
    private int maxBorrowLimit = MAX_BORROW_LIMIT;
    private int defaultLoanDays = DEFAULT_LOAN_DAYS;
    private double finePerDay = FINE_PER_DAY;
    private int maxRenewalCount = MAX_RENEWAL_COUNT;

    /**
     * Enhanced IssueRecord with additional functionality.
     */
    public static final class IssueRecord implements Serializable {
        private static final long serialVersionUID = 2L;

        private final String isbn;
        private final String bookTitle;
        private final String userId;
        private final LocalDate issueDate;
        private final LocalDate originalDueDate;

        private LocalDate currentDueDate;
        private int quantity;
        private boolean returned;
        private LocalDate returnDate;
        private double fineAmount;
        private int renewalCount;
        private String notes;

        public IssueRecord(String isbn, String bookTitle, String userId, LocalDate issueDate, int quantity) {
            this.isbn = Objects.requireNonNull(isbn, "ISBN cannot be null");
            this.bookTitle = Objects.requireNonNull(bookTitle, "Book title cannot be null");
            this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
            this.issueDate = Objects.requireNonNull(issueDate, "Issue date cannot be null");
            this.originalDueDate = issueDate.plusDays(DEFAULT_LOAN_DAYS);
            this.currentDueDate = originalDueDate;
            this.quantity = Math.max(1, quantity);
            this.returned = false;
            this.returnDate = null;
            this.fineAmount = 0.0;
            this.renewalCount = 0;
            this.notes = null;
        }

        // Getters
        public String getIsbn() {
            return isbn;
        }

        public String getBookTitle() {
            return bookTitle;
        }

        public String getUserId() {
            return userId;
        }

        public LocalDate getIssueDate() {
            return issueDate;
        }

        public LocalDate getDueDate() {
            return currentDueDate;
        }

        public LocalDate getOriginalDueDate() {
            return originalDueDate;
        }

        public int getQuantity() {
            return quantity;
        }

        public boolean isReturned() {
            return returned;
        }

        public LocalDate getReturnDate() {
            return returnDate;
        }

        public double getFineAmount() {
            return fineAmount;
        }

        public int getRenewalCount() {
            return renewalCount;
        }

        public String getNotes() {
            return notes;
        }

        // Setters with validation
        public void setReturned(boolean returned) {
            this.returned = returned;
            if (returned && returnDate == null) {
                this.returnDate = LocalDate.now();
            }
        }

        public void setReturnDate(LocalDate returnDate) {
            this.returnDate = returnDate;
        }

        public void setFineAmount(double fineAmount) {
            this.fineAmount = Math.max(0.0, fineAmount);
        }

        public void setQuantity(int quantity) {
            this.quantity = Math.max(1, quantity);
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        /**
         * Renews the book for another loan period.
         *
         * @param additionalDays additional days to extend
         * @return true if renewal was successful
         */
        public boolean renew(int additionalDays) {
            if (returned || renewalCount >= MAX_RENEWAL_COUNT) {
                return false;
            }

            currentDueDate = currentDueDate.plusDays(additionalDays);
            renewalCount++;
            return true;
        }

        /**
         * Calculates days overdue based on current date or return date.
         *
         * @return number of days overdue (0 if not overdue)
         */
        public long getDaysOverdue() {
            LocalDate checkDate = returned && returnDate != null ? returnDate : LocalDate.now();
            long overdueDays = ChronoUnit.DAYS.between(currentDueDate, checkDate);
            return Math.max(0, overdueDays);
        }

        /**
         * Calculates fine amount based on overdue days.
         *
         * @return calculated fine amount
         */
        public double calculateFine() {
            return getDaysOverdue() * FINE_PER_DAY;
        }

        /**
         * Checks if the record is overdue.
         *
         * @return true if overdue
         */
        public boolean isOverdue() {
            return !returned && getDaysOverdue() > 0;
        }

        /**
         * Checks if the record is due soon (within specified days).
         *
         * @param daysThreshold number of days to consider as "due soon"
         * @return true if due within the threshold
         */
        public boolean isDueSoon(int daysThreshold) {
            if (returned) return false;
            long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), currentDueDate);
            return daysUntilDue >= 0 && daysUntilDue <= daysThreshold;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            IssueRecord that = (IssueRecord) obj;
            return Objects.equals(isbn, that.isbn) &&
                    Objects.equals(userId, that.userId) &&
                    Objects.equals(issueDate, that.issueDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(isbn, userId, issueDate);
        }

        @Override
        public String toString() {
            return String.format("IssueRecord{isbn='%s', user='%s', quantity=%d, issued=%s, due=%s, returned=%s}",
                    isbn, userId, quantity, issueDate, currentDueDate, returned);
        }
    }

    /**
     * Private constructor for singleton pattern.
     */
    private BooksDB() {
        this.books = new LinkedHashMap<>();
        this.issuedBooks = new LinkedHashMap<>();
        this.borrowerDetails = new LinkedHashMap<>();
        this.issueRecords = new LinkedHashMap<>();
        loadAllData();
    }

    /**
     * Gets the singleton instance with thread-safe lazy initialization.
     *
     * @return the singleton instance
     */
    public static BooksDB getInstance() {
        if (instance == null) {
            synchronized (BooksDB.class) {
                if (instance == null) {
                    try {
                        instance = DataStorage.readSerialized(BOOKS_DB_FILE, BooksDB.class);
                        if (instance == null) {
                            instance = new BooksDB();
                            LOGGER.log(Level.INFO, "Created new BooksDB instance");
                        } else {
                            instance.initializeAfterDeserialization();
                            LOGGER.log(Level.INFO, "Loaded existing BooksDB with {0} books",
                                    instance.books.size());
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to load books database, creating new instance", e);
                        instance = new BooksDB();
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Initializes transient fields and loads data after deserialization.
     */
    private void initializeAfterDeserialization() {
        autoSave = true;
        // Validate and set default values if needed
        if (maxBorrowLimit <= 0) maxBorrowLimit = MAX_BORROW_LIMIT;
        if (defaultLoanDays <= 0) defaultLoanDays = DEFAULT_LOAN_DAYS;
        if (finePerDay < 0) finePerDay = FINE_PER_DAY;
        if (maxRenewalCount < 0) maxRenewalCount = MAX_RENEWAL_COUNT;

        loadAllData();
    }

    // --- Book Management Operations ---

    /**
     * Adds a new book with validation.
     *
     * @param book the book to add
     * @throws BooksException if book is invalid or already exists
     */
    public void addBook(Book book) throws BooksException {
        if (book == null) {
            throw new BooksException("Book cannot be null");
        }

        if (book.getIsbn() == null || book.getIsbn().trim().isEmpty()) {
            throw new BooksException("Book ISBN cannot be empty");
        }

        String isbn = book.getIsbn().trim();

        lock.writeLock().lock();
        try {
            if (books.containsKey(isbn)) {
                throw new BooksException("Book with ISBN " + isbn + " already exists");
            }

            books.put(isbn, book);
            LOGGER.log(Level.INFO, "Book added: {0} - {1}", new Object[]{isbn, book.getTitle()});

            if (autoSave) {
                saveAllData();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Updates an existing book.
     *
     * @param book the book with updated information
     * @throws BooksException if book doesn't exist
     */
    public void modifyBook(Book book) throws BooksException {
        if (book == null) {
            throw new BooksException("Book cannot be null");
        }

        String isbn = book.getIsbn();
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new BooksException("Book ISBN cannot be empty");
        }

        lock.writeLock().lock();
        try {
            if (!books.containsKey(isbn)) {
                throw new BooksException("Book not found: " + isbn);
            }

            books.put(isbn, book);
            LOGGER.log(Level.INFO, "Book updated: {0} - {1}", new Object[]{isbn, book.getTitle()});

            if (autoSave) {
                saveAllData();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a book if it's not currently issued.
     *
     * @param isbn the ISBN of the book to remove
     * @throws BooksException if book is currently issued or doesn't exist
     */
    public void removeBook(String isbn) throws BooksException {
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new BooksException("ISBN cannot be empty");
        }

        String trimmedIsbn = isbn.trim();

        lock.writeLock().lock();
        try {
            // Check if book is currently issued
            Map<String, Integer> borrowers = borrowerDetails.get(trimmedIsbn);
            if (borrowers != null && !borrowers.isEmpty()) {
                int totalIssued = borrowers.values().stream().mapToInt(Integer::intValue).sum();
                throw new BooksException("Cannot remove book: " + totalIssued + " copies currently issued");
            }

            Book removedBook = books.remove(trimmedIsbn);
            if (removedBook == null) {
                throw new BooksException("Book not found: " + trimmedIsbn);
            }

            // Clean up related data
            issuedBooks.values().forEach(list -> list.removeIf(trimmedIsbn::equals));
            borrowerDetails.remove(trimmedIsbn);

            LOGGER.log(Level.INFO, "Book removed: {0} - {1}",
                    new Object[]{trimmedIsbn, removedBook.getTitle()});

            if (autoSave) {
                saveAllData();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves a book by ISBN.
     *
     * @param isbn the ISBN to search for
     * @return the book or null if not found
     */
    public Book getBook(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return null;
        }

        lock.readLock().lock();
        try {
            return books.get(isbn.trim());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets all books as an immutable list.
     *
     * @return list of all books
     */
    public List<Book> getBooks() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(books.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Searches books by query matching title, author, or category.
     *
     * @param query the search query
     * @return list of matching books
     */
    public List<Book> searchBooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getBooks();
        }

        String lowerQuery = query.trim().toLowerCase();

        lock.readLock().lock();
        try {
            return books.values().stream()
                    .filter(book ->
                            book.getTitle().toLowerCase().contains(lowerQuery) ||
                                    book.getAuthor().toLowerCase().contains(lowerQuery) ||
                                    book.getCategory().toLowerCase().contains(lowerQuery) ||
                                    book.getIsbn().toLowerCase().contains(lowerQuery))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    // --- Issue and Return Operations ---

    /**
     * Issues books to a user with comprehensive validation.
     *
     * @param isbn the book ISBN
     * @param user the user to issue to
     * @param quantity the number of copies to issue
     * @throws BooksException if validation fails or operation cannot be completed
     */
    public void issueBook(String isbn, User user, int quantity) throws BooksException {
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new BooksException("ISBN cannot be empty");
        }

        if (user == null) {
            throw new BooksException("User cannot be null");
        }

        if (quantity <= 0) {
            throw new BooksException("Quantity must be positive");
        }

        String trimmedIsbn = isbn.trim();
        String userId = user.getUserId();

        lock.writeLock().lock();
        try {
            Book book = books.get(trimmedIsbn);
            if (book == null) {
                throw new BooksException("Book not found: " + trimmedIsbn);
            }

            if (book.getQuantity() < quantity) {
                throw new BooksException("Insufficient copies available. Available: " +
                        book.getQuantity() + ", Requested: " + quantity);
            }

            int currentBorrowed = getUserBorrowedCount(userId);
            if (currentBorrowed + quantity > maxBorrowLimit) {
                throw new BooksException("Borrowing limit exceeded. Current: " + currentBorrowed +
                        ", Requested: " + quantity + ", Limit: " + maxBorrowLimit);
            }

            // Update book quantity
            book.setQuantity(book.getQuantity() - quantity);

            // Update issued books tracking
            issuedBooks.computeIfAbsent(userId, k -> new ArrayList<>());
            List<String> userBooks = issuedBooks.get(userId);
            for (int i = 0; i < quantity; i++) {
                userBooks.add(trimmedIsbn);
            }

            // Update borrower details
            borrowerDetails.computeIfAbsent(trimmedIsbn, k -> new HashMap<>());
            Map<String, Integer> bookBorrowers = borrowerDetails.get(trimmedIsbn);
            bookBorrowers.put(userId, bookBorrowers.getOrDefault(userId, 0) + quantity);

            // Create issue record
            IssueRecord record = new IssueRecord(trimmedIsbn, book.getTitle(), userId,
                    LocalDate.now(), quantity);
            issueRecords.computeIfAbsent(userId, k -> new ArrayList<>()).add(record);

            // Update book's issuedTo field
            updateBookIssuedToField(book);

            LOGGER.log(Level.INFO, "Issued {0} copies of book {1} to user {2}",
                    new Object[]{quantity, trimmedIsbn, userId});

            if (autoSave) {
                saveAllData();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns books from a user with comprehensive validation and fine calculation.
     *
     * @param isbn the book ISBN
     * @param user the user returning the book
     * @param quantity the number of copies to return
     * @throws BooksException if validation fails or operation cannot be completed
     */
    public void returnBook(String isbn, User user, int quantity) throws BooksException {
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new BooksException("ISBN cannot be empty");
        }

        if (user == null) {
            throw new BooksException("User cannot be null");
        }

        if (quantity <= 0) {
            throw new BooksException("Quantity must be positive");
        }

        String trimmedIsbn = isbn.trim();
        String userId = user.getUserId();

        lock.writeLock().lock();
        try {
            // Validate user has issued books
            List<String> userBooks = issuedBooks.get(userId);
            if (userBooks == null) {
                throw new BooksException("User has no issued books");
            }

            long issuedCount = userBooks.stream().filter(trimmedIsbn::equals).count();
            if (issuedCount < quantity) {
                throw new BooksException("User has only " + issuedCount +
                        " copies issued, cannot return " + quantity);
            }

            Book book = books.get(trimmedIsbn);
            if (book == null) {
                throw new BooksException("Book not found: " + trimmedIsbn);
            }

            // Remove from issued books list
            for (int i = 0; i < quantity; i++) {
                userBooks.remove(trimmedIsbn);
            }
            if (userBooks.isEmpty()) {
                issuedBooks.remove(userId);
            }

            // Update book quantity
            book.setQuantity(book.getQuantity() + quantity);

            // Update borrower details
            Map<String, Integer> bookBorrowers = borrowerDetails.get(trimmedIsbn);
            if (bookBorrowers != null) {
                int currentQuantity = bookBorrowers.getOrDefault(userId, 0);
                int newQuantity = currentQuantity - quantity;
                if (newQuantity <= 0) {
                    bookBorrowers.remove(userId);
                } else {
                    bookBorrowers.put(userId, newQuantity);
                }

                if (bookBorrowers.isEmpty()) {
                    borrowerDetails.remove(trimmedIsbn);
                }
            }

            // Process issue records and calculate fines
            processReturnRecords(userId, trimmedIsbn, quantity);

            // Update book's issuedTo field
            updateBookIssuedToField(book);

            LOGGER.log(Level.INFO, "Returned {0} copies of book {1} from user {2}",
                    new Object[]{quantity, trimmedIsbn, userId});

            if (autoSave) {
                saveAllData();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --- Query and Statistics Methods ---

    /**
     * Gets the number of books currently borrowed by a user.
     *
     * @param userId the user ID
     * @return number of borrowed books
     */
    public int getUserBorrowedCount(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }

        lock.readLock().lock();
        try {
            List<String> userBooks = issuedBooks.get(userId.trim());
            return userBooks != null ? userBooks.size() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets books currently borrowed by a user.
     *
     * @param user the user
     * @return list of borrowed books
     */
    public List<Book> getUserBooks(User user) {
        if (user == null) {
            return Collections.emptyList();
        }

        lock.readLock().lock();
        try {
            List<String> userIsbn = issuedBooks.get(user.getUserId());
            if (userIsbn == null || userIsbn.isEmpty()) {
                return Collections.emptyList();
            }

            return userIsbn.stream()
                    .map(books::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets borrower details for a specific book.
     *
     * @param isbn the book ISBN
     * @return map of user IDs to quantities borrowed
     */
    public Map<String, Integer> getBorrowerDetailsForBook(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        lock.readLock().lock();
        try {
            Map<String, Integer> details = borrowerDetails.get(isbn.trim());
            return details != null ? new HashMap<>(details) : Collections.emptyMap();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets all borrower details.
     *
     * @return nested map of all borrower details
     */
    public Map<String, Map<String, Integer>> getAllBorrowerDetails() {
        lock.readLock().lock();
        try {
            Map<String, Map<String, Integer>> result = new HashMap<>();
            for (Map.Entry<String, Map<String, Integer>> entry : borrowerDetails.entrySet()) {
                result.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets total issued quantity for a book.
     *
     * @param isbn the book ISBN
     * @return total issued quantity
     */
    public int getTotalIssued(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return 0;
        }

        lock.readLock().lock();
        try {
            Map<String, Integer> borrowers = borrowerDetails.get(isbn.trim());
            return borrowers != null ?
                    borrowers.values().stream().mapToInt(Integer::intValue).sum() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets available quantity for a book.
     *
     * @param isbn the book ISBN
     * @return available quantity
     */
    public int getAvailableQuantity(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return 0;
        }

        lock.readLock().lock();
        try {
            Book book = books.get(isbn.trim());
            return book != null ? book.getQuantity() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets original total quantity for a book.
     *
     * @param isbn the book ISBN
     * @return original total quantity
     */
    public int getOriginalQuantity(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            return 0;
        }

        lock.readLock().lock();
        try {
            Book book = books.get(isbn.trim());
            if (book == null) return 0;
            return book.getQuantity() + getTotalIssued(isbn);
        } finally {
            lock.readLock().unlock();
        }
    }

    // --- Issue Records and Fine Management ---

    /**
     * Gets all issue records.
     *
     * @return list of all issue records
     */
    public List<IssueRecord> getAllIssueRecords() {
        lock.readLock().lock();
        try {
            return issueRecords.values().stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets issue records for a specific user.
     *
     * @param userId the user ID
     * @return list of user's issue records
     */
    public List<IssueRecord> getUserIssueRecords(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        lock.readLock().lock();
        try {
            List<IssueRecord> records = issueRecords.get(userId.trim());
            return records != null ? new ArrayList<>(records) : Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets active (unreturned) issue records for a user.
     *
     * @param userId the user ID
     * @return list of active issue records
     */
    public List<IssueRecord> getUserActiveIssueRecords(String userId) {
        return getUserIssueRecords(userId).stream()
                .filter(record -> !record.isReturned())
                .collect(Collectors.toList());
    }

    /**
     * Gets all active issue records.
     *
     * @return list of all active issue records
     */
    public List<IssueRecord> getAllActiveIssueRecords() {
        return getAllIssueRecords().stream()
                .filter(record -> !record.isReturned())
                .collect(Collectors.toList());
    }

    /**
     * Gets overdue issue records for a user.
     *
     * @param userId the user ID
     * @return list of overdue issue records
     */
    public List<IssueRecord> getUserOverdueIssueRecords(String userId) {
        return getUserActiveIssueRecords(userId).stream()
                .filter(IssueRecord::isOverdue)
                .collect(Collectors.toList());
    }

    /**
     * Gets all overdue issue records.
     *
     * @return list of all overdue issue records
     */
    public List<IssueRecord> getAllOverdueIssueRecords() {
        return getAllActiveIssueRecords().stream()
                .filter(IssueRecord::isOverdue)
                .collect(Collectors.toList());
    }

    /**
     * Calculates total fine for a user.
     *
     * @param userId the user ID
     * @return total fine amount
     */
    public double calculateUserFine(String userId) {
        return getUserActiveIssueRecords(userId).stream()
                .mapToDouble(IssueRecord::calculateFine)
                .sum();
    }

    /**
     * Gets total fines across all users.
     *
     * @return total fine amount
     */
    public double getTotalFines() {
        return getAllActiveIssueRecords().stream()
                .mapToDouble(IssueRecord::calculateFine)
                .sum();
    }

    // --- Configuration Methods ---

    public int getMaxBorrow() { return maxBorrowLimit; }
    public int getLoanDays() { return defaultLoanDays; }
    public double getFinePerDay() { return finePerDay; }
    public int getMaxRenewalCount() { return maxRenewalCount; }

    public void setMaxBorrow(int maxBorrow) {
        this.maxBorrowLimit = Math.max(1, maxBorrow);
    }

    public void setLoanDays(int loanDays) {
        this.defaultLoanDays = Math.max(1, loanDays);
    }

    public void setFinePerDay(double finePerDay) {
        this.finePerDay = Math.max(0.0, finePerDay);
    }

    public void setMaxRenewalCount(int maxRenewalCount) {
        this.maxRenewalCount = Math.max(0, maxRenewalCount);
    }

    // --- Persistence Operations ---

    /**
     * Forces persistence of all data.
     *
     * @throws IOException if persistence fails
     */
    public void forcePersist() throws IOException {
        lock.readLock().lock();
        try {
            saveAllData();
            LOGGER.log(Level.INFO, "Books database forcibly persisted");
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    // --- Private Helper Methods ---

    private void loadAllData() {
        try {
            loadIssuedBooks();
            loadBorrowerDetails();
            loadIssueRecords();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load some data files", e);
        }
    }

    private void loadIssuedBooks() {
        try {
            Map<String, List<String>> loaded = DataStorage.readSerializedMap(ISSUED_BOOKS_FILE);
            if (loaded != null) {
                issuedBooks.clear();
                issuedBooks.putAll(loaded);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load issued books data", e);
        }
    }

    private void loadBorrowerDetails() {
        try {
            Map<String, Map<String, Integer>> loaded = DataStorage.readSerializedNestedMap(BORROWER_DETAILS_FILE);
            if (loaded != null) {
                borrowerDetails.clear();
                borrowerDetails.putAll(loaded);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load borrower details data", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadIssueRecords() {
        try {
            Map<String, List<IssueRecord>> loaded = DataStorage.readSerialized(ISSUE_RECORDS_FILE, Map.class);
            if (loaded != null) {
                issueRecords.clear();
                issueRecords.putAll(loaded);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load issue records data", e);
        }
    }

    private void saveAllData() {
        try {
            DataStorage.writeSerialized(BOOKS_DB_FILE, this);
            DataStorage.writeSerializedMap(ISSUED_BOOKS_FILE, issuedBooks);
            DataStorage.writeSerializedNestedMap(BORROWER_DETAILS_FILE, borrowerDetails);
            DataStorage.writeSerialized(ISSUE_RECORDS_FILE, issueRecords);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save database files", e);
        }
    }

    private void processReturnRecords(String userId, String isbn, int quantity) {
        List<IssueRecord> userRecords = issueRecords.get(userId);
        if (userRecords == null) return;

        int remainingToReturn = quantity;
        for (Iterator<IssueRecord> it = userRecords.iterator(); it.hasNext() && remainingToReturn > 0;) {
            IssueRecord record = it.next();
            if (record.getIsbn().equals(isbn) && !record.isReturned()) {
                int recordQuantity = record.getQuantity();
                if (recordQuantity <= remainingToReturn) {
                    record.setReturned(true);
                    record.setReturnDate(LocalDate.now());
                    record.setFineAmount(record.calculateFine());
                    remainingToReturn -= recordQuantity;
                } else {
                    // Split the record
                    record.setQuantity(recordQuantity - remainingToReturn);
                    IssueRecord returnedRecord = new IssueRecord(isbn, record.getBookTitle(),
                            userId, record.getIssueDate(), remainingToReturn);
                    returnedRecord.setReturned(true);
                    returnedRecord.setReturnDate(LocalDate.now());
                    returnedRecord.setFineAmount(returnedRecord.calculateFine());
                    userRecords.add(returnedRecord);
                    remainingToReturn = 0;
                }
            }
        }
    }

    private void updateBookIssuedToField(Book book) {
        Map<String, Integer> borrowers = borrowerDetails.get(book.getIsbn());
        if (borrowers == null || borrowers.isEmpty()) {
            book.setIssuedTo(null);
        } else {
            String issuedTo = String.join(", ", borrowers.keySet());
            book.setIssuedTo(issuedTo);
        }
    }

    @Override
    public String toString() {
        return String.format("BooksDB{bookCount=%d, totalIssued=%d, autoSave=%s}",
                books.size(), getAllActiveIssueRecords().size(), autoSave);
    }
}