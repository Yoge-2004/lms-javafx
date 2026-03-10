# LMSJavaFX

A desktop Library Management System (LMS) built with JavaFX. It provides a clean GUI for managing books, users, and issue/return workflows.

## Highlights

- JavaFX desktop UI with modern layout and responsive panels
- Book catalog management with add/update/delete actions
- User management with registration and profile details
- Issue/return workflow with due-date tracking
- Search and filtering for quick lookup
- Role-based login for librarian access

## Screenshots

**Login Screen**  
Clean sign-in experience for staff access with basic validation and feedback.

![Login Screen](assets/screenshots/login.png)

**Main Dashboard**  
Central workspace with navigation, quick actions, and status information for daily operations.

![Dashboard](assets/screenshots/dashboard.png)

**Issued Books View**  
Focused view of issued items with return and user actions.

![Issued Books](assets/screenshots/dashboard_books_issued.png)

## Features (Detailed)

- Authentication
  Staff login with validation and feedback.
- Book Management
  Add new titles, edit metadata, delete records, and track availability.
- User Management
  Register new users, view user details, and manage basic profiles.
- Issuing Workflow
  Issue books to users, return books, and view current issue records.
- Search And Filters
  Search by key fields and filter lists to find items quickly.
- Data Persistence
  Uses serialized data files in `data/` for local storage.

## Requirements

- JDK 25
- Internet access on first build (downloads Maven dependencies)

## Project Layout

```
LMSJavaFX/
├── src/
│   └── main/
│       └── java/
│           └── com/example/...
├── data/
│   └── *.ser
├── assets/
│   └── screenshots/
├── pom.xml
└── README.md
```

## Run In IDE (Run Button)

Import the project as Maven, then:

- Run `com.example.application.LibraryApp` from the IDE, or
- Run the Maven goal `javafx:run`

If the IDE complains about missing JavaFX runtime, use `javafx:run`. It sets up the JavaFX module path automatically.

## Build A Windows App Image (Recommended)

This creates a self-contained app image that runs on Windows without requiring JavaFX to be installed:

```powershell
.\mvnw -q -DskipTests package -Pwindows
```

Run the app from:

`target\installer\LibraryApp\LibraryApp.exe`

Distribute the whole folder:

`target\installer\LibraryApp\`

## Data Files

The app stores data in `data/` using serialized `.ser` files. Keep this folder next to the app when distributing.

## Troubleshooting

- JavaFX runtime error when running a jar:
  Use the app image built by `jpackage` (see above). A plain `java -jar` does not bundle JavaFX native runtime.
- Maven not found:
  Use the Maven Wrapper provided in this repo: `.\mvnw`.

## License

MIT License. See `LICENSE`.

## Author

Yogeshwaran
