package com.lms.review.dto;

import java.time.LocalDateTime;

public record ApprovedReviewRow(Integer reviewId, String memberName, Integer rating, String reviewText,
                                 LocalDateTime submittedAt, boolean canFlag) {
}
