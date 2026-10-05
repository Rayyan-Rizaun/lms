package com.lms.fine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Reject an appeal: comments are required — this task's own words, "reject with required comments." */
@Getter
@Setter
public class AppealRejectForm {

    @NotBlank(message = "Enter a reason for rejecting this appeal")
    @Size(max = 1000, message = "Comments must be at most 1000 characters")
    private String comments;
}
