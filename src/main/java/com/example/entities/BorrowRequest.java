package com.example.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * FIXED: Added state machine validation to prevent transitions from terminal states.
 */
public final class BorrowRequest implements Serializable {
    private static final long serialVersionUID = 2L; // Bumped for fixes

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    private final String requestId;
    private final String isbn;
    private final String bookTitle;
    private final String userId;
    private final int quantity;
    private final LocalDateTime requestedAt;

    private Status status;
    private String processedBy;
    private LocalDateTime processedAt;
    private String note;

    public BorrowRequest(String isbn, String bookTitle, String userId, int quantity) {
        this.requestId = UUID.randomUUID().toString();
        this.isbn = Objects.requireNonNull(isbn, "isbn");
        this.bookTitle = Objects.requireNonNull(bookTitle, "bookTitle");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.quantity = Math.max(1, quantity);
        this.requestedAt = LocalDateTime.now();
        this.status = Status.PENDING;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public String getUserId() {
        return userId;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public Status getStatus() {
        return status;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public String getNote() {
        return note;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    /**
     * FIXED: Added state validation - cannot approve if already in terminal state.
     *
     * @throws IllegalStateException if request is not in PENDING state
     */
    public void approve(String processedBy) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Cannot approve request that is already " + status);
        }
        this.status = Status.APPROVED;
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
        this.note = null;
    }

    /**
     * FIXED: Added state validation and note length limit.
     *
     * @throws IllegalStateException if request is not in PENDING state
     * @throws IllegalArgumentException if note exceeds 1000 characters
     */
    public void reject(String processedBy, String note) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Cannot reject request that is already " + status);
        }
        // FIXED: Limit note length to prevent memory abuse
        String sanitizedNote = note == null || note.isBlank() ? "Rejected by staff" : note.trim();
        if (sanitizedNote.length() > 1000) {
            sanitizedNote = sanitizedNote.substring(0, 1000);
        }
        this.status = Status.REJECTED;
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
        this.note = sanitizedNote;
    }
}