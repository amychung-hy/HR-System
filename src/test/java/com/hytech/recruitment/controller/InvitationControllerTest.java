package com.hytech.recruitment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.entity.InvitationRecord;
import com.hytech.recruitment.domain.enums.ContactStatus;
import com.hytech.recruitment.dto.request.CreateInvitationRequest;
import com.hytech.recruitment.dto.request.DeclineRequest;
import com.hytech.recruitment.dto.request.ResultRequest;
import com.hytech.recruitment.dto.response.DuplicateCheckResponse;
import com.hytech.recruitment.dto.response.InvitationResponse;
import com.hytech.recruitment.dto.response.PageResponse;
import com.hytech.recruitment.service.InvitationService;
import com.hytech.recruitment.service.MailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 邀約端點 MockMvc 測試：回應信封、狀態碼、全域例外處理、參數驗證。 */
@DisplayName("邀約端點")
@WebMvcTest(InvitationController.class)
class InvitationControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @MockBean
    private InvitationService invitationService;
    @MockBean
    private MailService mailService;

    private InvitationResponse resp(Long id, ContactStatus status) {
        InvitationRecord r = new InvitationRecord();
        r.setId(id);
        r.setName("王小明");
        r.setEmail("ming@x.com");
        r.setChannel("104");
        r.setJobTitle("後端工程師");
        r.setInviter("amy.chung");
        r.setContactStatus(status);
        return InvitationResponse.from(r);
    }

    @Test
    @DisplayName("建立邀約成功回 201 與 success 信封")
    void create_success_returns201WithEnvelope() throws Exception {
        when(invitationService.create(any())).thenReturn(resp(1L, ContactStatus.EMAIL_CONTACT));
        CreateInvitationRequest req = new CreateInvitationRequest(
                "王小明", "ming@x.com", "0966888999", "R1", "104", "後端工程師", "amy.chung");

        mvc.perform(post("/api/v1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.contactStatus").value("EMAIL_CONTACT"));
    }

    @Test
    @DisplayName("建立邀約缺必填欄位回 400 VALIDATION_ERROR")
    void create_missingRequiredFields_returns400() throws Exception {
        // name/email/channel/jobTitle 皆空 → @NotBlank 觸發 MethodArgumentNotValidException
        String body = "{\"name\":\"\",\"email\":\"bad\",\"channel\":\"\",\"jobTitle\":\"\"}";
        mvc.perform(post("/api/v1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("查詢清單回分頁信封")
    void list_returnsPageEnvelope() throws Exception {
        PageResponse<InvitationResponse> page = new PageResponse<>(
                List.of(resp(1L, ContactStatus.EMAIL_CONTACT)), 0, 20, 1, 1);
        when(invitationService.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mvc.perform(get("/api/v1/invitations").param("status", "EMAIL_CONTACT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    @DisplayName("防重複檢查回結果")
    void checkDuplicate_returnsResult() throws Exception {
        when(invitationService.checkDuplicate(any(), any(), any()))
                .thenReturn(new DuplicateCheckResponse(true, 9L, LocalDateTime.now()));
        mvc.perform(get("/api/v1/invitations/check-duplicate").param("name", "王小明"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(true))
                .andExpect(jsonPath("$.data.existingInvitationId").value(9));
    }

    @Test
    @DisplayName("查詢單筆：查無回 404 並帶錯誤信封")
    void get_notFound_returns404() throws Exception {
        when(invitationService.get(99L)).thenThrow(BusinessException.notFound("邀約紀錄", 99L));
        mvc.perform(get("/api/v1/invitations/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("業務例外以正確狀態碼與平鋪 extras 輸出")
    void businessException_mapsStatusAndFlattensExtras() throws Exception {
        when(invitationService.sendInvite(1L)).thenThrow(
                new BusinessException(ErrorCode.DUPLICATE_INVITE_WITHIN_3M).with("existingInvitationId", 7L));
        mvc.perform(post("/api/v1/invitations/1/send-invite"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_INVITE_WITHIN_3M"))
                .andExpect(jsonPath("$.error.existingInvitationId").value(7));
    }

    @Test
    @DisplayName("非預期例外回 500 INTERNAL_ERROR")
    void unexpectedException_returns500() throws Exception {
        when(invitationService.get(1L)).thenThrow(new RuntimeException("boom"));
        mvc.perform(get("/api/v1/invitations/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("欄位級更新委派 service 並回 success")
    void patch_delegatesToService() throws Exception {
        when(invitationService.patch(eq(1L), any())).thenReturn(resp(1L, ContactStatus.EMAIL_CONTACT));
        mvc.perform(patch("/api/v1/invitations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"李四\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("二次邀約端點")
    void secondInvite_endpoint() throws Exception {
        when(invitationService.secondInvite(1L)).thenReturn(resp(1L, ContactStatus.SECOND_INVITE_PENDING));
        mvc.perform(post("/api/v1/invitations/1/second-invite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactStatus").value("SECOND_INVITE_PENDING"));
    }

    @Test
    @DisplayName("回覆有意願端點：可無 body")
    void replyReceived_endpoint_noBodyAllowed() throws Exception {
        when(invitationService.replyReceived(eq(1L), any())).thenReturn(resp(1L, ContactStatus.COMMUNICATING));
        mvc.perform(post("/api/v1/invitations/1/reply-received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactStatus").value("COMMUNICATING"));
    }

    @Test
    @DisplayName("協調時間與確認面試端點")
    void confirmTime_and_confirmInterview_endpoints() throws Exception {
        when(invitationService.confirmTime(1L)).thenReturn(resp(1L, ContactStatus.TIME_CONFIRMING));
        when(invitationService.confirmInterview(1L)).thenReturn(resp(1L, ContactStatus.INTERVIEW_CONFIRMED));
        mvc.perform(post("/api/v1/invitations/1/confirm-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactStatus").value("TIME_CONFIRMING"));
        mvc.perform(post("/api/v1/invitations/1/confirm-interview"))
                .andExpect(jsonPath("$.data.contactStatus").value("INTERVIEW_CONFIRMED"));
    }

    @Test
    @DisplayName("婉拒端點：原因必填時可通過")
    void decline_endpoint_withReason() throws Exception {
        when(invitationService.decline(eq(1L), any())).thenReturn(resp(1L, ContactStatus.DECLINED));
        mvc.perform(post("/api/v1/invitations/1/decline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new DeclineRequest("薪資不符"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactStatus").value("DECLINED"));
    }

    @Test
    @DisplayName("婉拒端點：缺原因回 400")
    void decline_missingReason_returns400() throws Exception {
        mvc.perform(post("/api/v1/invitations/1/decline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("黑名單與設定結果端點")
    void blacklist_and_result_endpoints() throws Exception {
        when(invitationService.blacklist(1L)).thenReturn(resp(1L, ContactStatus.BLACKLIST));
        when(invitationService.result(eq(1L), any())).thenReturn(resp(1L, ContactStatus.OFFER_ACCEPTED));
        mvc.perform(post("/api/v1/invitations/1/blacklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactStatus").value("BLACKLIST"));
        mvc.perform(post("/api/v1/invitations/1/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ResultRequest(ContactStatus.OFFER_ACCEPTED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactStatus").value("OFFER_ACCEPTED"));
    }

    @Test
    @DisplayName("寄推薦信端點回 success 且無 data")
    void sendRecommendation_endpoint_successNoData() throws Exception {
        mvc.perform(post("/api/v1/invitations/1/send-recommendation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(mailService).sendRecommendation(1L);
    }
}
