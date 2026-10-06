package com.lms.fine.calculation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.lms.common.domain.BookIncident;
import com.lms.common.domain.Loan;

public record FineChargeRequest(Loan loan, LocalDateTime returnedAt, BookIncident incident, BigDecimal proposedAmount) {

    public static FineChargeRequest forOverdueReturn(Loan loan, LocalDateTime returnedAt) {
        return new FineChargeRequest(loan, returnedAt, null, null);
    }

    public static FineChargeRequest forLostBook(BookIncident incident) {
        return new FineChargeRequest(null, null, incident, null);
    }

    public static FineChargeRequest forDamagedBook(BookIncident incident, BigDecimal proposedAmount) {
        return new FineChargeRequest(null, null, incident, proposedAmount);
    }
}
