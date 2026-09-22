package com.lms.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * The staff "Cancel" action's own form — just the required reason. Bound
 * from a plain {@code name="reason"} textarea inside a hand-built modal
 * (not {@code components/form-field}, which needs a {@code th:object} —
 * one shared per-page command object doesn't fit a form repeated once per
 * row in a {@code th:each}) — Spring still binds it by request-parameter
 * name regardless of how the input was rendered.
 */
@Getter
@Setter
public class StaffCancelForm {

    @NotBlank(message = "A reason is required")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
