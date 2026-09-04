package com.hytech.recruitment.service;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.entity.InvitationRecord;
import com.hytech.recruitment.domain.enums.ContactStatus;
import com.hytech.recruitment.dto.request.CreateInvitationRequest;
import com.hytech.recruitment.dto.request.DeclineRequest;
import com.hytech.recruitment.dto.request.PatchInvitationRequest;
import com.hytech.recruitment.dto.request.ReplyReceivedRequest;
import com.hytech.recruitment.dto.request.ResultRequest;
import com.hytech.recruitment.dto.response.DuplicateCheckResponse;
import com.hytech.recruitment.dto.response.InvitationResponse;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 邀約主檔領域服務單元測試。 */
@DisplayName("邀約主檔領域服務")
@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private InvitationRecordRepository invitationRepository;
    @Mock
    private InterviewArrangementRepository arrangementRepository;
    @Mock
    private StatusMachine statusMachine;
    @Mock
    private MailService mailService;

    @InjectMocks
    private InvitationService service;

    private InvitationRecord record(Long id, ContactStatus status) {
        InvitationRecord r = new InvitationRecord();
        r.setId(id);
        r.setName("王小明");
        r.setEmail("ming@x.com");
        r.setJobTitle("後端工程師");
        r.setContactStatus(status);
        return r;
    }

    private CreateInvitationRequest createReq(String phone, String inviter) {
        return new CreateInvitationRequest("王小明", "ming@x.com", phone,
                "R001", "104", "後端工程師", inviter);
    }

    // ---------- create ----------

    @Test
    @DisplayName("建立邀約：正規化手機、帶預設邀請人、自動履歷連結")
    void create_success_normalizesPhone_defaultInviter_autoResumeLink() {
        when(invitationRepository.findRecentInvitesByNameAndPhone(anyString(), anyString(), any()))
                .thenReturn(List.of());
        when(invitationRepository.save(any(InvitationRecord.class))).thenAnswer(inv -> {
            InvitationRecord r = inv.getArgument(0);
            r.setId(42L);
            return r;
        });

        InvitationResponse resp = service.create(createReq("0966888999", "  "));

        assertEquals("0966-888-999", resp.phone());
        assertEquals("amy.chung", resp.inviter());
        assertEquals(ContactStatus.EMAIL_CONTACT, resp.contactStatus());
        assertEquals("https://drive.example.invalid/resume/42", resp.resumeLink());
    }

    @Test
    @DisplayName("建立邀約：使用自填邀請人並去除前後空白")
    void create_usesProvidedInviter_trimmed() {
        when(invitationRepository.findRecentInvitesByNameAndEmail(anyString(), anyString(), any()))
                .thenReturn(List.of());
        when(invitationRepository.save(any(InvitationRecord.class))).thenAnswer(inv -> {
            InvitationRecord r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        InvitationResponse resp = service.create(createReq(null, "  bob.lin "));
        assertEquals("bob.lin", resp.inviter());
        assertNull(resp.phone());
    }

    @Test
    @DisplayName("建立邀約：手機格式錯誤丟 VALIDATION_ERROR")
    void create_invalidPhone_throwsValidationError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(createReq("12345", null)));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("建立邀約：命中三個月重複丟 DUPLICATE_INVITE_WITHIN_3M")
    void create_duplicateWithin3Months_throwsDuplicate() {
        when(invitationRepository.findRecentInvitesByNameAndPhone(anyString(), anyString(), any()))
                .thenReturn(List.of(record(7L, ContactStatus.EMAIL_CONTACT)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(createReq("0966888999", null)));
        assertEquals(ErrorCode.DUPLICATE_INVITE_WITHIN_3M, ex.getErrorCode());
        assertEquals(7L, ex.getExtras().get("existingInvitationId"));
    }

    // ---------- checkDuplicate ----------

    @Test
    @DisplayName("防重複檢查：無手機以信箱為鍵、命中")
    void checkDuplicate_noPhone_matchByEmail_hit() {
        InvitationRecord hit = record(9L, ContactStatus.EMAIL_CONTACT);
        hit.setInviteSentAt(LocalDateTime.now().minusDays(3));
        when(invitationRepository.findRecentInvitesByNameAndEmail(eq("王小明"), eq("ming@x.com"), any()))
                .thenReturn(List.of(hit));
        DuplicateCheckResponse resp = service.checkDuplicate("王小明", "ming@x.com", null);
        assertTrue(resp.duplicate());
        assertEquals(9L, resp.existingInvitationId());
    }

    @Test
    @DisplayName("防重複檢查：查無回 false")
    void checkDuplicate_noMatch_returnsFalse() {
        when(invitationRepository.findRecentInvitesByNameAndPhone(anyString(), anyString(), any()))
                .thenReturn(List.of());
        DuplicateCheckResponse resp = service.checkDuplicate("王小明", "ming@x.com", "0966-888-999");
        assertFalse(resp.duplicate());
        assertNull(resp.existingInvitationId());
    }

    @Test
    @DisplayName("防重複檢查：姓名空白直接回 false")
    void checkDuplicate_blankName_returnsFalse() {
        DuplicateCheckResponse resp = service.checkDuplicate(" ", "ming@x.com", null);
        assertFalse(resp.duplicate());
    }

    // ---------- get / patch ----------

    @Test
    @DisplayName("查詢：查無丟 NOT_FOUND")
    void get_notFound_throwsNotFound() {
        when(invitationRepository.findById(99L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.get(99L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("欄位級更新：只更新非 null 欄位並正規化手機")
    void patch_updatesOnlyNonNullFields_normalizesPhone() {
        InvitationRecord r = record(1L, ContactStatus.EMAIL_CONTACT);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        PatchInvitationRequest req = new PatchInvitationRequest(
                "李四", null, "0912345678", null, "yourator", null);
        InvitationResponse resp = service.patch(1L, req);
        assertEquals("李四", resp.name());
        assertEquals("ming@x.com", resp.email());       // 未傳，保留
        assertEquals("0912-345-678", resp.phone());
        assertEquals("yourator", resp.channel());
    }

    // ---------- sendInvite ----------

    @Test
    @DisplayName("寄一面邀請：成功記錄寄出時間")
    void sendInvite_success_recordsSentTime() {
        InvitationRecord r = record(1L, ContactStatus.EMAIL_CONTACT);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(invitationRepository.findRecentInvitesByNameAndEmail(anyString(), anyString(), any()))
                .thenReturn(List.of());
        InvitationResponse resp = service.sendInvite(1L);
        assertTrue(resp.inviteSentAt() != null);
    }

    @Test
    @DisplayName("寄一面邀請：非 EMAIL_CONTACT 丟 ILLEGAL_STATE_TRANSITION")
    void sendInvite_notEmailContact_throwsIllegalState() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(record(1L, ContactStatus.COMMUNICATING)));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendInvite(1L));
        assertEquals(ErrorCode.ILLEGAL_STATE_TRANSITION, ex.getErrorCode());
    }

    @Test
    @DisplayName("寄一面邀請：命中他人重複列丟 DUPLICATE")
    void sendInvite_duplicateOther_throwsDuplicate() {
        InvitationRecord self = record(1L, ContactStatus.EMAIL_CONTACT);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(self));
        when(invitationRepository.findRecentInvitesByNameAndEmail(anyString(), anyString(), any()))
                .thenReturn(List.of(record(2L, ContactStatus.EMAIL_CONTACT)));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendInvite(1L));
        assertEquals(ErrorCode.DUPLICATE_INVITE_WITHIN_3M, ex.getErrorCode());
    }

    // ---------- secondInvite ----------

    @Test
    @DisplayName("二次邀約：由 EMAIL_CONTACT 轉 SECOND_INVITE_PENDING")
    void secondInvite_fromEmailContact_toSecondInvitePending() {
        InvitationRecord r = record(1L, ContactStatus.EMAIL_CONTACT);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        InvitationResponse resp = service.secondInvite(1L);
        assertEquals(ContactStatus.SECOND_INVITE_PENDING, resp.contactStatus());
        assertTrue(resp.secondInviteAt() != null);
    }

    @Test
    @DisplayName("二次邀約：非法來源狀態丟 ILLEGAL_STATE")
    void secondInvite_illegalSourceStatus_throwsIllegalState() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(record(1L, ContactStatus.COMMUNICATING)));
        assertThrows(BusinessException.class, () -> service.secondInvite(1L));
    }

    // ---------- replyReceived ----------

    @Test
    @DisplayName("回覆有意願：轉 COMMUNICATING、建面試安排、寄推薦信")
    void replyReceived_toCommunicating_createsArrangement_sendsRecommendation() {
        InvitationRecord r = record(1L, ContactStatus.EMAIL_CONTACT);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(arrangementRepository.existsByInvitationId(1L)).thenReturn(false);

        InvitationResponse resp = service.replyReceived(1L, new ReplyReceivedRequest("http://resume/1"));

        assertEquals(ContactStatus.COMMUNICATING, resp.contactStatus());
        verify(statusMachine).assertTransition(ContactStatus.EMAIL_CONTACT, ContactStatus.COMMUNICATING);
        verify(arrangementRepository).save(any());
        verify(mailService).sendRecommendation(r, "http://resume/1");
    }

    @Test
    @DisplayName("回覆有意願：面試安排已存在則不重建")
    void replyReceived_arrangementExists_noRecreate() {
        InvitationRecord r = record(1L, ContactStatus.EMAIL_CONTACT);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        when(arrangementRepository.existsByInvitationId(1L)).thenReturn(true);
        service.replyReceived(1L, null);
        verify(arrangementRepository, never()).save(any());
        verify(mailService).sendRecommendation(r, null);
    }

    // ---------- confirmTime / confirmInterview / decline ----------

    @Test
    @DisplayName("協調時間：走狀態機轉 TIME_CONFIRMING")
    void confirmTime_viaStateMachine_toTimeConfirming() {
        InvitationRecord r = record(1L, ContactStatus.COMMUNICATING);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        InvitationResponse resp = service.confirmTime(1L);
        assertEquals(ContactStatus.TIME_CONFIRMING, resp.contactStatus());
        verify(statusMachine).assertTransition(ContactStatus.COMMUNICATING, ContactStatus.TIME_CONFIRMING);
    }

    @Test
    @DisplayName("確認面試：轉 INTERVIEW_CONFIRMED")
    void confirmInterview_toInterviewConfirmed() {
        InvitationRecord r = record(1L, ContactStatus.TIME_CONFIRMING);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        InvitationResponse resp = service.confirmInterview(1L);
        assertEquals(ContactStatus.INTERVIEW_CONFIRMED, resp.contactStatus());
    }

    @Test
    @DisplayName("婉拒：轉 DECLINED 並帶原因")
    void decline_toDeclined_withReason() {
        InvitationRecord r = record(1L, ContactStatus.COMMUNICATING);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        InvitationResponse resp = service.decline(1L, new DeclineRequest("薪資不符"));
        assertEquals(ContactStatus.DECLINED, resp.contactStatus());
        assertEquals("薪資不符", resp.declineReason());
    }

    // ---------- result / applyResult / blacklist ----------

    @Test
    @DisplayName("設定結果：由確認面試轉 OFFER_EXTENDED、最終結果留白")
    void result_fromConfirmed_toOfferExtended_resultBlank() {
        InvitationRecord r = record(1L, ContactStatus.INTERVIEW_CONFIRMED);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        InvitationResponse resp = service.result(1L, new ResultRequest(ContactStatus.OFFER_EXTENDED, null));
        assertEquals(ContactStatus.OFFER_EXTENDED, resp.contactStatus());
        assertNull(resp.result());
    }

    @Test
    @DisplayName("設定結果：由 OFFER_EXTENDED 改判 DECLINED 寫入原因")
    void result_fromOfferExtended_toDeclined_writesReason() {
        InvitationRecord r = record(1L, ContactStatus.OFFER_EXTENDED);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        InvitationResponse resp = service.result(1L, new ResultRequest(ContactStatus.DECLINED, "人選拒絕聘約"));
        assertEquals(ContactStatus.DECLINED, resp.result());
        assertEquals("人選拒絕聘約", resp.declineReason());
    }

    @Test
    @DisplayName("設定結果：接受聘約清空婉拒原因")
    void result_offerAccepted_clearsDeclineReason() {
        InvitationRecord r = record(1L, ContactStatus.OFFER_EXTENDED);
        r.setDeclineReason("舊原因");
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        InvitationResponse resp = service.result(1L, new ResultRequest(ContactStatus.OFFER_ACCEPTED, "忽略"));
        assertEquals(ContactStatus.OFFER_ACCEPTED, resp.result());
        assertNull(resp.declineReason());
    }

    @Test
    @DisplayName("設定結果：目標非結果狀態丟 VALIDATION_ERROR")
    void result_targetNotResultState_throwsValidationError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.result(1L, new ResultRequest(ContactStatus.COMMUNICATING, null)));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("設定結果：來源狀態未達確認面試丟 ILLEGAL_STATE")
    void result_sourceBeforeConfirmed_throwsIllegalState() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(record(1L, ContactStatus.COMMUNICATING)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.result(1L, new ResultRequest(ContactStatus.THANKS_LETTER, null)));
        assertEquals(ErrorCode.ILLEGAL_STATE_TRANSITION, ex.getErrorCode());
    }

    @Test
    @DisplayName("黑名單：由確認面試轉黑名單")
    void blacklist_fromConfirmed_toBlacklist() {
        InvitationRecord r = record(1L, ContactStatus.INTERVIEW_CONFIRMED);
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(r));
        InvitationResponse resp = service.blacklist(1L);
        assertEquals(ContactStatus.BLACKLIST, resp.contactStatus());
        assertEquals(ContactStatus.BLACKLIST, resp.result());
    }
}
