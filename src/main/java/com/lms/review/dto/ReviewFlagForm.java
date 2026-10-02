package com.lms.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewFlagForm {

    @NotBlank(message = "Enter a reason for flagging this review")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
