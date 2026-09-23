package com.lms.review.dto;

import java.util.List;

public record BookReviewPanelView(List<ApprovedReviewRow> reviews, MyReviewRow ownReview, ReviewForm ownForm,
                                   boolean canReview, Double averageRating, long ratingCount) {
}
