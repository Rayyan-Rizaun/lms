package com.lms.review.dto;

import java.time.LocalDateTime;

public record FlagView(Integer flagId, String reportedByName, String reason, String status, LocalDateTime reportedAt) {
}
