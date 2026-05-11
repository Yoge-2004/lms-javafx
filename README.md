# 📚 Library OS - Premium Management System

![Version](https://img.shields.io/badge/Version-3.4.0-0D9488?style=for-the-badge)
![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Windows%20|%20Linux%20|%20macOS-E2E8F0?style=for-the-badge)

**Library OS** is a state-of-the-art library management solution. It bridges the gap between powerful administrative tools and a premium user experience, offering native performance, multi-database flexibility, and enterprise-grade security.

---

## 📖 Table of Contents
1. [User Documentation](#user-documentation)
    * [Installation](#installation)
    * [Uninstallation](#uninstallation)
    * [Feature Guide](#feature-guide)
2. [Developer Documentation](#developer-documentation)
    * [Technology Stack](#technology-stack)
    * [Project Architecture](#project-architecture)
    * [Security Implementation](#security-implementation)
    * [Building from Source](#building-from-source)

---

## 🛠 User Documentation <a name="user-documentation"></a>

### 📥 Installation <a name="installation"></a>

#### 🐧 Linux
*   **Debian / Ubuntu / Zorin OS**: `sudo apt install ./dist/libraryos_3.4.0_amd64.deb`
*   **Fedora / Red Hat / CentOS**: `sudo dnf install ./dist/libraryos-3.4.0-1.x86_64.rpm`

#### 🚀 Launching (Linux)
Once installed, you can launch the application in two ways:
1.  **Application Menu**: Search for **"Library OS"** in your desktop menu.
2.  **Terminal**: Simply type `libraryos` from any terminal.


#### 🪟 Windows
1.  Download the `LibraryOS-3.4.0.exe` installer.
2.  Run the executable and follow the setup wizard.
3.  The installer will automatically create a desktop shortcut and a Start Menu entry.

#### 🍎 macOS
1.  Open the `LibraryOS-3.4.0.dmg` disk image.
2.  Drag the **Library OS** icon into your **Applications** folder.

### 🗑 Uninstallation <a name="uninstallation"></a>

#### 🐧 Linux
*   **Debian/Ubuntu**: `sudo apt remove libraryos`
*   **Fedora/RedHat**: `sudo dnf remove libraryos`

#### 🪟 Windows
*   Go to **Settings > Apps > Apps & Features**.
*   Search for **Library OS** and select **Uninstall**.

#### 🍎 macOS
*   Open the **Applications** folder.
*   Drag **Library OS** to the **Trash** and empty it.

---

### 🌟 Feature Guide <a name="feature-guide"></a>

#### 1. Smart Catalog Management
*   **Book Lifecycle**: Comprehensive tracking from acquisition to retirement.
*   **Advanced Search**: High-performance filtering by author, ISBN, category, and availability.

#### 2. Circulation & Borrowing Workflow
*   **Request System**: Multi-stage borrowing requests with librarian approval flows.
*   **Automated Fines**: Real-time fine calculation based on configurable library policies.

#### 3. Financial Module
*   **Invoicing**: Automatic invoice generation for fines and membership fees.
*   **Payment Approvals**: Secure workflow for verifying and recording payments.

#### 4. Multi-Database Engine
Switch between database backends without changing the application logic:
*   **Relational**: SQLite (Default), MySQL, PostgreSQL, Oracle.
*   **NoSQL**: MongoDB support for flexible schema scenarios.

---

## 💻 Developer Documentation <a name="developer-documentation"></a>

### 🚀 Technology Stack <a name="technology-stack"></a>

*   **Runtime**: [Java 26](https://openjdk.org/) (OpenJDK)
*   **UI Framework**: [JavaFX 26](https://openjfx.io/) with custom Vanilla CSS styling.
*   **Database Connectivity**: 
    *   JDBC for Relational DBs (SQLite, MySQL, Postgres, Oracle).
    *   MongoDB Sync Driver for NoSQL.
*   **Security**: AES-256-GCM authenticated encryption, PBKDF2 for password hashing.
*   **Messaging**: Jakarta Mail (Angus Mail) for SMTP notifications.
*   **Packaging**: [jpackage](https://docs.oracle.com/en/java/javase/14/jpackage/index.html) for native OS-specific bundles.
*   **Build System**: Maven 3.9+ with custom profiles for cross-platform installers.

### 🏗 Project Architecture <a name="project-architecture"></a>

The application follows a modular, service-oriented architecture:

*   **UI Layer (`com.example.application.ui`)**: Decoupled views using the Model-View-Controller pattern.
*   **Service Layer (`com.example.services`)**: Singleton-based business logic providers.
*   **Storage Layer (`com.example.storage`)**: Abstracted persistence layer supporting multiple database vendors.

### 🔒 Security Implementation <a name="security-implementation"></a>

*   **Credential Protection**: Passwords are never stored in plain text.
*   **Hardware-Linked Keys**: Uses machine-specific identifiers (HWID) to derive encryption keys, preventing database portable-theft.
*   **Atomic Persistence**: Ensures data snapshots are written safely to prevent header corruption.

### 🏗 Building from Source <a name="building-from-source"></a>

```bash
./mvnw clean install -Dinstaller -DskipTests
```
The command automatically detects your OS and triggers the corresponding `jpackage` profile (`installer-linux`, `installer-windows`, or `installer-mac`).

---

## 📄 License
Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

---
*Library OS: Cross-platform elegance and enterprise power.*
