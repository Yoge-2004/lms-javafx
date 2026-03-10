package com.example.exceptions;

/**
 * Custom exception class for book-related errors in the library management system.
 * This exception is thrown when book operations fail due to validation errors,
 * availability issues, or other book-specific problems.
 */
public class BooksException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new BooksException with no detail message.
     */
    public BooksException() {
        super();
    }

    /**
     * Constructs a new BooksException with the specified detail message.
     *
     * @param message the detail message explaining the exception
     */
    public BooksException(String message) {
        super(message);
    }

    /**
     * Constructs a new BooksException with the specified detail message and cause.
     *
     * @param message the detail message explaining the exception
     * @param cause the underlying cause of this exception
     */
    public BooksException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new BooksException with the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    public BooksException(Throwable cause) {
        super(cause);
    }
}