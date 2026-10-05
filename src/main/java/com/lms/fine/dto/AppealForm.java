package com.lms.fine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** The member's own "submit an appeal" form — just the reason, matching {@code CK_FineAppeal_ReasonNotBlank}. */
@Getter
@Setter
public class AppealForm {

    @NotBlank(message = "Enter a reason for your appeal")
    @Size(max = 1000, message = "Reason must be at most 1000 characters")
    private String reason;
}
