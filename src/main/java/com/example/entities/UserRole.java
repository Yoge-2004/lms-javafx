package com.example.entities;

/**
 * Enumeration of the roles that a {@link User} account may hold within
 * Library OS.
 *
 * <p>Roles determine which views and operations are accessible to a logged-in
 * user.  The access hierarchy from least to most privileged is:</p>
 * <pre>
 *   USER  &lt;  LIBRARIAN  &lt;  RESTRICTED_ADMIN  &lt;  ADMIN
 * </pre>
 *
 * <p>Use the predicate methods {@link #isAdmin()} and {@link #isStaff()} for
 * capability checks rather than comparing enum constants directly, so that
 * any future role additions require only localised updates here.</p>
 */
public enum UserRole {

    /** A regular patron who may search the catalogue and borrow books. */
    USER("User"),

    /**
     * A full administrator who has unrestricted access to all management
     * functions including user creation, deletion, and system configuration.
     */
    ADMIN("Administrator"),

    /**
     * A librarian who can manage circulation (issue, return, overdue) and
     * approve borrow/payment requests but cannot modify system configuration.
     */
    LIBRARIAN("Librarian"),

    /**
     * An administrator whose privileges are intentionally narrowed — for
     * example, a branch manager who can approve payments but cannot alter
     * the global library setup.
     */
    RESTRICTED_ADMIN("Restricted Admin");

    // ──────────────────────────────────────────────────────────────────────

    /** The localised label shown in the user interface for this role. */
    private final String displayName;

    /**
     * Enum constructor — associates a human-readable display name with the
     * constant.
     *
     * @param displayName the label shown in drop-downs and user-detail panels
     */
    UserRole(String displayName) {
        this.displayName = displayName;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /**
     * Returns the human-readable name for this role as shown in the UI.
     *
     * @return a non-null, non-empty display string (e.g. {@code "Librarian"})
     */
    public String getDisplayName() {
        return displayName;
    }

    // ── Capability predicates ─────────────────────────────────────────────

    /**
     * Returns {@code true} if this role grants administrative privileges.
     *
     * <p>Both {@link #ADMIN} and {@link #RESTRICTED_ADMIN} satisfy this
     * predicate.  Use this check to guard configuration and user-management
     * screens.</p>
     *
     * @return {@code true} for {@code ADMIN} and {@code RESTRICTED_ADMIN}
     */
    public boolean isAdmin() {
        return this == ADMIN || this == RESTRICTED_ADMIN;
    }

    /**
     * Returns {@code true} if this role belongs to library staff.
     *
     * <p>Staff members ({@link #LIBRARIAN}, {@link #ADMIN}, and
     * {@link #RESTRICTED_ADMIN}) have access to the circulation panel,
     * user-management view, and the analytics dashboard.</p>
     *
     * @return {@code true} for any role other than {@link #USER}
     */
    public boolean isStaff() {
        return this == LIBRARIAN || this == ADMIN || this == RESTRICTED_ADMIN;
    }
}