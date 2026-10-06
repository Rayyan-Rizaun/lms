package com.lms.borrowing;

/**
 * Thrown by {@link BorrowingService#issue} when one of business-rules.md
 * §1's borrowing checks fails — membership not active, borrowing limit
 * reached, an overdue loan outstanding, the copy is reference-only, or the
 * copy is not available. UC-03's own alternative flows: "If the book is
 * unavailable, the system does not allow issuing it" / "If the Library
 * Member has outstanding restrictions, the system prevents borrowing."
 *
 * <p>Not a field-validation error — {@code memberId}/{@code copyId} are
 * both perfectly valid selections, the current state of the data just
 * refuses this particular pairing — so {@link LoanController} shows it as
 * a flash-attribute error toast on redirect back to the Issue Book screen,
 * with the same member and copy still selected, rather than an inline
 * {@code form-field__error}.
 */
public class BorrowingRuleException extends RuntimeException {

    public BorrowingRuleException(String message) {
        super(message);
    }
}
