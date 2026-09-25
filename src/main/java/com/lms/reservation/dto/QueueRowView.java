package com.lms.reservation.dto;

import java.time.LocalDateTime;

/**
 * One reservation inside its title's group on the staff "Reservation Queue"
 * screen ({@link com.lms.reservation.ReservationService#queueForStaff}).
 *
 * @param queuePosition R12, as in {@link MyReservationRow} — null once the
 *                       row is no longer Waiting.
 * @param expiresAt      set only once the row is Ready (business-rules §3's
 *                        3-day hold window); null for a still-Waiting row.
 * @param canMarkReady    true only for the single Waiting row at the front
 *                         of this title's queue, and only while an available
 *                         copy actually exists to hold for them — mirrors
 *                         the same two checks {@code reserve()} itself makes,
 *                         so a direct POST to a hidden action can't skip
 *                         either one.
 * @param canFulfill      true only while this row is Ready.
 * @param canExpire       true only while this row is Ready (the service also
 *                         re-checks {@code ExpiresAt} has actually passed).
 */
public record QueueRowView(Integer reservationId, String memberName, LocalDateTime requestedAt,
                            Integer queuePosition, String status, LocalDateTime expiresAt,
                            boolean canMarkReady, boolean canFulfill, boolean canExpire) {
}
