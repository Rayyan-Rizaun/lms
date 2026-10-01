package com.lms.review.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewDetailView(Integer reviewId, String bookTitle, Integer bookId, String memberName, Integer rating,
                                String reviewText, String status, LocalDateTime submittedAt, LocalDateTime updatedAt,
                                boolean hasOpenFlags, List<FlagView> flags, List<ModerationHistoryView> history) {
}
