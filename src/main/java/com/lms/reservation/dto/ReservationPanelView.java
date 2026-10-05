package com.lms.reservation.dto;

/**
 * The book detail page's "Borrowing &amp; reservations" panel, for whoever
 * is currently viewing it — computed by {@link com.lms.reservation.ReservationService#panelFor},
 * called directly from {@code catalogue/book-detail.html} as {@code
 * @reservationService.panelFor(book.bookId, currentUserId)}, the same
 * shared-bean-from-template pattern {@code components/status-pill.html}
 * already uses for {@code @statusPill}. Every field is safe to read
 * regardless of whether the viewer is a guest, a member, or staff — the
 * template never has to null-check this object itself.
 *
 * @param message a ready-made sentence for {@code hasActiveReservation ==
 *                true} (queue position, or "ready for collection"), built
 *                in Java rather than assembled in the template — the
 *                template's own {@code th:text} just displays it.
 */
public record ReservationPanelView(boolean hasAvailableCopy, boolean hasActiveReservation, String message) {
}
