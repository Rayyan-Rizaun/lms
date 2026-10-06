package com.lms.fine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The member's own fine detail page: how the amount was calculated, every
 * payment made against it, and any appeal.
 *
 * @param daysOverdue/ratePerDay populated only for an Overdue fine — the
 *        exact two numbers {@code com.lms.borrowing.BorrowingService
 *        #returnBook} multiplied together when this fine was raised.
 * @param replacementCost/incidentDescription populated only for a Lost or
 *        Damaged fine — the copy's {@code PurchasePrice} (business-rules
 *        §5) and the librarian's own justification note on the {@code
 *        BookIncident}.
 * @param appealable true only while {@code status} is Pending — UC-06's
 *        own words, "the member can submit an appeal on a Pending fine."
 */
public record MyFineDetailView(Integer fineId, String fineType, String bookTitle, BigDecimal amountAssessed,
                                BigDecimal amountPaid, BigDecimal balance, String status, LocalDateTime assessedAt,
                                Integer daysOverdue, BigDecimal ratePerDay, BigDecimal replacementCost,
                                String incidentDescription, List<PaymentRow> payments, AppealView appeal,
                                boolean appealable) {
}
