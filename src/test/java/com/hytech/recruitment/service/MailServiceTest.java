package com.hytech.recruitment.service;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.entity.InterviewArrangement;
import com.hytech.recruitment.domain.entity.InvitationRecord;
import com.hytech.recruitment.domain.entity.MailSendRecord;
import com.hytech.recruitment.domain.entity.MailTemplate;
import com.hytech.recruitment.domain.enums.ContactStatus;
import com.hytech.recruitment.domain.enums.Gender;
import com.hytech.recruitment.domain.enums.MailSendStatus;
import com.hytech.recruitment.dto.request.TriggerMailRequest;
import com.hytech.recruitment.dto.response.MailRecordResponse;
import com.hytech.recruitment.repository.InterviewArrangementRepository;
import com.hytech.recruitment.repository.InvitationRecordRepository;
import com.hytech.recruitment.repository.MailSendRecordRepository;
import com.hytech.recruitment.repository.MailTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 寄信領域服務單元測試：冪等、狀態校驗、缺欄、查無範本、成功與變數合併。 */
@DisplayName("寄信領域服務")
@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private InvitationRecordRepository invitationRepository;
    @Mock
    private InterviewArrangementRepository arrangementRepository;
    @Mock
    private MailTemplateRepository templateRepository;
    @Mock
    private MailSendRecordRepository sendRecordRepository;
    @Mock
    private MailSender mailSender;
    @Mock
    private ManagerResolver managerResolver;

    @InjectMocks
    private MailService service;

    private InvitationRecord inv(ContactStatus status) {
        InvitationRecord r = new InvitationRecord();
        r.setId(1L);
        r.setName("王小明");
        r.setEmail("ming@x.com");
        r.setJobTitle("後端工程師");
        r.setContactStatus(status);
        return r;
    }

    private InterviewArrangement fullArr() {
        InterviewArrangement a = new InterviewArrangement();
        a.setInvitationId(1L);
        a.setGender(Gender.FEMALE);
        a.setExamType("基本");
        a.setLocation("南港");
        a.setJobTitle("後端工程師");
        a.setInterviewTime(LocalDateTime.of(2026, 9, 8, 10, 0));
        a.setInterviewManager("Tom");
        return a;
    }

    private void stubSaveEcho() {
        lenient().when(sendRecordRepository.save(any(MailSendRecord.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("已成功寄送則冪等略過")
    void alreadySuccess_idempotentSkip() {
        MailSendRecord existing = new MailSendRecord();
        existing.setInvitationId(1L);
        existing.setStatus(MailSendStatus.SUCCESS);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(inv(ContactStatus.INTERVIEW_CONFIRMED)));
        when(sendRecordRepository.findByInvitationId(1L)).thenReturn(Optional.of(existing));

        MailRecordResponse resp = service.triggerMail(1L, new TriggerMailRequest(null));

        assertEquals(MailSendStatus.SUCCESS, resp.status());
        verify(mailSender, never()).send(anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("邀約不存在丟 NOT_FOUND")
    void invitationNotFound_throwsNotFound() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.triggerMail(1L, new TriggerMailRequest(null)));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("未確認面試落 FAILED 不寄")
    void notConfirmed_marksFailed_noSend() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(inv(ContactStatus.COMMUNICATING)));
        when(sendRecordRepository.findByInvitationId(1L)).thenReturn(Optional.empty());
        when(arrangementRepository.findByInvitationId(1L)).thenReturn(Optional.of(fullArr()));
        stubSaveEcho();

        MailRecordResponse resp = service.triggerMail(1L, new TriggerMailRequest(null));

        assertEquals(MailSendStatus.FAILED, resp.status());
        verify(mailSender, never()).send(anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("基本資料缺漏丟 MAIL_REQUIRED_FIELDS_MISSING")
    void missingBasicInfo_throwsRequiredFieldsMissing() {
        InterviewArrangement arr = fullArr();
        arr.setGender(null);
        arr.setExamType(null);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(inv(ContactStatus.INTERVIEW_CONFIRMED)));
        when(sendRecordRepository.findByInvitationId(1L)).thenReturn(Optional.empty());
        when(arrangementRepository.findByInvitationId(1L)).thenReturn(Optional.of(arr));
        stubSaveEcho();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.triggerMail(1L, new TriggerMailRequest(null)));
        assertEquals(ErrorCode.MAIL_REQUIRED_FIELDS_MISSING, ex.getErrorCode());
    }

    @Test
    @DisplayName("查無範本丟 TEMPLATE_NOT_FOUND 並落 FAILED")
    void templateNotFound_throwsAndMarksFailed() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(inv(ContactStatus.INTERVIEW_CONFIRMED)));
        when(sendRecordRepository.findByInvitationId(1L)).thenReturn(Optional.empty());
        when(arrangementRepository.findByInvitationId(1L)).thenReturn(Optional.of(fullArr()));
        when(managerResolver.chooseCcManagers(any())).thenReturn(List.of("Tom"));
        when(managerResolver.resolveEmails(any())).thenReturn(List.of("tom@hy-tech.com.tw"));
        when(templateRepository.findByExamTypeAndLocationAndJobTitle("基本", "南港", "後端工程師"))
                .thenReturn(Optional.empty());
        stubSaveEcho();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.triggerMail(1L, new TriggerMailRequest(null)));
        assertEquals(ErrorCode.TEMPLATE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("成功寄送：合併變數、CC 主管、回寫已處理")
    void success_mergesVariables_ccManager_marksProcessed() {
        InvitationRecord inv = inv(ContactStatus.INTERVIEW_CONFIRMED);
        InterviewArrangement arr = fullArr();
        MailTemplate tpl = new MailTemplate();
        tpl.setBody("{{姓氏}}{{先生/小姐}} 您好，面試時間 {{面試時間}}，資料：{{面試前資料}}");

        when(invitationRepository.findById(1L)).thenReturn(Optional.of(inv));
        when(sendRecordRepository.findByInvitationId(1L)).thenReturn(Optional.empty());
        when(arrangementRepository.findByInvitationId(1L)).thenReturn(Optional.of(arr));
        when(managerResolver.chooseCcManagers(arr)).thenReturn(List.of("Tom"));
        when(managerResolver.resolveEmails(List.of("Tom"))).thenReturn(List.of("tom@hy-tech.com.tw"));
        when(templateRepository.findByExamTypeAndLocationAndJobTitle("基本", "南港", "後端工程師"))
                .thenReturn(Optional.of(tpl));
        when(mailSender.send(eq("ming@x.com"), eq("tom@hy-tech.com.tw"), eq("面試通知"), anyString()))
                .thenReturn(true);
        stubSaveEcho();

        MailRecordResponse resp = service.triggerMail(1L, new TriggerMailRequest("http://form/1"));

        assertEquals(MailSendStatus.SUCCESS, resp.status());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(eq("ming@x.com"), eq("tom@hy-tech.com.tw"), eq("面試通知"), body.capture());
        assertTrue(body.getValue().contains("王小姐"), "應套用姓氏與女性稱謂");
        assertTrue(body.getValue().contains("2026/09/08 10:00"));
        assertTrue(body.getValue().contains("http://form/1"));
        verify(arrangementRepository).save(any());   // 回寫 mailSystemStatus
    }

    @Test
    @DisplayName("退信時落 FAILED 回傳")
    void bounce_marksFailed() {
        InterviewArrangement arr = fullArr();
        MailTemplate tpl = new MailTemplate();
        tpl.setBody("hi");
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(inv(ContactStatus.INTERVIEW_CONFIRMED)));
        when(sendRecordRepository.findByInvitationId(1L)).thenReturn(Optional.empty());
        when(arrangementRepository.findByInvitationId(1L)).thenReturn(Optional.of(arr));
        when(managerResolver.chooseCcManagers(arr)).thenReturn(List.of());
        when(managerResolver.resolveEmails(any())).thenReturn(List.of());
        when(templateRepository.findByExamTypeAndLocationAndJobTitle(any(), any(), any()))
                .thenReturn(Optional.of(tpl));
        when(mailSender.send(anyString(), isNull(), anyString(), anyString())).thenReturn(false);
        stubSaveEcho();

        MailRecordResponse resp = service.triggerMail(1L, new TriggerMailRequest(null));
        assertEquals(MailSendStatus.FAILED, resp.status());
    }

    @Test
    @DisplayName("寄推薦信給主管")
    void sendRecommendation_toManagers() {
        InvitationRecord inv = inv(ContactStatus.COMMUNICATING);
        service.sendRecommendation(inv, "http://resume/1");
        verify(mailSender).send(eq("managers@hy-tech.com.tw"), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("查詢寄信結果：查無丟 NOT_FOUND")
    void getByInvitationId_notFound_throwsNotFound() {
        when(sendRecordRepository.findByInvitationId(1L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.getByInvitationId(1L));
    }
}
