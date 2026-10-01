package com.lms.borrowing.dto;

/**
 * One member search result on the Issue Book screen, already carrying UC-03's
 * "the system displays the Library Member's borrowing eligibility" — computed
 * once in {@link com.lms.borrowing.BorrowingService}, not re-derived in the
 * template.
 *
 * @param membershipStatus  {@code Member.MembershipStatus} exactly as stored,
 *                           for the status pill (domain {@code "membership"}).
 * @param activeLoanCount   the member's current Active loans.
 * @param loanLimit          borrowing limit for this member's type (SystemSetting).
 * @param eligible           true only if every business-rules.md §1 check passes.
 * @param ineligibleReason   the first failing reason, or null when eligible.
 */
public record MemberEligibilityRow(Integer memberId, String fullName, String membershipNo, String memberType,
                                    String membershipStatus, long activeLoanCount, int loanLimit,
                                    boolean eligible, String ineligibleReason) {
}
