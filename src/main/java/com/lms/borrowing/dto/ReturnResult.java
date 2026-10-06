package com.lms.borrowing.dto;

import java.math.BigDecimal;

public record ReturnResult(Integer loanId, String bookTitle, boolean fineRaised, BigDecimal fineAmount,
                            boolean damaged) {

    public Integer getLoanId() {
        return loanId;
    }
}
