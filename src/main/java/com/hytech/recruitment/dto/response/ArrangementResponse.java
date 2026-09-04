package com.hytech.recruitment.dto.response;

import com.hytech.recruitment.domain.entity.InterviewArrangement;
import com.hytech.recruitment.domain.enums.Gender;

import java.time.LocalDateTime;

/** 面試安排輸出。含由邀約紀錄移置過來的基本資料（gender／examType／location）。 */
public record ArrangementResponse(
        Long id,
        Long invitationId,
        String candidateName,
        String jobTitle,
        String resumeLink,
        Gender gender,
        String examType,
        String location,
        String interviewManager,
        String selectedManager,
        String managerPreferredDates,
        LocalDateTime interviewTime,
        String managerRemark,
        String interviewPrepSheet,
        String basicInfoSheet,
        String candidateReplyLink,
        String mailSystemStatus,
        LocalDateTime mailSentAt,
        LocalDateTime managerAssignedAt,
        String calendarId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ArrangementResponse from(InterviewArrangement a) {
        return new ArrangementResponse(
                a.getId(), a.getInvitationId(), a.getCandidateName(), a.getJobTitle(),
                a.getResumeLink(), a.getGender(), a.getExamType(), a.getLocation(),
                a.getInterviewManager(), a.getSelectedManager(),
                a.getManagerPreferredDates(), a.getInterviewTime(), a.getManagerRemark(),
                a.getInterviewPrepSheet(), a.getBasicInfoSheet(), a.getCandidateReplyLink(),
                a.getMailSystemStatus(), a.getMailSentAt(),
                a.getManagerAssignedAt(), a.getCalendarId(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
