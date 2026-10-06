package com.lms.report.dto;

import java.time.LocalDateTime;

public record ReportHistoryRow(Integer reportAuditId, String requestedByName, String reportType, String filterJson,
                                LocalDateTime generatedAt) {
}
