package com.lms.borrowing.dto;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The confirm-issue POST body: which member, which copy. Both are set by
 * hidden fields once the librarian has searched for and picked each one on
 * {@code borrowing/issue.html} — this form has no free-text fields of its
 * own, but CLAUDE.md rule 6 ("every form-backed DTO uses Jakarta Bean
 * Validation annotations") still applies: a missing id here means the
 * confirm button was reachable before both a member and a copy were
 * actually selected, and {@code @NotNull} catches that before {@link
 * com.lms.borrowing.BorrowingService#issue} ever runs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IssueLoanForm {

    @NotNull(message = "Select a member first")
    private Integer memberId;

    @NotNull(message = "Select a copy first")
    private Integer copyId;
}
