package com.hytech.recruitment.controller;

import com.hytech.recruitment.common.ApiResponse;
import com.hytech.recruitment.dto.response.MailRecordResponse;
import com.hytech.recruitment.dto.response.MailTemplateResponse;
import com.hytech.recruitment.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 寄信結果與範本查詢端點。Base：/api/v1。
 */
@RestController
@RequestMapping("/api/v1")
public class MailController {

    @Autowired
    private MailService mailService;

    /** 查寄信結果（依 invitationId）。 */
    @GetMapping("/mail-records")
    public ApiResponse<MailRecordResponse> getMailRecord(@RequestParam Long invitationId) {
        return ApiResponse.ok(mailService.getByInvitationId(invitationId));
    }

    /** 範本清單／查詢（examType, location, jobTitle）。 */
    @GetMapping("/mail-templates")
    public ApiResponse<List<MailTemplateResponse>> listTemplates(
            @RequestParam(required = false) String examType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String jobTitle) {
        return ApiResponse.ok(mailService.listTemplates(examType, location, jobTitle));
    }
}
