package com.lms.report.dto;

public record ReviewsReportRow(String bookTitle, long submitted, long approved, long rejected, Double averageRating) {
}
