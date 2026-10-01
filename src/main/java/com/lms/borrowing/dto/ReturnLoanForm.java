package com.lms.borrowing.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/**
 * The confirm-return POST body: just the condition the book came back in.
 * Bound as a String to a plain {@code <select>} inside the return modal —
 * {@code components/form-field} needs a {@code th:object}, which does not
 * fit a form repeated once per row in {@code borrowing/loans.html}'s
 * {@code th:each} — the same reasoning {@code
 * com.lms.reservation.dto.StaffCancelForm} already documents for its own
 * per-row modal. {@link com.lms.borrowing.BorrowingService#returnBook}
 * converts it to {@link com.lms.common.domain.ReturnCondition}, the same
 * way {@code FeedbackForm.priority} already converts to {@code
 * FeedbackPriority}.
 */
@Getter
@Setter
public class ReturnLoanForm {

    @NotBlank(message = "Select the condition the book was returned in")
    private String returnCondition;
}
