package com.example.application;
/**
 * Interface for displaying toast notifications.
 */
public interface ToastDisplay {
    void showSuccess(String message);
    void showError(String message);
    void showInfo(String message);
    void showWarning(String message);
}