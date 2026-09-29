package com.lms.borrowing.dto;

/**
 * One copy search result on the Issue Book screen — found by exact barcode
 * or by the title of the book it belongs to (UC-03: "searches for and
 * selects an available book"). Shown whether or not it is actually
 * issuable right now, so a librarian scanning a barcode sees why a copy
 * cannot be selected (already on loan, reference-only) rather than an
 * empty result.
 *
 * @param status    {@code BookCopy.Status} exactly as stored, for the status
 *                  pill (domain {@code "bookCopy"}).
 * @param eligible  true only when {@code status == Available} and the copy
 *                  is not reference-only — the two copy-side business-rules.md
 *                  §1 checks {@link com.lms.borrowing.BorrowingService#issue}
 *                  itself re-checks before ever recording a loan.
 */
public record CopyCandidateRow(Integer copyId, String accessionNumber, String barcode, String bookTitle,
                                String shelfLocation, String status, boolean referenceOnly, boolean eligible) {
}
