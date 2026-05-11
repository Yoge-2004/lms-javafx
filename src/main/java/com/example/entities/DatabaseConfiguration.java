package com.example.entities;

import java.io.Serializable;

/**
 * The {@code DatabaseConfiguration} class encapsulates all parameters required to establish 
 * a connection to a local or remote database engine.
 * 
 * <p>Supported Engines:
 * <ul>
 *   <li><b>SQLITE:</b> Local file-based relational database.</li>
 *   <li><b>MYSQL/POSTGRESQL:</b> Centralized network-based relational databases.</li>
 *   <li><b>MONGODB:</b> Document-based NoSQL storage.</li>
 * </ul>
 * 
 * <p>This class includes <b>Transparent Encryption</b> for credentials when stored.</p>
 */
public final class DatabaseConfiguration implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Engine {
        NONE("File only (no database)"),
        SQLITE("SQLite (embedded)"),
        MYSQL("MySQL / MariaDB"),
        POSTGRESQL("PostgreSQL"),
        ORACLE("Oracle Database"),
        MONGODB("MongoDB");

        private final String displayName;

        Engine(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        /** Default JDBC port for this engine (0 = not applicable). */
        public int defaultPort() {
            return switch (this) {
                case MYSQL      -> 3306;
                case POSTGRESQL -> 5432;
                case ORACLE     -> 1521;
                case MONGODB    -> 27017;
                default         -> 0;
            };
        }

        /** Whether this engine needs host/port/credentials. */
        public boolean isRemote() {
            return this == MYSQL || this == POSTGRESQL || this == ORACLE || this == MONGODB;
        }
    }

    // ── Which backend to use
    private Engine engine = Engine.NONE;

    // ── SQLite-only: file path (absolute or relative to AppPaths.dataDirectory)
    private String sqliteFile = "library.db";

    // ── Remote engines (MySQL, PostgreSQL, MongoDB)
    private String host     = "localhost";
    private int    port     = 0;           // 0 = use engine default
    private String database = "libraryos";
    private String username = "";
    private String password = "";

    // ── Connection pool
    private int connectionTimeout  = 10;   // seconds
    private int maxPoolSize        = 5;
    private boolean sslEnabled     = false;

    // ── Whether DB writes shadow file persistence (dual-write) or replace it
    private boolean dualWrite      = true;

    // ══ Getters / Setters ═══════════════════════════════════════════

    public Engine getEngine()               { return engine != null ? engine : Engine.NONE; }
    public void   setEngine(Engine v)       { engine = v != null ? v : Engine.NONE; }

    public String getSqliteFile()           { return sqliteFile != null ? sqliteFile : "library.db"; }
    public void   setSqliteFile(String v)   { sqliteFile = blank(v, "library.db"); }

    public String getHost()                 { return host != null ? host : "localhost"; }
    public void   setHost(String v)         { host = blank(v, "localhost"); }

    public int    getPort()                 { return port > 0 ? port : getEngine().defaultPort(); }
    public void   setPort(int v)            { port = Math.max(0, v); }

    public String getDatabase()             { return database != null ? database : "libraryos"; }
    public void   setDatabase(String v)     { database = blank(v, "libraryos"); }

    public String getUsername()             { return username != null ? username : ""; }
    public void   setUsername(String v)     { username = v != null ? v.trim() : ""; }

    public String getPassword()             { return password != null ? password : ""; }
    public void   setPassword(String v)     { password = v != null ? v : ""; }

    public int    getConnectionTimeout()    { return connectionTimeout > 0 ? connectionTimeout : 10; }
    public void   setConnectionTimeout(int v){ connectionTimeout = Math.max(1, v); }

    public int    getMaxPoolSize()          { return maxPoolSize > 0 ? maxPoolSize : 5; }
    public void   setMaxPoolSize(int v)     { maxPoolSize = Math.max(1, v); }

    public boolean isSslEnabled()           { return sslEnabled; }
    public void    setSslEnabled(boolean v) { sslEnabled = v; }

    public boolean isDualWrite()            { return dualWrite; }
    public void    setDualWrite(boolean v)  { dualWrite = v; }

    /** Builds a JDBC URL for SQL engines. Returns empty string for NONE/MONGODB. */
    public String buildJdbcUrl() {
        return switch (getEngine()) {
            case SQLITE     -> "jdbc:sqlite:" + getSqliteFile();
            case MYSQL      -> "jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getDatabase()
                    + "?useSSL=" + sslEnabled + "&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            case POSTGRESQL -> "jdbc:postgresql://" + getHost() + ":" + getPort() + "/" + getDatabase()
                    + (sslEnabled ? "?sslmode=require" : "");
            case ORACLE     -> "jdbc:oracle:thin:@//" + getHost() + ":" + getPort() + "/" + getDatabase();
            default         -> "";
        };
    }

    /** MongoDB connection string. */
    public String buildMongoUri() {
        if (getEngine() != Engine.MONGODB) return "";
        String creds = "";
        if (!getUsername().isEmpty()) {
            // URL-encode credentials so special characters (@ : / ? # [ ]) don't corrupt the URI
            try {
                String encodedUser = java.net.URLEncoder.encode(getUsername(), java.nio.charset.StandardCharsets.UTF_8);
                String encodedPass = java.net.URLEncoder.encode(getPassword(), java.nio.charset.StandardCharsets.UTF_8);
                creds = encodedUser + ":" + encodedPass + "@";
            } catch (Exception e) {
                // URLEncoder.encode with StandardCharsets cannot throw — fallback is raw (unsafe for special chars)
                creds = getUsername() + ":" + getPassword() + "@";
            }
        }
        String ssl = sslEnabled ? "?tls=true&tlsInsecure=true" : "";
        return "mongodb://" + creds + getHost() + ":" + getPort() + "/" + getDatabase() + ssl;
    }

    public boolean isConfigured() {
        if (getEngine() == Engine.NONE) return false;
        if (getEngine() == Engine.SQLITE) {
            return getSqliteFile() != null && !getSqliteFile().isBlank();
        }
        if (getEngine() == Engine.MONGODB || getEngine() == Engine.MYSQL
                || getEngine() == Engine.POSTGRESQL) {
            return getHost() != null && !getHost().isBlank()
                && getDatabase() != null && !getDatabase().isBlank();
        }
        return true;
    }

    public DatabaseConfiguration copy() {
        DatabaseConfiguration copy = new DatabaseConfiguration();
        copy.setEngine(getEngine());
        copy.setSqliteFile(getSqliteFile());
        copy.setHost(getHost());
        copy.setPort(port);
        copy.setDatabase(getDatabase());
        copy.setUsername(getUsername());
        copy.setPassword(getPassword());
        copy.setConnectionTimeout(getConnectionTimeout());
        copy.setMaxPoolSize(getMaxPoolSize());
        copy.setSslEnabled(isSslEnabled());
        copy.setDualWrite(isDualWrite());
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DatabaseConfiguration that)) {
            return false;
        }
        return getEngine() == that.getEngine()
                && port == that.port
                && getConnectionTimeout() == that.getConnectionTimeout()
                && getMaxPoolSize() == that.getMaxPoolSize()
                && isSslEnabled() == that.isSslEnabled()
                && isDualWrite() == that.isDualWrite()
                && getSqliteFile().equals(that.getSqliteFile())
                && getHost().equals(that.getHost())
                && getDatabase().equals(that.getDatabase())
                && getUsername().equals(that.getUsername())
                && getPassword().equals(that.getPassword());
    }

    @Override
    public int hashCode() {
        int result = getEngine().hashCode();
        result = 31 * result + getSqliteFile().hashCode();
        result = 31 * result + getHost().hashCode();
        result = 31 * result + port;
        result = 31 * result + getDatabase().hashCode();
        result = 31 * result + getUsername().hashCode();
        result = 31 * result + getPassword().hashCode();
        result = 31 * result + getConnectionTimeout();
        result = 31 * result + getMaxPoolSize();
        result = 31 * result + Boolean.hashCode(isSslEnabled());
        result = 31 * result + Boolean.hashCode(isDualWrite());
        return result;
    }

    // ── Helpers ─────────────────────────────────────────────────────
    private static String blank(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    /**
     * CUSTOM SERIALIZATION: Transparently encrypts sensitive fields before they hit the disk.
     */
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        String originalUser = this.username;
        String originalPass = this.password;

        this.username = com.example.services.SecurityProvider.encrypt(this.username);
        this.password = com.example.services.SecurityProvider.encrypt(this.password);

        out.defaultWriteObject();

        this.username = originalUser;
        this.password = originalPass;
    }

    /**
     * CUSTOM DESERIALIZATION: Transparently decrypts sensitive fields after reading from disk.
     */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        decryptFields();
    }

    /**
     * Attempts to decrypt sensitive fields. Can be called multiple times safely.
     */
    public void decryptFields() {
        this.username = com.example.services.SecurityProvider.decrypt(this.username);
        this.password = com.example.services.SecurityProvider.decrypt(this.password);
    }
}
