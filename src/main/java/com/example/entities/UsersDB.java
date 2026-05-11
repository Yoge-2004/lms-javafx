package com.example.entities;

import com.example.exceptions.UserException;
import com.example.storage.AppPaths;
import com.example.storage.DataStorage;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code UsersDB} is a thread-safe singleton repository for managing all user accounts.
 *
 * <p>It handles the lifecycle of {@link User} objects, ensuring that data is persisted 
 * to the {@code users_db.ser} file whenever a user is added, updated, or removed.</p>
 */
public final class UsersDB implements Serializable {
    private static final long serialVersionUID = 3L; // Bumped for fixes

    private static final Logger LOGGER = Logger.getLogger(UsersDB.class.getName());
    private static final String USERS_DB_FILE = "users_db.ser";

    private static volatile UsersDB instance;
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Concrete implementation is LinkedHashMap which IS Serializable;
    // @SuppressWarnings avoids the [serial] lint on the Map interface declaration.
    @SuppressWarnings("serial")
    private final Map<String, User> users;
    private transient boolean autoSave = true;

    // FIXED: Track if roles were explicitly assigned to prevent override
    private boolean rolesInitialized = false;

    private UsersDB() {
        this.users = new LinkedHashMap<>();
        // NOTE: Do NOT call loadUsersFromStorage() here.
        // getInstance() already reads from disk via DataStorage.readSerialized() and
        // only calls new UsersDB() when that returns null (i.e., no file exists yet).
        // Calling loadUsersFromStorage() in the constructor would read the file a
        // second time (double load) and — worse — would try to read a non-existent
        // file every time a truly fresh database is created, logging a spurious warning.
    }

    /**
     * Gets the singleton instance with thread-safe lazy initialization.
     *
     * @return the singleton instance
     */
    public static UsersDB getInstance() {
        if (instance == null) {
            synchronized (UsersDB.class) {
                if (instance == null) {
                    try {
                        instance = DataStorage.readSerialized(dataFile(), UsersDB.class);
                        if (instance == null) {
                            instance = new UsersDB();
                            LOGGER.log(Level.INFO, "Created new UsersDB instance");
                        } else {
                            instance.initializeAfterDeserialization();
                            LOGGER.log(Level.INFO, "Loaded existing UsersDB with {0} users",
                                    instance.users.size());
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to load users database, creating new instance", e);
                        instance = new UsersDB();
                    }
                }
            }
        }
        return instance;
    }

    /**
     * FIXED: Initialize transient fields after deserialization.
     * Only ensure admin exists on first creation, not on every load.
     */
    private void initializeAfterDeserialization() {
        if (users == null) {
            throw new IllegalStateException("Users map cannot be null after deserialization");
        }
        autoSave = true;
        // FIXED: Only ensure admin on fresh creation, not on load
        // This prevents demoted users from being re-promoted on restart
        if (!rolesInitialized && users.isEmpty()) {
            ensureAdminUserExists();
        }
    }

    /**
     * Authenticates a user with the provided credentials.
     *
     * @param userId the user ID
     * @param password the password
     * @return true if authentication is successful
     */
    public boolean authenticate(String userId, String password) {
        if (userId == null || password == null) {
            LOGGER.log(Level.WARNING, "Authentication attempted with null credentials");
            return false;
        }

        // BUG FIX: Use a write lock instead of a read lock here.
        // User.checkPassword() can mutate the User object when it performs legacy
        // plaintext-to-hash password migration. Mutating shared state while only
        // holding a read lock is a thread-safety violation that can cause data
        // corruption under concurrent access. A write lock guarantees exclusive access.
        lock.writeLock().lock();
        try {
            User user = users.get(userId.trim());
            boolean isAuthenticated = user != null && user.isActive() && user.checkPassword(password);

            LOGGER.log(Level.FINE, "Authentication for user {0}: {1}",
                    new Object[]{userId, isAuthenticated ? "SUCCESS" : "FAILURE"});

            return isAuthenticated;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds a new user with validation.
     *
     * @param userId the user ID
     * @param password the password
     * @throws UserException if user already exists or addition fails
     */
    public void addUser(String userId, String password) throws UserException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new UserException("User ID cannot be empty");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new UserException("Password cannot be empty");
        }

        String trimmedUserId = userId.trim();

        lock.writeLock().lock();
        try {
            if (users.containsKey(trimmedUserId)) {
                throw new UserException("User already exists: " + trimmedUserId);
            }

            User newUser = new User(trimmedUserId, password);
            users.put(trimmedUserId, newUser);

            // FIXED: Only auto-promote on very first user creation, not every add
            if (users.size() == 1) {
                ensureAdminUserExists();
            }

            rolesInitialized = true;

            LOGGER.log(Level.INFO, "User added successfully: {0}", trimmedUserId);

            if (autoSave) {
                saveUsersToStorage();
            }
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to add user: " + trimmedUserId, e);
            throw new UserException("Failed to add user: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds a user object directly.
     *
     * @param user the user to add
     * @throws UserException if user is null or addition fails
     */
    public void addUser(User user) throws UserException {
        if (user == null) {
            throw new UserException("User cannot be null");
        }

        String userId = user.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            throw new UserException("User ID cannot be empty");
        }

        lock.writeLock().lock();
        try {
            if (users.containsKey(userId.trim())) {
                throw new UserException("User already exists: " + userId.trim());
            }
            users.put(userId.trim(), user);

            // FIXED: Only auto-promote on very first user
            if (users.size() == 1) {
                ensureAdminUserExists();
            }

            rolesInitialized = true;
            LOGGER.log(Level.INFO, "User object added successfully: {0}", userId);

            if (autoSave) {
                saveUsersToStorage();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to add user object: " + userId, e);
            throw new UserException("Failed to add user: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves a user by ID.
     *
     * @param userId the user ID
     * @return the user or null if not found
     */
    public User getUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        lock.readLock().lock();
        try {
            return users.get(userId.trim());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets an immutable collection of all users.
     *
     * @return unmodifiable collection of users
     */
    public Collection<User> getUsers() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(new ArrayList<>(users.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets a list of all users.
     *
     * @return list of all users
     */
    public List<User> getAllUsers() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(users.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Updates an existing user.
     *
     * @param user the user with updated information
     * @throws UserException if user doesn't exist or update fails
     */
    public void updateUser(User user) throws UserException {
        if (user == null) {
            throw new UserException("User cannot be null");
        }

        String userId = user.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            throw new UserException("User ID cannot be empty");
        }

        String trimmedUserId = userId.trim();

        lock.writeLock().lock();
        try {
            if (!users.containsKey(trimmedUserId)) {
                throw new UserException("User not found: " + trimmedUserId);
            }

            users.put(trimmedUserId, user);
            rolesInitialized = true;
            LOGGER.log(Level.INFO, "User updated successfully: {0}", trimmedUserId);

            if (autoSave) {
                saveUsersToStorage();
            }
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update user: " + trimmedUserId, e);
            throw new UserException("Failed to update user: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a user by ID.
     *
     * @param userId the user ID to remove
     * @throws UserException if removal fails
     */
    public void removeUser(String userId) throws UserException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new UserException("User ID cannot be empty");
        }

        String trimmedUserId = userId.trim();

        lock.writeLock().lock();
        try {
            User existingUser = users.get(trimmedUserId);
            if (existingUser != null && existingUser.isAdmin() && getAdminCount() <= 1) {
                throw new UserException("At least one administrator must remain in the system");
            }

            User removedUser = users.remove(trimmedUserId);
            if (removedUser != null) {
                LOGGER.log(Level.INFO, "User removed successfully: {0}", trimmedUserId);
            } else {
                LOGGER.log(Level.WARNING, "Attempted to remove non-existent user: {0}", trimmedUserId);
            }

            if (autoSave) {
                saveUsersToStorage();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to remove user: " + trimmedUserId, e);
            throw new UserException("Failed to remove user: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets the total number of users.
     *
     * @return user count
     */
    public int getUserCount() {
        lock.readLock().lock();
        try {
            return users.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if a user exists.
     *
     * @param userId the user ID to check
     * @return true if user exists
     */
    public boolean userExists(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }

        lock.readLock().lock();
        try {
            return users.containsKey(userId.trim());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Forces persistence of the database regardless of auto-save setting.
     *
     * @throws IOException if persistence fails
     */
    public void forcePersist() throws IOException {
        lock.readLock().lock();
        try {
            DataStorage.writeSerialized(dataFile(), this);
            LOGGER.log(Level.INFO, "Users database forcibly persisted");
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Sets the auto-save behavior.
     *
     * @param autoSave true to enable auto-save on modifications
     */
    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
        LOGGER.log(Level.INFO, "Auto-save set to: {0}", autoSave);
    }

    public boolean hasUsers() {
        lock.readLock().lock();
        try {
            return !users.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears all users (with confirmation for safety).
     *
     * @param confirmation must be "CONFIRM_CLEAR_ALL"
     * @throws UserException if confirmation doesn't match
     */
    public void clearAllUsers(String confirmation) throws UserException {
        if (!"CONFIRM_CLEAR_ALL".equals(confirmation)) {
            throw new UserException("Invalid confirmation for clearing all users");
        }

        lock.writeLock().lock();
        try {
            int userCount = users.size();
            users.clear();
            LOGGER.log(Level.WARNING, "Cleared all {0} users from database", userCount);

            if (autoSave) {
                saveUsersToStorage();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to clear all users", e);
            throw new UserException("Failed to clear users: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void forceReload() {
        lock.writeLock().lock();
        try {
            users.clear();
            rolesInitialized = false;
            // Re-read from disk using the current AppConfiguration's branch path.
            // After selectKnownLibrary() updates AppConfigurationService.getConfiguration(),
            // dataFile() now resolves to the correct branch-scoped directory.
            try {
                UsersDB fromDisk = DataStorage.readSerialized(dataFile(), UsersDB.class);
                if (fromDisk != null && fromDisk.users != null) {
                    users.putAll(fromDisk.users);
                    this.rolesInitialized = fromDisk.rolesInitialized;
                    LOGGER.log(Level.INFO, "UsersDB force-reloaded: {0} users from {1}",
                            new Object[]{users.size(), dataFile()});
                } else {
                    LOGGER.log(Level.INFO, "UsersDB force-reload: no file at {0}, starting empty", dataFile());
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "UsersDB force-reload failed, keeping empty state", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Loads users from storage during initialization.
     */
    private void loadUsersFromStorage() {
        try {
            UsersDB loadedInstance = DataStorage.readSerialized(dataFile(), UsersDB.class);
            if (loadedInstance != null && loadedInstance.users != null) {
                users.putAll(loadedInstance.users);
                this.rolesInitialized = loadedInstance.rolesInitialized;
                // FIXED: Don't ensure admin on load - respect saved roles
                LOGGER.log(Level.INFO, "Loaded {0} users from storage", users.size());
            } else {
                LOGGER.log(Level.INFO, "No existing user data found, starting with empty database");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load users from storage, starting fresh", e);
        }
    }

    /**
     * Saves users to storage.
     */
    private void saveUsersToStorage() {
        try {
            DataStorage.writeSerialized(dataFile(), this);
            LOGGER.log(Level.FINE, "Users database saved successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save users database", e);
        }
    }

    private static String dataFile() {
        return AppPaths.resolveDataFile(USERS_DB_FILE).toString();
    }

    /**
     * Custom serialization method.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    /**
     * Custom deserialization method.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initializeAfterDeserialization();
    }

    // Legacy method names for backward compatibility

    /**
     * @deprecated Use {@link #removeUser(String)} instead
     */
    @Deprecated
    public void remove(String userId) {
        try {
            removeUser(userId);
        } catch (UserException e) {
            LOGGER.log(Level.WARNING, "Legacy remove method failed", e);
        }
    }

    @Override
    public String toString() {
        return String.format("UsersDB{userCount=%d, autoSave=%s}", getUserCount(), autoSave);
    }

    /**
     * FIXED: Only promotes first user to admin if this is a fresh database (no users yet).
     * Does not override explicit role assignments on subsequent loads.
     */
    private void ensureAdminUserExists() {
        if (users.isEmpty()) {
            return;
        }

        boolean hasAdmin = users.values().stream().anyMatch(User::isAdmin);
        if (!hasAdmin && users.size() == 1) {
            // Only auto-promote if this is the very first user ever created
            User firstUser = users.values().iterator().next();
            firstUser.setRole(UserRole.ADMIN);
            LOGGER.log(Level.INFO, "Promoted first user {0} to ADMIN", firstUser.getUserId());
        }
    }

    private long getAdminCount() {
        return users.values().stream().filter(User::isAdmin).count();
    }

}