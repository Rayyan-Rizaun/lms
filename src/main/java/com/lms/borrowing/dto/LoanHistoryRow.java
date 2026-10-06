package com.lms.borrowing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of "Loan History" — every Returned loan.
 *
 * @param daysOverdue whole days between {@code DueAt} and {@code
 *                     ReturnedAt}, clamped at zero for a loan returned on
 *                     time or early — never negative.
 * @param fineAmount   the Overdue fine {@link
 *                      com.lms.borrowing.BorrowingService#returnBook}
 *                      raised for this loan, or null when it was returned
 *                      on time and no fine exists.
 */
public record LoanHistoryRow(Integer loanId, String memberName, String bookTitle, LocalDateTime borrowedAt,
                              LocalDateTime returnedAt, long daysOverdue, BigDecimal fineAmount) {
}
