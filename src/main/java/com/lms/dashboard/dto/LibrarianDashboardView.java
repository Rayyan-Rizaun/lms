package com.lms.dashboard.dto;

import java.util.List;

import com.lms.borrowing.dto.CurrentLoanRow;
import com.lms.common.web.ChartData;
import com.lms.common.web.StatTile;

public record LibrarianDashboardView(List<StatTile> tiles, ChartData loansPerDay, ChartData mostBorrowedTitles,
        List<CurrentLoanRow> todaysOverdueLoans, List<ReadyReservationRow> reservationsAwaitingCollection) {
}
