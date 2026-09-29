package com.lms.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * The submit-feedback form. {@code priority} is a String bound to a
 * Low/Medium/High select — {@code components/form-field.html} deals only
 * in Strings for a select, so the service converts it to {@link
 * com.lms.common.domain.FeedbackPriority} the same way {@code
 * BookCopyForm.copyCondition} already does for {@code CopyCondition}.
 */
@Getter
@Setter
public class FeedbackForm {

    @NotNull(message = "Select a category")
    private Integer categoryId;

    @NotBlank(message = "Subject is required")
    @Size(max = 150, message = "Subject must be at most 150 characters")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @NotBlank(message = "Select a priority")
    private String priority = "Medium";
}
