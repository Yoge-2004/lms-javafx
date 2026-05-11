package com.example.entities;

import com.example.storage.AppPaths;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The {@code AppConfiguration} class represents the global settings for the Library OS.
 * It manages directory paths, active library selection, and links to database configurations.
 * 
 * <p>This object is the "Root Configuration" and is persisted as the primary state 
 * of the application across sessions.</p>
 * 
 * @author Library OS Development Team
 */
public final class AppConfiguration implements Serializable {
    private static final long serialVersionUID = 3L;
    private static final List<Integer> COMMON_SMTP_PORTS = List.of(25, 465, 587, 2525);

    // ── Library identity
    private String libraryId      = UUID.randomUUID().toString();
    private String libraryName    = "Select Library";
    private String branchId       = UUID.randomUUID().toString();
    private String branchName     = "Main Branch";

    // ── Data storage
    private String dataDirectory   = AppPaths.defaultDataDirectory().toString();
    private String exportDirectory = AppPaths.defaultExportDirectory().toString();

    // ── UI preferences
    private BranchConfiguration branchConfig = new BranchConfiguration();
    private boolean darkMode            = false;

    // ── Optional database persistence
    private DatabaseConfiguration databaseConfiguration = new DatabaseConfiguration();

    // ── Security & Portability
    private String libraryMasterKey = null;

    // ── First-run flag
    private boolean initialSetupDone    = false;
    @SuppressWarnings("serial") // ArrayList IS Serializable; lint fires on List interface
    private List<String> savedCategories = new ArrayList<>();

    // ════════════════════════════════════════════════════════════════
    // Library identity
    // ════════════════════════════════════════════════════════════════
    public String getLibraryId()               { return libraryId; }
    public String getLibraryName()             { return libraryName; }
    public void   setLibraryName(String v)     { libraryName  = blankOr(v, "My Library"); }

    public String getBranchId()                { return branchId; }
    public String getBranchName()              { return branchName; }
    public void   setBranchName(String v)      { branchName   = blankOr(v, "Main Branch"); }

    public String getCurrentLibraryDisplayName() {
        return getLibraryName() + " - " + getBranchName();
    }

    // ════════════════════════════════════════════════════════════════
    // Data storage
    // ════════════════════════════════════════════════════════════════
    public String getDataDirectory()           { return dataDirectory; }
    public void   setDataDirectory(String v)   {
        dataDirectory = AppPaths.resolveConfiguredDirectory(blankToNull(v), AppPaths.defaultDataDirectory())
                .toString();
    }

    public String getExportDirectory()         { return exportDirectory; }
    public void   setExportDirectory(String v) {
        exportDirectory = AppPaths.resolveConfiguredDirectory(blankToNull(v), AppPaths.defaultExportDirectory())
                .toString();
    }

    // ════════════════════════════════════════════════════════════════
    // Fine / currency / SMTP (Delegated to BranchConfiguration)
    // ════════════════════════════════════════════════════════════════
    public BranchConfiguration getBranchConfig() {
        if (branchConfig == null) {
            branchConfig = new BranchConfiguration();
            branchConfig.normalize();
        }
        return branchConfig;
    }

    public void setBranchConfig(BranchConfiguration v) {
        this.branchConfig = v != null ? v : new BranchConfiguration();
        this.branchConfig.normalize();
    }

    public double getFinePerDay()              { return getBranchConfig().getFinePerDay(); }
    public void   setFinePerDay(double v)      { getBranchConfig().setFinePerDay(v); }

    public String getCurrencySymbol()          { return getBranchConfig().getCurrencySymbol(); }
    public void   setCurrencySymbol(String v)  { getBranchConfig().setCurrencySymbol(v); }

    public String getCurrencyCode()            { return getBranchConfig().getCurrencyCode(); }
    public void   setCurrencyCode(String v)    { getBranchConfig().setCurrencyCode(v); }

    public String formatAmount(double amount)  { return getCurrencySymbol() + String.format("%,.2f", amount); }

    public String  getSmtpHost()               { return getBranchConfig().getSmtpHost(); }
    public void    setSmtpHost(String v)       { getBranchConfig().setSmtpHost(v); }

    public int     getSmtpPort()               { return getBranchConfig().getSmtpPort(); }
    public void    setSmtpPort(int v)          { getBranchConfig().setSmtpPort(v); }

    public String  getSmtpUsername()           { return getBranchConfig().getSmtpUsername(); }
    public void    setSmtpUsername(String v)   { getBranchConfig().setSmtpUsername(v); }

    public String  getSmtpPassword()           { return getBranchConfig().getSmtpPassword(); }
    public void    setSmtpPassword(String v)   { getBranchConfig().setSmtpPassword(v); }

    public String  getFromAddress()            { return getBranchConfig().getFromAddress(); }
    public void    setFromAddress(String v)    { getBranchConfig().setFromAddress(v); }

    public boolean isSmtpAuth()                { return getBranchConfig().isSmtpAuth(); }
    public void    setSmtpAuth(boolean v)      { getBranchConfig().setSmtpAuth(v); }

    public boolean isStartTlsEnabled()         { return getBranchConfig().isStartTlsEnabled(); }
    public void    setStartTlsEnabled(boolean v){ getBranchConfig().setStartTlsEnabled(v); }

    public boolean isEmailConfigured()         { return getBranchConfig().isEmailConfigured(); }

    public List<Integer> getCommonSmtpPorts() {
        int port = getSmtpPort();
        if (COMMON_SMTP_PORTS.contains(port)) {
            return COMMON_SMTP_PORTS;
        }
        List<Integer> ports = new ArrayList<>(COMMON_SMTP_PORTS);
        ports.add(port);
        ports.sort(Integer::compareTo);
        return List.copyOf(ports);
    }

    // ════════════════════════════════════════════════════════════════
    // UI
    // ════════════════════════════════════════════════════════════════
    public boolean isDarkMode()                { return darkMode; }
    public void    setDarkMode(boolean v)      { darkMode = v; }
    public void    toggleDarkMode()            { darkMode = !darkMode; }

    public DatabaseConfiguration getDatabaseConfiguration() {
        if (databaseConfiguration == null) {
            databaseConfiguration = new DatabaseConfiguration();
        }
        return databaseConfiguration;
    }

    public void setDatabaseConfiguration(DatabaseConfiguration databaseConfiguration) {
        this.databaseConfiguration = databaseConfiguration != null
                ? databaseConfiguration
                : new DatabaseConfiguration();
    }

    public String getLibraryMasterKey() {
        return libraryMasterKey;
    }

    public void setLibraryMasterKey(String libraryMasterKey) {
        this.libraryMasterKey = libraryMasterKey;
    }

    // ════════════════════════════════════════════════════════════════
    // First-run setup
    // ════════════════════════════════════════════════════════════════
    public boolean isInitialSetupDone()        { return initialSetupDone; }
    public void    markSetupDone()             { initialSetupDone = true; }

    public List<String> getKnownLibraries() {
        return LibrariesDB.getInstance().getLibraries();
    }




    public void rememberCurrentLibrary() {
        LibrariesDB.getInstance().addLibrary(
                getLibraryName(), getBranchName(), getBranchId(), getDataDirectory(), getExportDirectory());
    }

    public boolean selectKnownLibrary(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return false;
        }

        LibrariesDB.LibraryEntry entry = LibrariesDB.getInstance().findEntry(displayName);
        if (entry != null) {
            setLibraryName(entry.libraryName());
            setBranchName(entry.branchName());
            this.branchId = entry.branchId();
            if (entry.dataDirectory() != null && !entry.dataDirectory().isBlank()) {
                setDataDirectory(entry.dataDirectory());
            }
            if (entry.exportDirectory() != null && !entry.exportDirectory().isBlank()) {
                setExportDirectory(entry.exportDirectory());
            }
            return true;
        }

        // Fallback to parsing if not found in DB (unlikely)
        String trimmed = displayName.trim();
        LibraryIdentity parsed = parseDisplayName(trimmed);
        if (parsed != null) {
            setLibraryName(parsed.libraryName());
            setBranchName(parsed.branchName());
            // Note: branchId will remain whatever it was, or be generated in normalize()
            rememberCurrentLibrary();
            return true;
        }
        return false;
    }

    public List<String> getSavedCategories() {
        ensureSavedCategories();
        return List.copyOf(savedCategories);
    }

    public void setSavedCategories(List<String> categories) {
        savedCategories = new ArrayList<>();
        if (categories != null) {
            for (String category : categories) {
                addSavedCategory(category);
            }
        }
        ensureSavedCategories();
    }

    public void rememberCategory(String category) {
        addSavedCategory(category);
        ensureSavedCategories();
    }

    public void normalize() {
        if (libraryId == null || libraryId.isBlank()) {
            libraryId = UUID.randomUUID().toString();
        }
        if (branchId == null || branchId.isBlank()) {
            branchId = UUID.randomUUID().toString();
        }
        if (branchConfig == null) branchConfig = new BranchConfiguration();
        branchConfig.normalize();
        branchConfig.decryptFields();
        setLibraryName(libraryName);
        setBranchName(branchName);
        setDataDirectory(dataDirectory);
        setExportDirectory(exportDirectory);
        if (databaseConfiguration == null) databaseConfiguration = new DatabaseConfiguration();
        databaseConfiguration.decryptFields();
        setDatabaseConfiguration(databaseConfiguration);
        setLibraryMasterKey(libraryMasterKey);
        ensureSavedCategories();
    }

    // ════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════
    private void ensureSavedCategories() {
        if (savedCategories == null) {
            savedCategories = new ArrayList<>();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String category : savedCategories) {
            if (category != null && !category.isBlank()) {
                unique.add(category.trim());
            }
        }
        savedCategories = new ArrayList<>(unique);
    }

    private void addSavedCategory(String category) {
        if (category == null || category.isBlank()) {
            return;
        }
        if (savedCategories == null) {
            savedCategories = new ArrayList<>();
        }
        String trimmed = category.trim();
        savedCategories.removeIf(existing -> existing.equalsIgnoreCase(trimmed));
        savedCategories.add(trimmed);
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
    private static String blankOr(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    private static LibraryIdentity parseDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        String trimmed = displayName.trim();
        int separator = trimmed.lastIndexOf(" - ");
        if (separator <= 0 || separator >= trimmed.length() - 3) {
            return new LibraryIdentity(trimmed, "Main Branch");
        }
        return new LibraryIdentity(trimmed.substring(0, separator), trimmed.substring(separator + 3));
    }

    private static final class LibraryIdentity implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String libraryName;
        private final String branchName;

        private LibraryIdentity(String libraryName, String branchName) {
            this.libraryName = blankOr(libraryName, "My Library");
            this.branchName = blankOr(branchName, "Main Branch");
        }

        private String libraryName() {
            return libraryName;
        }

        private String branchName() {
            return branchName;
        }



        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LibraryIdentity that)) {
                return false;
            }
            return libraryName.equalsIgnoreCase(that.libraryName)
                    && branchName.equalsIgnoreCase(that.branchName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(libraryName.toLowerCase(), branchName.toLowerCase());
        }
    }
}
