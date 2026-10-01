package com.lms.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * The staff review form: move the status and/or write a response, both in
 * one submit. {@code status} is a String bound to a select of {@code
 * FeedbackService.statusOptions()} — {@code components/form-field.html}
 * deals only in Strings for a select, and {@code FeedbackStatus} stores
 * spaced values ("Under Review") that cannot be a Java enum constant name,
 * so the service parses it back by {@code dbValue} rather than {@code
 * valueOf}. A blank {@code response} means "no response text" (stored as
 * {@code null}), not a validation error — a status-only move (e.g.
 * Submitted → Under Review) has nothing to write yet.
 */
@Getter
@Setter
public class FeedbackReviewForm {

    @NotBlank(message = "Select a status")
    private String status;

    @Size(max = 2000, message = "Response must be at most 2000 characters")
    private String response;
}
