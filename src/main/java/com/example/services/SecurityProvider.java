package com.example.services;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code SecurityProvider} class serves as the central cryptographic hub for
 * Library OS.
 *
 * <p>It provides two categories of cryptographic service:</p>
 * <ol>
 *   <li><b>PII encryption</b> — AES-256-GCM authenticated encryption for
 *       personally-identifiable fields (email, contact number) stored in
 *       {@link com.example.entities.User}.  Two key strategies are supported:
 *       <ul>
 *         <li><em>User-key encryption</em> — the field is encrypted with a key
 *             derived from the user's own password via PBKDF2.  The plaintext is
 *             only recoverable by the user (or the system master key as fallback).
 *             This is the preferred strategy and is used whenever
 *             {@code plainPasswordForSession} is available at serialisation time.</li>
 *         <li><em>System-master-key encryption</em> — used as a fallback when the
 *             user's password is not held in memory (e.g. during admin edits).
 *             The master key is either an explicit library secret set via
 *             {@link #setLibraryMasterKey}, a JVM property, an environment
 *             variable, or a hardware-fingerprint-derived secret.</li>
 *       </ul>
 *   </li>
 *   <li><b>Password hashing</b> — PBKDF2-HMAC-SHA256 with a per-user random
 *       salt (10 000 iterations, 256-bit output) for credential storage.  A
 *       legacy SHA-256 path is retained so that accounts created by older
 *       versions can still authenticate.</li>
 * </ol>
 *
 * <p><b>Migration note:</b> When a {@code .lms} package is imported on a
 * different machine, call {@link #setLibraryMasterKey(String)} with the
 * embedded admin secret so that the master key matches the one used at export
 * time.  Without this step, master-key decryption produces garbled text.</p>
 *
 * <p>This is a non-instantiable utility class; all members are {@code static}.</p>
 */
public final class SecurityProvider {

    /** Logger for encryption/decryption diagnostics. */
    private static final Logger LOGGER = Logger.getLogger(SecurityProvider.class.getName());

    /**
     * Cryptographically-strong random number generator shared across all
     * encryption operations.  {@link SecureRandom} is thread-safe.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** JCA algorithm string for AES in GCM mode without padding. */
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";

    /** JCA algorithm string for the PBKDF2 key-derivation function. */
    private static final String PBKDF2_ALGO = "PBKDF2WithHmacSHA256";

    /** GCM authentication tag length in bits (128 is the recommended NIST minimum). */
    private static final int TAG_LENGTH_BIT = 128;

    /** AES-GCM initialisation-vector length in bytes (96 bits, NIST SP 800-38D). */
    private static final int IV_LENGTH_BYTE = 12;

    /** Random salt length in bytes used for PBKDF2 and migration package encryption. */
    private static final int SALT_LENGTH_BYTE = 16;

    /** AES key length in bits. */
    private static final int KEY_LENGTH_BIT = 256;

    /** PBKDF2 iteration count — 10 000 makes brute-force attacks impractical. */
    private static final int PBKDF2_ITERATIONS = 10000;

    /**
     * Application-specific entropy string mixed into the hardware fingerprint
     * when no explicit library secret has been configured.
     */
    private static final String INTERNAL_SEED = "LibraryOS-Static-Seed-2024-Secure-Persistence-V3";

    /**
     * Derived AES key bytes for system-wide (master key) encryption.
     * Re-initialised every time {@link #setLibraryMasterKey(String)} is called.
     */
    private static byte[] MASTER_KEY_BYTES;
    private static byte[] MACHINE_KEY_BYTES;

    /**
     * The explicit library master key set by an administrator, or {@code null}
     * when the key is derived from hardware / environment.
     */
    private static String libraryMasterKey = null;

    // Initialise the master key as soon as the class is loaded
    static {
        initializeKey();
    }

    /**
     * Sets a shared library-wide master key, enabling consistent encryption
     * across multiple machines on the same network (multi-machine mode).
     *
     * <p>After this call, all subsequent {@link #encrypt}/{@link #decrypt}
     * operations use a key derived from {@code key} rather than from the local
     * hardware fingerprint.  This is called automatically when a {@code .lms}
     * migration package is imported so that PII encrypted on the source machine
     * can be decrypted on the target machine.</p>
     *
     * <p>A {@code null} or blank value is silently ignored; the existing key
     * remains in effect.</p>
     *
     * @param key the administrator-set library secret; should be at least
     *            16 characters for adequate entropy
     */
    public static void setLibraryMasterKey(String key) {
        if (key != null && !key.isBlank()) {
            libraryMasterKey = key;
            initializeKey();
        }
    }

    /** Private constructor — prevents instantiation of this utility class. */
    private SecurityProvider() {}

    /**
     * (Re-)derives {@link #MASTER_KEY_BYTES} from the best-available secret.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>{@link #libraryMasterKey} (set by admin or migration import)</li>
     *   <li>{@code -Dlibrary.secret} JVM property</li>
     *   <li>{@code LIBRARY_OS_SECRET} environment variable</li>
     *   <li>{@value #INTERNAL_SEED} + {@code :} + hardware MAC fingerprint
     *       (machine-specific, single-machine default)</li>
     * </ol>
     *
     * <p>The chosen secret is hashed with SHA-256 to produce the 256-bit AES
     * key material stored in {@link #MASTER_KEY_BYTES}.</p>
     *
     * @throws RuntimeException if SHA-256 is not available in the JVM
     *                          (should never happen on any standard JRE)
     */
    private static void initializeKey() {
        try {
            String hardwareId = getMachineFingerprint();
            String machineSecret = INTERNAL_SEED + ":" + hardwareId;
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            MACHINE_KEY_BYTES = sha.digest(machineSecret.getBytes(StandardCharsets.UTF_8));

            String secret = libraryMasterKey;

            if (secret == null || secret.isBlank()) {
                secret = System.getProperty("library.secret");
            }
            if (secret == null || secret.isBlank()) {
                secret = System.getenv("LIBRARY_OS_SECRET");
            }
            if (secret == null || secret.isBlank()) {
                MASTER_KEY_BYTES = MACHINE_KEY_BYTES;
                LOGGER.info("Using machine-specific hardware fingerprint for encryption key.");
            } else {
                LOGGER.info("Using explicit Master Key for encryption (Multi-machine mode).");
            }

            if (secret != null && !secret.isBlank()) {
                MASTER_KEY_BYTES = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Security initialization failed", e);
        }
    }

    // ── User-Specific Encryption (Personal PC Portability) ────────────────

    /**
     * Encrypts a sensitive user field (email or contact number) using a key
     * derived from the user's own password via PBKDF2.
     *
     * <p>The output format is Base64({@value #IV_LENGTH_BYTE}-byte IV ‖ AES-GCM ciphertext).</p>
     *
     * <p>Returns the original {@code plainText} unchanged if encryption fails,
     * so the caller is always given a non-null result.</p>
     *
     * @param plainText the plaintext to encrypt; returns {@code null} if blank or null
     * @param userId    the user's unique identifier (mixed into the key derivation)
     * @param password  the user's plain-text password (used for PBKDF2 key derivation)
     * @param salt      the per-user random salt (generated at account creation)
     * @return Base64-encoded ciphertext, or the original plaintext on failure
     */
    public static String encryptUserField(String plainText, String userId, String password, String salt) {
        if (plainText == null || plainText.isBlank()) return null;
        try {
            byte[] data = plainText.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec key = deriveUserKey(userId, password, salt);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] ciphertext = cipher.doFinal(data);
            ByteBuffer bb = ByteBuffer.allocate(iv.length + ciphertext.length);
            bb.put(iv);
            bb.put(ciphertext);
            return Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Encryption failed for user " + userId, e);
            return plainText;
        }
    }

    /**
     * Decrypts a user field, first trying the user's own password then falling
     * back to the system master key.
     *
     * <p>This two-step fallback allows admin/librarian views to display PII even
     * when the user's plain-text password is not held in memory — as long as
     * the system master key has not changed (e.g. no cross-machine migration).</p>
     *
     * <p>If both attempts fail the original {@code encryptedBase64} string is
     * returned unchanged (it will appear as a garbled hash in the UI, signalling
     * a key mismatch).</p>
     *
     * @param encryptedBase64 the Base64-encoded ciphertext to decrypt
     * @param userId          the user's unique identifier (used for key derivation)
     * @param password        the user's plain-text password; may be empty/null to
     *                        skip the user-key attempt and go straight to master key
     * @param salt            the per-user random salt
     * @return the decrypted plaintext, or the original ciphertext if both attempts fail
     */
    public static String decryptUserField(String encryptedBase64, String userId, String password, String salt) {
        if (encryptedBase64 == null || encryptedBase64.isBlank()) return encryptedBase64;

        // Attempt 1 — user-specific key derived from their password
        if (password != null && !password.isEmpty()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(encryptedBase64);
                ByteBuffer bb = ByteBuffer.wrap(decoded);

                byte[] iv = new byte[IV_LENGTH_BYTE];
                if (bb.remaining() >= IV_LENGTH_BYTE) {
                    bb.get(iv);
                    byte[] ciphertext = new byte[bb.remaining()];
                    bb.get(ciphertext);

                    SecretKeySpec key = deriveUserKey(userId, password, salt != null ? salt : "");
                    Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
                    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

                    return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
                // Fall through to master-key attempt
            }
        }

        // Attempt 2 — system master key (used by admin views and older serialised data)
        try {
            return decrypt(encryptedBase64);
        } catch (Exception ignored) {}

        // Both failed — return ciphertext as-is; caller should surface a key-mismatch warning
        return encryptedBase64;
    }

    /**
     * Derives a user-specific AES key from the user's password and salt using
     * PBKDF2-HMAC-SHA256.
     *
     * <p>The derivation salt is {@code accountSalt + ":" + userId} to ensure
     * that the same password on two different accounts produces different keys.</p>
     *
     * @param userId   the user's unique identifier
     * @param password the user's plain-text password
     * @param salt     the per-user account salt
     * @return a 256-bit AES key specification
     * @throws Exception if PBKDF2 is unavailable or key derivation fails
     */
    private static SecretKeySpec deriveUserKey(String userId, String password, String salt) throws Exception {
        // Mix the account salt with the userId to prevent cross-account key reuse
        String combinedSalt = salt + ":" + userId.toLowerCase().trim();
        return deriveKeyWithPBKDF2(password, combinedSalt.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Derives a 256-bit AES key from a password and raw salt bytes using
     * PBKDF2-HMAC-SHA256 with {@value #PBKDF2_ITERATIONS} iterations.
     *
     * @param password the input password (plain text)
     * @param salt     the cryptographic salt bytes
     * @return an {@link SecretKeySpec} suitable for AES-GCM encryption
     * @throws Exception if the PBKDF2 provider is unavailable
     */
    private static SecretKeySpec deriveKeyWithPBKDF2(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGO);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BIT);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    // ── System-Wide Migration (LMS V2 Packages) ───────────────────────────

    /**
     * Encrypts arbitrary byte data with a password-derived key for use in
     * {@code .lms} migration packages.
     *
     * <p>Output layout (all concatenated):
     * {@value #SALT_LENGTH_BYTE}-byte PBKDF2 salt ‖
     * {@value #IV_LENGTH_BYTE}-byte AES-GCM IV ‖
     * AES-GCM ciphertext + 16-byte authentication tag.</p>
     *
     * @param data     the plaintext bytes to encrypt; must not be {@code null}
     * @param password the encryption password (the administrator's secret)
     * @return the encrypted byte array in the layout described above
     * @throws Exception if key derivation or encryption fails
     */
    public static byte[] encryptBytesWithPassword(byte[] data, String password) throws Exception {
        byte[] salt = new byte[SALT_LENGTH_BYTE];
        SECURE_RANDOM.nextBytes(salt);
        SecretKeySpec key = deriveKeyWithPBKDF2(password, salt);

        byte[] iv = new byte[IV_LENGTH_BYTE];
        SECURE_RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

        byte[] ciphertext = cipher.doFinal(data);
        ByteBuffer bb = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
        bb.put(salt);
        bb.put(iv);
        bb.put(ciphertext);
        return bb.array();
    }

    /**
     * Decrypts a byte array produced by {@link #encryptBytesWithPassword}.
     *
     * <p>The method reads the embedded PBKDF2 salt and AES-GCM IV from the
     * first {@value #SALT_LENGTH_BYTE} + {@value #IV_LENGTH_BYTE} bytes of
     * {@code encryptedData}, derives the key from {@code password}, and returns
     * the authenticated plaintext.</p>
     *
     * @param encryptedData the encrypted bytes (salt ‖ IV ‖ ciphertext)
     * @param password      the decryption password
     * @return the original plaintext bytes
     * @throws Exception if the password is wrong (GCM tag verification fails),
     *                   the data is truncated, or key derivation fails
     */
    public static byte[] decryptBytesWithPassword(byte[] encryptedData, String password) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(encryptedData);
        byte[] salt = new byte[SALT_LENGTH_BYTE];
        bb.get(salt);
        byte[] iv = new byte[IV_LENGTH_BYTE];
        bb.get(iv);
        byte[] ciphertext = new byte[bb.remaining()];
        bb.get(ciphertext);

        SecretKeySpec key = deriveKeyWithPBKDF2(password, salt);
        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        return cipher.doFinal(ciphertext);
    }

    // ── Core Cryptography (System Key) ─────────────────────────────────────

    /**
     * Encrypts a string using the system master key (AES-256-GCM).
     *
     * <p>Use this method for data that must be readable by any machine sharing
     * the same library secret (e.g. database credentials stored in
     * {@link com.example.entities.DatabaseConfiguration}).  For user PII, prefer
     * {@link #encryptUserField} so the data is additionally protected by the
     * user's password.</p>
     *
     * <p>Returns {@code plainText} unchanged on any failure so callers always
     * get a non-null result.</p>
     *
     * @param plainText the string to encrypt; {@code null} or empty is returned as-is
     * @return Base64-encoded ciphertext (IV ‖ GCM tag ‖ ciphertext), or the
     *         original string on failure
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(MASTER_KEY_BYTES, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer bb = ByteBuffer.allocate(iv.length + ciphertext.length);
            bb.put(iv);
            bb.put(ciphertext);
            return Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            // Return plaintext so callers always have a usable value
            return plainText;
        }
    }

    /**
     * Decrypts a string produced by {@link #encrypt} using the system master key.
     *
     * <p>Returns {@code encryptedText} unchanged if decryption fails, which
     * allows callers to detect a key mismatch by checking whether the result
     * looks like plaintext.</p>
     *
     * @param encryptedText the Base64-encoded ciphertext to decrypt
     * @return the decrypted plaintext, or the original string on failure
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) return encryptedText;
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            ByteBuffer bb = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            bb.get(iv);
            byte[] ciphertext = new byte[bb.remaining()];
            bb.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(MASTER_KEY_BYTES, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Key mismatch or corrupt ciphertext — return as-is so callers can detect it
            return encryptedText;
        }
    }

    /**
     * Encrypts arbitrary byte data using the system master key (AES-256-GCM).
     *
     * @param plainBytes the plaintext bytes to encrypt; returns null if input is null
     * @return the encrypted byte array (IV ‖ ciphertext)
     */
    public static byte[] encryptBytes(byte[] plainBytes) {
        if (plainBytes == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(MASTER_KEY_BYTES, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] ciphertext = cipher.doFinal(plainBytes);
            ByteBuffer bb = ByteBuffer.allocate(iv.length + ciphertext.length);
            bb.put(iv);
            bb.put(ciphertext);
            return bb.array();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to encrypt bytes with master key", e);
            return plainBytes;
        }
    }

    /**
     * Decrypts a byte array produced by {@link #encryptBytes} using the system master key.
     *
     * @param encryptedBytes the encrypted bytes (IV ‖ ciphertext); returns null if input is null
     * @return the decrypted plaintext bytes, or the original bytes on failure
     */
    public static byte[] decryptBytes(byte[] encryptedBytes) {
        if (encryptedBytes == null) return null;
        try {
            return decryptWithKey(encryptedBytes, MASTER_KEY_BYTES);
        } catch (Exception ignored) {
            if (MACHINE_KEY_BYTES != null && !Arrays.equals(MASTER_KEY_BYTES, MACHINE_KEY_BYTES)) {
                try {
                    return decryptWithKey(encryptedBytes, MACHINE_KEY_BYTES);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to decrypt bytes with either Master or Machine key");
                }
            }
        }
        return encryptedBytes;
    }

    private static byte[] decryptWithKey(byte[] encryptedBytes, byte[] keyBytes) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(encryptedBytes);
        byte[] iv = new byte[IV_LENGTH_BYTE];
        bb.get(iv);
        byte[] ciphertext = new byte[bb.remaining()];
        bb.get(ciphertext);

        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        return cipher.doFinal(ciphertext);
    }

    // ── Password Hashing (PBKDF2) ──────────────────────────────────────────

    /**
     * Hashes a password using PBKDF2-HMAC-SHA256 with the given per-user salt.
     *
     * <p>The resulting hash is Base64-encoded and suitable for storage in
     * {@link com.example.entities.User#getPasswordHash()}.  A legacy SHA-256
     * fallback is tried if the PBKDF2 provider is unexpectedly unavailable (this
     * should never occur on any standard JRE but prevents a total authentication
     * outage).</p>
     *
     * @param password the user's plain-text password; must not be {@code null}
     * @param salt     the per-user random salt generated at account creation;
     *                 must not be {@code null}
     * @return a Base64-encoded PBKDF2 hash string
     */
    public static String hashPassword(String password, String salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGO);
            KeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8),
                    PBKDF2_ITERATIONS,
                    KEY_LENGTH_BIT);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            // Fallback to SHA-256 — should never be reached on a standard JRE
            LOGGER.log(Level.SEVERE, "Password hashing failed, falling back to weak hash", e);
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(salt.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(
                        md.digest(password.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException ex) {
                return password; // Last-resort: store plaintext (very bad, but avoids a crash)
            }
        }
    }

    /**
     * Verifies a password against a stored hash, supporting both PBKDF2 and
     * legacy SHA-256 hashes for backwards compatibility.
     *
     * <p>Legacy accounts created before PBKDF2 was introduced store SHA-256
     * hashes.  The method tries PBKDF2 first; if that does not match it retries
     * with SHA-256 so that users are not locked out after an upgrade.</p>
     *
     * @param password the candidate plain-text password to verify
     * @param salt     the per-user salt stored alongside the hash
     * @param hash     the stored hash to compare against
     * @return {@code true} if the password matches the stored hash via either
     *         PBKDF2 or the legacy SHA-256 path; {@code false} otherwise
     */
    public static boolean verifyPassword(String password, String salt, String hash) {
        if (hash == null || password == null || salt == null) return false;

        // Primary: PBKDF2 comparison
        if (hash.equals(hashPassword(password, salt))) return true;

        // Legacy: SHA-256 fallback for accounts that pre-date PBKDF2 migration
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            String legacyHash = Base64.getEncoder().encodeToString(
                    md.digest(password.getBytes(StandardCharsets.UTF_8)));
            return hash.equals(legacyHash);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    // ── Infrastructure ─────────────────────────────────────────────────────

    /**
     * Builds a best-effort hardware fingerprint for this machine by combining
     * the OS name, CPU architecture, and the MAC address of the first active
     * non-loopback network interface.
     *
     * <p>This fingerprint is used as part of the default master key derivation
     * so that a database serialised on one machine cannot be trivially read on
     * another without the explicit library secret.  If network enumeration fails
     * a hard-coded fallback string is used, which means the key is weaker but
     * the app remains functional.</p>
     *
     * @return a non-null string that is stable across reboots on the same machine
     *         (as long as the primary NIC does not change)
     */
    private static String getMachineFingerprint() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(System.getProperty("os.name")).append(System.getProperty("os.arch"));
            java.util.Enumeration<java.net.NetworkInterface> nics =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                java.net.NetworkInterface nic = nics.nextElement();
                if (nic.isLoopback() || !nic.isUp()) continue;
                byte[] mac = nic.getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) sb.append(String.format("%02X", b));
                    break; // Only need one NIC's MAC
                }
            }
        } catch (Exception e) {
            sb.append("HARDWARE-FALLBACK-ID-V3");
        }
        return sb.toString();
    }

    /** Sets a shared library-wide master key for multi-machine networks. */
}
