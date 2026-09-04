package com.hytech.recruitment.service;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.entity.InterviewArrangement;
import com.hytech.recruitment.domain.entity.InvitationRecord;
import com.hytech.recruitment.domain.enums.ContactStatus;
import com.hytech.recruitment.domain.enums.Gender;
import com.hytech.recruitment.dto.request.InterviewTimeRequest;
import com.hytech.recruitment.dto.response.ArrangementResponse;
import com.hytech.recruitment.repository.InterviewArrangementRepository;
import com.hytech.recruitment.repository.InvitationRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 面試安排領域服務單元測試：主管白名單、排面試時間、自動寄信判斷。 */
@DisplayName("面試安排領域服務")
@ExtendWith(MockitoExtension.class)
class InterviewArrangementServiceTest {

    @Mock
    private InterviewArrangementRepository arrangementRepository;
    @Mock
    private InvitationRecordRepository invitationRepository;
    @Mock
    private ManagerResolver managerResolver;
    @Mock
    private MailService mailService;

    @InjectMocks
    private InterviewArrangementService service;

    private InterviewArrangement arr() {
        InterviewArrangement a = new InterviewArrangement();
        a.setId(1L);
        a.setInvitationId(1L);
        return a;
    }

    // ---------- updateManagerFields ----------

    @Test
    @DisplayName("主管首次填面試主管記 assignedAt")
    void updateManagerFields_firstAssign_recordsAssignedAt() {
        InterviewArrangement a = arr();
        when(arrangementRepository.findById(1L)).thenReturn(Optional.of(a));
        ArrangementResponse resp = service.updateManagerFields(1L,
                Map.of("interviewManager", "Tom", "managerRemark", "優先給 Tom"));
        assertEquals("Tom", resp.interviewManager());
        assertTrue(a.getManagerAssignedAt() != null);
    }

    @Test
    @DisplayName("主管填非白名單欄位丟 FIELD_NOT_WRITABLE_BY_ROLE")
    void updateManagerFields_notWhitelisted_throwsFieldNotWritable() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateManagerFields(1L, Map.of("gender", "MALE")));
        assertEquals(ErrorCode.FIELD_NOT_WRITABLE_BY_ROLE, ex.getErrorCode());
        verify(arrangementRepository, never()).findById(any());
    }

    // ---------- updateInterviewTime ----------

    @Test
    @DisplayName("排面試時間：填基本資料並選定主管")
    void updateInterviewTime_fillsBasicInfo_selectsManager() {
        InterviewArrangement a = arr();
        a.setInterviewManager("Tom/John");
        when(arrangementRepository.findById(1L)).thenReturn(Optional.of(a));
        when(managerResolver.parseManagers("Tom/John")).thenReturn(List.of("Tom", "John"));
        when(managerResolver.matchInList(List.of("Tom", "John"), "john")).thenReturn(Optional.of("John"));

        InterviewTimeRequest req = new InterviewTimeRequest(
                LocalDateTime.of(2026, 12, 1, 10, 0), Gender.MALE, "AI", "板橋", "john");
        ArrangementResponse resp = service.updateInterviewTime(1L, req);

        assertEquals(Gender.MALE, resp.gender());
        assertEquals("AI", resp.examType());
        assertEquals("板橋", resp.location());
        assertEquals("John", a.getSelectedManager());
    }

    @Test
    @DisplayName("排面試時間：examType 非法丟 VALIDATION_ERROR")
    void updateInterviewTime_invalidExamType_throwsValidationError() {
        when(arrangementRepository.findById(1L)).thenReturn(Optional.of(arr()));
        InterviewTimeRequest req = new InterviewTimeRequest(null, null, "進階", null, null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateInterviewTime(1L, req));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("排面試時間：location 非法丟 VALIDATION_ERROR")
    void updateInterviewTime_invalidLocation_throwsValidationError() {
        when(arrangementRepository.findById(1L)).thenReturn(Optional.of(arr()));
        InterviewTimeRequest req = new InterviewTimeRequest(null, null, null, "新竹", null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateInterviewTime(1L, req));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("排面試時間：選定主管但尚未填主管丟 MANAGER_SELECTION_REQUIRED")
    void updateInterviewTime_selectWithoutManagers_throwsManagerSelectionRequired() {
        InterviewArrangement a = arr();
        when(arrangementRepository.findById(1L)).thenReturn(Optional.of(a));
        when(managerResolver.parseManagers(null)).thenReturn(List.of());
        InterviewTimeRequest req = new InterviewTimeRequest(null, null, null, null, "Tom");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateInterviewTime(1L, req));
        assertEquals(ErrorCode.MANAGER_SELECTION_REQUIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("排面試時間：空字串代表不選定、清除既有")
    void updateInterviewTime_blankSelection_clearsExisting() {
        InterviewArrangement a = arr();
        a.setSelectedManager("Tom");
        when(arrangementRepository.findById(1L)).thenReturn(Optional.of(a));
        InterviewTimeRequest req = new InterviewTimeRequest(null, null, null, null, "");
        service.updateInterviewTime(1L, req);
        assertNull(a.getSelectedManager());
    }

    // ---------- autoTriggerMailIfReady ----------

    @Test
    @DisplayName("自動寄信：未確認面試不寄")
    void autoTrigger_notConfirmed_noSend() {
        InterviewArrangement a = arr();
        when(arrangementRepository.findById(1L)).thenReturn(Optional.of(a));
        InvitationRecord inv = new InvitationRecord();
        inv.setContactStatus(ContactStatus.COMMUNICATING);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(inv));
        service.autoTriggerMailIfReady(1L);
        verify(mailService, never()).triggerMail(any(), any());
    }

    @Test
    @DisplayName("自動寄信：基本資料未齊不寄")
    void autoTrigger_incompleteBasicInfo_noSend() {
        InterviewArrangement a = arr();   // gender/examType/location 皆 null
        when(arrangementRepository.findById(1L)).thenReturn(Optional.of(a));
        InvitationRecord inv = new InvitationRecord();
        inv.setContactStatus(ContactStatus.INTERVIEW_CONFIRMED);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(inv));
        service.autoTriggerMailIfReady(1L);
        verify(mailService, never()).triggerMail(any(), any());
    }

    @Test
    @DisplayName("自動寄信：齊備時觸發寄信")
    void autoTrigger_ready_triggersMail() {
        InterviewArrangement a = arr();
        a.setGender(Gender.MALE);
        a.setExamType("基本");
        a.setLocation("南港");
        when(arrangementRepository.findById(1L)).thenReturn(Optional.of(a));
        InvitationRecord inv = new InvitationRecord();
        inv.setContactStatus(ContactStatus.INTERVIEW_CONFIRMED);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(inv));
        service.autoTriggerMailIfReady(1L);
        verify(mailService).triggerMail(eq(1L), any());
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
