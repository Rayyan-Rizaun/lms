package com.lms.review.dto;

import java.time.LocalDateTime;

public record ReviewQueueRow(Integer reviewId, String bookTitle, String memberName, Integer rating,
                              String reviewExcerpt, String status, int flagCount, LocalDateTime submittedAt) {
}
