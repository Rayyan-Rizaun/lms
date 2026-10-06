package com.lms.review.dto;

import java.time.LocalDateTime;

public record MyReviewRow(Integer reviewId, Integer bookId, String bookTitle, Integer rating, String status,
                           LocalDateTime submittedAt) {
}
