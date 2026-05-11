package com.example.services;

import com.example.entities.BooksDB;
import com.example.entities.BooksDB.IssueRecord;
import com.example.entities.PaymentApprovalRequest;
import com.example.entities.User;
import com.example.entities.UserRole;
import com.example.entities.UsersDB;
import com.example.storage.AppPaths;
import com.example.storage.DataStorage;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages the approval lifecycle for user-submitted fine-payment requests.
 *
 * <p>When a regular user wants to pay a fine they submit a {@link PaymentApprovalRequest}.
 * Any admin or librarian on the same branch can then approve or reject it.
 * On approval the payment is finalised via {@link InvoiceService} and a receipt is generated.
 * On rejection the request is archived with a reason; the user sees the outcome on their
 * "My Fines" tab.</p>
 */
public final class PaymentApprovalService {

    private static final Logger LOGGER = Logger.getLogger(PaymentApprovalService.class.getName());
    private static final String STORE_FILE = "payment_approvals.ser";

    private PaymentApprovalService() {}

    // ── In-memory store (lazy, serialised to disk) ───────────────────────────

    private static List<PaymentApprovalRequest> requests;
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private static List<PaymentApprovalRequest> store() {
        if (requests == null) {
            synchronized (PaymentApprovalService.class) {
                if (requests == null) {
                    try {
                        @SuppressWarnings("unchecked")
                        List<PaymentApprovalRequest> loaded =
                                DataStorage.readSerialized(
                                        AppPaths.resolveDataFile(STORE_FILE).toString(),
                                        (Class<List<PaymentApprovalRequest>>) (Class<?>) List.class);
                        requests = loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
                    } catch (Exception e) {
                        requests = new ArrayList<>();
                    }
                }
            }
        }
        return requests;
    }

    private static void persist() {
        try {
            DataStorage.writeSerialized(
                    AppPaths.resolveDataFile(STORE_FILE).toString(), new ArrayList<>(store()));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to persist payment approval requests", e);
        }
    }

    /**
     * FIX #4: Public entry point for the manual "Save Now" action in DataManagementView.
     * Flushes all in-memory payment approval requests to permanent storage immediately.
     *
     * @throws IOException if the write fails
     */
    public static void persistPublic() throws IOException {
        try {
            DataStorage.writeSerialized(
                    AppPaths.resolveDataFile(STORE_FILE).toString(), new ArrayList<>(store()));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to persist payment approval requests (manual save)", e);
            throw e;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called by a regular user when they click "Pay Fine".
     * Creates a pending approval request and notifies all staff on this branch
     * via an in-app notification (email if configured).
     *
     * @return the newly created request
     */
    public static PaymentApprovalRequest submitRequest(String userId, IssueRecord record, double amount) {
        double total = record.isReturned()
                ? record.getRemainingFine()
                : record.calculateFine() - record.getPaidAmount();

        PaymentApprovalRequest req = new PaymentApprovalRequest(
                userId, record.getRecordId(), record.getIsbn(), record.getBookTitle(),
                amount, total);

        lock.writeLock().lock();
        try {
            store().add(req);
            persist();
        } finally {
            lock.writeLock().unlock();
        }

        notifyStaff(req);
        return req;
    }

    /**
     * Returns all pending requests visible to a staff member (all pending in their branch).
     */
    public static List<PaymentApprovalRequest> getPendingRequests() {
        lock.readLock().lock();
        try {
            return store().stream()
                    .filter(PaymentApprovalRequest::isPending)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all requests (pending, approved, rejected) for a specific user.
     */
    public static List<PaymentApprovalRequest> getRequestsForUser(String userId) {
        lock.readLock().lock();
        try {
            return store().stream()
                    .filter(r -> userId.equals(r.getUserId()))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Returns count of pending payment requests for badge display. */
    public static int getPendingCount() {
        lock.readLock().lock();
        try {
            return (int) store().stream().filter(PaymentApprovalRequest::isPending).count();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Staff approves a payment request. The fine is posted to the IssueRecord,
     * an invoice is added to history, and the record is persisted.
     *
     * @param requestId the ID of the request to approve
     * @param staffId   the approving staff member's userId
     * @param toast     optional toast for UI feedback (may be null)
     */
    public static void approveRequest(String requestId, String staffId,
                                      com.example.application.ToastDisplay toast) {
        lock.writeLock().lock();
        PaymentApprovalRequest req;
        try {
            req = store().stream()
                    .filter(r -> requestId.equals(r.getRequestId()) && r.isPending())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Request not found or already processed: " + requestId));
            req.approve(staffId);
            persist();
        } finally {
            lock.writeLock().unlock();
        }

        // Apply the payment to the IssueRecord
        try {
            IssueRecord record = BooksDB.getInstance().findIssueRecordById(req.getIssueRecordId());
            if (record == null) {
                LOGGER.warning("IssueRecord not found for approved payment: " + req.getIssueRecordId());
                return;
            }
            double amount = req.getAmount();
            record.setPaidAmount(record.getPaidAmount() + amount);
            double accrued = record.isReturned() ? record.getFineAmount() : record.calculateFine();
            if (record.getPaidAmount() >= accrued - 0.01) {
                record.setFineAmount(accrued);
                record.setFinePaid(true);
                if (!record.isReturned()) {
                    // Active-loan full payment: reset overdue state
                    record.setDueDate(java.time.LocalDate.now());
                    record.setPaidAmount(0);
                    record.setFineAmount(0);
                    record.setFinePaid(false);
                }
            }
            BooksDB.getInstance().saveAllData();

            String invoiceId = "INV-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            BooksDB.getInstance().addInvoiceRecord(
                    new BooksDB.InvoiceData(invoiceId, req.getUserId(), req.getIsbn(),
                            req.getBookTitle(), amount));

            // Email receipt to user if configured
            try {
                User user = UserService.getUserById(req.getUserId());
                if (user.getEmail() != null && !user.getEmail().isBlank()
                        && AppConfigurationService.getConfiguration().isEmailConfigured()) {
                    InvoiceService.emailInvoice(user, record, amount, invoiceId, toast);
                }
            } catch (Exception e) {
                // Email delivery failure must not roll back a successful payment approval.
                LOGGER.log(Level.WARNING, "Invoice email could not be sent for payment "
                        + invoiceId + " (user: " + req.getUserId() + "): " + e.getMessage(), e);
            }

            if (toast != null) toast.showSuccess(
                    "Payment of " + com.example.application.ui.AppTheme.formatCurrency(amount)
                            + " approved. Invoice: " + invoiceId);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to finalise approved payment", e);
            if (toast != null) toast.showError("Approval saved but payment posting failed: " + e.getMessage());
        }
    }

    /**
     * Staff rejects a payment request with a reason.
     */
    public static void rejectRequest(String requestId, String staffId, String reason,
                                     com.example.application.ToastDisplay toast) {
        lock.writeLock().lock();
        try {
            PaymentApprovalRequest req = store().stream()
                    .filter(r -> requestId.equals(r.getRequestId()) && r.isPending())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
            req.reject(staffId, reason);
            persist();
            if (toast != null) toast.showInfo("Payment request rejected.");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Force reload from disk (called after DB sync on startup). */
    public static void forceReload() {
        synchronized (PaymentApprovalService.class) { requests = null; }
        store(); // triggers reload
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void notifyStaff(PaymentApprovalRequest req) {
        // Collect all admins and librarians on this branch for email notification
        try {
            List<User> staff = UsersDB.getInstance().getAllUsers().stream()
                    .filter(u -> u.getRole() == UserRole.ADMIN || u.getRole() == UserRole.LIBRARIAN)
                    .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                    .collect(Collectors.toList());

            if (staff.isEmpty() || !AppConfigurationService.getConfiguration().isEmailConfigured()) return;

            User requester;
            try { requester = UserService.getUserById(req.getUserId()); }
            catch (Exception e) { return; }

            String subject = "Payment Approval Required — " + req.getBookTitle();
            String body = "Member "  + requester.getUserId() + " (" + requester.getFullName() + ")"
                    + " has submitted a fine-payment request that requires your approval.\n\n"
                    + "  Book  : " + req.getBookTitle() + "\n"
                    + "  Amount: " + com.example.application.ui.AppTheme.formatCurrency(req.getAmount()) + "\n"
                    + "  Total : " + com.example.application.ui.AppTheme.formatCurrency(req.getTotalFineAtRequest()) + "\n\n"
                    + "Please log in and navigate to Circulation → Settlements to approve or reject.";

            for (User s : staff) {
                try {
                    EmailService.sendEmail(s.getEmail(), subject, body);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to notify staff member "
                            + s.getUserId() + " of payment request: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Staff notification failed for payment request", e);
        }
    }
}