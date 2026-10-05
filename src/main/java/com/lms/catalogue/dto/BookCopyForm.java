package com.lms.catalogue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Getter;
import lombok.Setter;

/**
 * Create/edit form for one {@link com.lms.common.domain.BookCopy}. Field
 * names match {@code catalogue/copy-form.html}'s {@code th:field} bindings.
 *
 * <p>Deliberately has <b>no {@code status} field</b>. {@code BookCopy}'s
 * status column mixes loan-driven states (On Loan, On Hold) with decision
 * states (Damaged, Lost, Withdrawn); the schema's own comment on
 * {@code CREATE TABLE BookCopy} says triggers on Loan/Reservation/
 * BookIncident are its intended writers. Those triggers do not exist yet
 * in this phase of the project, so this form's new copies simply take the
 * column's {@code DEFAULT ('Available')} by never setting it, and {@code
 * BookCopyService.withdraw} is the one narrow, explicitly-scoped exception
 * — see that method's javadoc.
 */
@Getter
@Setter
public class BookCopyForm {

    @NotBlank(message = "Accession number is required")
    @Size(max = 30, message = "Accession number must be at most 30 characters")
    private String accessionNumber;

    @NotBlank(message = "Barcode is required")
    @Size(max = 50, message = "Barcode must be at most 50 characters")
    private String barcode;

    @Size(max = 50, message = "Shelf location must be at most 50 characters")
    private String shelfLocation;

    // Without this, Spring renders the field using the request's Locale-style
    // pattern (e.g. "9/14/26" for en-US) instead of ISO yyyy-MM-dd — an
    // <input type="date"> only accepts the ISO form and silently shows
    // empty for anything else, so the field looked blank despite a real
    // default value being set below.
    @NotNull(message = "Acquisition date is required")
    @PastOrPresent(message = "Acquisition date cannot be in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate acquisitionDate = LocalDate.now();

    @NotNull(message = "Purchase price is required")
    @DecimalMin(value = "0.00", message = "Purchase price cannot be negative")
    private BigDecimal purchasePrice;

    /** CK_BookCopy_CopyCondition: New, Good, Fair, Poor. */
    @NotBlank(message = "Select a condition")
    private String copyCondition = "Good";

    /** Bound against a Yes/No select (see BookCopyController) — form-field.html has no checkbox type. */
    private boolean referenceOnly = false;
}
