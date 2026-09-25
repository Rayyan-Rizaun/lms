package com.lms.reservation.dto;

import java.util.List;

/**
 * One title's queue on the staff "Reservation Queue" screen — every Waiting
 * or Ready reservation for that book, oldest request first. Groups
 * themselves are ordered by their oldest member row (the title whose queue
 * has waited longest appears first), matching {@link
 * com.lms.reservation.ReservationService#queueForStaff}'s own ordering.
 */
public record QueueGroupView(Integer bookId, String bookTitle, List<QueueRowView> rows) {
}
