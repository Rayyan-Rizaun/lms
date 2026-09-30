package com.lms.borrowing.dto;

import java.time.LocalDateTime;

public record CurrentLoanRow(Integer loanId, String memberName, String bookTitle,
                              LocalDateTime borrowedAt, LocalDateTime dueAt, String pillStatus, boolean renewable) {
}
