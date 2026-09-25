package com.lms.reservation;

/**
 * Thrown by {@link ReservationService#reserve} for this task's own two
 * named refusal cases — a copy is actually available ("suggest borrowing
 * instead"), or the member already has an active reservation on this title
 * — and by {@link ReservationService#cancel} when a reservation is no
 * longer in a cancellable state. Shown as a flash-attribute error toast,
 * never a stack trace.
 */
public class ReservationException extends RuntimeException {

    public ReservationException(String message) {
        super(message);
    }
}
