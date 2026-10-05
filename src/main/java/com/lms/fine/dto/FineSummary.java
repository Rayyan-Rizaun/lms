package com.lms.fine.dto;

import java.math.BigDecimal;

/** The three stat tiles above Outstanding Fines. */
public record FineSummary(BigDecimal totalOutstanding, BigDecimal totalCollectedThisMonth, long openAppeals) {
}
