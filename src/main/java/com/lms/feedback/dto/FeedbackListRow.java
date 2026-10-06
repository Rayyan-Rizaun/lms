package com.lms.feedback.dto;

import java.time.LocalDateTime;

/** One row of the staff "Feedback" list. */
public record FeedbackListRow(Integer feedbackId, String reference, String memberName, String categoryName,
                               String subject, String status, String priority, LocalDateTime submittedAt) {
}
