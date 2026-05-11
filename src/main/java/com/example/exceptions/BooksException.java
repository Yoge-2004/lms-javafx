package com.example.exceptions;

/**
 * Unchecked exception thrown by the book-management layer when a book-related
 * operation cannot be completed.
 *
 * <p>Typical triggers include attempting to add a book whose ISBN is already
 * registered, requesting more copies than are in stock, or referencing a book
 * that does not exist in the catalogue.</p>
 *
 * <p>Being a {@link RuntimeException} subclass, callers are not required to
 * declare or catch it, but the circulation and catalogue layers typically do so
 * in order to convert the failure into a user-visible message.</p>
 *
 * @see com.example.services.BookService
 */
public class BooksException extends RuntimeException {
    private static final long serialVersionUID = 1L;


    /**
     * Constructs a {@code BooksException} with the given human-readable message.
     *
     * @param message a description of the error, shown to the user or logged
     */
    public BooksException(String message) { super(message); }

    /**
     * Constructs a {@code BooksException} with a message and an underlying cause.
     *
     * <p>Use this constructor when wrapping a lower-level exception (for example
     * an {@link java.io.IOException} from persistence) so that the original stack
     * trace is preserved for debugging.</p>
     *
     * @param message a description of the error
     * @param cause   the lower-level exception that triggered this one
     */
    public BooksException(String message, Throwable cause) { super(message, cause); }
}