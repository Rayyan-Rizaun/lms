package com.lms.dashboard.dto;

import java.time.LocalDateTime;

public record ReadyReservationRow(Integer reservationId, String memberName, String bookTitle, LocalDateTime expiresAt) {
}
