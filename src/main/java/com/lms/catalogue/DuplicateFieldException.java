package com.lms.catalogue;

/**
 * Thrown when a catalogue create/edit form submits a value that has to be
 * unique and is not — a duplicate ISBN-13 (UC-02's own named alternative
 * flow: "If a duplicate ISBN is entered, the system displays an error"),
 * plus the same shape of conflict on {@code BookCopy.AccessionNumber} /
 * {@code Barcode} and on an {@code Author} / {@code Category} /
 * {@code Publisher} name (each has its own {@code UNIQUE} constraint in
 * {@code database/01_schema.sql}).
 *
 * <p>One exception class for all of these rather than one per field: every
 * case is handled the same way by the controller that catches it —
 * {@code BindingResult.rejectValue(field(), "duplicate", getMessage())},
 * so the offending field gets the normal inline {@code form-field--error}
 * treatment and the form re-renders with everything else the user typed
 * still in place. Same pattern as {@code com.lms.user.DuplicateRegistrationException}.
 */
public class DuplicateFieldException extends RuntimeException {

    private final String field;

    public DuplicateFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
