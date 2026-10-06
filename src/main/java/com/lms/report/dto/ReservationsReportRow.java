package com.lms.report.dto;

public record ReservationsReportRow(String bookTitle, long requested, long fulfilled, long expired,
                                     long currentQueueLength) {
}
