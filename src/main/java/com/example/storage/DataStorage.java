package com.example.storage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe utility class for handling serialization operations with improved error handling,
 * atomic operations, and Windows compatibility.
 */
public final class DataStorage {

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
        if (!Files.exists(filePath)) {
            System.out.println("INFO: File does not exist: " + filename);
            return null;
        }

        lock.readLock().lock();
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(filePath)))) {

            Object obj = ois.readObject();
            System.out.println("Successfully read object from: " + filename);
            return clazz.cast(obj);

        } catch (ClassCastException e) {
            throw new IOException("Object in file cannot be cast to " + clazz.getSimpleName(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Writes an object to file using serialization with improved atomic operation handling.
     * This version addresses Windows file system issues and provides fallback mechanisms.
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

        // Ensure parent directories exist
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Create a unique temporary file name to avoid conflicts
        String tempFileName = filename + ".tmp." + System.currentTimeMillis() + "." + Thread.currentThread().getId();
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
                if (Files.exists(filePath)) {
                    Files.move(tempPath, filePath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } else {
                    Files.move(tempPath, filePath, StandardCopyOption.ATOMIC_MOVE);
                }
                moveSuccessful = true;
                System.out.println("Successfully wrote object to: " + filename + " (atomic)");

            } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException e1) {
                // Strategy 2: Try non-atomic move with replace
                try {
                    Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                    moveSuccessful = true;
                    System.out.println("Successfully wrote object to: " + filename + " (non-atomic)");

                } catch (IOException e2) {
                    // Strategy 3: Delete existing file first, then move
                    try {
                        if (Files.exists(filePath)) {
                            Files.delete(filePath);
                        }
                        Files.move(tempPath, filePath);
                        moveSuccessful = true;
                        System.out.println("Successfully wrote object to: " + filename + " (delete-first)");

                    } catch (IOException e3) {
                        // Strategy 4: Copy and delete (last resort)
                        try {
                            Files.copy(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                            Files.delete(tempPath);
                            moveSuccessful = true;
                            System.out.println("Successfully wrote object to: " + filename + " (copy-delete)");

                        } catch (IOException e4) {
                            System.err.println("All write strategies failed for: " + filename);
                            throw new IOException("Failed to write file after trying all strategies. " +
                                    "Original error: " + e1.getMessage() +
                                    ". Final error: " + e4.getMessage(), e4);
                        }
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Failed to write to temporary file: " + tempFileName + " - " + e.getMessage());
            throw new IOException("Failed to create temporary file for writing", e);
        } finally {
            // Clean up temporary file if it still exists
            try {
                if (Files.exists(tempPath)) {
                    Files.delete(tempPath);
                }
            } catch (IOException cleanupException) {
                System.err.println("Warning: Failed to clean up temporary file: " + tempPath +
                        " - " + cleanupException.getMessage());
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
            System.err.println("Failed to delete file: " + filename + " - " + e.getMessage());
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
}