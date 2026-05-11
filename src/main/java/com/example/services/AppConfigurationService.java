package com.example.services;

import com.example.entities.AppConfiguration;
import com.example.entities.BranchConfiguration;
import com.example.entities.DatabaseConfiguration;
import com.example.entities.LibrariesDB;
import com.example.storage.AppPaths;
import com.example.storage.DataStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code AppConfigurationService} is the primary manager for the application's 
 * lifecycle and global settings.
 *
 * <p>It handles loading/saving the {@link AppConfiguration}, managing database 
 * connection transitions, and orchestrating data isolation when switching 
 * between different library branches.</p>
 */
public final class AppConfigurationService {
    private static final Logger LOGGER = Logger.getLogger(AppConfigurationService.class.getName());
    private static final Path LEGACY_GLOBAL_CONFIG_FILE = AppPaths.configFile();
    private static final Path LEGACY_DATA_CONFIG_FILE = Path.of("data", "app_config.ser").toAbsolutePath().normalize();
    private static final String[] LEGACY_DIRECTORIES = {"data", "exports"};

    private static AppConfiguration configuration = loadConfiguration();

    private AppConfigurationService() {
    }

    /**
     * Retrieves the current application configuration.
     * <p>If called during early initialization, returns a safe default to 
     * prevent circular dependencies.</p>
     *
     * @return the active AppConfiguration
     */
    public static AppConfiguration getConfiguration() {
        if (configuration == null) {
            // Return a default configuration if accessed during static initialization
            // to prevent circular dependencies/StackOverflowError.
            AppConfiguration fallback = new AppConfiguration();
            fallback.normalize();
            return fallback;
        }
        configuration.normalize();
        return configuration;
    }

    /**
     * Forces a reload of the configuration from primary storage (File or Database).
     * Call this after establishing a new database connection to ensure settings
     * mirrored in the database are applied.
     */
    /**
     * Forces a full reload of the application configuration from persistent
     * storage, discarding the current in-memory state.
     *
     * <p>This should be called after a {@code .lms} migration package is imported
     * so that any updated configuration files (app_config.ser, libraries_db.ser)
     * are picked up without requiring a restart.</p>
     */
    public static void reloadConfiguration() {
        LOGGER.info("Reloading application configuration...");
        configuration = loadConfiguration();
    }

    /**
     * Updates and persists the active library's configuration.
     * <p>This method handles complex side effects including:
     * <ul>
     *   <li>Re-initializing database connections if engine settings changed.</li>
     *   <li>Triggering a "Data Mirror" if moving from local storage to a remote DB.</li>
     *   <li>Reloading singleton databases if the active library branch changed.</li>
     * </ul>
     *
     * @param updated the new configuration to apply
     * @throws IOException if persistence to file fails
     */
    public static void updateConfiguration(AppConfiguration updated) throws IOException {
        if (updated == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        // Sync security provider with the secret key BEFORE normalization
        // so that decryptFields() uses the correct key.
        com.example.services.SecurityProvider.setLibraryMasterKey(updated.getLibraryMasterKey());

        AppConfiguration previous = configuration;
        updated.normalize();
        updated.rememberCurrentLibrary();
        configuration = updated;

        // Save library-scoped config. The only globally shared serialized file
        // is LibrariesDB; every branch keeps its own rules, SMTP, secret, and
        // operational settings under its branch data directory.
        Path libraryConfigFile = AppPaths.libraryConfigFile(configuration);
        Files.createDirectories(libraryConfigFile.getParent());
        DataStorage.writeSerialized(libraryConfigFile.toString(), configuration);
        saveDatabaseProperties(configuration);

        // Save legacy branch-specific config for older installs.
        Path branchConfigFile = AppPaths.branchConfigFile(configuration);
        Files.createDirectories(branchConfigFile.getParent());
        DataStorage.writeSerialized(branchConfigFile.toString(), configuration.getBranchConfig());

        // Check if library branch changed to trigger data reload
        String oldLib = previous != null ? previous.getCurrentLibraryDisplayName() : null;
        String newLib = updated.getCurrentLibraryDisplayName();
        if (oldLib != null && !oldLib.equals(newLib)) {
            LOGGER.log(Level.INFO, "Library branch changed: {0} -> {1}. Reloading data isolation context...",
                    new Object[]{oldLib, newLib});
            com.example.entities.UsersDB.getInstance().forceReload();
            com.example.entities.BooksDB.getInstance().forceReload();
            com.example.services.BorrowRequestService.forceReload();

            UserService.logout();
        }

        // Handle database connection lifecycle if config changed
        DatabaseConfiguration oldDb = DatabaseConnectionService.getActiveConfig();
        DatabaseConfiguration newDb = updated.getDatabaseConfiguration();

        boolean dbChanged = (oldDb == null && newDb != null && newDb.isConfigured())
                || (oldDb != null && (newDb == null || !oldDb.equals(newDb)));
        if (dbChanged) {
            if (newDb != null && newDb.isConfigured()) {
                LOGGER.info("Database configuration changed. Reconnecting...");
                DatabaseConnectionService.connect(newDb);
            } else {
                LOGGER.info("Database configuration disabled. Disconnecting...");
                DatabaseConnectionService.disconnect();
            }
        }

        // If we just connected to a new database, we should mirror all active databases to it
        if (dbChanged && DatabaseConnectionService.isConnected()) {
            LOGGER.info("Mirroring current state to new database...");
            try {
                com.example.entities.UsersDB.getInstance().forcePersist();
                com.example.entities.BooksDB.getInstance().saveAllData();
                LibrariesDB.getInstance().save();
                DataStorage.mirrorSnapshot(libraryConfigFile, configuration);
                DataStorage.mirrorSnapshot(branchConfigFile, configuration.getBranchConfig());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to mirror state to new database", e);
            }
        }
    }

    /**
     * Loads and assembles the active {@link AppConfiguration} at startup.
     *
     * <p>Steps performed:</p>
     * <ol>
     *   <li>Load the bootstrap configuration (global or branch-scoped legacy file)</li>
     *   <li>Propagate the library master key to {@link SecurityProvider}</li>
     *   <li>Normalise defaults (SMTP, paths, branch identity)</li>
     *   <li>Apply any {@code database.properties} overrides</li>
     *   <li>Migrate legacy data directories if present</li>
     *   <li>Mark setup as done if known libraries exist</li>
     * </ol>
     *
     * @return a non-null, normalised {@link AppConfiguration}; falls back to
     *         defaults if any step fails
     */
    private static AppConfiguration loadConfiguration() {
        try {
            AppConfiguration config = loadBootstrapConfiguration();
            SecurityProvider.setLibraryMasterKey(config.getLibraryMasterKey());
            config.normalize();
            applyDatabaseProperties(config);
            migrateLegacyStorage(config);
            if (hasKnownLibraries()) {
                config.markSetupDone();
            }
            return config;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load app configuration, using defaults", e);
            AppConfiguration fallback = new AppConfiguration();
            fallback.normalize();
            applyDatabaseProperties(fallback);
            if (hasKnownLibraries()) {
                fallback.markSetupDone();
            }
            return fallback;
        }
    }

    /**
     * Reads the initial configuration without committing to a specific library
     * branch — used only during the very first phase of startup, before the user
     * selects a library.
     *
     * <p>If {@link LibrariesDB} already contains known libraries, a fresh default
     * configuration is returned (no pre-selection).  Otherwise the legacy global
     * and data-directory config files are tried in order to migrate existing data.</p>
     *
     * @return a non-null bootstrap configuration; never already branch-selected
     *         when known libraries exist
     */
    private static AppConfiguration loadBootstrapConfiguration() {
        // If libraries exist, do not pre-select any branch at boot time
        if (hasKnownLibraries()) {
            return new AppConfiguration();
        }

        // Try to migrate a legacy single-library installation
        AppConfiguration legacy = readLegacyConfiguration(LEGACY_GLOBAL_CONFIG_FILE);
        if (legacy == null) {
            legacy = readLegacyConfiguration(LEGACY_DATA_CONFIG_FILE);
        }
        if (legacy == null) {
            return new AppConfiguration();
        }

        legacy.normalize();
        if (isConfiguredLibraryIdentity(legacy)) {
            legacy.rememberCurrentLibrary();
        }
        try {
            Path libraryConfigFile = AppPaths.libraryConfigFile(legacy);
            if (!Files.exists(libraryConfigFile)) {
                DataStorage.writeSerialized(libraryConfigFile.toString(), legacy);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not migrate legacy app configuration to branch storage", e);
        }
        return legacy;
    }

    /**
     * Attempts to deserialise an {@link AppConfiguration} from a legacy file
     * path, returning {@code null} if the file does not exist or cannot be read.
     *
     * @param path the path to the serialised configuration file
     * @return the deserialised configuration, or {@code null} on any failure
     */
    private static AppConfiguration readLegacyConfiguration(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return DataStorage.readSerialized(path.toString(), AppConfiguration.class);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not read legacy configuration: " + path, e);
            return null;
        }
    }

    /**
     * Checks the {@link LibrariesDB} registry and marks the current configuration
     * as setup-complete if at least one library is registered.
     *
     * <p>Called after a new library is added during the setup wizard so that the
     * wizard does not re-launch on next startup.</p>
     */
    public static void refreshSetupStateFromLibraries() {
        if (configuration != null && hasKnownLibraries()) {
            configuration.markSetupDone();
        }
    }

    /**
     * Returns {@code true} if the global {@link LibrariesDB} registry contains
     * at least one library entry.
     *
     * @return {@code true} when one or more libraries have been configured
     */
    private static boolean hasKnownLibraries() {
        try {
            return !LibrariesDB.getInstance().getLibraries().isEmpty();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not inspect known libraries", e);
            return false;
        }
    }

    /**
     * Returns {@code true} if the given configuration holds a real library name
     * (i.e. the administrator has completed at least the "library name" step of
     * the setup wizard).
     *
     * @param config the configuration to inspect; may be {@code null}
     * @return {@code true} if {@code config} has a non-blank, non-placeholder
     *         library name
     */
    private static boolean isConfiguredLibraryIdentity(AppConfiguration config) {
        return config != null
                && config.getLibraryName() != null
                && !config.getLibraryName().isBlank()
                && !"Select Library".equalsIgnoreCase(config.getLibraryName());
    }

    /**
     * The ordered list of built-in book categories shown in the catalogue and
     * circulation forms when no custom categories have been defined.
     *
     * <p>Administrators can extend this list via the category-remember mechanism
     * ({@link #rememberBookCategory}).  Custom categories are persisted in
     * {@link AppConfiguration#getSavedCategories()} and merged with this list at
     * runtime.</p>
     */
    public static final java.util.List<String> DEFAULT_BOOK_CATEGORIES = java.util.List.of(
            "Arts", "Biography", "Fiction", "History", "Law", "Literature",
            "Mathematics", "Medicine", "Non-Fiction", "Philosophy",
            "Psychology", "Reference", "Science", "Technology");

    /**
     * Returns the full union of available book categories: built-in defaults,
     * categories inferred from the current book catalogue, and any administrator-
     * saved custom categories — sorted case-insensitively and deduplicated.
     *
     * @param books the current book collection used to discover ad-hoc categories;
     *              may be {@code null} (treated as empty)
     * @return an immutable, sorted list of unique category names
     */
    public static java.util.List<String> getAvailableBookCategories(
            java.util.Collection<com.example.entities.Book> books) {
        java.util.TreeSet<String> categories = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categories.addAll(DEFAULT_BOOK_CATEGORIES);
        if (books != null) {
            books.stream()
                    .map(com.example.entities.Book::getCategory)
                    .filter(c -> c != null && !c.isBlank())
                    .map(String::trim)
                    .forEach(categories::add);
        }
        getConfiguration().getSavedCategories().stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .forEach(categories::add);
        return java.util.List.copyOf(categories);
    }

    /**
     * Persists a newly encountered book category to the active configuration so
     * that it appears in the category drop-down on subsequent launches.
     *
     * <p>Blank or null values are ignored.</p>
     *
     * @param category the category name to persist; ignored if blank
     * @throws IOException if saving the updated configuration fails
     */
    public static void rememberBookCategory(String category) throws IOException {
        if (category == null || category.isBlank()) {
            return;
        }
        AppConfiguration updated = getConfiguration();
        updated.rememberCategory(category);
        updateConfiguration(updated);
    }

    /**
     * Switches the active library branch to the one identified by
     * {@code displayName}, loading and applying all branch-specific settings.
     *
     * <p>The method looks up the entry in {@link LibrariesDB}, builds a minimal
     * identity configuration, merges it with the globally saved database and
     * path settings, then loads the branch-scoped {@code app_config.ser}.
     * Database reconnection is performed if the new branch uses different JDBC
     * settings.</p>
     *
     * <p>A blank or unknown display name is ignored.</p>
     *
     * @param displayName the library display name as stored in {@link LibrariesDB}
     *                    (format: {@code "Library Name — Branch Name"})
     * @throws IOException if saving the updated configuration fails
     */
    public static void selectKnownLibrary(String displayName) throws IOException {
        if (displayName == null || displayName.isBlank()) {
            return;
        }
        String trimmed = displayName.trim();
        LibrariesDB.LibraryEntry entry = LibrariesDB.getInstance().findEntry(trimmed);
        if (entry == null) {
            return;
        }

        // Build a minimal identity configuration from the registry entry
        AppConfiguration selected = new AppConfiguration();
        selected.setLibraryName(entry.libraryName());
        selected.setBranchName(entry.branchName());
        selected.selectKnownLibrary(entry.getDisplayName());
        selected.markSetupDone();

        // Inherit global path and database settings from the current config
        AppConfiguration current = getConfiguration();
        if (entry.dataDirectory() == null || entry.dataDirectory().isBlank()) {
            selected.setDataDirectory(current.getDataDirectory());
        }
        if (entry.exportDirectory() == null || entry.exportDirectory().isBlank()) {
            selected.setExportDirectory(current.getExportDirectory());
        }
        selected.setDatabaseConfiguration(current.getDatabaseConfiguration());
        selected.setLibraryMasterKey(current.getLibraryMasterKey());

        // Temporarily set the identity so resolveDataDirectory() points to the right branch
        configuration = selected;
        AppConfiguration loaded = loadLibraryConfiguration(selected);
        loaded.setLibraryName(entry.libraryName());
        loaded.setBranchName(entry.branchName());
        loaded.selectKnownLibrary(entry.getDisplayName());
        loaded.markSetupDone();
        applyDatabaseProperties(loaded);
        loaded.normalize();

        configuration = loaded;
        SecurityProvider.setLibraryMasterKey(loaded.getLibraryMasterKey());
        reconnectIfNeeded(loaded.getDatabaseConfiguration());

        // CRITICAL FIX: UsersDB (and other singletons) are initialised lazily using
        // AppPaths.resolveDataFile(), which depends on the current configuration's
        // branchId / dataDirectory. At app start, loadBootstrapConfiguration() returns
        // a blank config (no branch) when known libraries exist, so any singleton that
        // was touched before a library is explicitly selected will have loaded from the
        // wrong (blank-branch) path and will appear empty.
        //
        // Calling forceReload() here ensures that immediately after a library is
        // selected — whether during login or from the registration dialog — all
        // singletons re-read their data from the correct branch-scoped directory.
        try {
            com.example.entities.UsersDB.getInstance().forceReload();
            com.example.entities.BooksDB.getInstance().forceReload();
            com.example.services.BorrowRequestService.forceReload();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to reload branch data after library selection", e);
        }

        LOGGER.log(Level.INFO, "Active library selected: ''{0}''", trimmed);
    }

    /**
     * Loads the branch-specific {@code app_config.ser} for the given identity
     * configuration, falling back to the identity itself when no persisted file
     * exists.
     *
     * @param identity a minimal configuration identifying the target branch
     * @return the loaded branch configuration, or {@code identity} as the fallback
     */
    private static AppConfiguration loadLibraryConfiguration(AppConfiguration identity) {
        try {
            Path libraryConfigFile = AppPaths.libraryConfigFile(identity);
            if (Files.exists(libraryConfigFile) || DatabaseConnectionService.isConnected()) {
                AppConfiguration loaded = DataStorage.readSerialized(
                        libraryConfigFile.toString(), AppConfiguration.class);
                if (loaded != null) {
                    loadLegacyBranchConfiguration(loaded);
                    return loaded;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not load library-scoped app configuration", e);
        }

        loadLegacyBranchConfiguration(identity);
        return identity;
    }

    /**
     * Overlays a legacy {@link BranchConfiguration} (stored in a separate
     * {@code branch_config.ser} file) onto the given configuration object.
     *
     * <p>This provides backwards compatibility for installations that were
     * created before branch settings were merged into the main configuration.</p>
     *
     * @param config the configuration to overlay branch settings onto;
     *               modified in-place if a branch config file is found
     */
    private static void loadLegacyBranchConfiguration(AppConfiguration config) {
        try {
            Path branchConfigFile = AppPaths.branchConfigFile(config);
            if (Files.exists(branchConfigFile) || DatabaseConnectionService.isConnected()) {
                BranchConfiguration branchConfig = DataStorage.readSerialized(
                        branchConfigFile.toString(), BranchConfiguration.class);
                if (branchConfig != null) {
                    config.setBranchConfig(branchConfig);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not load legacy branch configuration", e);
        }
    }

    /**
     * Re-establishes the database connection if the database configuration has
     * changed compared with the currently active connection.
     *
     * <p>If the new configuration has no database set, the active connection is
     * disconnected.  This is called after switching library branches so that the
     * correct JDBC settings are applied.</p>
     *
     * @param newDb the database configuration for the newly selected branch;
     *              may be {@code null}
     */
    private static void reconnectIfNeeded(DatabaseConfiguration newDb) {
        DatabaseConfiguration oldDb = DatabaseConnectionService.getActiveConfig();
        boolean dbChanged = (oldDb == null && newDb != null && newDb.isConfigured())
                || (oldDb != null && (newDb == null || !oldDb.equals(newDb)));
        if (!dbChanged) {
            return;
        }
        if (newDb != null && newDb.isConfigured()) {
            DatabaseConnectionService.connect(newDb);
        } else {
            DatabaseConnectionService.disconnect();
        }
    }

    /**
     * Copies files from any legacy data/export directories (e.g. a plain
     * {@code "data"} folder in the working directory) to the current
     * OS-specific paths.
     *
     * <p>This is a best-effort migration; individual file copy failures are
     * silently skipped so that a single unreadable file does not abort startup.</p>
     *
     * @param config the configuration providing the target data and export paths
     */
    private static void migrateLegacyStorage(AppConfiguration config) {
        if (config == null) {
            return;
        }
        AppPaths.migrateLegacyDirectoryIfNeeded(
                LEGACY_DIRECTORIES[0], Path.of(config.getDataDirectory()));
        AppPaths.migrateLegacyDirectoryIfNeeded(
                LEGACY_DIRECTORIES[1], Path.of(config.getExportDirectory()));
    }

    /**
     * Reads JDBC connection overrides from the {@code database.properties} file
     * and applies them to the given configuration's database settings.
     *
     * <p>This file (if present) takes precedence over values stored in
     * {@code app_config.ser}, allowing sysadmins to patch database credentials
     * without touching the serialised file.  Unknown or malformed property values
     * are silently ignored.</p>
     *
     * @param config the configuration whose database settings are modified
     *               in-place; must not be {@code null}
     */
    private static void applyDatabaseProperties(AppConfiguration config) {
        Path propsFile = AppPaths.databasePropertiesFile();
        if (!Files.exists(propsFile)) {
            return;
        }

        LOGGER.info("Applying database overrides from " + propsFile);
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream is = Files.newInputStream(propsFile)) {
            props.load(is);
            com.example.entities.DatabaseConfiguration db = config.getDatabaseConfiguration();

            String engineStr = props.getProperty("db.engine");
            if (engineStr != null) {
                try {
                    db.setEngine(com.example.entities.DatabaseConfiguration.Engine
                            .valueOf(engineStr.toUpperCase().trim()));
                } catch (Exception ignored) { /* unknown engine — skip */ }
            }

            db.setSqliteFile(props.getProperty("db.sqlite.file", db.getSqliteFile()));
            db.setHost(props.getProperty("db.host", db.getHost()));

            String portStr = props.getProperty("db.port");
            if (portStr != null) {
                try { db.setPort(Integer.parseInt(portStr.trim())); }
                catch (Exception ignored) { /* malformed port — skip */ }
            }

            db.setDatabase(props.getProperty("db.database", db.getDatabase()));
            db.setUsername(props.getProperty("db.username", db.getUsername()));
            db.setPassword(props.getProperty("db.password", db.getPassword()));

            String sslStr = props.getProperty("db.ssl");
            if (sslStr != null) db.setSslEnabled(Boolean.parseBoolean(sslStr.trim()));

            String dwStr = props.getProperty("db.dualWrite");
            if (dwStr != null) db.setDualWrite(Boolean.parseBoolean(dwStr.trim()));

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not load database properties", e);
        }
    }

    /**
     * Persists the current database configuration to {@code database.properties}
     * so that it can be applied at next startup via {@link #applyDatabaseProperties}.
     *
     * <p>The file is updated atomically via the standard Java
     * {@link java.util.Properties#store} method.</p>
     *
     * @param config the configuration whose database settings are saved
     */
    private static void saveDatabaseProperties(AppConfiguration config) {
        Path propsFile = AppPaths.databasePropertiesFile();
        com.example.entities.DatabaseConfiguration db = config.getDatabaseConfiguration();

        java.util.Properties props = new java.util.Properties();
        props.setProperty("db.engine", db.getEngine().name());
        props.setProperty("db.sqlite.file", db.getSqliteFile());
        props.setProperty("db.host", db.getHost());
        props.setProperty("db.port", String.valueOf(db.getPort()));
        props.setProperty("db.database", db.getDatabase());
        props.setProperty("db.username", db.getUsername());
        props.setProperty("db.password", db.getPassword());
        props.setProperty("db.ssl", String.valueOf(db.isSslEnabled()));
        props.setProperty("db.dualWrite", String.valueOf(db.isDualWrite()));

        String instructions = """
            Library OS - Database Configuration Guide
            =========================================
            This file allows manual override of the database connection settings.
            The application MUST be restarted for changes to take effect.
            
            [db.engine] - The database type to use.
            Options:
            - NONE:       Pure local file storage (Default for new setups).
            - SQLITE:     Local relational database (file-based).
            - MYSQL:      Centralized MariaDB/MySQL server. Highly recommended for multi-user.
            - POSTGRESQL: Professional-grade relational database.
            - ORACLE:     Enterprise relational database.
            - MONGODB:    NoSQL document-based storage.
            
            [db.sqlite.file]
            Path to the SQLite database file (e.g., data/library.db). Only used if engine is SQLITE.
            
            [db.host]
            The IP address or hostname of your database server (e.g., 127.0.0.1 or db.example.com).
            
            [db.port]
            The port number your database server is listening on.
            Common defaults:
            - MySQL: 3306
            - PostgreSQL: 5432
            - Oracle: 1521
            - MongoDB: 27017
            
            [db.database]
            The name of the specific database/schema created for Library OS on your server.
            
            [db.username / db.password]
            The database credentials. Ensure the user has CREATE, SELECT, INSERT, UPDATE, DELETE permissions.
            
            [db.ssl]
            Set to 'true' to use SSL/TLS for remote connections (recommended for production).
            
            [db.dualWrite]
            Set to 'true' to keep local .ser files synchronized with the database as a fallback backup.
            """;

        try (java.io.OutputStream os = Files.newOutputStream(propsFile)) {
            props.store(os, instructions);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save database properties", e);
        }
    }
}
