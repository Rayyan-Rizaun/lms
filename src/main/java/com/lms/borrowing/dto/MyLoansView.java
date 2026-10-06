package com.lms.borrowing.dto;

import java.util.List;

public record MyLoansView(List<MyLoanRow> current, List<MyLoanRow> past) {
}
