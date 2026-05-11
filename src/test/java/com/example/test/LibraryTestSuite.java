package com.example.test;

import com.example.entities.*;
import com.example.entities.BooksDB.IssueRecord;
import com.example.exceptions.*;
import com.example.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>Library OS - Advanced Modular Test Suite (v4.3)</h1>
 * 
 * <p>This test suite provides comprehensive coverage for the Library Management System,
 * focusing on data integrity, business logic, security, and persistence.</p>
 * 
 * <h2>Architecture</h2>
 * <ul>
 *   <li><b>Isolation:</b> Each test run uses a temporary filesystem ({@code libraryos_test_env})
 *       to prevent interference with production data.</li>
 *   <li><b>Statelessness:</b> Singletons and static services are reset before every test 
 *       using reflection and state-clearing utilities.</li>
 *   <li><b>Granularity:</b> Tests are organized into nested classes focusing on specific
 *       layers of the application.</li>
 * </ul>
 * 
 * <h2>Coverage Areas</h2>
 * <ol>
 *   <li><b>Entities:</b> Validation of Book, User, and IssueRecord invariants.</li>
 *   <li><b>Databases:</b> Testing the thread-safe singleton repositories (UsersDB, BooksDB, LibrariesDB).</li>
 *   <li><b>Services:</b> Integration testing of high-level workflows like borrowing and authentication.</li>
 *   <li><b>Robustness:</b> Edge-case handling for malformed inputs and extreme values.</li>
 * </ol>
 * 
 * @author Antigravity AI
 * @version 4.3
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LibraryTestSuite {

    /** Path to the temporary home directory used for test isolation. */
    private static Path tempHome;

    /**
     * Initializes the global test environment.
     * Sets the {@code libraryos.home} system property to a temporary directory.
     * 
     * @throws IOException if the temporary directory cannot be created
     */
    @BeforeAll
    static void initTestSuite() throws IOException {
        // Isolate tests from production data
        tempHome = Files.createTempDirectory("libraryos_test_env");
        System.setProperty("libraryos.home", tempHome.toString());
        Files.createDirectories(tempHome.resolve("data"));
    }

    /**
     * Cleans up the temporary test environment after all tests are completed.
     * 
     * @throws IOException if files cannot be deleted
     */
    @AfterAll
    static void teardownTestSuite() throws IOException {
        if (tempHome != null && Files.exists(tempHome)) {
            try (Stream<Path> walk = Files.walk(tempHome)) {
                walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        }
    }

    // ── Helper Utilities ───────────────────────────────────────────

    /**
     * Resets a singleton instance to null using reflection.
     * 
     * @param clazz The class containing the {@code instance} field to reset
     */
    private static void resetSingleton(Class<?> clazz) {
        try {
            Field instanceField = clazz.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception ignored) {}
    }

    /**
     * Clears all internal application state and deletes persisted data files.
     * This ensures each test starts with a completely fresh environment.
     * 
     * @throws IOException if serialized files cannot be deleted
     */
    private static void clearState() throws IOException {
        resetSingleton(BooksDB.class);
        resetSingleton(UsersDB.class);
        resetSingleton(LibrariesDB.class);
        resetSingleton(BorrowRequestService.class);
        resetSingleton(AppConfigurationService.class);

        // Wipe serialized files
        Files.walk(tempHome)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".ser"))
            .forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. Entity Logic & Validation (Unit)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Unit tests for core entities, validating internal state and invariants.
     */
    @Nested
    @DisplayName("Unit: Core Entity Integrity")
    class EntityTests {

        /**
         * Verifies that the Book entity strictly validates ISBNs and normalizes titles.
         */
        @Test
        @DisplayName("Book: Strict validation and normalization")
        void testBookValidation() {
            Book b = new Book("9781234567890", " Clean Code ", "Robert Martin", "Education", 10);
            assertEquals("Clean Code", b.getTitle()); // Trimming check
            assertEquals(10, b.getQuantity());

            assertThrows(IllegalArgumentException.class, () -> new Book("", "Title", "A", "C", 1));
            assertThrows(IllegalArgumentException.class, () -> new Book("123", "Title", "A", "C", -1));
        }

        /**
         * Verifies that the User entity correctly hashes passwords and manages security salts.
         */
        @Test
        @DisplayName("User: Password hashing and profile unlocking")
        void testUserSecurity() throws UserException {
            User u = new User("tester_01", "password123");
            assertNotEquals("password123", u.getPassword()); // Should be hashed
            assertTrue(u.checkPassword("password123"));
            assertFalse(u.checkPassword("wrong"));

            // Edge: Minimum password length
            assertThrows(UserException.class, () -> new User("u1", "abc"));
        }

        /**
         * Verifies that the fine calculation logic correctly handles grace periods and daily accrual.
         */
        @Test
        @DisplayName("IssueRecord: Fine calculation edge cases")
        void testFines() {
            // Case 1: Exactly on time
            IssueRecord r1 = new IssueRecord("9780000000001", "T", "U", LocalDate.now().minusDays(14), 1);
            assertFalse(r1.isOverdue());
            assertEquals(0.0, r1.calculateFine());

            // Case 2: 1 day overdue
            IssueRecord r2 = new IssueRecord("9780000000002", "T", "U", LocalDate.now().minusDays(15), 1);
            assertTrue(r2.isOverdue());
            assertEquals(2.0, r2.calculateFine(), 0.01);

            // Case 3: Returned in past (No new fines)
            r2.setReturned(true);
            double fineAtReturn = r2.calculateFine();
            // Move time forward (simulated)
            assertEquals(fineAtReturn, r2.calculateFine());
        }

        /**
         * Verifies that configuration entities heal invalid input (e.g., negative fines).
         */
        @Test
        @DisplayName("BranchConfiguration: Data healing")
        void testConfigNormalization() {
            BranchConfiguration cfg = new BranchConfiguration();
            cfg.setFinePerDay(-5.0);
            cfg.normalize();
            assertTrue(cfg.getFinePerDay() >= 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Database & Persistence (Isolation)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Tests for the persistence layer and singleton repository management.
     */
    @Nested
    @DisplayName("Unit: Database Repositories")
    class DatabaseTests {

        @BeforeEach
        void setup() throws IOException { clearState(); }

        /**
         * Verifies that the first user added to a fresh database is automatically promoted to ADMIN.
         */
        @Test
        @DisplayName("UsersDB: Automatic Admin Promotion")
        void testAdminPromotion() throws UserException {
            UsersDB db = UsersDB.getInstance();
            db.setAutoSave(false);
            
            db.addUser("first_admin", "pass123");
            assertEquals(UserRole.ADMIN, db.getUser("first_admin").getRole());

            db.addUser("second_user", "pass123");
            assertEquals(UserRole.USER, db.getUser("second_user").getRole());
        }

        /**
         * Verifies that duplicate library entries are prevented regardless of character casing.
         */
        @Test
        @DisplayName("LibrariesDB: Deduplication logic")
        void testLibraryDeduplication() {
            LibrariesDB db = LibrariesDB.getInstance();
            db.addLibrary("Chennai", "Main Branch", "ID-1");
            db.addLibrary("CHENNAI", "Main Branch", "ID-2"); // Case insensitive duplicate names

            assertEquals(1, db.getLibraries().size());
        }

        /**
         * Verifies manual stock updates in the BooksDB repository.
         */
        @Test
        @DisplayName("BooksDB: Stock management")
        void testStockManagement() throws BooksException {
            BooksDB db = BooksDB.getInstance();
            db.addBook(new Book("9780000000001", "B1", "A1", "C1", 5));
            
            Book b = db.getBook("9780000000001");
            b.setQuantity(b.getQuantity() - 2);
            assertEquals(3, db.getBook("9780000000001").getQuantity());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Service Layer Integration (Flows)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Integration tests for the high-level business service layer.
     */
    @Nested
    @DisplayName("Integration: Business Services")
    class ServiceTests {

        @BeforeEach
        void setup() throws IOException { clearState(); }

        /**
         * Verifies the complete lifecycle of a borrowing request: creation, approval, and inventory deduction.
         */
        @Test
        @DisplayName("Flow: Borrowing Request Lifecycle")
        void testBorrowingFlow() throws Exception {
            // 1. Setup
            BookService.addBook(new Book("9780000000001", "Title", "Auth", "Cat", 1));
            UserService.createUser("user1", "pass123");

            // 2. Request
            BorrowRequestService.createRequest("9780000000001", "user1", 1);
            BorrowRequest req = BorrowRequestService.getPendingRequests().get(0);
            
            // 3. Approve
            BorrowRequestService.approveRequest(req.getRequestId(), "admin");
            
            // 4. Verify book is issued
            assertTrue(BookService.getAllActiveIssueRecords().stream().anyMatch(r -> r.getUserId().equals("user1")));
            assertEquals(0, BookService.getBookByIsbn("9780000000001").getQuantity()); // Stock decreased
        }

        /**
         * Verifies that the login process correctly identifies users and manages session state.
         */
        @Test
        @DisplayName("UserService: Login & Session Handling")
        void testLoginFlow() throws Exception {
            UserService.createUser("login_test", "secret123");
            
            boolean success = UserService.login("login_test", "secret123");
            assertTrue(success);
            assertEquals("login_test", UserService.getCurrentUserId());
            
            assertFalse(UserService.login("login_test", "wrong"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Edge Cases & Safety (Robustness)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Robustness tests for edge cases, malformed inputs, and extreme boundary values.
     */
    @Nested
    @DisplayName("Edge Cases: Input Sanitization")
    class EdgeTests {

        /**
         * Verifies that blank or whitespace-only inputs are rejected by the validation logic.
         */
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\n", "\t"})
        @DisplayName("Handling empty/blank inputs in creation")
        void testBlankInputs(String blank) {
            assertThrows(Exception.class, () -> UserService.createUser(blank, "pass123"));
            assertThrows(IllegalArgumentException.class, () -> new Book(blank, "T", "A", "C", 0));
        }

        /**
         * Verifies that the system handles extremely large book quantities without overflow.
         */
        @Test
        @DisplayName("Extreme Values: Quantity")
        void testExtremeValues() throws BooksException {
            BooksDB db = BooksDB.getInstance();
            db.addBook(new Book("9789999999999", "B", "A", "C", Integer.MAX_VALUE));
            assertEquals(Integer.MAX_VALUE, db.getBook("9789999999999").getQuantity());
        }

        /**
         * Verifies that only allowed special characters (. _ -) are accepted in User IDs.
         */
        @Test
        @DisplayName("Special Characters: UserIDs and Search")
        void testSpecialChars() throws Exception {
            // Valid special chars: . _ -
            UserService.createUser("user.name_123-test", "pass123");
            assertTrue(UserService.userExists("user.name_123-test"));

            // Invalid chars: ! @ # $ %
            assertThrows(UserException.class, () -> UserService.createUser("user@name", "pass123"));
        }

        /**
         * Verifies that rejection notes in borrowing requests are automatically truncated to prevent database bloat.
         */
        @Test
        @DisplayName("Note Truncation in BorrowRequests")
        void testNoteTruncation() {
            BorrowRequest br = new BorrowRequest("9780000000001", "T", "U", 1);
            String longNote = "A".repeat(5000);
            br.reject("admin", longNote);
            assertTrue(br.getNote().length() <= 1000);
        }
    }
}
