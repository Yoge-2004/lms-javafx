module com.example.application {
    requires transitive javafx.base;
    requires transitive javafx.controls;
    requires transitive javafx.graphics;
    requires javafx.swing;
    requires javafx.print;   // PrinterJob / Printer API
    requires java.desktop;
    requires java.logging;
    requires jakarta.mail;
    requires transitive java.sql;
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;

    exports com.example.application;
    exports com.example.application.ui;
    exports com.example.entities;
    exports com.example.services;
    exports com.example.storage;
    exports com.example.exceptions;

    // Allow JavaFX reflection for TableView property access etc.
    opens com.example.entities to javafx.base;
    opens com.example.application.ui to javafx.graphics, javafx.controls;
}
