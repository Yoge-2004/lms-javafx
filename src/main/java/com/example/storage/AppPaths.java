package com.example.storage;

import com.example.entities.AppConfiguration;
import com.example.services.AppConfigurationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * {@code AppPaths} provides a centralised utility for resolving every
 * directory and file path used by Library OS.
 *
 * <p>It follows OS-specific conventions so that data, configuration, logs, and
 * exports land in the right places on Windows, macOS, and Linux without any
 * manual configuration:</p>
 * <ul>
 *   <li><b>Windows</b> — {@code %APPDATA%\LibraryOS} (data/config),
 *       {@code %LOCALAPPDATA%\LibraryOS\logs} (logs)</li>
 *   <li><b>macOS</b> — {@code ~/Library/Application Support/LibraryOS} (data),
 *       {@code ~/Library/Logs/LibraryOS} (logs)</li>
 *   <li><b>Linux/Unix</b> — {@code $XDG_DATA_HOME/LibraryOS} or
 *       {@code ~/.local/share/LibraryOS} (data),
 *       {@code $XDG_STATE_HOME/LibraryOS/logs} or
 *       {@code ~/.local/state/LibraryOS/logs} (logs)</li>
 * </ul>
 *
 * <p>Every method in this class that returns a {@link Path} also creates the
 * directory on disk if it does not already exist, so callers never need to
 * call {@link java.nio.file.Files#createDirectories} themselves.</p>
 *
 * <p>Data is further isolated by library branch: each branch's files live
 * under a stable {@code branchId} subdirectory inside the base data folder,
 * so renaming a branch never causes data loss.</p>
 *
 * <p>This is a non-instantiable utility class; all members are {@code static}.</p>
 */
public final class AppPaths {

    /** The global application name used as the root directory identifier. */
    public static final String APP_NAME = "LibraryOS";

    /**
     * JVM system property that overrides the computed application home directory.
     * Set {@code -Dlibraryos.home=/custom/path} to redirect all app files.
     */
    private static final String HOME_OVERRIDE = "libraryos.home";

    /**
     * Private constructor — prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private AppPaths() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Resolves the primary home directory for Library OS on the current OS.
     *
     * <p>The resolution order is:</p>
     * <ol>
     *   <li>The {@code libraryos.home} system property (if set and non-blank).</li>
     *   <li>The OS-specific user-data directory combined with {@value #APP_NAME}.</li>
     * </ol>
     *
     * <p>The directory is created on disk if it does not yet exist.</p>
     *
     * @return the absolute, normalised path to the application's home directory;
     *         never {@code null}
     */
    public static Path appHome() {
        String override = System.getProperty(HOME_OVERRIDE);
        if (override != null && !override.isBlank()) {
            return ensureDirectory(Paths.get(override.trim()).toAbsolutePath().normalize());
        }

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path baseDirectory;
        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            baseDirectory = appData != null && !appData.isBlank()
                    ? Paths.get(appData)
                    : Paths.get(System.getProperty("user.home"), "AppData", "Roaming");
        } else if (osName.contains("mac")) {
            baseDirectory = Paths.get(System.getProperty("user.home"), "Library", "Application Support");
        } else {
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            baseDirectory = xdgDataHome != null && !xdgDataHome.isBlank()
                    ? Paths.get(xdgDataHome)
                    : Paths.get(System.getProperty("user.home"), ".local", "share");
        }

        return ensureDirectory(baseDirectory.resolve(APP_NAME).toAbsolutePath().normalize());
    }

    /**
     * Returns the global configuration directory shared by all library branches.
     *
     * <p>Resolves to {@code <appHome>/config}.  This directory stores the
     * master {@code app_config.ser} and the {@code libraries_db.ser} registry.</p>
     *
     * @return the absolute path to the global config directory
     */
    public static Path configDirectory() {
        return ensureDirectory(appHome().resolve("config"));
    }

    /**
     * Returns the path to the global application configuration file.
     *
     * <p>Resolves to {@code <configDirectory>/app_config.ser}.</p>
     *
     * @return the absolute path to {@code app_config.ser}
     */
    public static Path configFile() {
        return configDirectory().resolve("app_config.ser");
    }

    /**
     * Returns the path to the per-branch application configuration file for
     * the given {@link AppConfiguration}.
     *
     * @param config the configuration identifying the branch; must not be {@code null}
     * @return the absolute path to the branch-specific {@code app_config.ser}
     */
    public static Path libraryConfigFile(AppConfiguration config) {
        return resolveDataDirectory(config).resolve("app_config.ser");
    }

    /**
     * Returns the path to the per-branch {@code branch_config.ser} file, which
     * stores branch-level overrides such as a custom library secret.
     *
     * @param config the configuration identifying the branch; must not be {@code null}
     * @return the absolute path to the branch-specific {@code branch_config.ser}
     */
    public static Path branchConfigFile(AppConfiguration config) {
        return resolveDataDirectory(config).resolve("branch_config.ser");
    }

    /**
     * Returns the path to the JDBC {@code database.properties} file that stores
     * external database connection settings for MySQL/PostgreSQL mode.
     *
     * @return the absolute path to {@code database.properties}
     */
    public static Path databasePropertiesFile() {
        return configDirectory().resolve("database.properties");
    }

    /**
     * Returns the default (unconfigured) base data directory.
     *
     * <p>Resolves to {@code <appHome>/data}.  In practice the effective data
     * directory is always further scoped by a branch subdirectory via
     * {@link #resolveDataDirectory()}.</p>
     *
     * @return the absolute path to the default data directory
     */
    public static Path defaultDataDirectory() {
        return ensureDirectory(appHome().resolve("data"));
    }

    /**
     * Returns the default (unconfigured) export directory where PDF and CSV
     * report files are written.
     *
     * <p>Resolves to {@code <appHome>/exports}.</p>
     *
     * @return the absolute path to the default export directory
     */
    public static Path defaultExportDirectory() {
        return ensureDirectory(appHome().resolve("exports"));
    }

    /**
     * Returns the directory where manual backup snapshots are stored.
     *
     * <p>Resolves to {@code <appHome>/backups}.</p>
     *
     * @return the absolute path to the backups directory
     */
    public static Path backupDirectory() {
        return ensureDirectory(appHome().resolve("backups"));
    }

    /**
     * Returns the OS-specific directory where rotating log files are stored.
     *
     * <p>Follows platform conventions:</p>
     * <ul>
     *   <li>Windows — {@code %LOCALAPPDATA%\LibraryOS\logs}</li>
     *   <li>macOS   — {@code ~/Library/Logs/LibraryOS}</li>
     *   <li>Linux   — {@code $XDG_STATE_HOME/LibraryOS/logs} or
     *                 {@code ~/.local/state/LibraryOS/logs}</li>
     * </ul>
     *
     * <p>When the {@code libraryos.home} override is active the logs directory
     * defaults to {@code <appHome>/logs}.</p>
     *
     * @return the absolute path to the log directory
     */
    public static Path logDirectory() {
        String override = System.getProperty(HOME_OVERRIDE);
        if (override != null && !override.isBlank()) {
            return ensureDirectory(appHome().resolve("logs"));
        }

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path logBaseDirectory;
        if (osName.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            logBaseDirectory = localAppData != null && !localAppData.isBlank()
                    ? Paths.get(localAppData)
                    : Paths.get(System.getProperty("user.home"), "AppData", "Local");
            return ensureDirectory(logBaseDirectory.resolve(APP_NAME).resolve("logs").toAbsolutePath().normalize());
        }
        if (osName.contains("mac")) {
            return ensureDirectory(Paths.get(System.getProperty("user.home"), "Library", "Logs", APP_NAME)
                    .toAbsolutePath().normalize());
        }

        String xdgStateHome = System.getenv("XDG_STATE_HOME");
        logBaseDirectory = xdgStateHome != null && !xdgStateHome.isBlank()
                ? Paths.get(xdgStateHome)
                : Paths.get(System.getProperty("user.home"), ".local", "state");
        return ensureDirectory(logBaseDirectory.resolve(APP_NAME).resolve("logs").toAbsolutePath().normalize());
    }

    /**
     * Returns the branch-specific data directory for the currently active
     * library configuration.
     *
     * <p>Equivalent to {@code resolveDataDirectory(AppConfigurationService.getConfiguration())}.</p>
     *
     * @return the absolute path to the current branch's data directory
     */
    public static Path resolveDataDirectory() {
        return resolveDataDirectory(AppConfigurationService.getConfiguration());
    }

    /**
     * Returns the branch-specific data directory for the given configuration.
     *
     * <p>The path is built as
     * {@code <baseDataDir>/<branchId>} where {@code branchId} is a stable UUID
     * that survives branch renames.  If {@code branchId} is not yet set (e.g.
     * during first-time setup) a sanitised {@code libraryName_branchName}
     * fallback is used instead.</p>
     *
     * @param config the configuration whose branch identity is used; may be
     *               {@code null}, in which case defaults are applied
     * @return the absolute path to the branch data directory, created if absent
     */
    public static Path resolveDataDirectory(AppConfiguration config) {
        if (config == null) {
            config = new AppConfiguration();
        }
        Path baseDataDir = resolveConfiguredDirectory(config.getDataDirectory(), defaultDataDirectory());

        // Use branchId (stable UUID) rather than branchName (volatile string)
        // so that renaming a branch never orphans its data directory.
        String branchSubdir = config.getBranchId();
        if (branchSubdir == null || branchSubdir.isBlank()) {
            // Safety fallback during transitions — markSetupDone should always populate branchId
            branchSubdir = config.getLibraryName().trim() + "_" + config.getBranchName().trim();
            branchSubdir = branchSubdir.replaceAll("[\\\\/:*?\"<>|]", "_");
        }

        return ensureDirectory(baseDataDir.resolve(branchSubdir));
    }

    /**
     * Returns the export directory for the currently active library configuration.
     *
     * <p>Equivalent to
     * {@code resolveExportDirectory(AppConfigurationService.getConfiguration())}.</p>
     *
     * @return the absolute path to the current branch's export directory
     */
    public static Path resolveExportDirectory() {
        return resolveExportDirectory(AppConfigurationService.getConfiguration());
    }

    /**
     * Returns the export directory for the given configuration, honouring any
     * custom export path the administrator may have configured.
     *
     * @param config the configuration to read the export path from; may be
     *               {@code null}, in which case the default export directory
     *               is returned
     * @return the absolute path to the export directory, created if absent
     */
    public static Path resolveExportDirectory(AppConfiguration config) {
        if (config == null) {
            config = new AppConfiguration();
        }
        return resolveConfiguredDirectory(config.getExportDirectory(), defaultExportDirectory());
    }

    /**
     * Resolves a named file inside the current branch's data directory.
     *
     * <p>Convenience wrapper for {@code resolveDataDirectory().resolve(fileName)}.</p>
     *
     * @param fileName the file name (no path separators); must not be {@code null}
     * @return the absolute, normalised path to the file
     */
    public static Path resolveDataFile(String fileName) {
        return resolveDataDirectory().resolve(fileName).toAbsolutePath().normalize();
    }

    /**
     * Resolves a directory path from an administrator-supplied string, falling
     * back to a default when the string is blank or not set.
     *
     * <p>Relative paths are resolved relative to {@link #appHome()}.
     * The resulting directory is created on disk if it does not yet exist.</p>
     *
     * @param configuredDirectory the path string from the configuration; may be
     *                            {@code null} or blank
     * @param fallbackDirectory   the directory to use when
     *                            {@code configuredDirectory} is absent
     * @return the absolute, normalised path to the resolved directory
     */
    public static Path resolveConfiguredDirectory(String configuredDirectory, Path fallbackDirectory) {
        if (configuredDirectory == null || configuredDirectory.isBlank()) {
            return ensureDirectory(fallbackDirectory.toAbsolutePath().normalize());
        }

        Path configuredPath = Paths.get(configuredDirectory.trim());
        if (!configuredPath.isAbsolute()) {
            configuredPath = appHome().resolve(configuredPath);
        }
        return ensureDirectory(configuredPath.toAbsolutePath().normalize());
    }

    /**
     * Copies regular files from a legacy directory to a new target directory
     * without overwriting any files that already exist at the destination.
     *
     * <p>This method is used during version upgrades when the data layout
     * changes.  It is a no-op when the source and target are the same path or
     * when the source directory does not exist.  Individual copy failures are
     * silently ignored so that a single unreadable file does not abort the
     * entire migration.</p>
     *
     * @param legacyDirectoryName the path string of the old directory to migrate
     *                            from; may be relative or absolute
     * @param targetDirectory     the destination directory; created if absent
     */
    public static void migrateLegacyDirectoryIfNeeded(String legacyDirectoryName, Path targetDirectory) {
        Path sourceDirectory = Paths.get(legacyDirectoryName).toAbsolutePath().normalize();
        Path resolvedTarget = ensureDirectory(targetDirectory.toAbsolutePath().normalize());

        // Nothing to migrate if source == target or source doesn't exist
        if (sourceDirectory.equals(resolvedTarget) || !Files.isDirectory(sourceDirectory)) {
            return;
        }

        try (Stream<Path> stream = Files.list(sourceDirectory)) {
            stream.filter(Files::isRegularFile).forEach(sourceFile -> {
                Path targetFile = resolvedTarget.resolve(sourceFile.getFileName());
                // Never overwrite a file that already exists at the destination
                if (Files.exists(targetFile)) {
                    return;
                }
                try {
                    Files.copy(sourceFile, targetFile);
                } catch (IOException ignored) {
                    // Best-effort; a single unreadable file must not abort the migration
                }
            });
        } catch (IOException ignored) {
            // Source directory unreadable — skip migration silently
        }
    }

    /**
     * Creates the given directory (and any missing parents) on disk and returns
     * the same path.
     *
     * @param directory the directory path to create; must not be {@code null}
     * @return {@code directory}, guaranteed to exist on disk
     * @throws IllegalStateException if the directory cannot be created due to
     *                               permission or I/O errors
     */
    private static Path ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create application directory: " + directory, e);
        }
        return directory;
    }

    private static Path resolveAppHome() {
        String override = System.getProperty(HOME_OVERRIDE);
        if (override != null && !override.isBlank()) {
            return ensureDirectory(Paths.get(override.trim()).toAbsolutePath().normalize());
        }

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path baseDirectory;
        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            baseDirectory = appData != null && !appData.isBlank()
                    ? Paths.get(appData)
                    : Paths.get(System.getProperty("user.home"), "AppData", "Roaming");
        } else if (osName.contains("mac")) {
            baseDirectory = Paths.get(System.getProperty("user.home"), "Library", "Application Support");
        } else {
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            baseDirectory = xdgDataHome != null && !xdgDataHome.isBlank()
                    ? Paths.get(xdgDataHome)
                    : Paths.get(System.getProperty("user.home"), ".local", "share");
        }

        return ensureDirectory(baseDirectory.resolve(APP_NAME).toAbsolutePath().normalize());
    }
}
