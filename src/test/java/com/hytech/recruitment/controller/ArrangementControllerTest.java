package com.hytech.recruitment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.entity.InterviewArrangement;
import com.hytech.recruitment.domain.enums.Gender;
import com.hytech.recruitment.dto.response.ArrangementResponse;
import com.hytech.recruitment.service.InterviewArrangementService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 面試安排端點 MockMvc 測試。 */
@DisplayName("面試安排端點")
@WebMvcTest(ArrangementController.class)
class ArrangementControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @MockBean
    private InterviewArrangementService arrangementService;

    private ArrangementResponse arr(Long id, Gender gender) {
        InterviewArrangement a = new InterviewArrangement();
        a.setId(id);
        a.setInvitationId(id);
        a.setCandidateName("王小明");
        a.setGender(gender);
        a.setExamType("基本");
        a.setLocation("南港");
        return ArrangementResponse.from(a);
    }

    @Test
    @DisplayName("查詢清單回清單信封")
    void list_returnsListEnvelope() throws Exception {
        when(arrangementService.list(any())).thenReturn(List.of(arr(1L, Gender.MALE)));
        mvc.perform(get("/api/v1/arrangements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].examType").value("基本"));
    }

    @Test
    @DisplayName("查詢單筆")
    void get_returnsSingle() throws Exception {
        when(arrangementService.get(1L)).thenReturn(arr(1L, Gender.FEMALE));
        mvc.perform(get("/api/v1/arrangements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gender").value("FEMALE"));
    }

    @Test
    @DisplayName("主管填非白名單欄位回 403")
    void managerFields_notWhitelisted_returns403() throws Exception {
        when(arrangementService.updateManagerFields(eq(1L), any())).thenThrow(
                new BusinessException(ErrorCode.FIELD_NOT_WRITABLE_BY_ROLE, "主管不可寫入欄位：gender")
                        .with("field", "gender"));
        mvc.perform(patch("/api/v1/arrangements/1/manager-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gender\":\"MALE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FIELD_NOT_WRITABLE_BY_ROLE"))
                .andExpect(jsonPath("$.error.field").value("gender"));
    }

    @Test
    @DisplayName("排面試時間：預設 sendMail=true 會嘗試觸發寄信")
    void interviewTime_defaultSendMail_triggersMail() throws Exception {
        when(arrangementService.get(1L)).thenReturn(arr(1L, Gender.MALE));
        String body = json.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("interviewTime", LocalDateTime.now().plusDays(3).toString());
            put("gender", "MALE");
            put("examType", "基本");
            put("location", "南港");
        }});

        mvc.perform(patch("/api/v1/arrangements/1/interview-time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(arrangementService).updateInterviewTime(eq(1L), any());
        verify(arrangementService).autoTriggerMailIfReady(1L);
    }

    @Test
    @DisplayName("排面試時間：sendMail=false 僅儲存不觸發寄信")
    void interviewTime_sendMailFalse_savesOnly() throws Exception {
        when(arrangementService.get(1L)).thenReturn(arr(1L, Gender.MALE));
        mvc.perform(patch("/api/v1/arrangements/1/interview-time")
                        .param("sendMail", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"examType\":\"基本\"}"))
                .andExpect(status().isOk());

        verify(arrangementService).updateInterviewTime(eq(1L), any());
        verify(arrangementService, never()).autoTriggerMailIfReady(any());
    }

    @Test
    @DisplayName("排面試時間：過去時間回 400")
    void interviewTime_pastTime_returns400() throws Exception {
        String body = "{\"interviewTime\":\"2000-01-01T10:00:00\"}";
        mvc.perform(patch("/api/v1/arrangements/1/interview-time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(arrangementService, never()).updateInterviewTime(any(), any());
    }
}
