package com.lms.fine.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * Approve an appeal: just the reduction amount — comments are optional
 * here (required only to reject, per this task's own instruction).
 * Whether it exceeds the outstanding balance is a business-rule check
 * ({@link com.lms.fine.FineException}), not a syntactic one — {@code
 * @DecimalMin} here only rules out a zero or negative amount, matching
 * {@code CK_FineAppeal_ApprovedReduction}.
 */
@Getter
@Setter
public class AppealApproveForm {

    @NotNull(message = "Enter a reduction amount")
    @DecimalMin(value = "0.01", message = "Reduction must be greater than zero")
    private BigDecimal reduction;
}
