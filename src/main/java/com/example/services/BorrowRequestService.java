package com.example.services;

import com.example.entities.Book;
import com.example.entities.BorrowRequest;
import com.example.entities.User;
import com.example.exceptions.BooksException;
import com.example.exceptions.UserException;
import com.example.storage.AppPaths;
import com.example.storage.DataStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The {@code BorrowRequestService} manages the workflow for book borrowing requests 
 * that require administrative approval.
 *
 * <p>Workflow:
 * <ol>
 *   <li>User submits a request for a book.</li>
 *   <li>Request is stored in a pending state.</li>
 *   <li>Librarian reviews, then approves or rejects the request.</li>
 *   <li>Upon approval, the book is automatically issued via {@link BookService}.</li>
 * </ol>
 */
public final class BorrowRequestService {
    private static final Logger LOGGER = Logger.getLogger(BorrowRequestService.class.getName());
    private static final String REQUESTS_FILE = "borrow_requests.ser";
    private static final String ARCHIVED_REQUESTS_FILE = "borrow_requests_archive.ser";
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // FIXED: Maximum limits to prevent memory exhaustion
    private static final int MAX_ACTIVE_REQUESTS = 10000;
    private static final int MAX_REQUESTS_PER_USER = 50;
    private static final int ARCHIVE_THRESHOLD = 5000; // Archive when exceeding this

    private static List<BorrowRequest> requests = loadRequests();
    private static List<BorrowRequest> archivedRequests = loadArchivedRequests();

    private BorrowRequestService() {
    }

    public static BorrowRequest createRequest(String isbn, String userId, int quantity) {
        if (quantity <= 0) {
            throw new BooksException("Quantity must be positive");
        }

        // FIXED: Check global limit before creating
        if (getPendingRequestCount() >= MAX_ACTIVE_REQUESTS) {
            throw new BooksException("System request queue is full. Please try again later.");
        }

        Book book = BookService.getBookByIsbn(isbn);
        if (book == null) {
            throw new BooksException("Book not found: " + isbn);
        }

        // BUG FIX: UserService.getUserById() always throws UserException when the user
        // is not found — it never returns null. The previous null-check was therefore
        // unreachable dead code that silently swallowed the real exception. We now let
        // the UserException propagate naturally, which gives callers an accurate error.
        User user = UserService.getUserById(userId);

        // FIXED: Check per-user limit
        long userRequestCount = getRequestsForUser(userId).size();
        if (userRequestCount >= MAX_REQUESTS_PER_USER) {
            throw new BooksException("You have reached the maximum number of requests (" + MAX_REQUESTS_PER_USER +
                    "). Please wait for existing requests to be processed or cancel pending ones.");
        }

        BorrowRequest request = new BorrowRequest(book.getIsbn(), book.getTitle(), user.getUserId(), quantity);
        lock.writeLock().lock();
        try {
            requests.add(request);

            // FIXED: Auto-archive if exceeding threshold to prevent memory leak
            if (requests.size() > ARCHIVE_THRESHOLD) {
                archiveOldRequests();
            }

            saveRequests();
            LOGGER.log(Level.INFO, "New borrow request created: ID={0}, ISBN={1}, User={2}, Qty={3}",
                    new Object[]{request.getRequestId(), isbn, userId, quantity});
            return request;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static List<BorrowRequest> getAllRequests() {
        lock.readLock().lock();
        try {
            return requests.stream()
                    .sorted(Comparator.comparing(BorrowRequest::getRequestedAt).reversed())
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public static List<BorrowRequest> getPendingRequests() {
        return getAllRequests().stream()
                .filter(BorrowRequest::isPending)
                .collect(Collectors.toList());
    }

    public static List<BorrowRequest> getPendingRequestsForBooks(List<String> isbns) {
        if (isbns == null || isbns.isEmpty()) return new ArrayList<>();
        return getPendingRequests().stream()
                .filter(r -> isbns.contains(r.getIsbn()))
                .collect(Collectors.toList());
    }

    public static List<BorrowRequest> getRequestsForUser(String userId) {
        return getAllRequests().stream()
                .filter(request -> request.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    public static int getPendingRequestCount() {
        return getPendingRequests().size();
    }

    public static BorrowRequest getRequestById(String requestId) {
        lock.readLock().lock();
        try {
            return requests.stream()
                    .filter(r -> r.getRequestId().equals(requestId))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    public static void approveRequest(String requestId, String processedBy) {
        lock.writeLock().lock();
        try {
            BorrowRequest request = findPendingRequest(requestId);
            BookService.issueBookToUser(request.getIsbn(), request.getUserId(), request.getQuantity());
            request.approve(processedBy);
            saveRequests();
            LOGGER.log(Level.INFO, "Borrow request APPROVED: ID={0}, ISBN={1}, User={2}, ProcessedBy={3}",
                    new Object[]{requestId, request.getIsbn(), request.getUserId(), processedBy});
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Marks a request as approved because it was manually fulfilled via Issue Book dialog.
     */
    public static void markRequestAsApproved(String requestId, String processedBy) {
        lock.writeLock().lock();
        try {
            BorrowRequest request = findPendingRequest(requestId);
            if (request != null) {
                request.approve(processedBy);
                saveRequests();
                LOGGER.log(Level.INFO, "Borrow request marked as MANUAL-APPROVED: ID={0}", requestId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void rejectRequest(String requestId, String processedBy, String note) {
        lock.writeLock().lock();
        try {
            BorrowRequest request = findPendingRequest(requestId);
            request.reject(processedBy, note == null || note.isBlank() ? "Rejected by staff" : note.trim());
            saveRequests();
            LOGGER.log(Level.INFO, "Borrow request REJECTED: ID={0}, ISBN={1}, User={2}, ProcessedBy={3}, Reason=''{4}''",
                    new Object[]{requestId, request.getIsbn(), request.getUserId(), processedBy, request.getNote()});
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static void persist() throws IOException {
        lock.readLock().lock();
        try {
            saveRequests();
        } finally {
            lock.readLock().unlock();
        }
    }

    public static void forceReload() {
        lock.writeLock().lock();
        try {
            requests = loadRequests();
            archivedRequests = loadArchivedRequests();
            LOGGER.log(Level.INFO, "Borrow requests forcibly reloaded from storage");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * FIXED: Archive old processed requests to prevent memory leak.
     * Moves APPROVED/REJECTED requests older than 30 days to archive file.
     */
    private static void archiveOldRequests() {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(30);

        List<BorrowRequest> toArchive = requests.stream()
                .filter(r -> !r.isPending())
                .filter(r -> r.getProcessedAt() != null && r.getProcessedAt().isBefore(cutoff))
                .collect(Collectors.toList());

        if (toArchive.isEmpty()) {
            // If no old requests, archive oldest 1000 processed requests anyway
            toArchive = requests.stream()
                    .filter(r -> !r.isPending())
                    .sorted(Comparator.comparing(BorrowRequest::getProcessedAt))
                    .limit(1000)
                    .collect(Collectors.toList());
        }

        if (!toArchive.isEmpty()) {
            archivedRequests.addAll(toArchive);
            requests.removeAll(toArchive);
            try {
                saveArchivedRequests();
                LOGGER.log(Level.INFO, "Archived {0} old requests", toArchive.size());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to archive requests", e);
                // Restore on failure
                requests.addAll(toArchive);
                archivedRequests.removeAll(toArchive);
            }
        }
    }

    private static BorrowRequest findPendingRequest(String requestId) {
        return requests.stream()
                .filter(request -> request.getRequestId().equals(requestId))
                .filter(BorrowRequest::isPending)
                .findFirst()
                .orElseThrow(() -> new BooksException("Pending request not found"));
    }

    @SuppressWarnings("unchecked")
    private static List<BorrowRequest> loadRequests() {
        try {
            List<BorrowRequest> loaded = DataStorage.readSerialized(dataFile(REQUESTS_FILE), List.class);
            return loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load borrow requests, starting empty", e);
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BorrowRequest> loadArchivedRequests() {
        try {
            List<BorrowRequest> loaded = DataStorage.readSerialized(dataFile(ARCHIVED_REQUESTS_FILE), List.class);
            return loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load archived requests", e);
            return new ArrayList<>();
        }
    }

    private static void saveRequests() {
        try {
            DataStorage.writeSerialized(dataFile(REQUESTS_FILE), new ArrayList<>(requests));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to persist borrow requests", e);
            throw new BooksException("Failed to persist borrow requests: " + e.getMessage(), e);
        }
    }

    private static void saveArchivedRequests() throws IOException {
        DataStorage.writeSerialized(dataFile(ARCHIVED_REQUESTS_FILE), new ArrayList<>(archivedRequests));
    }

    private static String dataFile(String fileName) {
        return AppPaths.resolveDataFile(fileName).toString();
    }
}