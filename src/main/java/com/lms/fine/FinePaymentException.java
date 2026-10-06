package com.lms.fine;

/**
 * Thrown by {@link FineService#pay} when the amount entered is larger than
 * the fine's current outstanding balance — the one business rule this
 * task's own spec calls out: "Reject payments larger than the outstanding
 * balance with an inline error." {@link FineController} attaches it to the
 * {@code amount} field via {@code BindingResult.rejectValue}, the same
 * pattern every other "this exact field is the problem" case in this
 * codebase uses (e.g. {@code com.lms.catalogue.DuplicateFieldException}).
 */
public class FinePaymentException extends RuntimeException {

    public FinePaymentException(String message) {
        super(message);
    }
}
