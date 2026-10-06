package com.lms.dashboard.dto;

import java.math.BigDecimal;

public record OutstandingFineRow(Integer fineId, String memberName, String fineType, BigDecimal balance) {
}
