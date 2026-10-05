package com.lms.fine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** UC-07: "show the receipt after a successful payment." Carried across the POST → GET redirect as a flash attribute. */
public record ReceiptView(String receiptNumber, Integer fineId, String memberName, String fineType,
                           BigDecimal amountPaid, String method, LocalDateTime paidAt,
                           BigDecimal balanceRemaining, String newStatus, String receivedByStaffName) {

    /**
     * A plain JavaBean-style accessor alongside the record's own {@link
     * #fineId()}, purely so {@code AuditAspect}'s reflection — which looks
     * for {@code get<Field>Id()}, the convention every real entity in
     * {@code com.lms.common.domain} follows — finds one on this DTO too.
     * {@link com.lms.fine.FineService#pay} returns this record rather than
     * the {@code Fine} entity itself (the receipt needs fields no single
     * entity has, like the staff member's name), so without this the
     * payment's own {@code @AuditAction} would log a null {@code EntityID}.
     */
    public Integer getFineId() {
        return fineId;
    }
}
