package com.example.entities;

import java.io.Serializable;

/**
 * The {@code BranchConfiguration} class stores settings that are isolated to a 
 * specific library branch.
 * 
 * <p>This includes local financial policies (fine rates) and local communication 
 * settings (SMTP) which may vary between different branches of the same library system.</p>
 */
public final class BranchConfiguration implements Serializable {
    private static final long serialVersionUID = 1L;

    private double finePerDay = 2.00;
    private String currencySymbol = "$";
    private String currencyCode = "USD";

    // SMTP Configuration
    private String smtpHost;
    private int smtpPort = 587;
    private String smtpUsername;
    private String smtpPassword;
    private String fromAddress;
    private boolean smtpAuth = true;
    private boolean startTlsEnabled = true;

    public double getFinePerDay() { return finePerDay; }
    public void setFinePerDay(double v) { finePerDay = Math.max(0.0, v); }

    public String getCurrencySymbol() { return currencySymbol != null ? currencySymbol : "$"; }
    public void setCurrencySymbol(String v) { currencySymbol = v; }

    public String getCurrencyCode() { return currencyCode != null ? currencyCode : "USD"; }
    public void setCurrencyCode(String v) { currencyCode = v; }

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String v) { smtpHost = v; }

    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int v) { smtpPort = Math.max(1, v); }

    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String v) { smtpUsername = v; }

    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String v) { smtpPassword = v; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String v) { fromAddress = v; }

    public boolean isSmtpAuth() { return smtpAuth; }
    public void setSmtpAuth(boolean v) { smtpAuth = v; }

    public boolean isStartTlsEnabled() { return startTlsEnabled; }
    public void setStartTlsEnabled(boolean v) { startTlsEnabled = v; }

    public boolean isEmailConfigured() {
        return smtpHost != null && !smtpHost.isBlank()
            && fromAddress != null && !fromAddress.isBlank()
            && (!smtpAuth || (smtpUsername != null && !smtpUsername.isBlank()
                           && smtpPassword != null && !smtpPassword.isBlank()));
    }

    public void normalize() {
        if (currencyCode == null) currencyCode = "USD";
        if (currencySymbol == null) currencySymbol = "$";
        decryptFields();
    }

    /**
     * CUSTOM SERIALIZATION: Transparently encrypts sensitive fields before they hit the disk.
     */
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        String originalUser = this.smtpUsername;
        String originalPass = this.smtpPassword;

        this.smtpUsername = com.example.services.SecurityProvider.encrypt(this.smtpUsername);
        this.smtpPassword = com.example.services.SecurityProvider.encrypt(this.smtpPassword);

        out.defaultWriteObject();

        this.smtpUsername = originalUser;
        this.smtpPassword = originalPass;
    }

    /**
     * CUSTOM DESERIALIZATION: Transparently decrypts sensitive fields after reading from disk.
     */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        decryptFields();
    }

    /**
     * Attempts to decrypt sensitive fields. Can be called multiple times safely.
     */
    public void decryptFields() {
        this.smtpUsername = com.example.services.SecurityProvider.decrypt(this.smtpUsername);
        this.smtpPassword = com.example.services.SecurityProvider.decrypt(this.smtpPassword);
    }
}
