package com.lms.fine.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.lms.common.domain.FineType;
import com.lms.common.domain.SystemSettingRepository;

@Component
public class OverdueFineCalculationStrategy implements FineCalculationStrategy {

    private final SystemSettingRepository settings;

    public OverdueFineCalculationStrategy(SystemSettingRepository settings) {
        this.settings = settings;
    }

    @Override
    public FineType fineType() {
        return FineType.Overdue;
    }

    @Override
    public FineChargeResult calculate(FineChargeRequest request) {
        BigDecimal rate = ratePerDay();
        long daysOverdue = ChronoUnit.DAYS.between(request.loan().getDueAt(), request.returnedAt());
        if (daysOverdue <= 0) {
            return new FineChargeResult(BigDecimal.ZERO, rate);
        }
        BigDecimal cap = maxPerLoan();
        BigDecimal amount = rate.multiply(BigDecimal.valueOf(daysOverdue)).min(cap).setScale(2, RoundingMode.HALF_UP);
        return new FineChargeResult(amount, rate);
    }

    private BigDecimal ratePerDay() {
        return settings.findById("Fine.RatePerDay")
                .map(s -> new BigDecimal(s.getSettingValue()))
                .orElse(new BigDecimal("20.00"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal maxPerLoan() {
        return settings.findById("Fine.MaxPerLoan")
                .map(s -> new BigDecimal(s.getSettingValue()))
                .orElse(new BigDecimal("500.00"));
    }
}
