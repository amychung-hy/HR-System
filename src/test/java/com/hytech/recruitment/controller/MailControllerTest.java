package com.hytech.recruitment.controller;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.domain.entity.MailSendRecord;
import com.hytech.recruitment.domain.entity.MailTemplate;
import com.hytech.recruitment.domain.enums.MailSendStatus;
import com.hytech.recruitment.dto.response.MailRecordResponse;
import com.hytech.recruitment.dto.response.MailTemplateResponse;
import com.hytech.recruitment.service.MailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 寄信結果與範本查詢端點 MockMvc 測試。 */
@DisplayName("寄信結果與範本查詢端點")
@WebMvcTest(MailController.class)
class MailControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MailService mailService;

    @Test
    @DisplayName("查詢寄信結果")
    void getMailRecord_returnsResult() throws Exception {
        MailSendRecord m = new MailSendRecord();
        m.setId(10L);
        m.setInvitationId(1L);
        m.setEmail("ming@x.com");
        m.setStatus(MailSendStatus.SUCCESS);
        when(mailService.getByInvitationId(1L)).thenReturn(MailRecordResponse.from(m));

        mvc.perform(get("/api/v1/mail-records").param("invitationId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.email").value("ming@x.com"));
    }

    @Test
    @DisplayName("查詢寄信結果：查無回 404")
    void getMailRecord_notFound_returns404() throws Exception {
        when(mailService.getByInvitationId(2L)).thenThrow(BusinessException.notFound("寄信結果", 2L));
        mvc.perform(get("/api/v1/mail-records").param("invitationId", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("查詢範本清單")
    void listTemplates_returnsList() throws Exception {
        MailTemplate t = new MailTemplate();
        t.setId(1L);
        t.setExamType("基本");
        t.setLocation("南港");
        t.setJobTitle("後端工程師");
        t.setBody("您好 {{姓氏}}");
        when(mailService.listTemplates(any(), any(), any()))
                .thenReturn(List.of(MailTemplateResponse.from(t)));

        mvc.perform(get("/api/v1/mail-templates").param("examType", "基本"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].jobTitle").value("後端工程師"))
                .andExpect(jsonPath("$.data[0].body").value("您好 {{姓氏}}"));
    }
}
