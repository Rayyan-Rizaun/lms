package com.lms.report.dto;

import java.math.BigDecimal;

public record FinesReportRow(String fineType, BigDecimal assessed, BigDecimal collected, BigDecimal waived,
                              BigDecimal outstanding) {
}
