package com.lms.feedback.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The member's own "My Feedback" detail page: the full submission, the
 * admin response when there is one, and the status history in order.
 *
 * @param editable/withdrawable both the same condition (UC-10's own words:
 *        "Same condition as edit") — {@code status == Submitted &&
 *        adminResponse == null} — computed once here so the template never
 *        re-derives a business rule itself (R-style: derived, never
 *        recomputed in the view).
 */
public record FeedbackDetailView(Integer feedbackId, String reference, String categoryName, String subject,
                                  String description, String priority, String status, String adminResponse,
                                  LocalDateTime submittedAt, LocalDateTime updatedAt, boolean editable,
                                  boolean withdrawable, List<HistoryEntryView> history) {
}
