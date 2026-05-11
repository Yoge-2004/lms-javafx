package com.example.entities;

import com.example.storage.AppPaths;
import com.example.storage.DataStorage;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code LibrariesDB} manages the registry of all known library branches in the system.
 *
 * <p>It tracks which branches are currently configured and provides the lookup logic 
 * used during application initialization and branch-switching.</p>
 */
public final class LibrariesDB implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(LibrariesDB.class.getName());
    private static final String FILE_NAME = "libraries_db.ser";

    private static volatile LibrariesDB instance;
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @SuppressWarnings("serial") // ArrayList IS Serializable; warning is on the List interface
    private final List<LibraryEntry> libraries = new ArrayList<>();

    public static record LibraryEntry(String libraryName, String branchName, String branchId,
                                      String dataDirectory, String exportDirectory) implements Serializable {
        public LibraryEntry(String libraryName, String branchName, String branchId) {
            this(libraryName, branchName, branchId, "", "");
        }

        public String getDisplayName() {
            return libraryName + " - " + branchName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LibraryEntry that)) return false;
            // Equality based on names to prevent duplicate display entries, 
            // but ID is the actual key for data.
            return libraryName.equalsIgnoreCase(that.libraryName) && branchName.equalsIgnoreCase(that.branchName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(libraryName.toLowerCase(), branchName.toLowerCase());
        }
    }

    private LibrariesDB() {}

    @SuppressWarnings("unchecked")
    private void migrate() {
        if (libraries == null) return;
        List<Object> raw = (List<Object>) (List<?>) libraries;
        Set<LibraryEntry> uniqueMigrated = new LinkedHashSet<>();
        boolean changed = false;
        for (Object item : raw) {
            if (item instanceof String s) {
                // Parse "Library - Branch"
                int sep = s.lastIndexOf(" - ");
                String lib, br;
                if (sep > 0 && sep < s.length() - 3) {
                    lib = s.substring(0, sep).trim();
                    br = s.substring(sep + 3).trim();
                } else {
                    lib = s.trim();
                    br = "Main Branch";
                }
                uniqueMigrated.add(new LibraryEntry(lib, br, UUID.randomUUID().toString()));
                changed = true;
            } else if (item instanceof LibraryEntry e) {
                if (!uniqueMigrated.add(e)) {
                    changed = true; // Duplicate found and removed
                }
            }
        }
        if (changed) {
            libraries.clear();
            libraries.addAll(uniqueMigrated);
            LOGGER.info("Migrated/Deduplicated " + libraries.size() + " library entries");
            save(); // Save the migrated format immediately
        }
    }

    /**
     * Gets the singleton instance, loading from disk if available.
     */
    public static LibrariesDB getInstance() {
        if (instance == null) {
            synchronized (LibrariesDB.class) {
                if (instance == null) {
                    instance = loadFromFile();
                    if (instance == null) {
                        instance = new LibrariesDB();
                        LOGGER.info("Created new empty libraries database");
                    } else {
                        instance.migrate();
                    }
                }
            }
        }
        return instance;
    }

    private static LibrariesDB loadFromFile() {
        try {
            String path = AppPaths.configDirectory().resolve(FILE_NAME).toString();
            return DataStorage.readSerialized(path, LibrariesDB.class);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load libraries database from file", e);
            return null;
        }
    }

    /**
     * Forces a reload from the database snapshot if connected.
     */
    public void forceReload() {
        lock.writeLock().lock();
        try {
            byte[] snapshot = com.example.services.DatabaseConnectionService.loadSnapshot("libraries_db.ser");
            if (snapshot != null) {
                try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(snapshot);
                     java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bis)) {
                    LibrariesDB loaded = (LibrariesDB) ois.readObject();
                    if (loaded != null && loaded.libraries != null) {
                        this.libraries.clear();
                        this.libraries.addAll(loaded.libraries);
                        migrate();
                        LOGGER.info("Libraries database synchronized and migrated from database snapshot");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to reload libraries from database snapshot", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a copy of the known libraries list.
     */
    public List<LibraryEntry> getEntries() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(libraries);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> getLibraries() {
        lock.readLock().lock();
        try {
            return libraries.stream().map(LibraryEntry::getDisplayName).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes a library from the database by its branch ID.
     * This is the correct removal method when a library name or branch name has been
     * edited — the display-name-based removeLibrary() cannot find the old entry once
     * the names have changed.
     */
    public void removeLibraryById(String branchId) {
        if (branchId == null || branchId.isBlank()) return;
        lock.writeLock().lock();
        try {
            boolean changed = libraries.removeIf(e -> branchId.equals(e.branchId()));
            if (changed) save();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds a new library to the database if it doesn't already exist.
     */
    public void addLibrary(String libName, String branchName, String branchId) {
        addLibrary(libName, branchName, branchId, "", "");
    }

    public void addLibrary(String libName, String branchName, String branchId,
                           String dataDirectory, String exportDirectory) {
        if (libName == null || branchName == null || branchId == null) return;
        lock.writeLock().lock();
        try {
            LibraryEntry entry = new LibraryEntry(
                    libName.trim(),
                    branchName.trim(),
                    branchId.trim(),
                    dataDirectory == null ? "" : dataDirectory.trim(),
                    exportDirectory == null ? "" : exportDirectory.trim());
            // Use removeIf to ensure ALL existing entries with matching names (case-insensitive) are removed
            libraries.removeIf(e -> e.equals(entry));
            libraries.add(entry);
            save();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public LibraryEntry findEntry(String displayName) {
        if (displayName == null) return null;
        lock.readLock().lock();
        try {
            return libraries.stream()
                    .filter(e -> e.getDisplayName().equalsIgnoreCase(displayName.trim()))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes a library from the database.
     */
    public void removeLibrary(String name) {
        lock.writeLock().lock();
        try {
            LibraryEntry entry = findEntry(name);
            if (entry != null && libraries.remove(entry)) {
                save();
                LOGGER.log(Level.INFO, "Library branch removed: {0}", name);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void save() {
        try {
            String path = AppPaths.configDirectory().resolve(FILE_NAME).toString();
            LOGGER.info("Saving LibrariesDB with " + libraries.size() + " branches to " + path);
            DataStorage.writeSerialized(path, this);
            // BUG FIX: The original code only wrote to the local file and never mirrored
            // the LibrariesDB to the database.  This means:
            //   (a) The library dropdown was always empty on first launch after connecting
            //       to a fresh database, because there was no snapshot to forceReload() from.
            //   (b) Multiple clients sharing a database could not see each other's branches.
            // Fix: always mirror the snapshot after a local save so the DB stays in sync.
            DataStorage.mirrorSnapshot(
                    AppPaths.configDirectory().resolve(FILE_NAME), this);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save libraries database", e);
        }
    }
}
