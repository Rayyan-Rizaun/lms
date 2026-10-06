package com.lms.fine.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.lms.common.domain.FineType;
import com.lms.common.domain.SystemSettingRepository;

@Component
public class LostFineCalculationStrategy implements FineCalculationStrategy {

    private final SystemSettingRepository settings;

    public LostFineCalculationStrategy(SystemSettingRepository settings) {
        this.settings = settings;
    }

    @Override
    public FineType fineType() {
        return FineType.Lost;
    }

    @Override
    public FineChargeResult calculate(FineChargeRequest request) {
        BigDecimal purchasePrice = request.incident().getCopy().getPurchasePrice();
        BigDecimal amount = purchasePrice.add(processingFee()).setScale(2, RoundingMode.HALF_UP);
        return FineChargeResult.ofAmount(amount);
    }

    private BigDecimal processingFee() {
        return settings.findById("Fine.LostBookProcessingFee")
                .map(s -> new BigDecimal(s.getSettingValue()))
                .orElse(new BigDecimal("500.00"));
    }
}
