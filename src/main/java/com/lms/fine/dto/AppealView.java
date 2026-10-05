package com.lms.fine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The appeal on one fine, if there is one — for the member's own fine
 * detail page. {@code decidedAt}/{@code decisionComments}/{@code
 * approvedReduction} are null while {@code status} is Pending
 * (CK_FineAppeal_DecisionMatchesStatus guarantees they arrive together).
 */
public record AppealView(Integer appealId, String reason, String status, LocalDateTime submittedAt,
                          LocalDateTime decidedAt, String decisionComments, BigDecimal approvedReduction) {
}
