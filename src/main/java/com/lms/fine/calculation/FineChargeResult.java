package com.lms.fine.calculation;

import java.math.BigDecimal;

public record FineChargeResult(BigDecimal amount, BigDecimal ratePerDay) {

    public static FineChargeResult ofAmount(BigDecimal amount) {
        return new FineChargeResult(amount, null);
    }
}
