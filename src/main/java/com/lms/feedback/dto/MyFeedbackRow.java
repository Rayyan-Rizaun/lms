package com.lms.feedback.dto;

import java.time.LocalDateTime;

/**
 * One row of "My Feedback" — built inside {@link com.lms.feedback.FeedbackService}'s
 * own transaction (never a raw {@link com.lms.common.domain.MemberFeedback}
 * entity), so the category name is already resolved and safe to read after
 * the service method returns.
 *
 * @param status {@code MemberFeedback.Status} exactly as stored, for the
 *               status pill (domain {@code "feedback"}).
 */
public record MyFeedbackRow(Integer feedbackId, String reference, String categoryName, String subject,
                             String status, LocalDateTime submittedAt) {
}
