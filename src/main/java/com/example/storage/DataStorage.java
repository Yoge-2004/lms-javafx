package com.example.storage;

import com.example.entities.AppConfiguration;
import com.example.services.DatabaseConnectionService;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code DataStorage} utility provides a robust, thread-safe persistence layer for 
 * Java object serialization. 
 *
 * <p>It implements advanced file handling features to ensure data integrity:
 * <ul>
 *   <li><b>Atomic Writes:</b> Utilizes temporary files and atomic move operations 
 *       (where supported by the OS) to prevent data corruption during power failures.</li>
 *   <li><b>Write Strategies:</b> Automatically falls back through multiple move/copy 
 *       strategies to ensure compatibility with Windows, Linux, and network filesystems.</li>
 *   <li><b>Database Mirroring:</b> Synchronizes local serialized data with a centralized 
 *       database snapshot if a remote database connection is active.</li>
 *   <li><b>Concurrency Control:</b> Employs {@link java.util.concurrent.locks.ReentrantReadWriteLock}
 *        to allow multiple simultaneous readers while ensuring exclusive access for writers.</li>
 * </ul>
 */
public final class DataStorage {
    private static final Logger LOGGER = Logger.getLogger(DataStorage.class.getName());

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Private constructor to prevent instantiation
    private DataStorage() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Reads a serialized object from file with type safety.
     *
     * @param <T> the type of object to read
     * @param filename the file path
     * @param clazz the class type for casting
     * @return the deserialized object or null if file doesn't exist
     * @throws IOException if read operation fails
     * @throws ClassNotFoundException if class cannot be found during deserialization
     */
    public static <T> T readSerialized(String filename, Class<T> clazz)
            throws IOException, ClassNotFoundException {

        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        if (clazz == null) {
            throw new IllegalArgumentException("Class type cannot be null");
        }

        Path filePath = Paths.get(filename);

        // PRIORITY: Try database snapshot first if connected
        if (DatabaseConnectionService.isConnected()) {
            byte[] snapshot = DatabaseConnectionService.loadSnapshot(snapshotKey(filePath));
            if (snapshot != null) {
                LOGGER.log(Level.INFO, "Loaded {0} from primary database snapshot", filename);
                try {
                    return clazz.cast(deserialize(snapshot, clazz));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to deserialize DB snapshot for {0}, falling back to file", filename);
                }
            }
        }

        // FALLBACK: Use local file system
        // BUG FIX: The original code checked Files.exists(filePath) OUTSIDE the lock,
        // then acquired the lock to read. Between those two steps, another thread could
        // delete or replace the file (TOCTOU race). We now acquire the lock first so that
        // any concurrent write (which also holds the write lock) is fully complete before
        // we check existence and open the stream.
        lock.readLock().lock();
        try {
            if (Files.exists(filePath)) {
                try (ObjectInputStream ois = new ObjectInputStream(
                        new BufferedInputStream(Files.newInputStream(filePath)))) {

                    Object obj = ois.readObject();
                    LOGGER.log(Level.FINE, "Successfully read object from local file: {0}", filename);
                    return clazz.cast(obj);

                } catch (ClassCastException e) {
                    throw new IOException("Object in file cannot be cast to " + clazz.getSimpleName(), e);
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        LOGGER.log(Level.INFO, "Data not found in database or file: {0}", filename);
        return null;
    }

    /**
     * Writes an object to file using serialization with improved atomic operation handling.
     * FIXED: Removed unreachable FileAlreadyExistsException catch block.
     *
     * @param filename the file path
     * @param obj the object to serialize
     * @throws IOException if write operation fails
     */
    public static void writeSerialized(String filename, Object obj) throws IOException {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        if (obj == null) {
            throw new IllegalArgumentException("Object to serialize cannot be null");
        }

        Path filePath = Paths.get(filename);
        Path parentDir = filePath.getParent();

        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        String tempFileName = filename + ".tmp." + System.currentTimeMillis() + "." + Thread.currentThread().threadId();
        Path tempPath = Paths.get(tempFileName);

        lock.writeLock().lock();
        try {
            // Write to temporary file first
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tempPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING)))) {

                oos.writeObject(obj);
                oos.flush();
            }

            // Attempt atomic move with different strategies
            boolean moveSuccessful = false;

            // Strategy 1: Try atomic move with replace
            try {
                Files.move(tempPath, filePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                moveSuccessful = true;
                LOGGER.log(Level.FINE, "Successfully wrote object to: {0} (atomic)", filename);

            } catch (AtomicMoveNotSupportedException e1) {
                // Strategy 2: Try non-atomic move with replace
                try {
                    Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                    moveSuccessful = true;
                    LOGGER.log(Level.FINE, "Successfully wrote object to: {0} (non-atomic)", filename);

                } catch (IOException e2) {
                    // Strategy 3: Delete existing file first, then move
                    try {
                        if (Files.exists(filePath)) {
                            Files.delete(filePath);
                        }
                        Files.move(tempPath, filePath);
                        moveSuccessful = true;
                        LOGGER.log(Level.FINE, "Successfully wrote object to: {0} (delete-first)", filename);

                    } catch (IOException e3) {
                        // Strategy 4: Copy and delete (last resort)
                        try {
                            Files.copy(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                            Files.delete(tempPath);
                            moveSuccessful = true;
                            LOGGER.log(Level.FINE, "Successfully wrote object to: {0} (copy-delete)", filename);

                        } catch (IOException e4) {
                            LOGGER.log(Level.SEVERE, "All write strategies failed for: {0}", filename);
                            throw new IOException("Failed to write file after trying all strategies. " +
                                    "Original error: " + e1.getMessage() +
                                    ". Final error: " + e4.getMessage(), e4);
                        }
                    }
                }
            }

            if (moveSuccessful) {
                LOGGER.info("Successfully wrote " + obj.getClass().getSimpleName() + " to " + filePath);
                mirrorSnapshot(filePath, obj);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write to temporary file: " + tempFileName, e);
            throw new IOException("Failed to create temporary file for writing", e);
        } finally {
            // Clean up temporary file if it still exists
            try {
                if (Files.exists(tempPath)) {
                    Files.delete(tempPath);
                }
            } catch (IOException cleanupException) {
                LOGGER.log(Level.WARNING, "Failed to clean up temporary file: " + tempPath, cleanupException);
            }
            lock.writeLock().unlock();
        }
    }

    /**
     * Reads a serialized map from file.
     *
     * @param filename the file path
     * @return the deserialized map or null if file doesn't exist
     * @throws IOException if read operation fails
     * @throws ClassNotFoundException if class cannot be found during deserialization
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> readSerializedMap(String filename)
            throws IOException, ClassNotFoundException {

        Object obj = readSerialized(filename, Object.class);
        if (obj == null) {
            return null;
        }

        try {
            return (Map<String, List<String>>) obj;
        } catch (ClassCastException e) {
            throw new IOException("File does not contain a valid Map<String, List<String>>", e);
        }
    }

    /**
     * Writes a map to file using serialization.
     *
     * @param filename the file path
     * @param map the map to serialize
     * @throws IOException if write operation fails
     */
    public static void writeSerializedMap(String filename, Map<String, List<String>> map)
            throws IOException {
        writeSerialized(filename, map);
    }

    /**
     * Reads a serialized nested map from file.
     *
     * @param filename the file path
     * @return the deserialized nested map or null if file doesn't exist
     * @throws IOException if read operation fails
     * @throws ClassNotFoundException if class cannot be found during deserialization
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Integer>> readSerializedNestedMap(String filename)
            throws IOException, ClassNotFoundException {

        Object obj = readSerialized(filename, Object.class);
        if (obj == null) {
            return null;
        }

        try {
            return (Map<String, Map<String, Integer>>) obj;
        } catch (ClassCastException e) {
            throw new IOException("File does not contain a valid Map<String, Map<String, Integer>>", e);
        }
    }

    /**
     * Writes a nested map to file using serialization.
     *
     * @param filename the file path
     * @param map the nested map to serialize
     * @throws IOException if write operation fails
     */
    public static void writeSerializedNestedMap(String filename, Map<String, Map<String, Integer>> map)
            throws IOException {
        writeSerialized(filename, map);
    }

    /**
     * Checks if a file exists and is readable.
     *
     * @param filename the file path to check
     * @return true if file exists and is readable
     */
    public static boolean fileExists(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }

        Path filePath = Paths.get(filename);
        return Files.exists(filePath) && Files.isReadable(filePath);
    }

    /**
     * Safely deletes a file if it exists.
     *
     * @param filename the file path to delete
     * @return true if file was deleted or didn't exist
     */
    public static boolean deleteFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }

        try {
            return Files.deleteIfExists(Paths.get(filename));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete file: " + filename, e);
            return false;
        }
    }

    /**
     * Creates a backup copy of a file before modification.
     *
     * @param filename the file to backup
     * @return true if backup was created successfully
     */
    public static boolean createBackup(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }

        Path originalPath = Paths.get(filename);
        if (!Files.exists(originalPath)) {
            return false;
        }

        try {
            String backupFileName = filename + ".backup." + System.currentTimeMillis();
            Path backupPath = Paths.get(backupFileName);
            Files.copy(originalPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Created backup: " + backupFileName);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to create backup for: " + filename + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the size of a file in bytes.
     *
     * @param filename the file path
     * @return file size in bytes, or -1 if file doesn't exist or error occurs
     */
    public static long getFileSize(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return -1;
        }

        try {
            Path filePath = Paths.get(filename);
            return Files.exists(filePath) ? Files.size(filePath) : -1;
        } catch (IOException e) {
            System.err.println("Failed to get file size for: " + filename + " - " + e.getMessage());
            return -1;
        }
    }

    /**
     * Ensures a directory exists, creating it if necessary.
     *
     * @param directoryPath the directory path to ensure
     * @return true if directory exists or was created successfully
     */
    public static boolean ensureDirectoryExists(String directoryPath) {
        if (directoryPath == null || directoryPath.trim().isEmpty()) {
            return false;
        }

        try {
            Path dirPath = Paths.get(directoryPath);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                System.out.println("Created directory: " + directoryPath);
            }
            return true;
        } catch (IOException e) {
            System.err.println("Failed to create directory: " + directoryPath + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Mirrors an in-memory object as a binary snapshot in the connected database
     * (if the current database engine supports snapshots).
     *
     * <p>The snapshot is keyed by the branch-scoped identifier produced by
     * {@link #snapshotKey(Path)}, so each library branch stores its own copy.
     * This method is a no-op when no database is connected or when the engine
     * does not support the snapshot API.</p>
     *
     * @param filePath the on-disk file path whose logical snapshot key is derived;
     *                 used only for key generation, the file itself is not read
     * @param obj      the object to serialise and store; must be
     *                 {@link java.io.Serializable}
     */
    public static void mirrorSnapshot(Path filePath, Object obj) {
        if (!DatabaseConnectionService.supportsSnapshots()) {
            return;
        }
        try {
            DatabaseConnectionService.saveSnapshot(snapshotKey(filePath), serialize(obj));
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to serialize snapshot for database mirror: " + filePath, ex);
        }
    }

    /**
     * Serialises an object to a raw byte array using Java object serialisation.
     *
     * <p>The resulting bytes can be stored in a database snapshot, transmitted
     * over a network, or compared for equality.  The caller is responsible for
     * ensuring that {@code obj} and all its reachable fields implement
     * {@link java.io.Serializable}.</p>
     *
     * @param obj the object to serialise; must not be {@code null}
     * @return a byte array containing the full serialised form
     * @throws IOException if serialisation fails (e.g. a field is not serialisable)
     */
    public static byte[] serialize(Object obj) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }
    }

    /**
     * Deserialises a byte array back into a typed object.
     *
     * <p>The payload must have been produced by {@link #serialize(Object)} or
     * an equivalent Java serialisation mechanism.  A {@link ClassCastException}
     * is wrapped in an {@link IOException} if the deserialised object does not
     * conform to {@code clazz}.</p>
     *
     * @param <T>     the expected type of the deserialised object
     * @param payload the raw bytes to deserialise; must not be {@code null}
     * @param clazz   the expected runtime class of the result; used for a safe cast
     * @return the deserialised object cast to {@code T}
     * @throws IOException            if deserialisation fails
     * @throws ClassNotFoundException if the class of the serialised object is not
     *                                available in the current class loader
     */
    private static <T> T deserialize(byte[] payload, Class<T> clazz)
            throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(payload);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return clazz.cast(ois.readObject());
        } catch (ClassCastException ex) {
            throw new IOException("Object in snapshot cannot be cast to " + clazz.getSimpleName(), ex);
        }
    }

    /**
     * Derives the database snapshot key for a given on-disk file path.
     *
     * <p>The key is scoped by library branch (using the stable {@code branchId})
     * so that multiple branches sharing one database do not overwrite each other's
     * snapshots.  The sole exception is {@code libraries_db.ser}, which is stored
     * under its own unscoped key because it is a global registry shared by all
     * branches.</p>
     *
     * <p>Examples:</p>
     * <pre>
     *   libraries_db.ser           → "libraries_db.ser"         (global)
     *   users_db.ser               → "branchUUID:users_db.ser"  (branch-scoped)
     * </pre>
     *
     * @param filePath the path whose filename is used to build the key
     * @return a non-null, lowercase snapshot key string
     */
    private static String snapshotKey(Path filePath) {
        Path fileNamePath = filePath.getFileName();
        if (fileNamePath == null) {
            return filePath.toString().toLowerCase();
        }

        String fileName = fileNamePath.toString().toLowerCase();

        // The library registry is global — all branches share the same entry
        if (fileName.equals("libraries_db.ser")) {
            return fileName;
        }

        // All other files are scoped to the current branch via branchId
        AppConfiguration config = com.example.services.AppConfigurationService.getConfiguration();
        String branchPrefix = config.getBranchId();
        if (branchPrefix == null || branchPrefix.isBlank()) {
            branchPrefix = (config.getLibraryName() + "_" + config.getBranchName())
                    .toLowerCase().replaceAll("[^a-z0-9]", "_");
        }

        return branchPrefix + ":" + fileName;
    }

    /**
     * Restores all branch-scoped data files from their database snapshots,
     * overwriting the local {@code .ser} files with the latest mirrored state.
     *
     * <p>This is typically called at startup when an external database (MySQL /
     * PostgreSQL / MongoDB) is configured, so that the local serialised files
     * are synchronised with any changes made by other machines sharing the same
     * database.</p>
     *
     * <p>Files for which no snapshot exists in the database are silently skipped;
     * the local file is left unchanged.</p>
     *
     * @param dataDir the branch-specific data directory to restore files into;
     *                must not be {@code null}
     * @throws IOException if directory creation or file writing fails
     */
    public static void syncFromDatabase(Path dataDir) throws IOException {
        if (dataDir == null) {
            return;
        }
        Files.createDirectories(dataDir);
        Path configDir = com.example.storage.AppPaths.configDirectory();
        Files.createDirectories(configDir);

        // Global library registry — branch-independent
        restoreSnapshot("libraries_db.ser", configDir.resolve("libraries_db.ser"));

        // Branch-scoped operational data files (legacy key aliases listed after the primary key)
        restoreBranchSnapshot(dataDir.resolve("app_config.ser"), "config.ser", "settings.ser");
        restoreBranchSnapshot(dataDir.resolve("branch_config.ser"));
        restoreBranchSnapshot(dataDir.resolve("users_db.ser"), "users.ser");
        restoreBranchSnapshot(dataDir.resolve("books_db.ser"), "books.ser");
        restoreBranchSnapshot(dataDir.resolve("issued_books.ser"));
        restoreBranchSnapshot(dataDir.resolve("borrower_details.ser"));
        restoreBranchSnapshot(dataDir.resolve("issue_records.ser"), "issues.ser");
        restoreBranchSnapshot(dataDir.resolve("borrow_requests.ser"), "requests.ser");
        restoreBranchSnapshot(dataDir.resolve("borrow_requests_archive.ser"));
        restoreBranchSnapshot(dataDir.resolve("payment_approvals.ser"));
        restoreBranchSnapshot(dataDir.resolve("fines.ser"));
    }

    /**
     * Restores a single branch-scoped file from the database snapshot store,
     * trying the branch-scoped key first and then each legacy alias in order.
     *
     * <p>The method is a no-op if no snapshot is found under any of the keys.</p>
     *
     * @param targetFile  the local path to write the restored bytes to
     * @param legacyKeys  zero or more fallback snapshot keys tried in order when
     *                    the primary scoped key yields no result
     * @throws IOException if writing the restored file fails
     */
    private static void restoreBranchSnapshot(Path targetFile, String... legacyKeys)
            throws IOException {
        // Try the current branch-scoped key first
        String scopedKey = snapshotKey(targetFile);
        byte[] snapshot = DatabaseConnectionService.loadSnapshot(scopedKey);

        // Unscoped filename fallback (for snapshots saved before branch-scoping was introduced)
        if (snapshot == null) {
            String fileName = targetFile.getFileName() != null
                    ? targetFile.getFileName().toString() : "";
            snapshot = DatabaseConnectionService.loadSnapshot(fileName);
        }

        // Legacy alias fallbacks
        if (snapshot == null && legacyKeys != null) {
            for (String legacyKey : legacyKeys) {
                snapshot = DatabaseConnectionService.loadSnapshot(legacyKey);
                if (snapshot != null) {
                    break;
                }
            }
        }

        if (snapshot != null) {
            writeSnapshotToFile(scopedKey, targetFile, snapshot);
        }
    }

    /**
     * Restores a globally-keyed snapshot (e.g. the library registry) from the
     * database to a specific target file.
     *
     * @param key        the exact snapshot key to look up in the database
     * @param targetFile the local path to write the restored bytes to
     * @throws IOException if writing the file fails
     */
    private static void restoreSnapshot(String key, Path targetFile) throws IOException {
        byte[] snapshot = DatabaseConnectionService.loadSnapshot(key);
        if (snapshot != null) {
            writeSnapshotToFile(key, targetFile, snapshot);
        }
    }

    /**
     * Writes a snapshot byte array to a local file under the global write lock,
     * creating any missing parent directories first.
     *
     * <p>Using the write lock ensures that no concurrent read operation sees a
     * partially written file.</p>
     *
     * @param key        the snapshot key (used only for log output)
     * @param targetFile the path to write to; parent directories are created
     * @param snapshot   the raw bytes to write
     * @throws IOException if the write fails
     */
    private static void writeSnapshotToFile(String key, Path targetFile, byte[] snapshot)
            throws IOException {
        lock.writeLock().lock();
        try {
            Files.createDirectories(targetFile.getParent());
            Files.write(targetFile, snapshot,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.log(Level.INFO, "Restored {0} from database snapshot to {1}",
                    new Object[]{key, targetFile});
        } finally {
            lock.writeLock().unlock();
        }
    }
}
