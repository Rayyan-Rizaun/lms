package com.lms.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.borrowing.BorrowingService;
import com.lms.common.domain.FineRepository;
import com.lms.common.domain.LoanRepository;
import com.lms.common.domain.LoanStatus;
import com.lms.common.domain.ReservationRepository;
import com.lms.common.domain.ReservationStatus;
import com.lms.common.web.StatTile;
import com.lms.dashboard.dto.MemberDashboardView;
import com.lms.fine.FineService;
import com.lms.fine.dto.MyFineRow;
import com.lms.reservation.ReservationService;

@Service
@Transactional(readOnly = true)
public class MemberDashboardService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(ReservationStatus.Waiting, ReservationStatus.Ready);
    private static final List<String> SETTLED_FINE_STATUSES = List.of("Fully Paid", "Waived");

    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final FineRepository fines;
    private final BorrowingService borrowingService;
    private final ReservationService reservationService;
    private final FineService fineService;

    public MemberDashboardService(LoanRepository loans, ReservationRepository reservations, FineRepository fines,
            BorrowingService borrowingService, ReservationService reservationService, FineService fineService) {
        this.loans = loans;
        this.reservations = reservations;
        this.fines = fines;
        this.borrowingService = borrowingService;
        this.reservationService = reservationService;
        this.fineService = fineService;
    }

    @PreAuthorize("isAuthenticated()")
    public MemberDashboardView view(Integer memberId) {
        LocalDateTime now = LocalDateTime.now();

        long borrowed = loans.countByMemberMemberIdAndStatus(memberId, LoanStatus.Active);
        long dueSoon = loans.countByMemberMemberIdAndStatusAndDueAtBetween(memberId, LoanStatus.Active, now, now.plusDays(3));
        BigDecimal outstandingFines = fines.outstandingBalanceForMember(memberId);
        long activeReservations = reservations.countByMemberMemberIdAndStatusIn(memberId, ACTIVE_RESERVATION_STATUSES);

        List<StatTile> tiles = List.of(
                new StatTile("Books Borrowed", String.valueOf(borrowed)),
                new StatTile("Due in 3 Days", String.valueOf(dueSoon), dueSoon > 0),
                new StatTile("Outstanding Fines", "LKR " + outstandingFines, outstandingFines.signum() > 0),
                new StatTile("Active Reservations", String.valueOf(activeReservations)));

        List<MyFineRow> unpaidFines = fineService.myFines(memberId).stream()
                .filter(row -> !SETTLED_FINE_STATUSES.contains(row.status()))
                .toList();

        return new MemberDashboardView(tiles, borrowingService.myLoans(memberId).current(),
                reservationService.myReservations(memberId), unpaidFines);
    }
}
