package com.example.services;

import com.example.entities.User;
import com.example.entities.UserRole;
import com.example.entities.UsersDB;
import com.example.entities.BorrowRequest;
import com.example.exceptions.UserException;
import com.example.exceptions.ValidationException;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code UserService} handles user-related business operations, including 
 * authentication, registration, and user management.
 * 
 * <p>It provides the bridge between the UI and the {@link UsersDB} persistence layer.
 * Security features like salted password verification are orchestrated through 
 * this service.</p>
 */
public final class UserService {

    private static final Logger LOGGER = Logger.getLogger(UserService.class.getName());

    private static UsersDB userDB() { return UsersDB.getInstance(); }
    private static String currentUser = null;

    // Validation constants
    /** Minimum allowed length for a username/user ID. */
    private static final int MIN_USERNAME_LENGTH = 3;
    /** Maximum allowed length for a username/user ID. */
    private static final int MAX_USERNAME_LENGTH = 50;
    /** Minimum allowed length for a plain-text password. */
    private static final int MIN_PASSWORD_LENGTH = 4;
    /** Maximum allowed length for a plain-text password. */
    private static final int MAX_PASSWORD_LENGTH = 100;

    // Private constructor to prevent instantiation
    private UserService() {
        throw new UnsupportedOperationException("Service class cannot be instantiated");
    }

    /**
     * Authenticates a user with the provided credentials.
     *
     * @param userId   the username
     * @param password the password
     * @return true if credentials are valid
     * @throws ValidationException if input validation fails
     * @throws UserException if authentication operation fails
     */
    public static boolean login(String userId, String password) throws ValidationException {
        validateLoginCredentials(userId, password);

        try {
            boolean isAuthenticated = userDB().authenticate(userId, password);
            if (isAuthenticated) {
                currentUser = userId.trim();
                // Portability Fix: Unlock personal profile for roaming access
                User user = getUserById(currentUser);
                user.unlockProfile(password);
            }
            LOGGER.log(Level.INFO, "Authentication attempt for user: {0} - {1}",
                    new Object[]{userId, isAuthenticated ? "SUCCESS" : "FAILURE"});
            return isAuthenticated;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Authentication error for user: " + userId, e);
            throw new UserException("Authentication system error", e);
        }
    }

    public static String getCurrentUserId() {
        return currentUser;
    }

    public static void logout() {
        currentUser = null;
    }

    /**
     * Creates a new user with the provided credentials.
     *
     * @param userId   the username
     * @param password the password
     * @throws ValidationException if validation fails
     * @throws UserException if user creation fails
     */
    public static void createUser(String userId, String password) throws ValidationException {
        createUser(userId, password, UserRole.USER);
    }

    public static void createUser(String userId, String password, UserRole role) throws ValidationException {
        validateUserCreationInput(userId, password);

        try {
            User user = new User(userId.trim(), password);
            user.setRole(role);
            
            // Portability Fix: Immediately lock profile with user password 
            // so the first save to disk is standalone and portable.
            user.unlockProfile(password);
            
            // Mandatory approval for staff roles if system is already initialized
            if (role != UserRole.USER && userDB().hasUsers()) {
                user.setActive(false);
                LOGGER.log(Level.INFO, "Staff user {0} created in PENDING APPROVAL state", userId);
            } else {
                user.setActive(true);
            }
            
            userDB().addUser(user);
            LOGGER.log(Level.INFO, "User created successfully: {0}", userId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create user: " + userId, e);
            if (e instanceof UserException) {
                throw e;
            }
            throw new UserException("Failed to create user: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a user by username.
     *
     * @param userId the username
     * @return User object
     * @throws UserException if user not found or retrieval fails
     */
    public static User getUserById(String userId) throws UserException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("User ID cannot be empty");
        }

        try {
            User user = userDB().getUser(userId.trim());
            if (user == null) {
                throw new UserException("User not found: " + userId);
            }
            return user;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve user: " + userId, e);
            if (e instanceof UserException) {
                throw e;
            }
            throw new UserException("Failed to retrieve user: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing user's information.
     *
     * @param user the user object with updated data
     * @throws UserException if user does not exist or update fails
     */
    public static void updateUser(User user) throws UserException {
        if (user == null) {
            throw new UserException("User object cannot be null");
        }

        validateUserData(user);

        try {
            // Security check: cannot demote/deactivate the last active admin
            User existing = userDB().getUser(user.getUserId());
            if (existing != null && existing.getRole().isAdmin() && existing.isActive()) {
                // If we are trying to change role or deactivate
                if (user.getRole() != UserRole.ADMIN || !user.isActive()) {
                    long adminCount = getAllUsers().stream()
                            .filter(u -> u.getRole().isAdmin() && u.isActive())
                            .count();
                    if (adminCount <= 1) {
                        throw new UserException("Cannot demote or deactivate the last active administrator.");
                    }
                }
            }

            userDB().updateUser(user);
            LOGGER.log(Level.INFO, "User account updated: {0} (Role: {1}, Active: {2})",
                    new Object[]{user.getUserId(), user.getRole(), user.isActive()});
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update user: " + user.getUserId(), e);
            if (e instanceof UserException) {
                throw (UserException) e;
            }
            throw new UserException("Failed to update user: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a user by username.
     *
     * @param userId the username
     * @throws UserException if deletion fails
     */
    public static void deleteUser(String userId) throws UserException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("User ID cannot be empty");
        }

        try {
            User target = getUserById(userId.trim());
            // Security check: at least one active ADMIN must remain
            if (target != null && target.isAdmin() && target.isActive()) {
                long activeAdminCount = getAllUsers().stream()
                        .filter(u -> u.isAdmin() && u.isActive())
                        .count();
                if (activeAdminCount <= 1) {
                    throw new UserException("Security Violation: This is the last active administrator account. " +
                            "You must promote or activate another administrator before removing this one.");
                }
            }

            assertAccountCanBeRemoved(userId.trim());
            userDB().removeUser(userId.trim());
            LOGGER.log(Level.INFO, "User account PERMANENTLY DELETED: {0} (Target was Admin: {1})",
                    new Object[]{userId, target != null && target.isAdmin()});
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete user: " + userId, e);
            throw new UserException("Failed to delete user: " + e.getMessage(), e);
        }
    }

    public static void deleteOwnAccount(String userId, String password) throws UserException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("User ID cannot be empty");
        }
        if (password == null || password.isBlank()) {
            throw new ValidationException("Current password is required");
        }

        User user = getUserById(userId.trim());
        if (!user.checkPassword(password)) {
            throw new ValidationException("Current password is incorrect");
        }

        deleteUser(userId.trim());
    }

    /**
     * Retrieves a defensive copy of all users.
     *
     * @return immutable list of all registered users
     */
    public static List<User> getAllUsers() {
        try {
            return List.copyOf(userDB().getUsers());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve all users", e);
            throw new UserException("Failed to retrieve users: " + e.getMessage(), e);
        }
    }

    public static List<User> searchUsers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllUsers();
        }
        String q = query.trim().toLowerCase();
        return getAllUsers().stream()
                .filter(u -> u.getUserId().toLowerCase().contains(q) || 
                            (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)))
                .toList();
    }

    /**
     * Gets the current user count.
     *
     * @return number of registered users
     */
    public static int getUserCount() {
        try {
            return userDB().getUsers().size();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get user count", e);
            return 0;
        }
    }

    public static boolean hasRegisteredUsers() {
        try {
            return userDB().hasUsers();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to determine whether users exist", e);
            return false;
        }
    }

    /**
     * FIXED: Added proper exception handling to prevent crashes when user not found.
     * Returns null instead of throwing exception for safer UI consumption.
     *
     * @param userId the user ID
     * @return UserRole or null if user not found
     */
    public static UserRole getUserRole(String userId) {
        try {
            return getUserById(userId).getRole();
        } catch (UserException e) {
            LOGGER.log(Level.WARNING, "Failed to get role for user: " + userId, e);
            return null;
        }
    }

    /**
     * FIXED: Added proper exception handling to prevent crashes when user not found.
     *
     * @param userId the user ID
     * @return true if user is admin, false if not found or not admin
     */
    public static boolean isAdmin(String userId) {
        try {
            UserRole role = getUserRole(userId);
            return role != null && role.isAdmin();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check admin status for user: " + userId, e);
            return false;
        }
    }

    /**
     * Checks if a user exists.
     *
     * @param userId the username to check
     * @return true if user exists
     */
    public static boolean userExists(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }

        try {
            return userDB().getUser(userId.trim()) != null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking user existence: " + userId, e);
            return false;
        }
    }

    public static boolean emailExists(String email) {
        return findUserByEmail(email).isPresent();
    }

    public static Optional<User> findUserByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalized = email.trim().toLowerCase();
        try {
            return userDB().getUsers().stream()
                    .filter(Objects::nonNull)
                    .filter(user -> {
                        String stored = user.getEmail();
                        if (stored == null) return false;
                        // Fast path: email was set in this session (plaintext) or is trivially equal
                        if (stored.equalsIgnoreCase(normalized)) return true;
                        // Slow path: email is stored as ciphertext (loaded from disk without
                        // unlockProfile). Decrypt with master-key fallback so the uniqueness
                        // check works correctly for persisted users (fix for branch-isolation
                        // check #3 — without this, duplicate emails slip through).
                        String decrypted = com.example.services.SecurityProvider
                                .decryptUserField(stored, user.getUserId(), "", "");
                        return decrypted != null && decrypted.equalsIgnoreCase(normalized);
                    })
                    .findFirst();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to search by email: " + email, e);
            return Optional.empty();
        }
    }

    /**
     * Persists users database to permanent storage.
     *
     * @throws IOException if persistence operation fails
     */
    public static void persistDatabase() throws IOException {
        try {
            userDB().forcePersist();
            LOGGER.log(Level.INFO, "User database persisted successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to persist user database", e);
            throw new IOException("Failed to persist users database: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts that a user account is in a valid state to be removed from the system.
     * <p>A user cannot be deleted if they have active book loans or pending 
     * borrowing requests, as this would orphan circulation records.</p>
     * 
     * @param userId the user ID to check
     * @throws UserException if the user has active commitments
     */
    private static void assertAccountCanBeRemoved(String userId) {
        if (!BookService.getUserActiveIssueRecords(userId).isEmpty()) {
            throw new UserException("Return all issued books before deleting this account");
        }

        long pendingRequests = BookService.getBorrowRequestsForUser(userId).stream()
                .filter(BorrowRequest::isPending)
                .count();
        if (pendingRequests > 0) {
            throw new UserException("Resolve pending borrow requests before deleting this account");
        }
    }

    // Private validation methods

    /**
     * Validates that login credentials are not null or empty strings.
     * 
     * @param userId the provided user ID
     * @param password the provided password
     * @throws ValidationException if any field is blank
     */
    private static void validateLoginCredentials(String userId, String password) throws ValidationException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("Username cannot be empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new ValidationException("Password cannot be empty");
        }
    }

    /**
     * Performs strict validation on user ID and password during account creation.
     * <p>Checks for minimum/maximum lengths and ensures the username only 
     * contains safe characters.</p>
     * 
     * @param userId the proposed user ID
     * @param password the proposed password
     * @throws ValidationException if the data violates system constraints
     */
    private static void validateUserCreationInput(String userId, String password) throws ValidationException {
        validateLoginCredentials(userId, password);

        String trimmedUserId = userId.trim();

        if (trimmedUserId.length() < MIN_USERNAME_LENGTH) {
            throw new ValidationException("Username must be at least " + MIN_USERNAME_LENGTH + " characters");
        }

        if (trimmedUserId.length() > MAX_USERNAME_LENGTH) {
            throw new ValidationException("Username cannot exceed " + MAX_USERNAME_LENGTH + " characters");
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new ValidationException("Password cannot exceed " + MAX_PASSWORD_LENGTH + " characters");
        }

        if (!trimmedUserId.matches("^[a-zA-Z0-9._-]+$")) {
            throw new ValidationException("Username can only contain letters, numbers, dots, underscores, and hyphens");
        }
    }

    /**
     * Validates deep user data including email formats and contact numbers.
     * <p>This method also checks for duplicate email or phone numbers across 
     * the entire database to maintain record integrity.</p>
     * 
     * @param user the user object to validate
     * @throws UserException if formats are invalid or duplicates are found
     */
    private static void validateUserData(User user) throws UserException {
        if (user.getUserId() == null || user.getUserId().trim().isEmpty()) {
            throw new UserException("User ID cannot be empty");
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new UserException("Password cannot be empty");
        }

        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            String email = user.getEmail().trim();
            if (!email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")) {
                throw new UserException("Invalid email format");
            }

            Optional<User> duplicate = userDB().getUsers().stream()
                    .filter(Objects::nonNull)
                    .filter(existing -> {
                        String stored = existing.getEmail();
                        if (stored == null) return false;
                        if (stored.equalsIgnoreCase(email)) return true;
                        // Decrypt ciphertext from disk before comparing (fix #3)
                        String dec = com.example.services.SecurityProvider
                                .decryptUserField(stored, existing.getUserId(), "", "");
                        return dec != null && dec.equalsIgnoreCase(email);
                    })
                    .filter(existing -> !existing.getUserId().equalsIgnoreCase(user.getUserId()))
                    .findFirst();
            if (duplicate.isPresent()) {
                throw new UserException("Email address is already used by " + duplicate.get().getUserId());
            }
        }

        if (user.getContactNumber() != null && !user.getContactNumber().trim().isEmpty()) {
            String contact = user.getContactNumber().trim();
            if (!contact.matches("^[+]?[0-9]{10,15}$")) {
                throw new UserException("Invalid contact number format");
            }

            Optional<User> duplicateContact = userDB().getUsers().stream()
                    .filter(Objects::nonNull)
                    .filter(existing -> {
                        String stored = existing.getContactNumber();
                        if (stored == null) return false;
                        if (stored.equalsIgnoreCase(contact)) return true;
                        // Decrypt ciphertext from disk before comparing (fix #3)
                        String dec = com.example.services.SecurityProvider
                                .decryptUserField(stored, existing.getUserId(), "", "");
                        return dec != null && dec.equalsIgnoreCase(contact);
                    })
                    .filter(existing -> !existing.getUserId().equalsIgnoreCase(user.getUserId()))
                    .findFirst();
            if (duplicateContact.isPresent()) {
                throw new UserException("Contact number is already used by " + duplicateContact.get().getUserId());
            }
        }
    }

    /**
     * @deprecated Use {@link #createUser(String, String)} instead
     */
    @Deprecated
    public static void create(String userId, String password) throws ValidationException {
        createUser(userId, password);
    }

    /**
     * @deprecated Use {@link #getUserById(String)} instead
     */
    @Deprecated
    public static User getUser(String userId) throws UserException {
        return getUserById(userId);
    }

    /**
     * @deprecated Use {@link #updateUser(User)} instead
     */
    @Deprecated
    public static void update(User user) throws UserException {
        updateUser(user);
    }

    /**
     * @deprecated Use {@link #deleteUser(String)} instead
     */
    @Deprecated
    public static void delete(String userId) {
        try {
            deleteUser(userId);
        } catch (UserException e) {
            LOGGER.log(Level.WARNING, "Delete operation failed for legacy method", e);
        }
    }

    /**
     * @deprecated Use {@link #getAllUsers()} instead
     */
    @Deprecated
    public static List<User> getUsers() {
        Collection<User> users = userDB().getUsers();
        return new ArrayList<>(users);
    }

    /**
     * @deprecated Use {@link #persistDatabase()} instead
     */
    @Deprecated
    public static void persist() throws IOException {
        persistDatabase();
    }

    public static void seedSampleData() {
        String[][] users = {
            {"staff_alice", "password123", "LIBRARIAN", "Alice Smith", "alice@library.os"},
            {"staff_bob", "password123", "LIBRARIAN", "Bob Johnson", "bob@library.os"},
            {"admin_charlie", "password123", "RESTRICTED_ADMIN", "Charlie Admin", "charlie@library.os"},
            {"user_dave", "password123", "USER", "Dave Member", "dave@gmail.com"},
            {"user_eve", "password123", "USER", "Eve Reading", "eve@yahoo.com"}
        };

        for (String[] u : users) {
            try {
                if (!userExists(u[0])) {
                    createUser(u[0], u[1], com.example.entities.UserRole.valueOf(u[2]));
                    User user = getUserById(u[0]);
                    user.setFirstName(u[3].split(" ")[0]);
                    user.setLastName(u[3].split(" ")[1]);
                    user.setEmail(u[4]);
                    user.setActive(true);
                    updateUser(user);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to apply seeded user row — row skipped: " + e.getMessage(), e);
            }
        }
        try {
            persistDatabase();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Database persist failed after user seed — data may not have been saved", e);
        }
    }
}