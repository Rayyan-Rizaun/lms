package com.lms.fine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One payment made against a fine, for the member's own fine detail page — receipt number included. */
public record PaymentRow(String receiptNumber, BigDecimal amountPaid, String method, LocalDateTime paidAt) {
}
