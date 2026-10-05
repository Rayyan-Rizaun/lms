package com.lms.review.dto;

import java.time.LocalDateTime;

public record ModerationHistoryView(LocalDateTime moderatedAt, String moderatorName, String previousStatus,
                                     String newStatus, String reason) {
}
