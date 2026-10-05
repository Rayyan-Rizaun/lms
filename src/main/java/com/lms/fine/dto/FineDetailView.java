package com.lms.fine.dto;

import java.math.BigDecimal;

/** The payment screen's header — one fine, already resolved to a computed balance. */
public record FineDetailView(Integer fineId, String memberName, String membershipNo, String fineType,
                              BigDecimal amountAssessed, BigDecimal amountPaid, BigDecimal balance, String status) {
}
