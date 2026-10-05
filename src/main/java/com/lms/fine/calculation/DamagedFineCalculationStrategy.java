package com.lms.fine.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.lms.common.domain.FineType;

@Component
public class DamagedFineCalculationStrategy implements FineCalculationStrategy {

    @Override
    public FineType fineType() {
        return FineType.Damaged;
    }

    @Override
    public FineChargeResult calculate(FineChargeRequest request) {
        BigDecimal purchasePrice = request.incident().getCopy().getPurchasePrice();
        BigDecimal amount = request.proposedAmount().min(purchasePrice).setScale(2, RoundingMode.HALF_UP);
        return FineChargeResult.ofAmount(amount);
    }
}
