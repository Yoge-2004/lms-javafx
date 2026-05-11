package com.example.services;

import com.example.entities.AppConfiguration;
import com.example.entities.BorrowRequest;
import com.example.entities.BooksDB.IssueRecord;
import com.example.storage.AppPaths;
import com.example.storage.DataStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code ReportExportService} provides utilities for exporting library data 
 * into portable formats like CSV.
 * 
 * <p>Reports are saved to the user-configured export directory with unique 
 * timestamps to prevent overwriting.</p>
 */
public final class ReportExportService {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter CSV_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ReportExportService() {
    }

    public static Path exportOverdueReportCsv(List<IssueRecord> overdueBooks) throws IOException {
        List<String> rows = overdueBooks.stream()
                .map(record -> {
                    com.example.entities.User user = com.example.entities.UsersDB.getInstance().getUser(record.getUserId());
                    String email = (user != null) ? user.getEmail() : "N/A";
                    String phone = (user != null) ? user.getContactNumber() : "N/A";
                    
                    return csv(
                        record.getBookTitle(),
                        record.getUserId(),
                        email,
                        phone,
                        record.getDueDate().format(CSV_DATE),
                        String.valueOf(record.getDaysOverdue()),
                        String.format("%.2f", record.calculateFine())
                    );
                })
                .toList();
        return writeCsv("overdue_report", "Book Title,User ID,Email,Contact Number,Due Date,Days Overdue,Fine", rows);
    }

    public static Path exportIssuedBooksCsv(List<IssueRecord> records) throws IOException {
        List<String> rows = records.stream()
                .map(record -> csv(
                        record.getBookTitle(),
                        record.getUserId(),
                        record.getIssueDate().format(CSV_DATE),
                        record.getDueDate().format(CSV_DATE),
                        String.valueOf(record.getQuantity()),
                        String.valueOf(record.isReturned())
                ))
                .toList();
        return writeCsv("issued_books", "Book Title,User ID,Issue Date,Due Date,Quantity,Returned", rows);
    }

    public static Path exportBorrowRequestsCsv(List<BorrowRequest> requests) throws IOException {
        List<String> rows = requests.stream()
                .map(request -> csv(
                        request.getBookTitle(),
                        request.getUserId(),
                        String.valueOf(request.getQuantity()),
                        request.getRequestedAt().format(CSV_DATETIME),
                        request.getStatus().name(),
                        request.getProcessedBy(),
                        request.getNote()
                ))
                .toList();
        return writeCsv("borrow_requests",
                "Book Title,User ID,Quantity,Requested At,Status,Processed By,Note",
                rows);
    }

    private static Path writeCsv(String prefix, String header, List<String> rows) throws IOException {
        AppConfiguration config = AppConfigurationService.getConfiguration();
        DataStorage.ensureDirectoryExists(config.getExportDirectory());
        Path file = AppPaths.resolveExportDirectory()
                .resolve(prefix + "_" + LocalDateTime.now().format(FILE_TS) + ".csv");

        List<String> lines = new ArrayList<>();
        lines.add(header);
        lines.addAll(rows);
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private static String csv(String... values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            String value = values[i] == null ? "" : values[i];
            builder.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        return builder.toString();
    }
}
