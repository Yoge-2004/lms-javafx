package com.example.services;

import com.example.entities.AppConfiguration;
import com.example.storage.AppPaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The {@code MigrationService} provides high-level logic for moving library data 
 * between different physical machines while preserving security integrity.
 */
public final class MigrationService {
    private static final Logger LOGGER = Logger.getLogger(MigrationService.class.getName());
    
    /** Version header for the migration package format to support future upgrades. */
    private static final byte[] PKG_HEADER = "LMS-V2".getBytes(StandardCharsets.UTF_8);

    private MigrationService() {}

    /**
     * Bundles all local database files into a dual-locked migration package.
     * <p>The package is encrypted with a random session key, which is then wrapped 
     * using the Administrative Secret.</p>
     * 
     * @param targetFile the path to save the .lms package
     * @param adminSecret the secret to lock the package with
     * @throws Exception if archiving or encryption fails
     */
    public static void prepareMigrationPackage(File targetFile, String adminSecret) throws Exception {
        Path dataDir   = AppPaths.resolveDataDirectory();
        Path configDir = AppPaths.configDirectory();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // Bundle all branch-specific .ser files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "*.ser")) {
                for (Path file : stream) {
                    ZipEntry entry = new ZipEntry("data/" + file.getFileName().toString());
                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                }
            }

            // Bundle the library-scoped app config (contains DB credentials,
            // email settings, and the libraryMasterKey used for PII encryption).
            Path libraryAppConfig = AppPaths.libraryConfigFile(
                    AppConfigurationService.getConfiguration());
            if (Files.exists(libraryAppConfig)) {
                ZipEntry entry = new ZipEntry("config/library_app_config.ser");
                zos.putNextEntry(entry);
                Files.copy(libraryAppConfig, zos);
                zos.closeEntry();
            }

            // Bundle the global app config as a fallback
            Path appConfig = AppPaths.configFile();
            if (Files.exists(appConfig)) {
                ZipEntry entry = new ZipEntry("config/app_config.ser");
                zos.putNextEntry(entry);
                Files.copy(appConfig, zos);
                zos.closeEntry();
            }

            // Bundle libraries registry (contains library/branch definitions)
            Path librariesDb = configDir.resolve("libraries_db.ser");
            if (Files.exists(librariesDb)) {
                ZipEntry entry = new ZipEntry("config/libraries_db.ser");
                zos.putNextEntry(entry);
                Files.copy(librariesDb, zos);
                zos.closeEntry();
            }

            // Bundle a human-readable library info file so the target can auto-select
            // the correct library name from the LibrariesDB after import
            AppConfiguration currentCfg = AppConfigurationService.getConfiguration();
            String libraryInfo = currentCfg.getLibraryName() + "\n" + currentCfg.getBranchName()
                    + "\n" + currentCfg.getBranchId();
            ZipEntry libInfoEntry = new ZipEntry("meta/library_info.txt");
            zos.putNextEntry(libInfoEntry);
            zos.write(libraryInfo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            // NOT the admin secret/password. These are different values:
            //   - adminSecret  = what the librarian types to lock this package
            //   - libraryMasterKey = the AES key used to encrypt PII (user email/mobile)
            //
            // On the target machine SecurityProvider.setLibraryMasterKey(embeddedKey)
            // must be called with the PII key, otherwise decryptUserField() will fail
            // with an AES-GCM tag-verification error for every user's email and phone.
            String realMasterKey = AppConfigurationService.getConfiguration().getLibraryMasterKey();
            String keyToEmbed = (realMasterKey != null && !realMasterKey.isBlank())
                    ? realMasterKey
                    : adminSecret; // fallback: if no separate key configured, admin secret doubles as it

            ZipEntry keyEntry = new ZipEntry("meta/master_key.txt");
            zos.putNextEntry(keyEntry);
            zos.write(keyToEmbed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.finish();

            byte[] fileData      = baos.toByteArray();
            byte[] encryptedData = SecurityProvider.encryptBytesWithPassword(fileData, adminSecret);

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(PKG_HEADER);
                fos.write(encryptedData);
            }

            LOGGER.info("Migration package created: " + targetFile.getName());
        }
    }

    /**
     * Unpacks an encrypted migration package and restores the database files,
     * app configuration, library registry, and master encryption key.
     */
    public static void importMigrationPackage(File packageFile, String password) throws Exception {
        byte[] raw = Files.readAllBytes(packageFile.toPath());

        byte[] encryptedData;
        if (startsWith(raw, PKG_HEADER)) {
            encryptedData = new byte[raw.length - PKG_HEADER.length];
            System.arraycopy(raw, PKG_HEADER.length, encryptedData, 0, encryptedData.length);
        } else {
            encryptedData = raw; // Legacy V1
        }

        byte[] decryptedData = SecurityProvider.decryptBytesWithPassword(encryptedData, password);

        Path dataDir   = AppPaths.resolveDataDirectory();
        Path configDir = AppPaths.configDirectory();
        Files.createDirectories(dataDir);
        Files.createDirectories(configDir);

        String embeddedKey = null;
        String[] libraryInfo = null; // [libraryName, branchName, branchId]

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(decryptedData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("data/")) {
                    Path target = dataDir.resolve(name.substring("data/".length()));
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);

                } else if (name.equals("config/library_app_config.ser")) {
                    Path libCfg = AppPaths.libraryConfigFile(
                            AppConfigurationService.getConfiguration());
                    Files.createDirectories(libCfg.getParent());
                    Files.copy(zis, libCfg, StandardCopyOption.REPLACE_EXISTING);

                } else if (name.equals("config/app_config.ser")) {
                    Files.copy(zis, AppPaths.configFile(), StandardCopyOption.REPLACE_EXISTING);

                } else if (name.equals("config/libraries_db.ser")) {
                    Files.copy(zis, configDir.resolve("libraries_db.ser"),
                            StandardCopyOption.REPLACE_EXISTING);

                } else if (name.equals("meta/master_key.txt")) {
                    embeddedKey = new String(zis.readAllBytes(), StandardCharsets.UTF_8).trim();

                } else if (name.equals("meta/library_info.txt")) {
                    String raw2 = new String(zis.readAllBytes(), StandardCharsets.UTF_8).trim();
                    libraryInfo = raw2.split("\n", 3);

                } else if (!name.isEmpty() && !name.endsWith("/")) {
                    // Legacy V1: no path prefix — all files go to data dir
                    Path target = dataDir.resolve(name);
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        // Apply the correct PII master key.
        String keyToApply = (embeddedKey != null && !embeddedKey.isBlank())
                ? embeddedKey : password;

        SecurityProvider.setLibraryMasterKey(keyToApply);

        try {
            AppConfigurationService.reloadConfiguration();
            com.example.entities.AppConfiguration cfg = AppConfigurationService.getConfiguration();
            cfg.setLibraryMasterKey(keyToApply);
            // Restore library identity from meta/library_info.txt if available
            if (libraryInfo != null && libraryInfo.length >= 2) {
                if (cfg.getLibraryName() == null || cfg.getLibraryName().isBlank())
                    cfg.setLibraryName(libraryInfo[0].trim());
                if (cfg.getBranchName() == null || cfg.getBranchName().isBlank())
                    cfg.setBranchName(libraryInfo[1].trim());
            }
            AppConfigurationService.updateConfiguration(cfg);
        } catch (Exception e) {
            LOGGER.warning("Could not persist master key into AppConfiguration after import: "
                    + e.getMessage());
        }

        LOGGER.info("Migration package imported successfully: " + packageFile.getName());
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
