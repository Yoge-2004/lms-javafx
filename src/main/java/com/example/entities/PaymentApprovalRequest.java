package com.example.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a fine-payment request submitted by a regular user that must be
 * approved (or rejected) by an admin or librarian of the same library branch
 * before the payment is finalised and a receipt is issued.
 *
 * <p>This fixes the trust-gap where a user could previously self-approve their
 * own payments without any staff oversight.</p>
 */
public final class PaymentApprovalRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    private final String requestId;
    /** The user who initiated the payment. */
    private final String userId;
    /** Stable identifier of the IssueRecord whose fine is being paid. */
    private final String issueRecordId;
    private final String isbn;
    private final String bookTitle;
    /** Amount the user wishes to pay (may be partial). */
    private final double amount;
    /** Total fine outstanding at the time the request was created. */
    private final double totalFineAtRequest;
    private final LocalDateTime requestedAt;

    private Status status;
    private String processedBy;
    private LocalDateTime processedAt;
    private String note;

    public PaymentApprovalRequest(String userId, String issueRecordId,
                                  String isbn, String bookTitle,
                                  double amount, double totalFineAtRequest) {
        this.requestId        = UUID.randomUUID().toString();
        this.userId           = java.util.Objects.requireNonNull(userId, "userId");
        this.issueRecordId    = java.util.Objects.requireNonNull(issueRecordId, "issueRecordId");
        this.isbn             = java.util.Objects.requireNonNull(isbn, "isbn");
        this.bookTitle        = java.util.Objects.requireNonNull(bookTitle, "bookTitle");
        this.amount           = amount;
        this.totalFineAtRequest = totalFineAtRequest;
        this.requestedAt      = LocalDateTime.now();
        this.status           = Status.PENDING;
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    public String        getRequestId()          { return requestId; }
    public String        getUserId()              { return userId; }
    public String        getIssueRecordId()       { return issueRecordId; }
    public String        getIsbn()                { return isbn; }
    public String        getBookTitle()           { return bookTitle; }
    public double        getAmount()              { return amount; }
    public double        getTotalFineAtRequest()  { return totalFineAtRequest; }
    public LocalDateTime getRequestedAt()         { return requestedAt; }
    public Status        getStatus()              { return status; }
    public String        getProcessedBy()         { return processedBy; }
    public LocalDateTime getProcessedAt()         { return processedAt; }
    public String        getNote()                { return note; }
    public boolean       isPending()              { return status == Status.PENDING; }

    // ── State transitions ────────────────────────────────────────────────────

    public void approve(String staffId) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Cannot approve a request that is already " + status);
        }
        this.status      = Status.APPROVED;
        this.processedBy = staffId;
        this.processedAt = LocalDateTime.now();
        this.note        = null;
    }

    public void reject(String staffId, String reason) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Cannot reject a request that is already " + status);
        }
        String sanitized = (reason == null || reason.isBlank()) ? "Rejected by staff" : reason.trim();
        if (sanitized.length() > 500) sanitized = sanitized.substring(0, 500);
        this.status      = Status.REJECTED;
        this.processedBy = staffId;
        this.processedAt = LocalDateTime.now();
        this.note        = sanitized;
    }

    @Override public String toString() {
        return "PaymentApprovalRequest[" + requestId + ", user=" + userId
                + ", book=" + bookTitle + ", amount=" + amount + ", status=" + status + "]";
    }
}