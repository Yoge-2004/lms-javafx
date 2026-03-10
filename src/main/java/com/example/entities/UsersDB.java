package com.example.entities;

import com.example.exceptions.UserException;
import com.example.storage.DataStorage;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe singleton database class for user management with enhanced error handling
 * and automatic persistence.
 */
public final class UsersDB implements Serializable {
    private static final long serialVersionUID = 2L; // Incremented for version tracking

    private static final Logger LOGGER = Logger.getLogger(UsersDB.class.getName());
    private static final String USERS_DB_FILE = "data/users_db.ser";

    private static volatile UsersDB instance;
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final Map<String, User> users;
    private transient boolean autoSave = true;

    private UsersDB() {
        this.users = new LinkedHashMap<>();
        loadUsersFromStorage();
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
                        instance = DataStorage.readSerialized(USERS_DB_FILE, UsersDB.class);
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
     * Initializes transient fields after deserialization.
     */
    private void initializeAfterDeserialization() {
        if (users == null) {
            throw new IllegalStateException("Users map cannot be null after deserialization");
        }
        autoSave = true;
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

        lock.readLock().lock();
        try {
            User user = users.get(userId.trim());
            boolean isAuthenticated = user != null && user.getPassword().equals(password);

            LOGGER.log(Level.FINE, "Authentication for user {0}: {1}",
                    new Object[]{userId, isAuthenticated ? "SUCCESS" : "FAILURE"});

            return isAuthenticated;
        } finally {
            lock.readLock().unlock();
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
            users.put(userId.trim(), user);
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
            DataStorage.writeSerialized(USERS_DB_FILE, this);
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

    /**
     * Loads users from storage during initialization.
     */
    private void loadUsersFromStorage() {
        try {
            UsersDB loadedInstance = DataStorage.readSerialized(USERS_DB_FILE, UsersDB.class);
            if (loadedInstance != null && loadedInstance.users != null) {
                users.putAll(loadedInstance.users);
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
            DataStorage.writeSerialized(USERS_DB_FILE, this);
            LOGGER.log(Level.FINE, "Users database saved successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save users database", e);
        }
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
}