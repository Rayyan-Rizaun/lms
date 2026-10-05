package com.lms.reservation.dto;

import java.time.LocalDateTime;

/**
 * One row of "My Reservations".
 *
 * @param queuePosition R12: never stored — one more than the number of
 *                       Waiting reservations for the same title requested
 *                       earlier, computed once in {@link
 *                       com.lms.reservation.ReservationService}. Null for
 *                       anything not currently Waiting (Ready, Fulfilled,
 *                       Cancelled, Expired) — a queue position only means
 *                       something while still waiting in line.
 * @param status {@code Reservation.Status} exactly as stored, for the
 *               status pill (domain {@code "reservation"}).
 * @param expiresAt the collection deadline once a copy is held (business-
 *                  rules §3's 3-day window) — null unless {@code status} is
 *                  {@code "Ready"}.
 */
public record MyReservationRow(Integer reservationId, String bookTitle, LocalDateTime requestedAt,
                                Integer queuePosition, String status, LocalDateTime expiresAt, boolean cancellable) {
}
