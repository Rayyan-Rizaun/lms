package com.lms.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OverdueReportRow(String memberName, String bookTitle, LocalDateTime dueAt, long daysOverdue,
                                BigDecimal fineAccrued) {
}
