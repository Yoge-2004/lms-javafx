package com.example.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Enhanced Book entity with comprehensive validation, proper encapsulation,
 * and additional book properties.
 */
public final class Book implements Serializable {
    private static final long serialVersionUID = 2L; // Incremented for version tracking

    // Validation patterns
    private static final Pattern ISBN_PATTERN = Pattern.compile(
            "^(?:ISBN(?:-1[03])?:? )?(?=[0-9X]{10}$|(?=(?:[0-9]+[- ]){3})[- 0-9X]{13}$|97[89][0-9]{10}$|(?=(?:[0-9]+[- ]){4})[- 0-9]{17}$)(?:97[89][- ]?)?[0-9]{1,5}[- ]?[0-9]+[- ]?[0-9]+[- ]?[0-9X]$"
    );

    // Validation constants
    private static final int MIN_TITLE_LENGTH = 1;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MIN_AUTHOR_LENGTH = 1;
    private static final int MAX_AUTHOR_LENGTH = 100;
    private static final int MIN_CATEGORY_LENGTH = 1;
    private static final int MAX_CATEGORY_LENGTH = 50;

    // Core properties
    private String isbn;
    private String title;
    private String author;
    private String category;
    private int quantity;
    private String issuedTo; // comma-separated list of user IDs

    // Additional properties
    private String publisher;
    private String description;
    private LocalDateTime addedAt;
    private LocalDateTime lastUpdatedAt;
    private double price;
    private String location; // shelf location
    private boolean isActive;
    private int totalCopiesAdded; // tracks original quantity for statistics

    /**
     * Creates a new book with required fields and validation.
     *
     * @param isbn the ISBN
     * @param title the title
     * @param author the author
     * @param category the category
     * @param quantity the initial quantity
     */
    public Book(String isbn, String title, String author, String category, int quantity) {
        validateAndSetIsbn(isbn);
        validateAndSetTitle(title);
        validateAndSetAuthor(author);
        validateAndSetCategory(category);
        validateAndSetQuantity(quantity);

        // Initialize additional properties
        this.issuedTo = null;
        this.addedAt = LocalDateTime.now();
        this.lastUpdatedAt = this.addedAt;
        this.isActive = true;
        this.totalCopiesAdded = quantity;
        this.price = 0.0;
        this.publisher = null;
        this.description = null;
        this.location = null;
    }

    /**
     * Creates a new book with all properties.
     *
     * @param isbn the ISBN
     * @param title the title
     * @param author the author
     * @param category the category
     * @param quantity the initial quantity
     * @param publisher the publisher
     * @param description the description
     * @param price the price
     * @param location the shelf location
     */
    public Book(String isbn, String title, String author, String category, int quantity,
                String publisher, String description, double price, String location) {
        this(isbn, title, author, category, quantity);
        setPublisher(publisher);
        setDescription(description);
        setPrice(price);
        setLocation(location);
    }

    // --- Getters ---

    public String getIsbn() { return isbn; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public String getIssuedTo() { return issuedTo; }
    public String getPublisher() { return publisher; }
    public String getDescription() { return description; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public double getPrice() { return price; }
    public String getLocation() { return location; }
    public boolean isActive() { return isActive; }
    public int getTotalCopiesAdded() { return totalCopiesAdded; }

    /**
     * Gets formatted ISBN for display.
     *
     * @return formatted ISBN string
     */
    public String getFormattedIsbn() {
        if (isbn == null) return null;

        // Simple formatting for ISBN-13
        if (isbn.length() == 13 && isbn.matches("\\d{13}")) {
            return isbn.substring(0, 3) + "-" + isbn.substring(3, 4) + "-" +
                    isbn.substring(4, 6) + "-" + isbn.substring(6, 12) + "-" + isbn.substring(12);
        }

        return isbn; // Return as-is if not standard format
    }

    /**
     * Gets book availability status.
     *
     * @return availability status string
     */
    public String getAvailabilityStatus() {
        if (!isActive) return "Inactive";
        if (quantity <= 0) return "Out of Stock";
        if (quantity <= 2) return "Low Stock (" + quantity + " available)";
        return "Available (" + quantity + " copies)";
    }

    // --- Setters with Validation ---

    public void setIsbn(String isbn) {
        validateAndSetIsbn(isbn);
        updateLastModified();
    }

    public void setTitle(String title) {
        validateAndSetTitle(title);
        updateLastModified();
    }

    public void setAuthor(String author) {
        validateAndSetAuthor(author);
        updateLastModified();
    }

    public void setCategory(String category) {
        validateAndSetCategory(category);
        updateLastModified();
    }

    public void setQuantity(int quantity) {
        validateAndSetQuantity(quantity);
        updateLastModified();
    }

    public void setIssuedTo(String issuedTo) {
        this.issuedTo = (issuedTo != null && !issuedTo.trim().isEmpty()) ?
                issuedTo.trim() : null;
        updateLastModified();
    }

    public void setPublisher(String publisher) {
        this.publisher = (publisher != null && !publisher.trim().isEmpty()) ?
                publisher.trim() : null;
        updateLastModified();
    }

    public void setDescription(String description) {
        this.description = (description != null && !description.trim().isEmpty()) ?
                description.trim() : null;
        updateLastModified();
    }

    public void setPrice(double price) {
        this.price = Math.max(0.0, price);
        updateLastModified();
    }

    public void setLocation(String location) {
        this.location = (location != null && !location.trim().isEmpty()) ?
                location.trim() : null;
        updateLastModified();
    }

    public void setActive(boolean active) {
        this.isActive = active;
        updateLastModified();
    }

    /**
     * Adds more copies to the book's inventory.
     *
     * @param additionalCopies number of copies to add
     */
    public void addCopies(int additionalCopies) {
        if (additionalCopies > 0) {
            this.quantity += additionalCopies;
            this.totalCopiesAdded += additionalCopies;
            updateLastModified();
        }
    }

    /**
     * Removes copies from the book's inventory (for damaged/lost books).
     *
     * @param copiesToRemove number of copies to remove
     */
    public void removeCopies(int copiesToRemove) {
        if (copiesToRemove > 0 && copiesToRemove <= quantity) {
            this.quantity -= copiesToRemove;
            updateLastModified();
        }
    }

    // --- Validation Methods ---

    private void validateAndSetIsbn(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new IllegalArgumentException("ISBN cannot be empty");
        }

        String trimmedIsbn = isbn.trim().replaceAll("[\\s-]", "");

        if (trimmedIsbn.length() < 10 || trimmedIsbn.length() > 17) {
            throw new IllegalArgumentException("ISBN must be between 10 and 17 characters");
        }

        // Additional ISBN validation could be added here
        this.isbn = trimmedIsbn;
    }

    private void validateAndSetTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }

        String trimmedTitle = title.trim();
        if (trimmedTitle.length() < MIN_TITLE_LENGTH || trimmedTitle.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Title must be between " + MIN_TITLE_LENGTH +
                    " and " + MAX_TITLE_LENGTH + " characters");
        }

        this.title = trimmedTitle;
    }

    private void validateAndSetAuthor(String author) {
        if (author == null || author.trim().isEmpty()) {
            throw new IllegalArgumentException("Author cannot be empty");
        }

        String trimmedAuthor = author.trim();
        if (trimmedAuthor.length() < MIN_AUTHOR_LENGTH || trimmedAuthor.length() > MAX_AUTHOR_LENGTH) {
            throw new IllegalArgumentException("Author must be between " + MIN_AUTHOR_LENGTH +
                    " and " + MAX_AUTHOR_LENGTH + " characters");
        }

        this.author = trimmedAuthor;
    }

    private void validateAndSetCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Category cannot be empty");
        }

        String trimmedCategory = category.trim();
        if (trimmedCategory.length() < MIN_CATEGORY_LENGTH || trimmedCategory.length() > MAX_CATEGORY_LENGTH) {
            throw new IllegalArgumentException("Category must be between " + MIN_CATEGORY_LENGTH +
                    " and " + MAX_CATEGORY_LENGTH + " characters");
        }

        this.category = trimmedCategory;
    }

    private void validateAndSetQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }

        this.quantity = quantity;
    }

    private void updateLastModified() {
        this.lastUpdatedAt = LocalDateTime.now();
    }

    // --- Utility Methods ---

    /**
     * Validates the current book object's state.
     */
    public void validate() {
        validateAndSetIsbn(this.isbn);
        validateAndSetTitle(this.title);
        validateAndSetAuthor(this.author);
        validateAndSetCategory(this.category);
        validateAndSetQuantity(this.quantity);
    }

    /**
     * Checks if the book is currently issued to anyone.
     *
     * @return true if book is issued
     */
    public boolean isIssued() {
        return issuedTo != null && !issuedTo.trim().isEmpty();
    }

    /**
     * Checks if the book is available for issuing.
     *
     * @return true if available
     */
    public boolean isAvailable() {
        return isActive && quantity > 0;
    }

    /**
     * Gets the number of copies currently on loan.
     *
     * @return number of issued copies
     */
    public int getIssuedCount() {
        return totalCopiesAdded - quantity;
    }

    /**
     * Creates a summary string for the book.
     *
     * @return formatted summary
     */
    public String getSummary() {
        return String.format("%s by %s (ISBN: %s) - %s",
                title, author, getFormattedIsbn(), getAvailabilityStatus());
    }

    // --- Object Methods ---

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Book book = (Book) obj;
        return Objects.equals(isbn, book.isbn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isbn);
    }

    @Override
    public String toString() {
        return String.format("Book{isbn='%s', title='%s', author='%s', category='%s', quantity=%d, active=%s}",
                isbn, title, author, category, quantity, isActive);
    }

    /**
     * Creates a detailed string representation.
     *
     * @return detailed string representation
     */
    public String toDetailedString() {
        return String.format(
                "Book{isbn='%s', title='%s', author='%s', category='%s', quantity=%d, " +
                        "publisher='%s', price=%.2f, location='%s', active=%s, added=%s}",
                isbn, title, author, category, quantity, publisher, price, location, isActive, addedAt
        );
    }

    // --- Static Utility Methods ---

    /**
     * Validates an ISBN format.
     *
     * @param isbn the ISBN to validate
     * @return true if ISBN format is valid
     */
    public static boolean isValidIsbn(String isbn) {
        if (isbn == null) return false;
        String cleaned = isbn.trim().replaceAll("[\\s-]", "");
        return cleaned.length() >= 10 && cleaned.length() <= 17 && cleaned.matches("\\d+[0-9X]?");
    }

    /**
     * Formats an ISBN for display.
     *
     * @param isbn the ISBN to format
     * @return formatted ISBN or original if formatting fails
     */
    public static String formatIsbn(String isbn) {
        if (isbn == null) return null;

        String cleaned = isbn.replaceAll("[\\s-]", "");
        if (cleaned.length() == 13 && cleaned.matches("\\d{13}")) {
            return cleaned.substring(0, 3) + "-" + cleaned.substring(3, 4) + "-" +
                    cleaned.substring(4, 6) + "-" + cleaned.substring(6, 12) + "-" + cleaned.substring(12);
        }

        return isbn;
    }
}