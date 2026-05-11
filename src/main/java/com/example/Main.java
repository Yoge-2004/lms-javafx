package com.example;

import com.example.application.LibraryApp;
import javafx.application.Application;

/**
 * Application entry point for Library OS.
 *
 * <p>This class performs three responsibilities before delegating control to
 * {@link LibraryApp}:</p>
 * <ol>
 *   <li><b>Desktop identity:</b> Calls
 *       {@link LibraryApp#configureDesktopIdentity()} to set the WM_CLASS /
 *       app-id properties required by GNOME, KDE Plasma, and macOS so that the
 *       taskbar icon, window grouping, and dock entry all display "Library OS"
 *       rather than a generic Java identifier.</li>
 *   <li><b>HiDPI rendering:</b> Enables {@code prism.allowhidpi} so JavaFX
 *       renders at native pixel density on 4K and Retina displays.</li>
 *   <li><b>JDBC driver pre-loading:</b> Attempts to load the MySQL and
 *       PostgreSQL drivers so they are registered with {@link java.sql.DriverManager}
 *       before any database connection is attempted.  The {@code ClassNotFoundException}
 *       is intentionally swallowed — if neither driver JAR is on the classpath the
 *       application degrades to SQLite-only mode.</li>
 * </ol>
 *
 * <p>JavaFX applications must be launched via {@link Application#launch}, which
 * is why the actual application class ({@link LibraryApp}) extends
 * {@link Application} while this class serves only as the JVM entry point.</p>
 */
public class Main {

    /**
     * JVM entry point — configures the runtime environment and launches the
     * JavaFX application.
     *
     * @param args command-line arguments forwarded verbatim to
     *             {@link Application#launch(Class, String...)}; no arguments
     *             are currently consumed by Library OS itself
     */
     public static void main(String[] args) {
         // Set WM_CLASS / glass.wm.class so GNOME/KDE recognise the window
         LibraryApp.configureDesktopIdentity();

         // Enable full HiDPI scaling on 4K / Retina screens
         System.setProperty("prism.allowhidpi", "true");

         // Pre-register optional database drivers; missing JARs are silently ignored
         // and the app falls back to the built-in SQLite driver automatically.
         try {
             Class.forName("com.mysql.cj.jdbc.Driver");
             Class.forName("org.postgresql.Driver");
             // Pre-load MongoDB sync driver for connection pooling
             Class.forName("com.mongodb.client.MongoClients");
         } catch (ClassNotFoundException ignored) {
             // Database drivers not on classpath — appropriate mode will be used
         }

         Application.launch(LibraryApp.class, args);
     }
}
