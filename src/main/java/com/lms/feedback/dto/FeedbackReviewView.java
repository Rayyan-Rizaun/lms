package com.lms.feedback.dto;

import java.time.LocalDateTime;
import java.util.List;

/** The staff review page: the full submission, who submitted it, and the status history in order. */
public record FeedbackReviewView(Integer feedbackId, String reference, String memberName, String membershipNo,
                                  String categoryName, String subject, String description, String priority,
                                  String status, String adminResponse, LocalDateTime submittedAt,
                                  LocalDateTime updatedAt, boolean closed, List<HistoryEntryView> history) {
}
