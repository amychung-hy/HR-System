package com.hytech.recruitment.dto.response;

import com.hytech.recruitment.domain.entity.InvitationRecord;
import com.hytech.recruitment.domain.enums.ContactStatus;

import java.time.LocalDateTime;

/** 邀約紀錄輸出。性別／上機試題類型／面試地點已移置面試安排（見 ArrangementResponse）。 */
public record InvitationResponse(
        Long id,
        String name,
        String email,
        String phone,
        String resumeNo,
        String resumeLink,
        String channel,
        String jobTitle,
        String inviter,
        ContactStatus contactStatus,
        LocalDateTime inviteSentAt,
        LocalDateTime secondInviteAt,
        String declineReason,
        ContactStatus result,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static InvitationResponse from(InvitationRecord r) {
        return new InvitationResponse(
                r.getId(), r.getName(), r.getEmail(), r.getPhone(),
                r.getResumeNo(), r.getResumeLink(), r.getChannel(), r.getJobTitle(), r.getInviter(),
                r.getContactStatus(), r.getInviteSentAt(), r.getSecondInviteAt(),
                r.getDeclineReason(), r.getResult(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
