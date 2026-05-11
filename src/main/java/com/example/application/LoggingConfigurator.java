package com.example.application;

import com.example.storage.AppPaths;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@code LoggingConfigurator} initialises and manages the application's
 * logging subsystem.
 *
 * <p>On the first call to {@link #configure()} it attaches a rotating
 * {@link FileHandler} to the JVM root logger so that every {@code INFO} and
 * above message is written to timestamped log files under the application's
 * {@link AppPaths#logDirectory() log directory}, in addition to the default
 * console handler that JavaFX already installs.</p>
 *
 * <p>Log rotation is handled automatically: up to {@value #LOG_FILE_COUNT}
 * files of {@value #LOG_FILE_SIZE} bytes each are kept.  Older files are
 * overwritten in round-robin order.</p>
 *
 * <p>This class is a non-instantiable utility — call {@link #configure()}
 * once from {@link com.example.application.LibraryApp#init()} before any
 * logger is used.</p>
 */
public final class LoggingConfigurator {

    /** Internal logger for tracking configuration status. */
    private static final Logger LOGGER = Logger.getLogger(LoggingConfigurator.class.getName());

    /** The file naming pattern for log rotation (e.g., {@code library-os.0.log}). */
    private static final String LOG_PATTERN =
            AppPaths.logDirectory().resolve("library-os.%g.log").toString();

    /** Maximum size of a single log file in bytes (5 MB). */
    private static final int LOG_FILE_SIZE = 5 * 1024 * 1024;

    /** Number of log files to keep in the rotation before overwriting the oldest. */
    private static final int LOG_FILE_COUNT = 5;

    /**
     * Guard flag — set to {@code true} after the first successful call to
     * {@link #configure()} so that subsequent calls are no-ops.
     * Declared {@code volatile} to ensure visibility across threads.
     */
    private static volatile boolean configured;

    /**
     * Private constructor — this class must not be instantiated.
     *
     * @throws UnsupportedOperationException always
     */
    private LoggingConfigurator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Configures the root {@link Logger} with a rotating {@link FileHandler}
     * and a plain-text formatter.
     *
     * <p>This method is idempotent: if it has already been called successfully
     * (or has failed and fallen back gracefully) any subsequent call returns
     * immediately without making further changes.</p>
     *
     * <p>Thread-safety: the method is {@code synchronized} so it is safe to
     * call from multiple threads during startup.</p>
     *
     * <p>If the log directory cannot be written (e.g. due to permission issues)
     * the error is printed to {@link System#err} and the application continues
     * with console-only logging.</p>
     */
    public static synchronized void configure() {
        if (configured) {
            return;
        }

        try {
            Logger rootLogger = Logger.getLogger("");
            // Only add the handler once — avoid duplicate log entries if called again
            if (!hasFileHandler(rootLogger)) {
                FileHandler fileHandler = new FileHandler(LOG_PATTERN, LOG_FILE_SIZE, LOG_FILE_COUNT, true);
                fileHandler.setEncoding(StandardCharsets.UTF_8.name());
                fileHandler.setLevel(Level.ALL);
                fileHandler.setFormatter(new PlainLogFormatter());
                rootLogger.addHandler(fileHandler);
            }
            rootLogger.setLevel(Level.INFO);
            configured = true;
            LOGGER.log(Level.INFO, "Logging configured at {0}", AppPaths.logDirectory());
        } catch (IOException e) {
            // Mark as configured anyway so we do not retry on every log call
            configured = true;
            System.err.println("Failed to initialize LibraryOS file logging: " + e.getMessage());
        }
    }

    /**
     * Returns {@code true} if the given {@link Logger} already has at least
     * one {@link FileHandler} attached to it.
     *
     * <p>Used to guard against attaching a second file handler on repeated
     * calls to {@link #configure()} in unusual startup sequences.</p>
     *
     * @param logger the logger whose handlers are inspected; must not be {@code null}
     * @return {@code true} if a {@code FileHandler} is already present
     */
    private static boolean hasFileHandler(Logger logger) {
        for (Handler handler : logger.getHandlers()) {
            if (handler instanceof FileHandler) {
                return true;
            }
        }
        return false;
    }

    /**
     * Single-line plain-text log formatter used by the {@link FileHandler}.
     *
     * <p>Each record is emitted as:</p>
     * <pre>
     *   YYYY-MM-DD HH:MM:SS [LEVEL] logger.name - message
     *   [optional stack trace on the next lines]
     * </pre>
     */
    private static final class PlainLogFormatter extends Formatter {

        /**
         * Formats a single {@link LogRecord} into a human-readable line.
         *
         * <p>If the record carries a {@link Throwable}, its full stack trace is
         * appended on subsequent lines after the primary message.</p>
         *
         * @param record the log record to format; must not be {@code null}
         * @return a non-null, newline-terminated formatted string
         */
        @Override
        public String format(LogRecord record) {
            // Capture the stack trace as a string if the record holds a Throwable
            String thrown = "";
            if (record.getThrown() != null) {
                StringWriter writer = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(writer));
                thrown = System.lineSeparator() + writer;
            }
            return String.format("%1$tF %1$tT [%2$s] %3$s - %4$s%5$s%n",
                    record.getMillis(),
                    record.getLevel().getName(),
                    record.getLoggerName(),
                    formatMessage(record),
                    thrown);
        }
    }
}
