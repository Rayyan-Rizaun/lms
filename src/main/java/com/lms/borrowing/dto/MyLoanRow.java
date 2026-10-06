package com.lms.borrowing.dto;

import java.time.LocalDateTime;

public record MyLoanRow(Integer loanId, String bookTitle, LocalDateTime borrowedAt, LocalDateTime dueAt,
                         LocalDateTime returnedAt, String pillStatus, boolean renewable) {
}
