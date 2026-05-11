package com.example.application.ui;

import com.example.entities.UserRole;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * <h1>SettingsView</h1>
 * 
 * <p>A centralized dashboard for managing user account preferences and system-wide
 * configurations within the Library OS environment. The view utilizes a highly
 * interactive, card-based layout with visual cues to segment standard user options
 * from restricted administrative functions.</p>
 * 
 * <h3>Core Features:</h3>
 * <ul>
 *   <li><b>Role-Based Access Control:</b> Dynamically renders administrative menus
 *       (User management, Library settings, Data tools) only for users possessing 
 *       {@link UserRole} staff privileges.</li>
 *   <li><b>Contextual Callbacks:</b> Decouples UI interactions from business logic
 *       via the {@link Actions} functional interface.</li>
 *   <li><b>Interactive Design:</b> Features hover-state animations, scale-based 
 *       touch feedback, and clean typography to ensure a professional user experience.</li>
 *   <li><b>Adaptive Theming:</b> Fully compatible with dark/light mode configurations 
 *       via {@link AppTheme}.</li>
 * </ul>
 * 
 * @author Yogesh
 * @version 3.2
 */
public class SettingsView extends ScrollPane {

    public interface Actions {
        void openProfile();
        void openPassword();
        void openUserManagement();
        void openLibraryConfiguration();
        void openDataManagement();
        void openAnalytics();
        void deleteAccount();
    }

    public SettingsView(UserRole userRole, Actions actions) {
        setFitToWidth(true);
        setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        setStyle("-fx-background:transparent; -fx-background-color:" +
                (AppTheme.darkMode ? "#0F172A" : "#F1F5F9") + "; -fx-border-width:0;");

        VBox root = new VBox(0);
        root.setFillWidth(true);
        root.setStyle("-fx-background-color:" + (AppTheme.darkMode ? "#0F172A" : "#F1F5F9") + ";" +
                "-fx-min-height: 100%;");

        // Header — flush to all edges, no inner margin
        VBox header = new VBox(6);
        header.setPadding(new Insets(24, 28, 20, 28));
        header.setStyle("-fx-background-color:#0F172A; -fx-min-width: 100%;");

        Label titleLbl = new Label("Settings");
        titleLbl.setStyle("-fx-font-size:22px; -fx-font-weight:800; -fx-text-fill:white;");
        Label subLbl = new Label("Account and system preferences");
        subLbl.setStyle("-fx-font-size:13px; -fx-text-fill:#94A3B8;");
        header.getChildren().addAll(titleLbl, subLbl);

        // Content
        VBox content = new VBox(6);
        content.setPadding(new Insets(20, 20, 8, 20));

        // ── Account section ──────────────────────────────────────
        content.getChildren().add(sectionLabel("ACCOUNT"));

        content.getChildren().addAll(
                settingItem(AppTheme.ICON_USER, "Profile",
                        "Update your personal information and display name",
                        "#0D9488", () -> openFromSettings(actions::openProfile)),

                settingItem(AppTheme.ICON_LOCK, "Password",
                        "Change your account password",
                        "#3B82F6", () -> openFromSettings(actions::openPassword)),

                settingItem(AppTheme.ICON_DELETE, "Delete Account",
                        "Permanently remove your account and all associated data",
                        "#DC2626", () -> openFromSettings(actions::deleteAccount))
        );

        // ── Administration section (staff only) ──────────────────
        if (userRole.isStaff()) {
            content.getChildren().add(sectionLabel("ADMINISTRATION"));

            content.getChildren().addAll(
                    settingItem(AppTheme.ICON_USER, "User Management",
                            "Add, remove or modify user accounts and roles",
                            "#8B5CF6", () -> openFromSettings(actions::openUserManagement)),

                    settingItem(AppTheme.ICON_LIBRARY, "Library Configuration",
                            "Borrowing rules, fines, email, storage, and the database tab",
                            "#F59E0B", () -> openFromSettings(actions::openLibraryConfiguration)),

                    settingItem(AppTheme.ICON_SAVE, "Data Management",
                            "Backup data, import/export, view system statistics",
                            "#16A34A", () -> openFromSettings(actions::openDataManagement))
            );
        }

        // ── About ────────────────────────────────────────────────
        content.getChildren().add(sectionLabel("ABOUT"));
        HBox aboutCard = new HBox(16);
        aboutCard.setPadding(new Insets(14, 16, 14, 16));
        aboutCard.setStyle(itemStyle(false));
        aboutCard.setAlignment(Pos.CENTER_LEFT);

        StackPane aboutIcon = createIconBubble(AppTheme.ICON_HELP, "#64748B");

        VBox aboutTxt = new VBox(2);
        Label aboutTitle = new Label("Library OS  v3.4 - Stable Release");
        aboutTitle.setStyle("-fx-font-size:15px; -fx-font-weight:700; -fx-text-fill:" + textPrimary() + ";");
        Label aboutSub = new Label("Product of Yogesh | JavaFX 26 . Java 26");
        aboutSub.setStyle("-fx-font-size:12px; -fx-text-fill:" + textMuted() + ";");
        aboutTxt.getChildren().addAll(aboutTitle, aboutSub);
        aboutCard.getChildren().addAll(aboutIcon, aboutTxt);
        content.getChildren().add(aboutCard);

        root.getChildren().addAll(header, content);

        // Staggered entrance animations
        Platform.runLater(() -> {
            AppTheme.slideUp(header, 50, 20, 400);
            double delay = 150;
            for (javafx.scene.Node n : content.getChildren()) {
                AppTheme.slideUp(n, delay);
                delay += 50;
            }
        });

        setContent(root);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:10px; -fx-font-weight:700; -fx-text-fill:#94A3B8; " +
                "-fx-padding:16 0 6 4;");
        return l;
    }

    private static HBox settingItem(String iconPath, String title, String desc,
                                    String accentColor, Runnable action) {
        HBox item = new HBox(14);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(14, 16, 14, 16));
        item.setStyle(itemStyle(false));
        item.setCursor(javafx.scene.Cursor.HAND);

        StackPane bubble = createIconBubble(iconPath, accentColor);

        VBox txt = new VBox(3);
        HBox.setHgrow(txt, Priority.ALWAYS);
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size:15px; -fx-font-weight:600; -fx-text-fill:" + textPrimary() + ";");
        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-font-size:12px; -fx-text-fill:" + textMuted() + ";");
        descLbl.setWrapText(true);
        txt.getChildren().addAll(titleLbl, descLbl);

        StackPane arrow = new StackPane(AppTheme.createIcon(AppTheme.ICON_CHEVRON_RIGHT, 14));

        item.getChildren().addAll(bubble, txt, arrow);

        item.setOnMouseEntered(e -> {
            item.setStyle(itemStyle(true));
            AppTheme.animateScale(item, 1.012, 120);
        });
        item.setOnMouseExited(e -> {
            item.setStyle(itemStyle(false));
            AppTheme.animateScale(item, 1.0, 120);
        });
        item.setOnMousePressed(e  -> AppTheme.animateScale(item, 0.97, 60));
        item.setOnMouseReleased(e -> AppTheme.animateScale(item, item.isHover() ? 1.012 : 1.0, 80));
        item.setOnMouseClicked(e -> action.run());

        return item;
    }

    private static String itemStyle(boolean hovered) {
        String surface = AppTheme.darkMode
                ? (hovered ? "#0F172A" : "transparent")
                : (hovered ? "#F8FAFC" : "transparent");
        return "-fx-background-color:" + surface + "; " +
                "-fx-background-radius:8px; " +
                (hovered ? " -fx-effect:dropshadow(gaussian,rgba(15,23,42,0.04),4,0,0,1);" : "");
    }

    private static StackPane createIconBubble(String iconPath, String accentColor) {
        StackPane bubble = new StackPane(AppTheme.createIcon(iconPath, 18));
        bubble.setMinSize(44, 44);
        bubble.setPrefSize(44, 44);
        bubble.setMaxSize(44, 44);
        bubble.setStyle("-fx-background-color:" + accentColor + "22; -fx-background-radius:10px;");
        return bubble;
    }

    private static String textPrimary() {
        return AppTheme.darkMode ? "#F8FAFC" : "#1E293B";
    }

    private static String textMuted() {
        return AppTheme.darkMode ? "#94A3B8" : "#64748B";
    }

    private static void openFromSettings(Runnable action) {
        Platform.runLater(action);
    }

}