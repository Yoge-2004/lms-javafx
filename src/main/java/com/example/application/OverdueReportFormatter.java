package com.example.application;

import com.example.entities.BooksDB.IssueRecord;
import com.example.services.AppConfigurationService;

import java.util.List;
import java.util.Objects;

/**
 * FIXED: Use platform-specific line separator for cross-platform compatibility.
 */
public final class OverdueReportFormatter {

    private static final String LINE_SEP = System.lineSeparator();

    private OverdueReportFormatter() {
    }

    public static String format(List<IssueRecord> overdueBooks) {
        Objects.requireNonNull(overdueBooks, "overdueBooks cannot be null");

        StringBuilder report = new StringBuilder();
        report.append("Overdue Books Report:").append(LINE_SEP).append(LINE_SEP);
        double totalFines = 0;

        for (IssueRecord record : overdueBooks) {
            double fine = record.calculateFine();
            totalFines += fine;
            report.append(String.format(
                    "Book: %s%sUser: %s%sDue Date: %s%sDays Overdue: %d%sFine: %s%s%s",
                    record.getBookTitle(), LINE_SEP,
                    record.getUserId(), LINE_SEP,
                    record.getDueDate(), LINE_SEP,
                    record.getDaysOverdue(), LINE_SEP,
                    AppConfigurationService.getConfiguration().formatAmount(fine), LINE_SEP,
                    LINE_SEP
            ));
        }

        report.append("Total Outstanding Fines: ")
                .append(AppConfigurationService.getConfiguration().formatAmount(totalFines));
        return report.toString();
    }
}
