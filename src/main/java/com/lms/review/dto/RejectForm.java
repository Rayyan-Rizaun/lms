package com.lms.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectForm {

    @NotBlank(message = "Enter a reason for rejecting this review")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
