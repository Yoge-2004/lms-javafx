package com.example.application.ui;

import java.util.ArrayList;
import java.util.List;

import com.example.entities.AppConfiguration;
import com.example.services.AppConfigurationService;
import com.example.storage.AppPaths;
import javafx.embed.swing.SwingFXUtils;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.scene.input.MouseEvent;

import java.awt.Taskbar;
import java.awt.image.BufferedImage;

import java.net.URL;
import java.util.Collection;

/**
 * <h1>AppTheme — The Visual Core of Library OS</h1>
 *
 * <p>The {@code AppTheme} class serves as the central design system and animation
 * engine for the entire application. It encapsulates the "Design Tokens" (colors,
 * spacing, icons) and provides a unified API for generating, styling, and
 * animating JavaFX components.</p>
 *
 * <h3>Design Philosophy:</h3>
 * <ul>
 *   <li><b>Visual Excellence:</b> Every component is designed to feel premium,
 *       utilizing smooth gradients, subtle shadows, and harmonious color palettes.</li>
 *   <li><b>Motion as Feedback:</b> Animations (fades, slides, pulses) are used not
 *       just for aesthetics, but to provide meaningful feedback and guide the user's
 *       attention.</li>
 *   <li><b>Thematic Consistency:</b> Supports both Light and Dark modes through
 *       a combination of CSS stylesheets and dynamic property bindings.</li>
 *   <li><b>Component Modularity:</b> Helper methods like {@link #createIconButton}
 *       and {@link #createButton} ensure a consistent look and feel across
 *       disconnected views.</li>
 * </ul>
 *
 * <h3>Key Subsystems:</h3>
 * <ul>
 *   <li><b>Iconography:</b> Centralized SVG management for sharp, scalable icons.</li>
 *   <li><b>Animation Engine:</b> High-performance transitions using the Quad interpolator
 *       for a "snappy" yet fluid feel.</li>
 *   <li><b>Theming:</b> Dynamic stylesheet switching and inline style generation.</li>
 * </ul>
 *
 * @author Yogesh
 * @version 3.2
 */
public final class AppTheme {

    // === ICON LIBRARY (Material Design SVG Paths) ==    /** Path content for 'Add' action icon. */
    public static final String ICON_ADD = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
    /** Path content for 'Edit' action icon. */
    public static final String ICON_EDIT = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";
    /** Path content for 'Delete' action icon. */
    public static final String ICON_DELETE = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";
    /** Path content for 'Refresh' action icon. */
    public static final String ICON_REFRESH = "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z";
    /** Path content for 'Logout' action icon. */
    public static final String ICON_LOGOUT = "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z";
    /** Path content for 'Visibility On' icon (password field). */
    public static final String ICON_VISIBILITY = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    /** Path content for 'Visibility Off' icon (password field). */
    public static final String ICON_VISIBILITY_OFF = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z";
    /** Path content for 'Settings' cog icon. */
    public static final String ICON_SETTINGS = "M19.43 12.98c.04-.32.07-.64.07-.98s-.03-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49 1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65C14.46 2.18 14.25 2 14 2h-4c-.25 0-.46.18-.49.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1c-.23-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49-.12-.64l2.11 1.65c-.04.32-.07.65-.07.98s.03.66.07.98l-2.11 1.65c-.19.15-.24.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65c.03.24.24.42.49.42h4c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1c.23.09.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65zM12 15.5c-1.93 0-3.5-1.57-3.5-3.5s1.57-3.5 3.5-3.5 3.5 1.57 3.5 3.5-1.57 3.5-3.5 3.5z";
    /** Path content for 'Book' glyph. */
    public static final String ICON_BOOK = "M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 4h5v8l-2.5-1.5L6 12V4z";
    /** Path content for 'Warning' triangle. */
    public static final String ICON_WARNING = "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z";
    /** Path content for 'Check' tick. */
    public static final String ICON_CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
    /** Path content for 'Close' X. */
    public static final String ICON_CLOSE = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z";
    /** Path content for 'User' avatar. */
    public static final String ICON_USER = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
    /** Path content for 'Return' arrow. */
    public static final String ICON_RETURN = "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z";
    /** Path content for 'Mail' envelope. */
    public static final String ICON_MAIL = "M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z";
    /** Path content for 'Search' magnifying glass. */
    public static final String ICON_SEARCH = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
    /** Path content for 'Credit Card' outline. */
    public static final String ICON_CARD = "M20 4H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4V6h16v12zM6 10h2v2H6v-2zm0 4h8v2H6v-2zm10 0h2v2h-2v-2zm-6-4h8v2h-8v-2z";
    /** Path content for 'Dashboard' layout. */
    public static final String ICON_DASHBOARD = "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z";
    /** Path content for 'Library' building/bookshelf. */
    public static final String ICON_LIBRARY = "M12 11.55C9.64 9.35 6.48 8 3 8v11c3.48 0 6.64 1.35 9 3.55 2.36-2.19 5.52-3.55 9-3.55V8c-3.48 0-6.64 1.35-9 3.55zM12 8c1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3 1.34 3 3 3z";
    /** Path content for 'Sync/Circulation' rotating arrows. */
    public static final String ICON_SYNC = "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z";
    /** Path content for 'Filter' funnel. */
    public static final String ICON_FILTER = "M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z";
    /** Path content for 'More' vertical dots. */
    public static final String ICON_MORE = "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z";
    /** Path content for 'Download' icon. */
    public static final String ICON_DOWNLOAD = "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";
    /** Path content for 'Upload' icon. */
    public static final String ICON_UPLOAD = "M9 16h6v-6h4l-7-7-7 7h4v6zm-4 2h14v2H5v-2z";
    /** Path content for 'Print' icon. */
    public static final String ICON_PRINT = "M19 8H5c-1.66 0-3 1.34-3 3v6h4v4h12v-4h4v-6c0-1.66-1.34-3-3-3zm-3 11H8v-5h8v5zm3-7c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zm-1-9H6v4h12V3z";
    /** Path content for 'Save' diskette. */
    public static final String ICON_SAVE = "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z";
    /** Path content for 'Cancel' circled X. */
    public static final String ICON_CANCEL = "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z";
    /** Path content for 'Back' arrow. */
    public static final String ICON_ARROW_BACK = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z";
    /** Path content for 'Forward' arrow. */
    public static final String ICON_ARROW_FORWARD = "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z";
    /** Path content for 'Trending Up' chart. */
    public static final String ICON_TRENDING_UP = "M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z";
    /** Path content for 'Trending Down' chart. */
    public static final String ICON_TRENDING_DOWN = "M16 18l2.29-2.29-4.88-4.88-4 4L2 7.41 3.41 6l6 6 4-4 6.3 6.29L22 12v6z";
    /** Path content for 'Schedule' clock. */
    public static final String ICON_SCHEDULE = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z";
    /** Path content for 'Notification' bell. */
    public static final String ICON_NOTIFICATION = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.5s4 2.02 4 4.5v6z";
    /** Path content for 'Help' question circle. */
    public static final String ICON_HELP = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z";
    /** Path content for 'Lock' padlock. */
    public static final String ICON_LOCK = "M12 17a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm6-8h-1V7a5 5 0 0 0-10 0v2H6a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2zm-6 8a4 4 0 0 1-1-7.87V7a1 1 0 1 1 2 0v2.13A4 4 0 0 1 12 17zm3-8H9V7a3 3 0 0 1 6 0v2z";
    /** Path content for 'Sun' (light theme toggle). */
    public static final String ICON_SUN = "M6.76 4.84 5.34 3.42 3.93 4.83l1.41 1.41 1.42-1.4zM1 13h3v-2H1v2zm10 9h2v-3h-2v3zm9-9h3v-2h-3v2zm-1.34 6.17 1.41 1.41 1.41-1.41-1.41-1.41-1.41 1.41zM17.24 4.84l1.41 1.41 1.41-1.42-1.41-1.41-1.41 1.42zM12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12zm0 10a4 4 0 1 1 0-8 4 4 0 0 1 0 8zm-8.07 4.24 1.41 1.41 1.42-1.41-1.42-1.41-1.41 1.41zM11 1h2v3h-2V1z";
    /** Path content for 'Moon' (dark theme toggle). */
    public static final String ICON_MOON = "M9.37,5.51C9.19,6.15,9.1,6.82,9.1,7.5c0,4.08,3.32,7.4,7.4,7.4c0.68,0,1.35-0.09,1.99-0.27C17.45,17.19,14.93,19,12,19 c-3.86,0-7-3.14-7-7C5,9.07,6.81,6.55,9.37,5.51z M12,3c-4.97,0-9,4.03-9,9s4.03,9,9,9s9-4.03,9-9c0-0.46-0.04-0.92-0.1-1.36 c-0.98,1.37-2.58,2.26-4.4,2.26c-2.98,0-5.4-2.42-5.4-5.4c0-1.81,0.89-3.42,2.26-4.4C12.92,3.04,12.46,3,12,3L12,3z";
    /** Path content for 'Chevron Right' arrow. */
    public static final String ICON_CHEVRON_RIGHT = "M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z";
    ;

    /**
     * Applies a robust fix to an integer spinner to handle blank input, non-numeric input, 
     * and arrow-click restoration.
     *
     * @param s The integer spinner to harden.
     */
    public static void fixSpinner(Spinner<Integer> s) {
        if (!(s.getValueFactory() instanceof SpinnerValueFactory.IntegerSpinnerValueFactory factory)) return;

        // Custom factory for arrow clicks
        s.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(factory.getMin(), factory.getMax(), factory.getValue()) {
            @Override
            public void increment(int steps) {
                String text = s.getEditor().getText();
                if (text == null || text.isBlank()) {
                    setValue(getMin());
                    s.getEditor().setText(String.valueOf(getMin()));
                } else {
                    super.increment(steps);
                }
            }
            @Override
            public void decrement(int steps) {
                String text = s.getEditor().getText();
                if (text == null || text.isBlank()) {
                    setValue(getMin());
                    s.getEditor().setText(String.valueOf(getMin()));
                } else {
                    super.decrement(steps);
                }
            }
        });

        // Strict character filtering
        s.getEditor().textProperty().addListener((o, old, v) -> {
            if (v == null || v.isBlank()) return;
            if (!v.matches("\\d*")) {
                s.getEditor().setText(old);
            }
        });

        // Key event filtering (extra layer)
        s.getEditor().addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, e -> {
            if (!e.getCharacter().matches("[0-9]")) e.consume();
        });

        // Handle focus loss and commit
        s.focusedProperty().addListener((o, old, nw) -> {
            if (!nw) {
                String text = s.getEditor().getText();
                if (text == null || text.isBlank()) {
                    s.getValueFactory().setValue(factory.getMin());
                    s.getEditor().setText(String.valueOf(factory.getMin()));
                } else {
                    try {
                        s.commitValue();
                    } catch (Exception e) {
                        s.getEditor().setText(s.getValue().toString());
                    }
                }
            }
        });

        // Custom converter to handle empty string during commit
        s.getValueFactory().setConverter(new javafx.util.StringConverter<Integer>() {
            @Override public String toString(Integer value) { return value == null ? "" : value.toString(); }
            @Override public Integer fromString(String string) {
                if (string == null || string.isBlank()) return factory.getMin();
                try { return Integer.parseInt(string.trim()); }
                catch (Exception e) { return s.getValue(); }
            }
        });
    }

    /**
     * Applies a robust fix to a double spinner, similar to {@link #fixSpinner(Spinner)}.
     *
     * @param s The double spinner to harden.
     */
    public static void fixDoubleSpinner(Spinner<Double> s) {
        if (!(s.getValueFactory() instanceof SpinnerValueFactory.DoubleSpinnerValueFactory factory)) return;

        s.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(factory.getMin(), factory.getMax(), factory.getValue(), factory.getAmountToStepBy()) {
            @Override
            public void increment(int steps) {
                String text = s.getEditor().getText();
                if (text == null || text.isBlank()) {
                    setValue(getMin());
                    s.getEditor().setText(String.valueOf(getMin()));
                } else {
                    super.increment(steps);
                }
            }
            @Override
            public void decrement(int steps) {
                String text = s.getEditor().getText();
                if (text == null || text.isBlank()) {
                    setValue(getMin());
                    s.getEditor().setText(String.valueOf(getMin()));
                } else {
                    super.decrement(steps);
                }
            }
        });

        s.getEditor().textProperty().addListener((o, old, v) -> {
            if (v == null || v.isBlank()) return;
            if (!v.matches("[0-9]*\\.?[0-9]*")) {
                s.getEditor().setText(old);
            }
        });

        s.getEditor().addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, e -> {
            if (!e.getCharacter().matches("[0-9\\.]")) e.consume();
        });

        s.focusedProperty().addListener((o, old, nw) -> {
            if (!nw) {
                String text = s.getEditor().getText();
                if (text == null || text.isBlank()) {
                    s.getValueFactory().setValue(factory.getMin());
                    s.getEditor().setText(String.valueOf(factory.getMin()));
                } else {
                    try {
                        s.commitValue();
                    } catch (Exception e) {
                        s.getEditor().setText(s.getValue().toString());
                    }
                }
            }
        });

        s.getValueFactory().setConverter(new javafx.util.StringConverter<Double>() {
            @Override public String toString(Double value) { return value == null ? "" : value.toString(); }
            @Override public Double fromString(String string) {
                if (string == null || string.isBlank()) return factory.getMin();
                try { return Double.parseDouble(string.trim()); }
                catch (Exception e) { return s.getValue(); }
            }
        });
    }

    /** Forces a TextField to only accept integers. */
    public static void makeNumeric(TextField field) {
        field.textProperty().addListener((o, old, v) -> {
            if (v == null || v.isEmpty()) return;
            if (!v.matches("\\d*")) {
                field.setText(old);
            }
        });
        field.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, e -> {
            if (!e.getCharacter().matches("[0-9]")) e.consume();
        });
    }

    /** Forces a TextField to only accept decimal numbers. */
    public static void makeDecimal(TextField field) {
        field.textProperty().addListener((o, old, v) -> {
            if (v == null || v.isEmpty()) return;
            if (!v.matches("[0-9]*\\.?[0-9]*")) {
                field.setText(old);
            }
        });
        field.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, e -> {
            if (!e.getCharacter().matches("[0-9\\.]")) e.consume();
        });
    }

    /**
     * Defines the standard button styles used throughout the application.
     * Each style maps to a specific CSS class and a fallback base color.
     */
    public enum ButtonStyle {
        /** Teal-based primary action style. */
        PRIMARY("btn-primary", Color.web("#0D9488")),
        /** Dark-slate secondary action style. */
        SECONDARY("btn-secondary", Color.web("#1E293B")),
        /** Green-based success/approval style. */
        SUCCESS("btn-success", Color.web("#16A34A")),
        /** Amber-based warning/pending style. */
        WARNING("btn-warning", Color.web("#D97706")),
        /** Red-based destructive/danger style. */
        DANGER("btn-danger", Color.web("#DC2626")),
        /** Indigo-based edit/modify style. */
        INDIGO("btn-indigo", Color.web("#6366F1")),
        /** Violet-based return/back style. */
        VIOLET("btn-violet", Color.web("#8B5CF6")),
        /** Rose-based account/delete style. */
        ROSE("btn-rose", Color.web("#F43F5E")),
        /** Amber-based payment/fine style. */
        AMBER("btn-amber", Color.web("#F59E0B")),
        /** Cyan-based info/alternate style. */
        CYAN("btn-cyan", Color.web("#06B6D4")),
        /** Transparent button with only an icon or text. */
        GHOST("btn-ghost", Color.TRANSPARENT),
        /** Transparent background with a border. */
        OUTLINE("btn-outline", Color.TRANSPARENT);

        private final String styleClass;
        private final Color baseColor;

        ButtonStyle(String styleClass, Color baseColor) {
            this.styleClass = styleClass;
            this.baseColor = baseColor;
        }

        /** @return The CSS class name for this style. */
        public String getStyleClass() { return styleClass; }
        /** @return The representative base color for this style. */
        public Color getBaseColor() { return baseColor; }
    }

    /**
     * Defines status chip styles for categorization and visual tagging.
     */
    public enum ChipStyle {
        /** Neutral gray chip. */
        NEUTRAL("chip-neutral"),
        /** Teal primary chip. */
        PRIMARY("chip-primary"),
        /** Green success chip. */
        SUCCESS("chip-success"),
        /** Orange warning chip. */
        WARNING("chip-warning"),
        /** Red error chip. */
        ERROR("chip-error"),
        /** Blue info chip. */
        INFO("chip-info"),
        /** Indigo accent chip. */
        INDIGO("chip-indigo"),
        /** Violet accent chip. */
        VIOLET("chip-violet"),
        /** Rose accent chip. */
        ROSE("chip-rose"),
        /** Amber accent chip. */
        AMBER("chip-amber");

        private final String styleClass;
        ChipStyle(String styleClass) { this.styleClass = styleClass; }
        /** @return The CSS class name for this chip style. */
        public String getStyleClass() { return styleClass; }
    }

    private static final String THEME_PATH = "/theme.css";
    private static String cachedStylesheet;
    // Icon size cache — load multiple sizes for OS/taskbar compatibility
    private static final java.util.List<Image> cachedIconSet = new java.util.ArrayList<>();
    private static Image cachedStandardIcon;
    public static boolean darkMode = false;

    public static final Interpolator SPRING_INTERPOLATOR = new Interpolator() {
        @Override protected double curve(double t) {
            double p1x=0.34,p1y=1.56,p2x=0.64,p2y=1.0;
            return cubicBezier(t,p1x,p1y,p2x,p2y);
        }
    };
    public static final Interpolator EASE_OUT_QUART = new Interpolator() {
        @Override protected double curve(double t) {
            return cubicBezier(t,0.25,0.46,0.45,0.94);
        }
    };
    public static final Interpolator EASE_STANDARD = new Interpolator() {
        @Override protected double curve(double t) {
            return cubicBezier(t,0.4,0.0,0.2,1.0);
        }
    };

    private static double cubicBezier(double t, double p1x, double p1y, double p2x, double p2y) {
        double x = t;
        for (int i = 0; i < 8; i++) {
            double cx = 3*p1x, bx = 3*(p2x-p1x)-cx, ax = 1-cx-bx;
            double xVal = ((ax*x+bx)*x+cx)*x;
            double dx = (3*ax*x+2*bx)*x+cx;
            if (Math.abs(dx) < 1e-6) break;
            x -= (xVal - t) / dx;
        }
        double cy = 3*p1y, by_ = 3*(p2y-p1y)-cy, ay = 1-cy-by_;
        return ((ay*x+by_)*x+cy)*x;
    }

    private AppTheme() {}

    /**
     * Creates a new JavaFX Scene with the global theme applied.
     *
     * @param root The root node of the scene.
     * @param width The initial width.
     * @param height The initial height.
     * @return A themed Scene instance.
     */
    public static Scene createScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        applyTheme(scene);
        return scene;
    }

    /**
     * Applies the global CSS stylesheet to a Scene.
     *
     * @param scene The scene to style.
     */
    public static void applyTheme(Scene scene) {
        if (scene == null) return;
        String stylesheet = getStylesheetUrl();
        if (!scene.getStylesheets().contains(stylesheet)) {
            scene.getStylesheets().add(stylesheet);
        }
    }

    /**
     * Applies the global theme to a DialogPane. This includes loading the stylesheet,
     * setting the background, and styling the standard button bar to match the app's
     * primary/secondary color schemes.
     *
     * @param pane The DialogPane to style.
     */
    public static void applyTheme(DialogPane pane) {
        if (pane == null) return;
        String stylesheet = getStylesheetUrl();
        if (!pane.getStylesheets().contains(stylesheet)) {
            pane.getStylesheets().add(stylesheet);
        }
        if (!pane.getStyleClass().contains("dialog-pane-modern"))
            pane.getStyleClass().add("dialog-pane-modern");
        if (darkMode && !pane.getStyleClass().contains("dark-mode"))
            pane.getStyleClass().add("dark-mode");
        else if (!darkMode)
            pane.getStyleClass().remove("dark-mode");

        for (ButtonType bt : pane.getButtonTypes()) {
            Button b = (Button) pane.lookupButton(bt);
            if (b != null) {
                b.getStyleClass().removeAll("btn-primary", "btn-secondary", "btn-danger",
                        "btn-warning", "btn-success", "btn-outline", "dialog-btn");

                // dialog-btn provides dialog-specific font size and padding without
                // any inline color — so CSS :hover/:pressed pseudo-states are never blocked.
                b.getStyleClass().add("dialog-btn");
                b.setStyle(""); // clear any leftover inline style that would block pseudo-states

                if (bt.getButtonData() == ButtonBar.ButtonData.OK_DONE ||
                        bt.getButtonData() == ButtonBar.ButtonData.FINISH ||
                        bt.getButtonData() == ButtonBar.ButtonData.YES ||
                        bt == ButtonType.OK || bt == ButtonType.YES) {

                    b.getStyleClass().add("btn-primary");

                } else if (bt.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE ||
                        bt.getButtonData() == ButtonBar.ButtonData.NO ||
                        bt == ButtonType.CANCEL || bt == ButtonType.NO) {

                    b.getStyleClass().add("btn-secondary");

                } else {
                    b.getStyleClass().add("btn-outline");
                }

                b.setOnMouseEntered(e  -> { b.setScaleX(1.035); b.setScaleY(1.035); });
                b.setOnMouseExited(e   -> { b.setScaleX(1.0);   b.setScaleY(1.0);   });
                b.setOnMousePressed(e  -> { b.setScaleX(0.96);  b.setScaleY(0.96);  });
                b.setOnMouseReleased(e -> {
                    b.setScaleX(b.isHover() ? 1.035 : 1.0);
                    b.setScaleY(b.isHover() ? 1.035 : 1.0);
                });
            }
        }

        pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && newScene.getWindow() instanceof Stage stage) {
                applyWindowIcon(stage);
                enableOsWindowControls(stage);
                stage.setOnShown(e -> popIn(pane, 0));
            }
        });
        if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
            applyWindowIcon(stage);
            enableOsWindowControls(stage);
        }
    }

    private static void enableOsWindowControls(Stage stage) {
        if (stage == null) return;
        if (!stage.isResizable()) stage.setResizable(true);
        try {
            java.awt.Window[] awtWindows = java.awt.Window.getWindows();
            for (java.awt.Window w : awtWindows) {
                if (w instanceof java.awt.Frame f && w.isShowing()) {
                    f.setResizable(true);
                }
            }
        } catch (Exception ignored) { }
    }

    private static String getStylesheetUrl() {
        if (cachedStylesheet == null) {
            URL resource = AppTheme.class.getResource(THEME_PATH);
            if (resource == null) {
                throw new IllegalStateException("Theme stylesheet not found: " + THEME_PATH);
            }
            cachedStylesheet = resource.toExternalForm();
        }
        return cachedStylesheet;
    }

    public static Stage createModalStage(Stage owner, String title) {
        return createModalStage(owner, title, 400, 300);
    }

    public static Stage createModalStage(Stage owner, String title, double minWidth, double minHeight) {
        Stage stage = new Stage();
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);
        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
        applyWindowIcon(stage);
        return stage;
    }

    public static SVGPath createIcon(String pathContent) {
        return createIcon(pathContent, 20);
    }

    public static SVGPath createIcon(String pathContent, double size) {
        SVGPath svg = new SVGPath();
        svg.setContent(pathContent);
        svg.getStyleClass().add("icon-svg");
        svg.setFill(Color.web("#64748B"));
        svg.setMouseTransparent(true);
        double scale = size <= 0 ? 1.0 : size / 24.0;
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        return svg;
    }

    /**
     * Creates an SVG icon with a specific fill color.
     *
     * @param pathContent The SVG path.
     * @param color The fill color.
     * @return A styled SVGPath node.
     */
    public static SVGPath createIcon(String pathContent, Color color) {
        SVGPath svg = createIcon(pathContent);
        svg.setFill(color);
        return svg;
    }

    public static void applyWindowIcon(Stage stage) {
        if (stage == null) return;
        try {
            if (cachedIconSet.isEmpty()) {
                // Load each dedicated per-size PNG for pixel-perfect rendering.
                // The white-background PNGs work on ALL OSes/DEs without transparency artifacts.
                int[] sizes = {16, 24, 32, 48, 64, 128, 256, 512};
                for (int sz : sizes) {
                    boolean loaded = false;
                    try (java.io.InputStream is = AppTheme.class.getResourceAsStream("/icon-" + sz + ".png")) {
                        if (is != null) {
                            Image img = new Image(is);
                            if (!img.isError()) { cachedIconSet.add(img); loaded = true; }
                        }
                    } catch (Exception ignored) {}
                    if (!loaded) {
                        // Runtime-resize fallback from master
                        try (java.io.InputStream is = AppTheme.class.getResourceAsStream("/icon.png")) {
                            if (is != null) {
                                Image img = new Image(is, sz, sz, true, true);
                                if (!img.isError()) cachedIconSet.add(img);
                            }
                        } catch (Exception ignored) {}
                    }
                }
                if (cachedIconSet.isEmpty()) {
                    for (String p : new String[]{"/icon.png", "icon.png"}) {
                        try (java.io.InputStream is = AppTheme.class.getResourceAsStream(p)) {
                            if (is != null) {
                                Image img = new Image(is);
                                if (!img.isError()) { cachedIconSet.add(img); break; }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                if (cachedIconSet.isEmpty()) cachedIconSet.add(createAppIcon(64));
                cachedStandardIcon = cachedIconSet.stream()
                        .filter(i -> i.getWidth() >= 256).findFirst()
                        .orElseGet(() -> cachedIconSet.stream()
                                .filter(i -> i.getWidth() >= 64).findFirst()
                                .orElse(cachedIconSet.get(0)));
            }

            // ── JavaFX stage icons (Windows/Linux titlebar, alt-tab) ──────────
            stage.getIcons().setAll(cachedIconSet);

            // ── AWT Frame icons ───────────────────────────────────────────────
            // On Windows and Linux the OS taskbar icon is read from the AWT Frame
            // that backs the JVM window. We must push the icon set there on the
            // AWT EDT to cover: IntelliJ/Eclipse IDE runs, `java -jar`, KDE Plasma,
            // GNOME Shell, XFCE, LXDE, Cinnamon, Budgie, and Windows taskbar.
            final java.util.List<java.awt.Image> awtIcons = buildAwtIconList();
            if (!awtIcons.isEmpty()) {
                // Immediate attempt
                java.awt.EventQueue.invokeLater(() -> applyAwtIcons(awtIcons));
                // Delayed attempt — catches IDE/cmd-launched windows where AWT peer
                // isn't created yet when start() first runs
                java.util.Timer iconRetry = new java.util.Timer("icon-retry", true);
                iconRetry.schedule(new java.util.TimerTask() {
                    @Override public void run() {
                        java.awt.EventQueue.invokeLater(() -> applyAwtIcons(awtIcons));
                        iconRetry.cancel();
                    }
                }, 500);
                // Persistent listener for future windows/dialogs
                java.awt.EventQueue.invokeLater(() -> {
                    try {
                        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                            if (event instanceof java.awt.event.WindowEvent we
                                    && we.getID() == java.awt.event.WindowEvent.WINDOW_OPENED
                                    && we.getWindow() instanceof java.awt.Frame af) {
                                af.setIconImages(awtIcons);
                            }
                        }, java.awt.AWTEvent.WINDOW_EVENT_MASK);
                    } catch (Exception ignored) {}
                });
            }

            // ── macOS Dock (Taskbar API) ──────────────────────────────────────
            applyTaskbarIcon(cachedStandardIcon);

        } catch (Exception e) {
            System.err.println("Icon application failed: " + e.getMessage());
        }
    }

    /** Sets the icon on all currently open AWT Frame windows. Called from AWT EDT. */
    private static void applyAwtIcons(java.util.List<java.awt.Image> icons) {
        if (icons == null || icons.isEmpty()) return;
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
                if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                    // Use the largest available icon for the taskbar/dock
                    java.awt.Image bestIcon = icons.stream()
                            .max(java.util.Comparator.comparingInt(img -> img.getWidth(null)))
                            .orElse(icons.get(0));
                    taskbar.setIconImage(bestIcon);
                }
            }
        } catch (Exception ignored) {}
        try {
            for (java.awt.Window aw : java.awt.Window.getWindows()) {
                if (aw instanceof java.awt.Frame af) af.setIconImages(icons);
            }
        } catch (Exception ignored) {}
    }

    /** Builds an AWT image list from the cached JavaFX icon set, prepending the .ico resource. */
    private static java.util.List<java.awt.Image> buildAwtIconList() {
        java.util.List<java.awt.Image> list = new java.util.ArrayList<>();
        // Load from ICO resource first — gives Windows the best compressed multi-res entry
        try (java.io.InputStream is = AppTheme.class.getResourceAsStream("/icon.ico")) {
            if (is != null) {
                Image fxIco = new Image(is, 256, 256, true, true);
                if (!fxIco.isError()) {
                    java.awt.image.BufferedImage bi = javafx.embed.swing.SwingFXUtils.fromFXImage(fxIco, null);
                    if (bi != null) list.add(bi);
                }
            }
        } catch (Exception ignored) {}
        // Add remaining sizes from the JavaFX icon set
        for (Image fxImg : cachedIconSet) {
            try {
                java.awt.image.BufferedImage bi = javafx.embed.swing.SwingFXUtils.fromFXImage(fxImg, null);
                if (bi != null) list.add(bi);
            } catch (Exception ignored) {}
        }
        return list;
    }

    /**
     * Creates a standard styled Button.
     *
     * @param text The button text.
     * @param style The visual style to apply.
     * @return A themed Button.
     */
    public static Button createButton(String text, ButtonStyle style) {
        Button button = new Button(text);
        button.getStyleClass().addAll("app-button", style.getStyleClass());
        installButtonAnimation(button);
        return button;
    }

    /**
     * Creates a standard styled Button with a click action.
     *
     * @param text The button text.
     * @param style The visual style to apply.
     * @param action The runnable to execute on click.
     * @return A themed Button.
     */
    public static Button createButton(String text, ButtonStyle style, Runnable action) {
        Button button = createButton(text, style);
        button.setOnAction(e -> action.run());
        return button;
    }

    /**
     * Creates a square button containing only an icon.
     *
     * @param iconPath The SVG path for the icon.
     * @param tooltip Tooltip text (optional).
     * @param style The visual style to apply.
     * @return A themed IconButton.
     */
    public static Button createIconButton(String iconPath, String tooltip, ButtonStyle style) {
        Button button = new Button();
        button.setGraphic(createIcon(iconPath));
        if (tooltip != null && !tooltip.isEmpty()) {
            installSmartTooltip(button, tooltip);
        }
        button.getStyleClass().addAll("app-button", "icon-button", style.getStyleClass());
        installButtonAnimation(button);
        return button;
    }

    public static void configureTooltip(Node node, String text) {
        if (node == null || text == null || text.isBlank()) return;
        installSmartTooltip(node, text);
    }

    /**
     * Installs a custom tooltip that:
     *  - stays visible indefinitely while the mouse remains over the node
     *    (works around the JavaFX TooltipBehavior 5-second timeout bug)
     *  - fades in quickly and fades out smoothly on mouse exit
     *  - follows the cursor so it never obscures the target
     */
    public static void installSmartTooltip(Node node, String text) {
        if (node == null || text == null || text.isBlank()) return;

        Label label = new Label(text);
        label.getStyleClass().add("modern-tooltip");
        label.setWrapText(true);
        label.setMaxWidth(320);
        label.setOpacity(0.0);

        Popup popup = new Popup();
        popup.getContent().add(label);
        popup.setAutoHide(false);
        popup.setAutoFix(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(120), label);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), label);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> { popup.hide(); label.setOpacity(0.0); });

        Timeline showDelay = new Timeline(new KeyFrame(Duration.millis(250), e -> {
            fadeOut.stop();
            label.setOpacity(0.0);
            fadeIn.play();
        }));
        showDelay.setCycleCount(1);

        final double[] cursorPos = {0, 0};

        node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            cursorPos[0] = e.getScreenX();
            cursorPos[1] = e.getScreenY();
            fadeOut.stop();
            if (!popup.isShowing()) {
                Window win = node.getScene() != null ? node.getScene().getWindow() : null;
                if (win != null) {
                    label.setOpacity(0.0);
                    popup.show(win, cursorPos[0] + 14, cursorPos[1] + 20);
                }
            }
            showDelay.stop();
            showDelay.play();
        });

        node.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            cursorPos[0] = e.getScreenX();
            cursorPos[1] = e.getScreenY();
            if (popup.isShowing()) {
                popup.setX(cursorPos[0] + 14);
                popup.setY(cursorPos[1] + 20);
            }
        });

        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            showDelay.stop();
            if (popup.isShowing()) {
                fadeIn.stop();
                fadeOut.play();
            }
        });
    }

    /** Kept for callers that explicitly need a Tooltip object (e.g. table column headers).
     *  For all node/button tooltips prefer {@link #installSmartTooltip}. */
    public static Tooltip createTooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.getStyleClass().add("modern-tooltip");
        tooltip.setShowDelay(Duration.millis(250));
        tooltip.setShowDuration(Duration.INDEFINITE);
        tooltip.setHideDelay(Duration.millis(300));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(320);
        return tooltip;
    }

    /**
     * Creates a button containing both an icon and text.
     *
     * @param text The button text.
     * @param iconPath The SVG path for the icon.
     * @param style The visual style to apply.
     * @return A themed IconTextButton.
     */
    public static Button createIconTextButton(String text, String iconPath, ButtonStyle style) {
        Button button = new Button(text);
        button.setGraphic(createIcon(iconPath));
        button.getStyleClass().addAll("app-button", "icon-text-button", style.getStyleClass());
        button.setGraphicTextGap(10);
        installButtonAnimation(button);
        return button;
    }

    /**
     * Creates a small, rounded visual tag (chip).
     *
     * @param text The tag text.
     * @param style The visual style for the chip.
     * @return A styled Label acting as a chip.
     */
    public static Label createChip(String text, ChipStyle style) {
        Label chip = new Label(text);
        chip.getStyleClass().addAll("chip", style.getStyleClass());
        return chip;
    }

    /**
     * Creates a status badge, typically for showing state like 'Active' or 'Paid'.
     *
     * @param text The badge text.
     * @param style The visual style.
     * @return A styled Label acting as a badge.
     */
    public static Label createStatusBadge(String text, ChipStyle style) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("status-badge", style.getStyleClass());
        return badge;
    }

    public static HBox createPasswordField(String prompt, StringProperty boundProperty) {
        PasswordField hiddenField = new PasswordField();
        hiddenField.setPromptText(prompt);
        TextField visibleField = new TextField();
        visibleField.setPromptText(prompt);
        visibleField.setVisible(false);
        visibleField.setManaged(false);
        visibleField.textProperty().bindBidirectional(hiddenField.textProperty());
        boundProperty.bind(hiddenField.textProperty());
        StackPane fieldStack = new StackPane(hiddenField, visibleField);
        HBox.setHgrow(fieldStack, Priority.ALWAYS);
        Button toggleBtn = createIconButton(ICON_VISIBILITY, "Toggle visibility", ButtonStyle.GHOST);
        toggleBtn.getStyleClass().add("password-toggle");
        toggleBtn.setOnAction(e -> {
            boolean isHidden = hiddenField.isVisible();
            hiddenField.setVisible(!isHidden);
            hiddenField.setManaged(!isHidden);
            visibleField.setVisible(isHidden);
            visibleField.setManaged(isHidden);
            toggleBtn.setGraphic(createIcon(isHidden ? ICON_VISIBILITY_OFF : ICON_VISIBILITY));
        });
        HBox wrapper = new HBox(fieldStack, toggleBtn);
        wrapper.setAlignment(Pos.CENTER_LEFT);
        wrapper.getStyleClass().add("input-with-icon");
        wrapper.setSpacing(4);
        return wrapper;
    }

    public static TextField createSearchField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("search-field");
        return field;
    }

    public static VBox createHeaderBlock(String kicker, String title, String subtitle) {
        VBox block = new VBox(8);
        block.setFillWidth(true);
        if (kicker != null && !kicker.isBlank()) {
            Label kickerLabel = new Label(kicker);
            kickerLabel.getStyleClass().add("label-tiny");
            block.getChildren().add(kickerLabel);
        }
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("heading-4");
        titleLabel.setWrapText(true);
        block.getChildren().add(titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.getStyleClass().add("body-regular");
            subtitleLabel.setWrapText(true);
            block.getChildren().add(subtitleLabel);
        }
        return block;
    }

    public static VBox createMetricCard(String label, String value, String change, boolean positive) {
        VBox card = new VBox(8);
        card.getStyleClass().add("metric-card");
        card.setPadding(new Insets(20));
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("metric-value-large");
        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("metric-label");
        card.getChildren().addAll(valueLabel, labelLabel);
        if (change != null && !change.isEmpty()) {
            Label changeLabel = new Label(change);
            changeLabel.getStyleClass().add(positive ? "metric-change-positive" : "metric-change-negative");
            card.getChildren().add(changeLabel);
        }
        installCardHoverEffect(card);
        return card;
    }

    public static void fadeIn(Node node, double delayMillis) {
        if (node == null) return;
        node.setOpacity(0);
        PauseTransition d = new PauseTransition(Duration.millis(delayMillis));
        FadeTransition f  = new FadeTransition(Duration.millis(260), node);
        f.setToValue(1); f.setInterpolator(Interpolator.EASE_OUT);
        d.setOnFinished(e -> f.play()); d.play();
    }

    public static void fadeOut(Node node, Runnable onDone) {
        if (node == null) { if (onDone != null) onDone.run(); return; }
        FadeTransition f = new FadeTransition(Duration.millis(200), node);
        f.setToValue(0); f.setInterpolator(Interpolator.EASE_IN);
        if (onDone != null) f.setOnFinished(e -> onDone.run());
        f.play();
    }

    public static void slideUp(Node node, double delayMillis) {
        slideUp(node, delayMillis, 28, 360);
    }

    public static void slideUp(Node node, double delayMillis, double fromY, int ms) {
        if (node == null) return;
        node.setOpacity(0); node.setTranslateY(fromY);
        PauseTransition d  = new PauseTransition(Duration.millis(delayMillis));
        FadeTransition f   = new FadeTransition(Duration.millis(ms), node);
        f.setToValue(1); f.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition s = new TranslateTransition(Duration.millis(ms), node);
        s.setToY(0); s.setInterpolator(EASE_OUT_QUART);
        d.setOnFinished(e -> new ParallelTransition(f, s).play()); d.play();
    }

    public static void staggeredEntrance(Collection<? extends Node> nodes,
                                         double initialDelay, double stepDelay) {
        double delay = initialDelay;
        for (Node node : nodes) { slideUp(node, delay); delay += stepDelay; }
    }

    public static void popIn(Node node, double delayMillis) {
        if (node == null) return;
        node.setOpacity(0); node.setScaleX(0.82); node.setScaleY(0.82);
        PauseTransition pause = new PauseTransition(Duration.millis(delayMillis));
        ScaleTransition spring = new ScaleTransition(Duration.millis(300), node);
        spring.setToX(1.04); spring.setToY(1.04);
        spring.setInterpolator(SPRING_INTERPOLATOR);
        ScaleTransition settle = new ScaleTransition(Duration.millis(130), node);
        settle.setToX(1.0); settle.setToY(1.0);
        settle.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fi = new FadeTransition(Duration.millis(220), node);
        fi.setToValue(1.0); fi.setInterpolator(Interpolator.EASE_OUT);
        pause.setOnFinished(e ->
                new ParallelTransition(new SequentialTransition(spring, settle), fi).play());
        pause.play();
    }

    public static void popOut(Node node, Runnable onDone) {
        if (node == null) { if (onDone != null) onDone.run(); return; }
        node.setCache(true); node.setCacheHint(CacheHint.SCALE);
        ScaleTransition s = new ScaleTransition(Duration.millis(180), node);
        s.setToX(0.88); s.setToY(0.88); s.setInterpolator(Interpolator.EASE_IN);
        FadeTransition f  = new FadeTransition(Duration.millis(180), node);
        f.setToValue(0); f.setInterpolator(Interpolator.EASE_IN);
        ParallelTransition p = new ParallelTransition(s, f);
        p.setOnFinished(e -> { node.setCache(false); if (onDone != null) onDone.run(); });
        p.play();
    }

    public static void crossfadeViews(Region outgoing, Region incoming,
                                      javafx.scene.layout.Pane container) {
        if (incoming == null || container == null) return;

        // Prepare incoming view (slide up slightly while fading in)
        incoming.setOpacity(0);
        incoming.setTranslateY(15);
        container.getChildren().add(incoming);

        if (outgoing != null) {
            FadeTransition fo = new FadeTransition(Duration.millis(220), outgoing);
            fo.setToValue(0);
            fo.setInterpolator(Interpolator.EASE_IN);

            TranslateTransition to = new TranslateTransition(Duration.millis(220), outgoing);
            to.setToY(-10);
            to.setInterpolator(Interpolator.EASE_IN);

            ParallelTransition out = new ParallelTransition(fo, to);
            out.setOnFinished(e -> container.getChildren().remove(outgoing));
            out.play();
        }

        FadeTransition fi = new FadeTransition(Duration.millis(380), incoming);
        fi.setToValue(1);
        fi.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition ti = new TranslateTransition(Duration.millis(380), incoming);
        ti.setToY(0);
        ti.setInterpolator(EASE_OUT_QUART);

        new ParallelTransition(fi, ti).play();
    }

    public static void pulse(Node node, int cycles) {
        if (node == null) return;
        ScaleTransition up   = new ScaleTransition(Duration.millis(160), node);
        up.setToX(1.08); up.setToY(1.08); up.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition down = new ScaleTransition(Duration.millis(160), node);
        down.setToX(1.0); down.setToY(1.0); down.setInterpolator(Interpolator.EASE_IN);
        SequentialTransition seq = new SequentialTransition(up, down);
        seq.setCycleCount(cycles); seq.play();
    }

    public static void shake(Node node) {
        if (node == null) return;
        double ox = node.getTranslateX();
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(node.translateXProperty(), ox)),
                new KeyFrame(Duration.millis(55),  new KeyValue(node.translateXProperty(), ox - 11, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(110), new KeyValue(node.translateXProperty(), ox + 9,  Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(165), new KeyValue(node.translateXProperty(), ox - 7,  Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(220), new KeyValue(node.translateXProperty(), ox + 5,  Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(275), new KeyValue(node.translateXProperty(), ox - 3,  Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(330), new KeyValue(node.translateXProperty(), ox,      Interpolator.EASE_BOTH))
        );
        tl.play();
    }

    public static void flashSuccess(Node node) {
        if (node == null) return;
        String orig = node.getStyle();
        node.setStyle(orig + ";-fx-background-color:#D1FAE5;");
        PauseTransition hold = new PauseTransition(Duration.millis(750));
        hold.setOnFinished(e -> node.setStyle(orig));
        hold.play();
    }

    public static void flashError(Node node) {
        if (node == null) return;
        String orig = node.getStyle();
        node.setStyle(orig + ";-fx-background-color:#FEE2E2;");
        shake(node);
        PauseTransition hold = new PauseTransition(Duration.millis(700));
        hold.setOnFinished(e -> node.setStyle(orig));
        hold.play();
    }

    public static void animateCount(Label label, int targetValue,
                                    String prefix, String suffix) {
        if (label == null) return;
        int from = 0;
        try {
            String t = label.getText().replaceAll("[^0-9]", "");
            if (!t.isEmpty()) from = Integer.parseInt(t);
        } catch (NumberFormatException ignored) {}
        final int f = from, to = targetValue;
        Timeline tl = new Timeline();
        int steps = 32, ms = 950;
        for (int i = 1; i <= steps; i++) {
            double t  = (double) i / steps;
            double ez = 1 - Math.pow(1 - t, 3);
            int val   = f + (int) Math.round((to - f) * ez);
            tl.getKeyFrames().add(new KeyFrame(Duration.millis((double) ms / steps * i),
                    e -> label.setText(prefix + val + suffix)));
        }
        tl.play();
    }

    public static void animateThemeChange(Node root, Runnable applyTheme) {
        if (root == null) { if (applyTheme != null) applyTheme.run(); return; }
        FadeTransition out = new FadeTransition(Duration.millis(140), root);
        out.setToValue(0.12); out.setInterpolator(Interpolator.EASE_IN);
        out.setOnFinished(e -> {
            if (applyTheme != null) applyTheme.run();
            FadeTransition in = new FadeTransition(Duration.millis(200), root);
            in.setToValue(1); in.setInterpolator(Interpolator.EASE_OUT);
            in.play();
        });
        out.play();
    }

    public static Timeline shimmer(Region target) {
        if (target == null) return new Timeline();
        String base = darkMode ? "#1E293B" : "#E2E8F0";
        String glow = darkMode ? "#334155" : "#F1F5F9";
        target.setStyle("-fx-background-color:" + base + ";-fx-background-radius:8px;");
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,        e -> target.setStyle("-fx-background-color:"+base+";-fx-background-radius:8px;")),
                new KeyFrame(Duration.millis(600), e -> target.setStyle("-fx-background-color:"+glow+";-fx-background-radius:8px;")),
                new KeyFrame(Duration.millis(1200),e -> target.setStyle("-fx-background-color:"+base+";-fx-background-radius:8px;"))
        );
        tl.setCycleCount(Animation.INDEFINITE); tl.play();
        return tl;
    }

    public static void animateScale(Node node, double scale, int ms) {
        node.setCache(true); node.setCacheHint(CacheHint.SCALE);
        ScaleTransition t = new ScaleTransition(Duration.millis(ms), node);
        t.setToX(scale); t.setToY(scale);
        t.setInterpolator(EASE_OUT_QUART);
        t.setOnFinished(e -> node.setCache(false));
        t.play();
    }

    public static void installButtonAnimation(Button button) {
        boolean scaleable = !button.getStyleClass().contains("icon-button");
        if (scaleable) {
            button.hoverProperty().addListener((obs, was, isHover) ->
                    animateScale(button, isHover ? 1.03 : 1.0, 120));
        }
        button.pressedProperty().addListener((obs, was, isPressed) ->
                animateScale(button, isPressed ? 0.94 : (scaleable && button.isHover() ? 1.03 : 1.0),
                        isPressed ? 50 : 110));
        button.focusedProperty().addListener((obs, was, isFocused) ->
                animateScale(button, isFocused ? 1.015 : 1.0, 160));
    }

    /**
     * Installs a premium hover effect on a region, including scaling, lift, and shadow.
     *
     * @param card The region to apply the effect to.
     */
    public static void installCardHoverEffect(Region card) {
        boolean isDark = darkMode;
        DropShadow lift = new DropShadow();
        lift.setColor(Color.web(isDark ? "#000000" : "#0F172A", isDark ? 0.6 : 0.15));
        lift.setRadius(isDark ? 30 : 25); lift.setOffsetY(isDark ? 10 : 8);

        DropShadow rest = new DropShadow();
        rest.setColor(Color.web(isDark ? "#000000" : "#0F172A", isDark ? 0.4 : 0.08));
        rest.setRadius(isDark ? 16 : 10); rest.setOffsetY(isDark ? 4 : 3);

        card.setEffect(rest);

        card.hoverProperty().addListener((obs, was, isHover) -> {
            animateScale(card, isHover ? 1.025 : 1.0, 200);

            Timeline tl = new Timeline();
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(200),
                    new KeyValue(card.effectProperty(), isHover ? lift : rest, Interpolator.EASE_BOTH),
                    new KeyValue(card.translateYProperty(), isHover ? -5 : 0, Interpolator.EASE_OUT)
            ));
            tl.play();
        });
    }

    /**
     * Adds a subtle floating/bobbing animation to a node.
     *
     * @param node The node to animate.
     * @param distance The vertical distance to float.
     * @param seconds The duration of one full cycle.
     */
    public static void applyFloatingAnimation(Node node, double distance, double seconds) {
        TranslateTransition tt = new TranslateTransition(Duration.seconds(seconds), node);
        tt.setFromY(0);
        tt.setToY(-distance);
        tt.setCycleCount(TranslateTransition.INDEFINITE);
        tt.setAutoReverse(true);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.play();
    }

    public static void runLater(Runnable action) {
        Platform.runLater(action);
    }

    public static void runLater(Runnable action, double delayMillis) {
        PauseTransition delay = new PauseTransition(Duration.millis(delayMillis));
        delay.setOnFinished(e -> action.run());
        delay.play();
    }

    public static String formatNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    public static String formatCurrency(double amount) {
        try {
            AppConfiguration configuration = AppConfigurationService.getConfiguration();
            return configuration.formatAmount(amount);
        } catch (Exception ignored) {
            return String.format("$%,.2f", amount);
        }
    }

    public static void showTemporaryMessage(Label label, String message, String styleClass, double durationSeconds) {
        label.setText(message);
        label.getStyleClass().setAll("validation-message", styleClass);
        label.setVisible(true);
        PauseTransition delay = new PauseTransition(Duration.seconds(durationSeconds));
        delay.setOnFinished(e -> {
            label.setVisible(false);
            label.setText("");
        });
        delay.play();
    }

    private static Image createAppIcon(int size) {
        try {
            URL url = AppTheme.class.getResource("/icon.png");
            if (url != null) {
                return new Image(url.openStream(), size, size, true, true);
            }
        } catch (Exception e) {
            System.err.println("Failed to load icon from resources: " + e.getMessage());
        }

        Canvas canvas = new Canvas(size, size);
        GraphicsContext graphics = canvas.getGraphicsContext2D();

        // Background
        graphics.setFill(new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0F172A")),
                new Stop(0.55, Color.web("#134E4A")),
                new Stop(1, Color.web("#14B8A6"))));
        graphics.fillRoundRect(0, 0, size, size, size * 0.28, size * 0.28);

        // Decorative background circle
        graphics.setFill(Color.web("#FFFFFF", 0.12));
        graphics.fillOval(size * (64.0-170.0)/256.0, size * (51.0-170.0)/256.0, size * 340.0/256.0, size * 340.0/256.0);

        // Book block
        graphics.setFill(Color.WHITE);
        graphics.fillRoundRect(size * 66.0/256.0, size * 46.0/256.0, size * 123.0/256.0, size * 148.0/256.0, size * 20.0/256.0, size * 20.0/256.0);

        // Book spine
        graphics.setFill(Color.web("#CCFBF1"));
        graphics.fillRoundRect(size * 66.0/256.0, size * 46.0/256.0, size * 37.0/256.0, size * 148.0/256.0, size * 10.0/256.0, size * 10.0/256.0);

        // Book lines
        graphics.setStroke(Color.web("#0F766E", 0.85));
        graphics.setLineWidth(size * 7.0/256.0);
        graphics.strokeLine(size * 92.0/256.0, size * 60.0/256.0, size * 172.0/256.0, size * 60.0/256.0);
        graphics.strokeLine(size * 92.0/256.0, size * 103.0/256.0, size * 172.0/256.0, size * 103.0/256.0);
        graphics.strokeLine(size * 92.0/256.0, size * 146.0/256.0, size * 147.0/256.0, size * 146.0/256.0);

        // Bottom decoration
        graphics.setFill(Color.web("#99F6E4"));
        graphics.fillRoundRect(size * 77.0/256.0, size * 190.0/256.0, size * 103.0/256.0, size * 20.0/256.0, size * 8.0/256.0, size * 8.0/256.0);
        WritableImage image = new WritableImage(size, size);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        canvas.snapshot(sp, image);
        return image;
    }

    public static void staggeredEntrance(Parent container, double initialDelay) {
        if (container == null) return;
        List<Node> children = new ArrayList<>();
        if (container instanceof Pane pane) children.addAll(pane.getChildren());
        else if (container instanceof GridPane gp) children.addAll(gp.getChildren());

        double delay = initialDelay;
        for (Node child : children) {
            child.setOpacity(0);
            child.setTranslateY(20);

            FadeTransition ft = new FadeTransition(Duration.millis(450), child);
            ft.setToValue(1.0);
            ft.setDelay(Duration.millis(delay));

            TranslateTransition tt = new TranslateTransition(Duration.millis(500), child);
            tt.setToY(0);
            tt.setDelay(Duration.millis(delay));
            tt.setInterpolator(EASE_OUT_QUART);

            new ParallelTransition(ft, tt).play();
            delay += 75; // Stagger
        }
    }

    private static void applyTaskbarIcon(Image fxImage) {
        if (fxImage == null) return;
        // SwingFXUtils.fromFXImage must run on the JavaFX Application Thread (already here).
        // Taskbar.setIconImage must be dispatched onto the AWT Event Dispatch Thread.
        try {
            BufferedImage awtImage = SwingFXUtils.fromFXImage(fxImage, null);
            if (awtImage == null) return;
            java.awt.EventQueue.invokeLater(() -> {
                try {
                    if (!Taskbar.isTaskbarSupported()) return;
                    Taskbar taskbar = Taskbar.getTaskbar();
                    if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return;
                    taskbar.setIconImage(awtImage);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    public static void showSecurityEnforcementError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Security Enforcement - Database Required");
        alert.setHeaderText("Access Denied: Central Database Not Configured");

        String propsPath = AppPaths.databasePropertiesFile().toString();

        String content = "Regular users are not permitted to access the system while it is running in local-file mode.\n\n" +
                "To resolve this, an Administrator must configure a central database (MySQL, PostgreSQL, etc.) " +
                "in the configuration properties file.\n\n" +
                "File Location:\n" + propsPath + "\n\n" +
                "Steps to fix:\n" +
                "1. Open the file above in a text editor.\n" +
                "2. Set 'db.engine' to a remote database type (e.g., MYSQL).\n" +
                "3. Provide the host, port, and credentials.\n" +
                "4. Restart the application.\n\n" +
                "The application will now terminate.";

        alert.setContentText(content);

        // Ensure the dialog is styled correctly
        DialogPane pane = alert.getDialogPane();
        pane.setMinWidth(600);
        applyTheme(pane);

        alert.showAndWait();
        Platform.exit();
        System.exit(1);
    }
}