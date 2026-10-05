package com.lms.catalogue;

/**
 * Thrown when a catalogue deactivate/withdraw action is blocked because an
 * {@code Active} {@link com.lms.common.domain.Loan} is in the way — UC-02's
 * own named alternative flow: "The Librarian may update book details or
 * delete a record that has no active borrowing transactions." Covers both
 * {@code BookService.deactivate} (any copy of the book on an Active loan)
 * and {@code BookCopyService.withdraw} (that exact copy on an Active loan).
 *
 * <p>Not a form-validation error (no field is wrong — the request itself is
 * fine, the current state of the data just does not allow it yet), so the
 * controller that catches this shows it as a flash-attribute error toast on
 * redirect back to the page the action was attempted from, rather than an
 * inline {@code form-field__error}.
 */
public class ActiveLoanException extends RuntimeException {

    public ActiveLoanException(String message) {
        super(message);
    }
}
