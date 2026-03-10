package com.example.exceptions;

/**
 * Custom exception class for user-related errors in the library management system.
 * This exception is thrown when user operations fail due to validation errors,
 * authentication failures, or other user-specific issues.
 */
public class UserException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new UserException with no detail message.
     */
    public UserException() {
        super();
    }

    /**
     * Constructs a new UserException with the specified detail message.
     *
     * @param message the detail message explaining the exception
     */
    public UserException(String message) {
        super(message);
    }

    /**
     * Constructs a new UserException with the specified detail message and cause.
     *
     * @param message the detail message explaining the exception
     * @param cause the underlying cause of this exception
     */
    public UserException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new UserException with the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    public UserException(Throwable cause) {
        super(cause);
    }
}
