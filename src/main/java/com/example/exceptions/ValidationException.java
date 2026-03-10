package com.example.exceptions;

/**
 * Custom exception class for input validation errors in the library management system.
 * This exception is thrown when user input fails validation rules.
 */
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ValidationException with no detail message.
     */
    public ValidationException() {
        super();
    }

    /**
     * Constructs a new ValidationException with the specified detail message.
     *
     * @param message the detail message explaining the validation error
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new ValidationException with the specified detail message and cause.
     *
     * @param message the detail message explaining the validation error
     * @param cause the underlying cause of this exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ValidationException with the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    public ValidationException(Throwable cause) {
        super(cause);
    }
}
