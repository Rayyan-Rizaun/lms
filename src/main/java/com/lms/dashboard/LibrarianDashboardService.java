package com.lms.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.borrowing.dto.CurrentLoanRow;
import com.lms.common.domain.Loan;
import com.lms.common.domain.LoanRepository;
import com.lms.common.domain.LoanStatus;
import com.lms.common.domain.Reservation;
import com.lms.common.domain.ReservationRepository;
import com.lms.common.domain.ReservationStatus;
import com.lms.common.web.ChartData;
import com.lms.common.web.ChartDataset;
import com.lms.common.web.StatTile;
import com.lms.dashboard.dto.LibrarianDashboardView;
import com.lms.dashboard.dto.ReadyReservationRow;

@Service
@Transactional(readOnly = true)
public class LibrarianDashboardService {

    private static final String STAFF_ROLES = "hasAuthority('Librarian') or hasAuthority('Library Administrator')";

    private final LoanRepository loans;
    private final ReservationRepository reservations;

    public LibrarianDashboardService(LoanRepository loans, ReservationRepository reservations) {
        this.loans = loans;
        this.reservations = reservations;
    }

    @PreAuthorize(STAFF_ROLES)
    public LibrarianDashboardView view() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);

        long issuedToday = loans.countByBorrowedAtBetween(startOfToday, startOfTomorrow);
        long dueToday = loans.countByStatusAndDueAtBetween(LoanStatus.Active, startOfToday, startOfTomorrow);
        long overdue = loans.countByStatusAndDueAtBefore(LoanStatus.Active, now);
        long readyReservations = reservations.countByStatus(ReservationStatus.Ready);

        List<StatTile> tiles = List.of(
                new StatTile("Books Issued Today", String.valueOf(issuedToday)),
                new StatTile("Returns Due Today", String.valueOf(dueToday)),
                new StatTile("Overdue Loans", String.valueOf(overdue), overdue > 0),
                new StatTile("Ready for Collection", String.valueOf(readyReservations)));

        ChartData loansPerDay = loansPerDayChart(now);
        ChartData mostBorrowedTitles = mostBorrowedTitlesChart(now);

        List<CurrentLoanRow> todaysOverdueLoans = loans.findTop5ByStatusAndDueAtBeforeOrderByDueAtAsc(LoanStatus.Active, now)
                .stream()
                .map(this::toOverdueRow)
                .toList();

        List<ReadyReservationRow> reservationsAwaitingCollection = reservations
                .findTop5ByStatusOrderByReadyAtAsc(ReservationStatus.Ready)
                .stream()
                .map(this::toReadyRow)
                .toList();

        return new LibrarianDashboardView(tiles, loansPerDay, mostBorrowedTitles, todaysOverdueLoans, reservationsAwaitingCollection);
    }

    private CurrentLoanRow toOverdueRow(Loan loan) {
        String memberName = loan.getMember().getUser().getFirstName() + " " + loan.getMember().getUser().getLastName();
        String bookTitle = loan.getCopy().getBook().getTitle();
        return new CurrentLoanRow(loan.getLoanId(), memberName, bookTitle, loan.getBorrowedAt(), loan.getDueAt(), "Overdue", false);
    }

    private ReadyReservationRow toReadyRow(Reservation reservation) {
        String memberName = reservation.getMember().getUser().getFirstName() + " " + reservation.getMember().getUser().getLastName();
        return new ReadyReservationRow(reservation.getReservationId(), memberName, reservation.getBook().getTitle(), reservation.getExpiresAt());
    }

    private ChartData loansPerDayChart(LocalDateTime now) {
        LocalDate today = LocalDate.now();
        LinkedHashMap<LocalDate, Integer> byDay = new LinkedHashMap<>();
        for (int i = 13; i >= 0; i--) {
            byDay.put(today.minusDays(i), 0);
        }
        LocalDateTime from = today.minusDays(13).atStartOfDay();
        LocalDateTime to = now.plusSeconds(1);
        for (Object[] row : loans.loansPerDay(from, to)) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            if (byDay.containsKey(day)) {
                byDay.put(day, ((Number) row[1]).intValue());
            }
        }
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> entry : byDay.entrySet()) {
            labels.add(entry.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + entry.getKey().getDayOfMonth());
            values.add(BigDecimal.valueOf(entry.getValue()));
        }
        return new ChartData(labels, List.of(new ChartDataset("Loans issued", values)));
    }

    private ChartData mostBorrowedTitlesChart(LocalDateTime now) {
        LocalDateTime from = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (Object[] row : loans.mostBorrowedTitles(from, now.plusSeconds(1), null)) {
            if (labels.size() == 8) {
                break;
            }
            labels.add((String) row[0]);
            values.add(BigDecimal.valueOf(((Number) row[1]).longValue()));
        }
        return new ChartData(labels, List.of(new ChartDataset("Times borrowed", values)));
    }
}
