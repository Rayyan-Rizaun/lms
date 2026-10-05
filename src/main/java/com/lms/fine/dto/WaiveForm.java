package com.lms.fine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Waive a fine entirely: a required reason, matching {@code CK_Fine_WaiverDetails}'s own column length. */
@Getter
@Setter
public class WaiveForm {

    @NotBlank(message = "A reason is required to waive a fine")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
