package com.hytech.recruitment.dto.response;

/** 三個月防重複檢查結果。 */
public record DuplicateCheckResponse(
        boolean duplicate,
        Long existingInvitationId,
        java.time.LocalDateTime lastInviteSentAt
) {
}
