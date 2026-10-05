package com.lms.fine.dto;

import java.math.BigDecimal;

/**
 * One row of the Outstanding Fines list.
 *
 * @param status {@code Fine.Status} exactly as stored, for the status pill (domain {@code "fine"}).
 * @param balance R18: never stored — {@code AmountAssessed − approved appeal reductions
 *                − completed payments}, computed once in {@link com.lms.fine.FineService}.
 * @param actionable true only for Pending or Partially Paid — Pay and Waive both require the
 *                    appeal (if any) to be decided first, same as {@link com.lms.fine.FineService#pay}
 *                    and {@link com.lms.fine.FineService#waive} each re-check server-side.
 */
public record FineListRow(Integer fineId, String memberName, String membershipNo, String fineType,
                           BigDecimal amountAssessed, BigDecimal amountPaid, BigDecimal balance, String status,
                           boolean actionable) {
}
