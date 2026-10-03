package com.lms.dashboard.dto;

import java.util.List;

import com.lms.borrowing.dto.MyLoanRow;
import com.lms.common.web.StatTile;
import com.lms.fine.dto.MyFineRow;
import com.lms.reservation.dto.MyReservationRow;

public record MemberDashboardView(List<StatTile> tiles, List<MyLoanRow> currentLoans,
        List<MyReservationRow> reservations, List<MyFineRow> unpaidFines) {
}
