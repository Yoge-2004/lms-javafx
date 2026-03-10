package com.example.services;

import com.example.entities.User;
import com.example.entities.UsersDB;
import com.example.exceptions.UserException;
import com.example.exceptions.ValidationException;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service layer for user management operations with comprehensive validation
 * and error handling.
 */
public final class UserService {

    private static final Logger LOGGER = Logger.getLogger(UserService.class.getName());

    private static final UsersDB userDB = UsersDB.getInstance();

    // Validation constants
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MIN_PASSWORD_LENGTH = 4;
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
            boolean isAuthenticated = userDB.authenticate(userId, password);
            LOGGER.log(Level.INFO, "Authentication attempt for user: {0} - {1}",
                    new Object[]{userId, isAuthenticated ? "SUCCESS" : "FAILURE"});
            return isAuthenticated;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Authentication error for user: " + userId, e);
            throw new UserException("Authentication system error", e);
        }
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
        validateUserCreationInput(userId, password);

        try {
            userDB.addUser(userId, password);
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
            User user = userDB.getUser(userId.trim());
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
            userDB.updateUser(user);
            LOGGER.log(Level.INFO, "User updated successfully: {0}", user.getUserId());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update user: " + user.getUserId(), e);
            if (e instanceof UserException) {
                throw e;
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
            userDB.removeUser(userId.trim());
            LOGGER.log(Level.INFO, "User deleted successfully: {0}", userId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete user: " + userId, e);
            throw new UserException("Failed to delete user: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a defensive copy of all users.
     *
     * @return immutable list of all registered users
     */
    public static List<User> getAllUsers() {
        try {
            return List.copyOf(userDB.getUsers());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve all users", e);
            throw new UserException("Failed to retrieve users: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the current user count.
     *
     * @return number of registered users
     */
    public static int getUserCount() {
        try {
            return userDB.getUsers().size();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get user count", e);
            return 0;
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
            return userDB.getUser(userId.trim()) != null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking user existence: " + userId, e);
            return false;
        }
    }

    /**
     * Persists users database to permanent storage.
     *
     * @throws IOException if persistence operation fails
     */
    public static void persistDatabase() throws IOException {
        try {
            // The UsersDB automatically persists on changes, but this ensures consistency
            userDB.forcePersist();
            LOGGER.log(Level.INFO, "User database persisted successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to persist user database", e);
            throw new IOException("Failed to persist users database: " + e.getMessage(), e);
        }
    }

    // Private validation methods

    private static void validateLoginCredentials(String userId, String password) throws ValidationException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("Username cannot be empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new ValidationException("Password cannot be empty");
        }
    }

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

        // Check for valid username characters (alphanumeric and common symbols)
        if (!trimmedUserId.matches("^[a-zA-Z0-9._-]+$")) {
            throw new ValidationException("Username can only contain letters, numbers, dots, underscores, and hyphens");
        }
    }

    private static void validateUserData(User user) throws UserException {
        if (user.getUserId() == null || user.getUserId().trim().isEmpty()) {
            throw new UserException("User ID cannot be empty");
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new UserException("Password cannot be empty");
        }

        // Validate email format if provided
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            String email = user.getEmail().trim();
            if (!email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")) {
                throw new UserException("Invalid email format");
            }
        }

        // Validate contact number format if provided
        if (user.getContactNumber() != null && !user.getContactNumber().trim().isEmpty()) {
            String contact = user.getContactNumber().trim();
            if (!contact.matches("^[+]?[0-9]{10,15}$")) {
                throw new UserException("Invalid contact number format");
            }
        }
    }


    // Legacy method names for backward compatibility

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
        // Fixed version:
        Collection<User> users = userDB.getUsers();
        return new ArrayList<>(users);
    }

    /**
     * @deprecated Use {@link #persistDatabase()} instead
     */
    @Deprecated
    public static void persist() throws IOException {
        persistDatabase();
    }
}
