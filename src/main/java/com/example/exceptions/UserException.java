package com.example.exceptions;

/**
 * Unchecked base exception for all user-management failures in Library OS.
 *
 * <p>This class serves as the common supertype for user-related error conditions
 * such as authentication failures, duplicate usernames, or missing accounts.
 * {@link ValidationException} is the principal subclass used when input data
 * does not satisfy business rules.</p>
 *
 * <p>Callers in the UI layer typically catch {@code UserException} (or its
 * subtypes) and translate the message into a toast notification or inline
 * form error.</p>
 *
 * @see com.example.services.UserService
 * @see ValidationException
 */
public class UserException extends RuntimeException {
    private static final long serialVersionUID = 1L;


    /**
     * Constructs a {@code UserException} with the given message.
     *
     * @param message a human-readable description of the error
     */
    public UserException(String message) { super(message); }

    /**
     * Constructs a {@code UserException} with a message and a root cause.
     *
     * @param message a human-readable description of the error
     * @param cause   the underlying exception that provoked this failure
     */
    public UserException(String message, Throwable cause) { super(message, cause); }
}