package com.lms.fine.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * The record-a-payment form. {@code method} is a String bound to a
 * Cash/Card/Online select — the service converts it to {@link
 * com.lms.common.domain.PaymentMethod}, the same String-select-then-{@code
 * valueOf} shape {@code BookCopyForm.copyCondition} and {@code
 * FeedbackForm.priority} already use.
 *
 * <p>Whether {@code amount} exceeds the outstanding balance is a
 * business-rule check ({@link com.lms.fine.FinePaymentException}), not a
 * syntactic one — {@code @DecimalMin} here only rules out a zero or
 * negative amount, matching {@code CK_FinePayment_AmountPaid}.
 */
@Getter
@Setter
public class PaymentForm {

    @NotNull(message = "Enter an amount")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Select a payment method")
    private String method = "Cash";
}
