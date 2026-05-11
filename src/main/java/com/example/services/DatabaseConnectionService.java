package com.example.services;

import com.example.entities.DatabaseConfiguration;
import com.example.entities.DatabaseConfiguration.Engine;
import com.example.storage.AppPaths;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.BsonBinarySubType;
import org.bson.Document;
import org.bson.types.Binary;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the optional database connection for Library OS.
 *
 * Design: file-based persistence is always the primary store.
 * When a database is configured (engine != NONE), writes are dual-written
 * to both files AND the database unless dualWrite=false, in which case
 * the database becomes the sole store.
 *
 * Call {@link #connect(DatabaseConfiguration)} when the user saves DB settings.
 * {@code DatabaseConnectionService} manages the physical connectivity to remote 
 * relational and document databases.
 * 
 * <p>It maintains connection pools and handles the transformation of domain objects 
 * into SQL/NoSQL operations.</p>
 * 
 * Call {@link #testConnection(DatabaseConfiguration)} from the settings UI.
 * Call {@link #disconnect()} on app shutdown.
 */
public final class DatabaseConnectionService {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConnectionService.class.getName());

    private static volatile Connection activeConnection;
    private static volatile MongoClient mongoClient;
    private static volatile DatabaseConfiguration activeConfig;

    private DatabaseConnectionService() {
        throw new UnsupportedOperationException();
    }

    // ══════════════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════════════

    /**
     * Connects to the database described by {@code config}.
     * Closes any existing connection first.
     *
     * @return true if connection succeeded
     */
    public static synchronized boolean connect(DatabaseConfiguration config) {
        if (config == null || !config.isConfigured()) {
            disconnect();
            LOGGER.info("Database disabled — file persistence only.");
            return false;
        }

        // Avoid redundant reconnection if already connected to the same config
        if (isConnected() && config.equals(activeConfig)) {
            return true;
        }

        disconnect();

        try {
            if (config.getEngine() == Engine.MONGODB) {
                mongoClient = openMongoClient(config);
            } else {
                activeConnection = openJdbc(config);
            }
            activeConfig = config.copy();
            LOGGER.log(Level.INFO, "Connected to {0} database.", config.getEngine().getDisplayName());
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to connect to " + config.getEngine().getDisplayName()
                    + " database: " + e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    /**
     * Tests a connection without storing it. Safe to call from a background thread.
     *
     * @return null on success, or an error message string on failure
     */
    public static String testConnection(DatabaseConfiguration config) {
        if (config == null || config.getEngine() == Engine.NONE) {
            return "No database engine selected.";
        }

        if (config.getEngine() == Engine.MONGODB) {
            return testMongoDB(config);
        }

        try (Connection c = openJdbc(config)) {
            if (c != null && c.isValid(config.getConnectionTimeout())) {
                return null; // success
            }
            return "Connection returned invalid state.";
        } catch (Exception e) {
            return friendlyError(e);
        }
    }

    /**
     * Returns true when a live connection is available.
     */
    public static boolean isConnected() {
        if (activeConfig == null) return false;

        if (activeConfig.getEngine() == Engine.MONGODB) {
            if (mongoClient == null) return false;
            // A MongoClient is always created successfully (it connects lazily).
            // We must actually ping the server to know whether the connection is live.
            try {
                mongoClient.getDatabase(activeConfig.getDatabase())
                        .runCommand(new Document("ping", 1));
                return true;
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "MongoDB ping failed — treating as disconnected", e);
                return false;
            }
        }

        try {
            return activeConnection != null && !activeConnection.isClosed() && activeConnection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Returns the active connection, or null if none is open.
     */
    public static Connection getConnection() {
        return isConnected() ? activeConnection : null;
    }

    public static DatabaseConfiguration getActiveConfig() {
        return activeConfig;
    }

    public static boolean supportsSnapshots() {
        return isConnected() && activeConfig != null;
    }

    public static void saveSnapshot(String snapshotKey, byte[] payload) {
        if (!supportsSnapshots() || snapshotKey == null || snapshotKey.isBlank() || payload == null) {
            return;
        }

        synchronized (DatabaseConnectionService.class) {
            if (!supportsSnapshots()) {
                return;
            }
            
            byte[] encryptedPayload = SecurityProvider.encryptBytes(payload);
            
            if (activeConfig.getEngine() == Engine.MONGODB) {
                saveMongoSnapshot(snapshotKey, encryptedPayload);
                return;
            }

            try {
                ensureSnapshotTable(activeConnection, activeConfig.getEngine());
                try (PreparedStatement statement = activeConnection.prepareStatement(snapshotUpsertSql(activeConfig.getEngine()))) {
                    statement.setString(1, snapshotKey.trim());
                    statement.setBytes(2, encryptedPayload);
                    if (activeConfig.getEngine() == Engine.ORACLE) {
                        statement.setString(3, snapshotKey.trim());
                        statement.setBytes(4, encryptedPayload);
                    }
                    statement.executeUpdate();
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to mirror snapshot to database: " + snapshotKey, ex);
            }
        }
    }

    public static byte[] loadSnapshot(String snapshotKey) {
        if (!supportsSnapshots() || snapshotKey == null || snapshotKey.isBlank()) {
            return null;
        }

        synchronized (DatabaseConnectionService.class) {
            if (!supportsSnapshots()) {
                return null;
            }
            
            byte[] rawPayload = null;
            if (activeConfig.getEngine() == Engine.MONGODB) {
                rawPayload = loadMongoSnapshot(snapshotKey);
            } else {
                try {
                    ensureSnapshotTable(activeConnection, activeConfig.getEngine());
                    try (PreparedStatement statement = activeConnection.prepareStatement(
                            "SELECT payload FROM libraryos_snapshots WHERE snapshot_key = ?")) {
                        statement.setString(1, snapshotKey.trim());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                rawPayload = resultSet.getBytes(1);
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failed to load snapshot from database: " + snapshotKey, ex);
                }
            }

            return rawPayload != null ? SecurityProvider.decryptBytes(rawPayload) : null;
        }
    }

    /**
     * Closes the active connection. Safe to call multiple times.
     */
    public static synchronized void disconnect() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
                LOGGER.info("MongoDB connection closed.");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing MongoDB client", e);
            } finally {
                mongoClient = null;
            }
        }

        if (activeConnection != null) {
            try {
                activeConnection.close();
                LOGGER.info("JDBC connection closed.");
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing JDBC connection", e);
            } finally {
                activeConnection = null;
            }
        }
        activeConfig = null;
    }

    // ══════════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════════

    private static MongoClient openMongoClient(DatabaseConfiguration config) {
        ConnectionString connString = new ConnectionString(config.buildMongoUri());
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .applyToSocketSettings(builder -> 
                    builder.connectTimeout(config.getConnectionTimeout(), TimeUnit.SECONDS))
                .build();
        return MongoClients.create(settings);
    }

    private static Connection openJdbc(DatabaseConfiguration config) throws SQLException {
        String url = resolveJdbcUrl(config);
        LOGGER.log(Level.FINE, "Connecting to JDBC URL: {0}", url.replaceAll("password=[^&]+", "password=***"));

        // SQLite is a file-based database — it has no server-side authentication.
        // Always connect without credentials regardless of what the config holds.
        // (Decryption failures after migration can leave non-empty garbled values
        // in getUsername(), which would otherwise trigger a spurious auth error.)
        if (config.getEngine() == Engine.SQLITE) {
            DriverManager.setLoginTimeout(config.getConnectionTimeout());
            return DriverManager.getConnection(url);
        }

        String user = config.getUsername();
        String pass = config.getPassword();

        DriverManager.setLoginTimeout(config.getConnectionTimeout());

        if (user.isEmpty()) {
            return DriverManager.getConnection(url);
        }
        return DriverManager.getConnection(url, user, pass);
    }

    private static String resolveJdbcUrl(DatabaseConfiguration config) {
        if (config.getEngine() == Engine.SQLITE) {
            // Resolve SQLite as an application-level database, not a branch data
            // file. Startup uses this connection before any library is selected
            // so branch-scoped resolution would point at a random/default branch.
            String sqliteFile = config.getSqliteFile();
            Path sqlitePath = Paths.get(sqliteFile);
            if (!sqlitePath.isAbsolute()) {
                sqliteFile = AppPaths.appHome().resolve(sqlitePath).toAbsolutePath().normalize().toString();
            }
            return "jdbc:sqlite:" + sqliteFile;
        }
        return config.buildJdbcUrl();
    }

    private static void ensureSnapshotTable(Connection connection, Engine engine) throws SQLException {
        if (connection == null || engine == null || engine == Engine.NONE || engine == Engine.MONGODB) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(snapshotTableDdl(engine));
        } catch (SQLException ex) {
            if (engine == Engine.ORACLE && ex.getMessage() != null && ex.getMessage().contains("ORA-00955")) {
                return;
            }
            throw ex;
        }
    }

    private static String snapshotTableDdl(Engine engine) {
        return switch (engine) {
            case POSTGRESQL -> """
                    CREATE TABLE IF NOT EXISTS libraryos_snapshots (
                        snapshot_key VARCHAR(190) PRIMARY KEY,
                        payload BYTEA NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS libraryos_snapshots (
                        snapshot_key VARCHAR(190) PRIMARY KEY,
                        payload LONGBLOB NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """;
            case ORACLE -> """
                    CREATE TABLE libraryos_snapshots (
                        snapshot_key VARCHAR2(190) PRIMARY KEY,
                        payload BLOB NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
            default -> """
                    CREATE TABLE IF NOT EXISTS libraryos_snapshots (
                        snapshot_key TEXT PRIMARY KEY,
                        payload BLOB NOT NULL,
                        updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
        };
    }

    private static String snapshotUpsertSql(Engine engine) {
        return switch (engine) {
            case MYSQL -> """
                    INSERT INTO libraryos_snapshots (snapshot_key, payload, updated_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE payload = VALUES(payload), updated_at = CURRENT_TIMESTAMP
                    """;
            case POSTGRESQL -> """
                    INSERT INTO libraryos_snapshots (snapshot_key, payload, updated_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (snapshot_key)
                    DO UPDATE SET payload = EXCLUDED.payload, updated_at = CURRENT_TIMESTAMP
                    """;
            case ORACLE -> """
                    MERGE INTO libraryos_snapshots target
                    USING (
                        SELECT ? snapshot_key, ? payload FROM dual
                    ) source
                    ON (target.snapshot_key = source.snapshot_key)
                    WHEN MATCHED THEN
                        UPDATE SET target.payload = source.payload, target.updated_at = CURRENT_TIMESTAMP
                    WHEN NOT MATCHED THEN
                        INSERT (snapshot_key, payload, updated_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                    """;
            default -> """
                    INSERT INTO libraryos_snapshots (snapshot_key, payload, updated_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(snapshot_key)
                    DO UPDATE SET payload = excluded.payload, updated_at = CURRENT_TIMESTAMP
                    """;
        };
    }

    private static void saveMongoSnapshot(String key, byte[] payload) {
        try {
            MongoDatabase database = mongoClient.getDatabase(activeConfig.getDatabase());
            MongoCollection<Document> collection = database.getCollection("libraryos_snapshots");

            // BsonBinarySubType.BINARY (0x00) is the correct subtype for arbitrary bytes.
            // Using new Binary(payload) defaults to subtype 0 which is correct,
            // but being explicit guards against driver version differences.
            Document doc = new Document("_id", key)
                    .append("snapshot_key", key)
                    .append("payload", new Binary(org.bson.BsonBinarySubType.BINARY, payload))
                    .append("updated_at", new java.util.Date());

            collection.replaceOne(
                    new Document("_id", key),
                    doc,
                    new ReplaceOptions().upsert(true));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to save MongoDB snapshot: " + key, ex);
        }
    }

    private static byte[] loadMongoSnapshot(String key) {
        try {
            MongoDatabase database = mongoClient.getDatabase(activeConfig.getDatabase());
            MongoCollection<Document> collection = database.getCollection("libraryos_snapshots");

            Document doc = collection.find(new Document("_id", key)).first();
            if (doc == null) return null;

            Object payloadObj = doc.get("payload");
            // The MongoDB Java driver may return the stored binary as either
            // org.bson.types.Binary or as a raw byte[], depending on the driver
            // version and codec configuration. Handle both.
            if (payloadObj instanceof Binary bin) {
                return bin.getData();
            } else if (payloadObj instanceof byte[] raw) {
                return raw;
            } else if (payloadObj != null) {
                LOGGER.log(Level.WARNING,
                        "Unexpected payload type for snapshot ''{0}'': {1}",
                        new Object[]{key, payloadObj.getClass().getName()});
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to load MongoDB snapshot: " + key, ex);
        }
        return null;
    }

    private static String testMongoDB(DatabaseConfiguration config) {
        try (MongoClient testClient = openMongoClient(config)) {
            // Ping the database to verify connectivity
            testClient.getDatabase(config.getDatabase()).runCommand(new Document("ping", 1));
            return null; // success
        } catch (Exception e) {
            return "MongoDB connection failed: " + e.getMessage();
        }
    }

    private static String friendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) msg = e.getClass().getSimpleName();
        if (msg.contains("refused") || msg.contains("Connection refused")) {
            return "Connection refused — is the database server running on the configured host/port?";
        }
        if (msg.contains("Unknown database") || msg.contains("does not exist")) {
            return "Database does not exist. Create it first: " + msg;
        }
        if (msg.contains("Access denied") || msg.contains("authentication")) {
            return "Authentication failed — check username and password.";
        }
        if (msg.contains("No suitable driver")) {
            return "JDBC driver not found. Ensure the dependency is in pom.xml and re-run mvn package.";
        }
        if (msg.contains("ORA-")) {
            return "Oracle connection failed: " + msg;
        }
        return msg;
    }
}
