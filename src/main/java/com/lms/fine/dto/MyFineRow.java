package com.lms.fine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of "My Fines" — outstanding fines first (the member's own most
 * pressing business), computed once in {@link com.lms.fine.FineService},
 * never re-sorted in the template.
 *
 * @param status {@code Fine.Status} exactly as stored, for the status pill (domain {@code "fine"}).
 * @param balance R18: never stored — computed once here, same formula as everywhere else in this class.
 */
public record MyFineRow(Integer fineId, String fineType, String bookTitle, BigDecimal amountAssessed,
                         BigDecimal amountPaid, BigDecimal balance, String status, LocalDateTime assessedAt) {
}
