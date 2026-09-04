package com.hytech.recruitment.dto.response;

import com.hytech.recruitment.domain.entity.MailSendRecord;
import com.hytech.recruitment.domain.enums.Gender;
import com.hytech.recruitment.domain.enums.MailSendStatus;

import java.time.LocalDateTime;

/** 寄信結果輸出。 */
public record MailRecordResponse(
        Long id,
        Long invitationId,
        String email,
        Gender gender,
        String jobTitle,
        String examType,
        String location,
        LocalDateTime interviewTime,
        String formLink,
        String ccManagerEmail,
        LocalDateTime sentAt,
        MailSendStatus status,
        String errorMessage
) {
    public static MailRecordResponse from(MailSendRecord m) {
        return new MailRecordResponse(
                m.getId(), m.getInvitationId(), m.getEmail(), m.getGender(), m.getJobTitle(),
                m.getExamType(), m.getLocation(), m.getInterviewTime(), m.getFormLink(),
                m.getCcManagerEmail(), m.getSentAt(), m.getStatus(), m.getErrorMessage());
    }
}
