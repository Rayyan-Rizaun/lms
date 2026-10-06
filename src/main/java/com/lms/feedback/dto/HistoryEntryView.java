package com.lms.feedback.dto;

import java.time.LocalDateTime;

/**
 * One {@code FeedbackHistory} row, in order, for both the member detail
 * page and the staff review page.
 *
 * @param statusChanged  true when this row records a status transition —
 *                        {@code previousStatus}/{@code newStatus} always
 *                        carry real values either way (the columns are
 *                        {@code NOT NULL}), but the template only needs to
 *                        say "status changed" when they actually differ.
 * @param responseChanged true when this row records a new or edited
 *                         response — {@code newResponse} is only shown
 *                         when this is true.
 */
public record HistoryEntryView(LocalDateTime changedAt, String changedByName, String previousStatus,
                                String newStatus, boolean statusChanged, boolean responseChanged, String newResponse) {
}
