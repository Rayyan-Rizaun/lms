package com.lms.fine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row of the staff appeal review queue — always a Pending appeal (the only status this screen lists). */
public record AppealQueueRow(Integer appealId, Integer fineId, String memberName, String fineType,
                              BigDecimal amountAssessed, BigDecimal balance, String appealReason,
                              LocalDateTime submittedAt, String referralNote) {
}
