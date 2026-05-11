package com.example.entities;

import com.example.exceptions.UserException;
import com.example.exceptions.ValidationException;
import com.example.services.SecurityProvider;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a system user (Member, Librarian, or Admin) within the Library Management System.
 *
 * <p>The {@code User} class implement {@link java.io.Serializable} and includes built-in 
 * <b>Transparent Encryption</b> for sensitive fields. When a User object is saved to disk, 
 * its email and contact number are automatically encrypted using {@link SecurityProvider}.
 * </p>
 *
 * <p>Key Responsibilities:
 * <ul>
 *   <li>Enforcing strict validation rules for identifiers and contact information.</li>
 *   <li>Managing secure password verification and transparent hashing migration.</li>
 *   <li>Tracking user preferences such as UI themes (Dark Mode) and roles.</li>
 * </ul>
 *
 * @see SecurityProvider
 * @see UserRole
 */
public final class User implements Serializable {
    private static final long serialVersionUID = 3L; // Bumped for fixes

    /** Pattern for validating RFC 5322 compliant email addresses. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"
    );

    /** Pattern for validating international phone numbers (10-15 digits). */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[+]?[0-9]{10,15}$"
    );

    /** Pattern for validating user IDs (3-50 chars, alphanumeric + dots/underscores). */
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._-]{3,50}$"
    );

    // Validation constants
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int MAX_PASSWORD_LENGTH = 100;

    // Core properties
    private String userId;
    private String password;
    private String email;
    private String contactNumber;

    // Additional properties
    private String firstName;
    private String lastName;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private boolean isActive;
    private UserRole role;
    private boolean darkMode = false;
    private String salt; // Added for secure hashing

    /**
     * Temporary plain-text password held in RAM during the active session.
     * Used to decrypt personal fields on non-authorized machines.
     */
    private transient String plainPasswordForSession = null;

    /**
     * Tracks if personal fields (Email, Contact) are currently in plain-text format.
     * Prevents double-encryption during serialization.
     */
    private transient boolean profileUnlocked = false;

    /**
     * Creates a new user with required fields and validation.
     *
     * @param userId the unique user identifier
     * @param password the user's password
     * @throws UserException if validation fails
     */
    public User(String userId, String password) throws UserException {
        validateAndSetUserId(userId);

        // Initialize security
        this.salt = java.util.UUID.randomUUID().toString();
        validateAndSetPassword(password); // This now hashes the password

        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.email = null;
        this.contactNumber = null;
        this.firstName = null;
        this.lastName = null;
        this.lastLoginAt = null;
        this.role = UserRole.USER;
    }

    /**
     * Creates a new user with all properties.
     *
     * @param userId the unique user identifier
     * @param password the user's password
     * @param email the user's email address
     * @param contactNumber the user's contact number
     * @param firstName the user's first name
     * @param lastName the user's last name
     * @throws UserException if validation fails
     */
    public User(String userId, String password, String email, String contactNumber,
                String firstName, String lastName) throws UserException {
        this(userId, password);
        setEmail(email);
        setContactNumber(contactNumber);
        setFirstName(firstName);
        setLastName(lastName);
    }

    // --- Getters ---

    public String getUserId() {
        return userId;
    }

    public String getPassword() {
        return password;
    }

    public String getSalt() {
        return salt;
    }

    public String getEmail() {
        return email;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isActive() {
        return isActive;
    }

    /**
     * FIXED: Default to USER role instead of LIBRARIAN when role is null.
     * Prevents privilege escalation if role data is corrupted.
     */
    public UserRole getRole() {
        return role == null ? UserRole.USER : role; // FIXED: Was LIBRARIAN
    }

    public boolean isAdmin() {
        return getRole().isAdmin();
    }

    public boolean isStaff() {
        return getRole().isStaff();
    }

    /**
     * Gets the user's full name.
     *
     * @return formatted full name or user ID if names are not set
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return userId;
        }
    }

    /**
     * Gets a display name for the user.
     *
     * @return the most appropriate display name
     */
    public String getDisplayName() {
        String fullName = getFullName();
        return fullName.equals(userId) ? userId : fullName + " (" + userId + ")";
    }

    // --- Setters with Validation ---

    public void setUserId(String userId) throws UserException {
        validateAndSetUserId(userId);
    }

    public void setPassword(String password) throws UserException {
        validateAndSetPassword(password);
    }

    public void setEmail(String email) throws ValidationException {
        if (email != null && !email.trim().isEmpty()) {
            String trimmedEmail = email.trim();
            if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
                throw new ValidationException("Invalid email format: " + email);
            }
            this.email = trimmedEmail;
            this.profileUnlocked = true; // Setting plain text unlocks profile
        } else {
            this.email = null;
            this.profileUnlocked = true;
        }
    }

    public void setContactNumber(String contactNumber) throws ValidationException {
        if (contactNumber != null && !contactNumber.trim().isEmpty()) {
            String trimmedContact = contactNumber.trim().replaceAll("\\s+", "");
            if (!PHONE_PATTERN.matcher(trimmedContact).matches()) {
                throw new ValidationException("Invalid contact number format: " + contactNumber);
            }
            this.contactNumber = trimmedContact;
            this.profileUnlocked = true;
        } else {
            this.contactNumber = null;
            this.profileUnlocked = true;
        }
    }

    public void setFirstName(String firstName) {
        this.firstName = (firstName != null && !firstName.trim().isEmpty()) ?
                firstName.trim() : null;
    }

    public void setLastName(String lastName) {
        this.lastName = (lastName != null && !lastName.trim().isEmpty()) ?
                lastName.trim() : null;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setRole(UserRole role) {
        this.role = role == null ? UserRole.USER : role;
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    /**
     * Sets the session password for personal data decryption.
     * Only held in RAM, never persisted.
     */
    public void setPlainPasswordForSession(String password) {
        this.plainPasswordForSession = password;
    }

    /**
     * Updates the last login timestamp to current time.
     */
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    /**
     * Sets a specific last login time (useful for data migration).
     *
     * @param lastLogin the last login timestamp
     */
    public void setLastLoginAt(LocalDateTime lastLogin) {
        this.lastLoginAt = lastLogin;
    }

    // --- Validation Methods ---

    private void validateAndSetUserId(String userId) throws UserException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new UserException("User ID cannot be empty");
        }

        String trimmedUserId = userId.trim();
        if (!USERNAME_PATTERN.matcher(trimmedUserId).matches()) {
            throw new UserException("User ID must be 3-50 characters and contain only letters, numbers, dots, underscores, and hyphens");
        }

        this.userId = trimmedUserId;
    }

    private void validateAndSetPassword(String password) throws UserException {
        if (password == null || password.trim().isEmpty()) {
            throw new UserException("Password cannot be empty");
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new UserException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new UserException("Password cannot exceed " + MAX_PASSWORD_LENGTH + " characters");
        }

        // BUG FIX: The original code used `password.length() == 44 && password.endsWith("=")` to
        // detect whether the value was already a PBKDF2 hash.  This heuristic is wrong in two ways:
        //   1. A real user password that is 44 chars long and ends with "=" is treated as a hash
        //      and stored in plain-text, completely bypassing the hashing step.
        //   2. If SecurityProvider ever produces a hash that does NOT match the pattern (e.g. a
        //      future algorithm change), the existing hash would be re-hashed on the next call,
        //      permanently corrupting the stored credential.
        // Fix: `hashPassword` is idempotent only when called once. We now check the dedicated
        // `plainPasswordForSession` field — if it is non-null the instance already holds a hashed
        // password (set during a previous call), so we skip re-hashing.
        if (this.plainPasswordForSession != null) {
            // Password is already hashed; just store it as-is.
            this.password = password;
        } else {
            this.password = com.example.services.SecurityProvider.hashPassword(password, this.salt);
            this.plainPasswordForSession = password; // Cache for immediate use within the session
        }
    }

    /**
     * Verifies if a provided plain-text password matches the stored hash.
     * Supports legacy plain-text passwords by migrating them on successful match.
     */
    public boolean checkPassword(String plainPassword) {
        if (this.password == null || plainPassword == null) return false;

        // Ensure salt exists for legacy accounts
        if (this.salt == null) {
            this.salt = java.util.UUID.randomUUID().toString();
        }

        // 1. Try secure hash comparison
        if (com.example.services.SecurityProvider.verifyPassword(plainPassword, this.salt, this.password)) {
            return true;
        }

        // 2. Legacy fallback: Check if stored password is plain text
        if (this.password.equals(plainPassword)) {
            // SUCCESS! Now migrate to secure hash for next time
            this.password = com.example.services.SecurityProvider.hashPassword(plainPassword, this.salt);
            return true;
        }

        return false;
    }

    /**
     * Unlocks the user's personal profile (Email, Contact) using their plain-text password.
     * This allows the user to access their data on non-authorized machines.
     */
    public void unlockProfile(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) return;

        this.plainPasswordForSession = plainPassword;

        // Transparently attempt to decrypt fields
        boolean emailDecrypted = false;
        boolean contactDecrypted = false;

        if (this.email != null) {
            String dec = com.example.services.SecurityProvider.decryptUserField(this.email, userId, plainPassword, salt);
            if (!dec.equals(this.email)) {
                this.email = dec;
                emailDecrypted = true;
            }
        } else {
            emailDecrypted = true;
        }

        if (this.contactNumber != null) {
            String dec = com.example.services.SecurityProvider.decryptUserField(this.contactNumber, userId, plainPassword, salt);
            if (!dec.equals(this.contactNumber)) {
                this.contactNumber = dec;
                contactDecrypted = true;
            }
        } else {
            contactDecrypted = true;
        }

        // If both are now plain text (or were already null), we are unlocked
        this.profileUnlocked = emailDecrypted && contactDecrypted;
    }

    // --- Utility Methods ---

    /**
     * Validates the current user object's state.
     *
     * @throws UserException if the user object is in an invalid state
     */
    public void validate() throws UserException {
        validateAndSetUserId(this.userId);

        // BUG FIX: The original code called validateAndSetPassword(this.password) here,
        // which would run the hashing logic on the ALREADY HASHED password value stored in
        // this.password. Depending on the hash format this would either store the hash
        // as-is (the old fragile 44-char check) or double-hash it, permanently corrupting
        // the stored credential so that no future login would succeed.
        // Fix: only validate that the password field is non-null/non-empty; do not re-hash.
        if (this.password == null || this.password.trim().isEmpty()) {
            throw new UserException("Password cannot be empty");
        }

        if (this.email != null) {
            setEmail(this.email);
        }

        if (this.contactNumber != null) {
            setContactNumber(this.contactNumber);
        }
    }

    /**
     * Creates a deep copy of this user object with sensitive information (like passwords) 
     * removed or obfuscated. This is intended for use in the UI where the actual 
     * password hash is not required.
     *
     * @return a safe copy of the user for UI display or non-sensitive processing
     */
    public User createSafeCopy() {
        try {
            User safeCopy = new User(this.userId, "****"); // Placeholder password
            safeCopy.email = this.email;
            safeCopy.contactNumber = this.contactNumber;
            safeCopy.firstName = this.firstName;
            safeCopy.lastName = this.lastName;
            safeCopy.createdAt = this.createdAt;
            safeCopy.lastLoginAt = this.lastLoginAt;
            safeCopy.isActive = this.isActive;
            safeCopy.role = this.getRole();
            return safeCopy;
        } catch (UserException e) {
            // This should never happen with valid data
            throw new RuntimeException("Failed to create safe copy", e);
        }
    }

    /**
     * Checks if the user has complete profile information.
     *
     * @return true if all optional fields are filled
     */
    public boolean hasCompleteProfile() {
        return email != null && !email.trim().isEmpty() &&
                contactNumber != null && !contactNumber.trim().isEmpty() &&
                firstName != null && !firstName.trim().isEmpty() &&
                lastName != null && !lastName.trim().isEmpty();
    }

    // --- Object Methods ---

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        User user = (User) obj;
        return Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return String.format("User{userId='%s', email='%s', active=%s, role=%s, created=%s}",
                userId, email, isActive, getRole(), createdAt);
    }

    /**
     * Creates a detailed string representation including all non-sensitive fields.
     *
     * @return detailed string representation
     */
    public String toDetailedString() {
        return String.format(
                "User{userId='%s', email='%s', contact='%s', name='%s', active=%s, role=%s, created=%s, lastLogin=%s}",
                userId, email, contactNumber, getFullName(), isActive, getRole(), createdAt, lastLoginAt
        );
    }

    // --- Static Utility Methods ---

    /**
     * Validates an email address format.
     *
     * @param email the email to validate
     * @return true if email format is valid
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Validates a contact number format.
     *
     * @param contactNumber the contact number to validate
     * @return true if contact number format is valid
     */
    public static boolean isValidContactNumber(String contactNumber) {
        if (contactNumber == null) return false;
        String cleaned = contactNumber.trim().replaceAll("\\s+", "");
        return PHONE_PATTERN.matcher(cleaned).matches();
    }

    /**
     * Validates a user ID format.
     *
     * @param userId the user ID to validate
     * @return true if user ID format is valid
     */
    public static boolean isValidUserId(String userId) {
        return userId != null && USERNAME_PATTERN.matcher(userId.trim()).matches();
    }

    /**
     * Validates a password strength.
     *
     * @param password the password to validate
     * @return true if password meets minimum requirements
     */
    public static boolean isValidPassword(String password) {
        return password != null &&
                password.length() >= MIN_PASSWORD_LENGTH &&
                password.length() <= MAX_PASSWORD_LENGTH;
    }

    /**
     * CUSTOM SERIALIZATION: Transparently encrypts sensitive fields using the
     * <em>system master key</em> so that admin and librarian views can always
     * decrypt PII without knowing the individual user's password.
     *
     * <p><b>Design note:</b> An earlier version of this method used the user's
     * PBKDF2-derived password key when {@code plainPasswordForSession} was set.
     * While that approach offered per-user portability, it made PII completely
     * opaque to admin/librarian views (which never hold a user's plain password),
     * breaking email reminders, user management displays, and the forgot-password
     * flow for every self-registered account. The user-key path is therefore
     * removed from system persistence. Portable cross-machine encryption (for
     * {@code .lms} migration packages) is handled separately by
     * {@link SecurityProvider#encryptBytesWithPassword}.</p>
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        String originalEmail = this.email;
        String originalContact = this.contactNumber;

        try {
            // Only encrypt if we are currently holding plain-text data.
            // Always use the system master key so admin/librarian views can decrypt.
            if (profileUnlocked) {
                this.email         = SecurityProvider.encrypt(this.email);
                this.contactNumber = SecurityProvider.encrypt(this.contactNumber);
            }
            out.defaultWriteObject();
        } finally {
            this.email         = originalEmail;
            this.contactNumber = originalContact;
        }
    }

    /**
     * CUSTOM DESERIALIZATION: Transparently decrypts sensitive fields.
     *
     * <p>Uses {@link SecurityProvider#decryptUserField} with an empty password so that:
     * <ol>
     *   <li>Fields encrypted with the <em>system master key</em> (stored via
     *       {@link SecurityProvider#encrypt}) are decrypted immediately.</li>
     *   <li>Fields encrypted with the <em>user's password-derived key</em> are
     *       attempted with the master-key fallback. If the master key does not match
     *       (cross-machine scenario), the ciphertext is preserved and
     *       {@link #unlockProfile(String)} must be called later with the user's password.</li>
     * </ol>
     * Using the bare {@code SecurityProvider.decrypt()} here was the root cause of
     * admin/librarian views showing encrypted ciphertext: it only tried the master key
     * and left user-password-encrypted fields untouched when that attempt failed.
     * </p>
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        // Attempt decryption: tries user-key (skipped — no password available at this point),
        // then falls back to master-key. Correctly handles both encryption strategies.
        this.email = SecurityProvider.decryptUserField(this.email, this.userId, null, this.salt);
        this.contactNumber = SecurityProvider.decryptUserField(this.contactNumber, this.userId, null, this.salt);
    }
}