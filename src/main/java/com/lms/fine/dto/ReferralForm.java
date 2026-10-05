package com.lms.fine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReferralForm {

    @NotBlank(message = "Enter a note explaining what needs to be corrected")
    @Size(max = 500, message = "Note must be at most 500 characters")
    private String note;
}
