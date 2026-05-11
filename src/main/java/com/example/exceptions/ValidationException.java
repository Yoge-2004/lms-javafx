package com.example.exceptions;

/**
 * Thrown when user-supplied input violates one or more business-rule constraints
 * before a write operation is attempted.
 *
 * <p>{@code ValidationException} is the primary exception type raised by
 * {@link com.example.services.UserService} during user creation, profile updates,
 * and password changes.  Examples of validation failures:</p>
 * <ul>
 *   <li>Username is blank or contains disallowed characters.</li>
 *   <li>Password does not meet the minimum length requirement.</li>
 *   <li>Email address is already associated with another account.</li>
 *   <li>Required fields are missing from a registration request.</li>
 * </ul>
 *
 * <p>The exception message is intentionally worded for direct display to the
 * end user without further processing.</p>
 *
 * @see UserException
 * @see com.example.services.UserService
 */
public class ValidationException extends UserException {
    private static final long serialVersionUID = 1L;


    /**
     * Constructs a {@code ValidationException} with the given user-facing message.
     *
     * @param message a human-readable description of the validation failure
     */
    public ValidationException(String message) { super(message); }

    /**
     * Constructs a {@code ValidationException} with a message and a root cause.
     *
     * @param message a human-readable description of the validation failure
     * @param cause   the underlying exception that triggered this validation error
     */
    public ValidationException(String message, Throwable cause) { super(message, cause); }
}