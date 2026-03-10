package com.example.entities;

import com.example.exceptions.UserException;
import com.example.exceptions.ValidationException;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Enhanced User entity with comprehensive validation, proper encapsulation,
 * and additional user properties.
 */
public final class User implements Serializable {
    private static final long serialVersionUID = 2L; // Incremented for version tracking

    // Validation patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^[+]?[0-9]{10,15}$"
    );
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

    /**
     * Creates a new user with required fields and validation.
     *
     * @param userId the unique user identifier
     * @param password the user's password
     * @throws UserException if validation fails
     */
    public User(String userId, String password) throws UserException {
        validateAndSetUserId(userId);
        validateAndSetPassword(password);

        // Initialize additional properties
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.email = null;
        this.contactNumber = null;
        this.firstName = null;
        this.lastName = null;
        this.lastLoginAt = null;
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
        } else {
            this.email = null;
        }
    }

    public void setContactNumber(String contactNumber) throws ValidationException {
        if (contactNumber != null && !contactNumber.trim().isEmpty()) {
            String trimmedContact = contactNumber.trim().replaceAll("\\s+", "");
            if (!PHONE_PATTERN.matcher(trimmedContact).matches()) {
                throw new ValidationException("Invalid contact number format: " + contactNumber);
            }
            this.contactNumber = trimmedContact;
        } else {
            this.contactNumber = null;
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

        this.password = password;
    }

    // --- Utility Methods ---

    /**
     * Validates the current user object's state.
     *
     * @throws UserException if the user object is in an invalid state
     */
    public void validate() throws UserException {
        validateAndSetUserId(this.userId);
        validateAndSetPassword(this.password);

        if (this.email != null) {
            setEmail(this.email);
        }

        if (this.contactNumber != null) {
            setContactNumber(this.contactNumber);
        }
    }

    /**
     * Creates a copy of this user with sensitive information removed.
     *
     * @return a safe copy without password information
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
        return String.format("User{userId='%s', email='%s', active=%s, created=%s}",
                userId, email, isActive, createdAt);
    }

    /**
     * Creates a detailed string representation including all non-sensitive fields.
     *
     * @return detailed string representation
     */
    public String toDetailedString() {
        return String.format(
                "User{userId='%s', email='%s', contact='%s', name='%s', active=%s, created=%s, lastLogin=%s}",
                userId, email, contactNumber, getFullName(), isActive, createdAt, lastLoginAt
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
}