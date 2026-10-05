package com.lms.fine.calculation;

import com.lms.common.domain.FineType;

public interface FineCalculationStrategy {

    FineType fineType();

    FineChargeResult calculate(FineChargeRequest request);
}
