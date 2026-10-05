package com.lms.fine;

/**
 * Thrown by {@link FineService} for every refusal that is not a stale id
 * and not the one specific "amount exceeds balance" case ({@link
 * FinePaymentException} keeps that one, since it attaches to the payment
 * form's own {@code amount} field): a fine that is not payable right now
 * (Under Appeal or already Waived), an appeal submitted outside the
 * Pending-only window, a decision on an appeal that is no longer Pending,
 * an approved reduction larger than the outstanding balance, or a waiver
 * missing its required reason. Shown as a flash-attribute error toast,
 * never a stack trace.
 */
public class FineException extends RuntimeException {

    public FineException(String message) {
        super(message);
    }
}
