package com.hytech.recruitment.controller;

import com.hytech.recruitment.common.ApiResponse;
import com.hytech.recruitment.domain.enums.ContactStatus;
import com.hytech.recruitment.dto.request.CreateInvitationRequest;
import com.hytech.recruitment.dto.request.DeclineRequest;
import com.hytech.recruitment.dto.request.PatchInvitationRequest;
import com.hytech.recruitment.dto.request.ReplyReceivedRequest;
import com.hytech.recruitment.dto.request.ResultRequest;
import com.hytech.recruitment.dto.request.TriggerMailRequest;
import com.hytech.recruitment.dto.response.DuplicateCheckResponse;
import com.hytech.recruitment.dto.response.InvitationResponse;
import com.hytech.recruitment.dto.response.MailRecordResponse;
import com.hytech.recruitment.dto.response.PageResponse;
import com.hytech.recruitment.service.InvitationService;
import com.hytech.recruitment.service.MailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邀約紀錄與狀態流轉端點。Base：/api/v1。
 * <p>Spring IoC 一律 Field Injection。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class InvitationController {

    @Autowired
    private InvitationService invitationService;
    @Autowired
    private MailService mailService;

    // ---------- 邀約 CRUD / 查詢 ----------

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvitationResponse> create(@Valid @RequestBody CreateInvitationRequest req) {
        return ApiResponse.ok(invitationService.create(req));
    }

    @GetMapping("/invitations")
    public ApiResponse<PageResponse<InvitationResponse>> list(
            @RequestParam(required = false) ContactStatus status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(invitationService.list(status, channel, keyword, page, size));
    }

    @GetMapping("/invitations/check-duplicate")
    public ApiResponse<DuplicateCheckResponse> checkDuplicate(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        return ApiResponse.ok(invitationService.checkDuplicate(name, email, phone));
    }

    @GetMapping("/invitations/{id}")
    public ApiResponse<InvitationResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(invitationService.get(id));
    }

    @PatchMapping("/invitations/{id}")
    public ApiResponse<InvitationResponse> patch(@PathVariable Long id,
                                                 @RequestBody PatchInvitationRequest req) {
        return ApiResponse.ok(invitationService.patch(id, req));
    }

    // ---------- 狀態流轉（動作型）----------

    @PostMapping("/invitations/{id}/send-invite")
    public ApiResponse<InvitationResponse> sendInvite(@PathVariable Long id) {
        return ApiResponse.ok(invitationService.sendInvite(id));
    }

    @PostMapping("/invitations/{id}/second-invite")
    public ApiResponse<InvitationResponse> secondInvite(@PathVariable Long id) {
        return ApiResponse.ok(invitationService.secondInvite(id));
    }

    @PostMapping("/invitations/{id}/reply-received")
    public ApiResponse<InvitationResponse> replyReceived(@PathVariable Long id,
                                                         @RequestBody(required = false) ReplyReceivedRequest req) {
        return ApiResponse.ok(invitationService.replyReceived(id, req));
    }

    @PostMapping("/invitations/{id}/confirm-time")
    public ApiResponse<InvitationResponse> confirmTime(@PathVariable Long id) {
        return ApiResponse.ok(invitationService.confirmTime(id));
    }

    @PostMapping("/invitations/{id}/confirm-interview")
    public ApiResponse<InvitationResponse> confirmInterview(@PathVariable Long id) {
        return ApiResponse.ok(invitationService.confirmInterview(id));
    }

    @PostMapping("/invitations/{id}/decline")
    public ApiResponse<InvitationResponse> decline(@PathVariable Long id,
                                                   @Valid @RequestBody DeclineRequest req) {
        return ApiResponse.ok(invitationService.decline(id, req));
    }

    @PostMapping("/invitations/{id}/blacklist")
    public ApiResponse<InvitationResponse> blacklist(@PathVariable Long id) {
        return ApiResponse.ok(invitationService.blacklist(id));
    }

    @PostMapping("/invitations/{id}/result")
    public ApiResponse<InvitationResponse> result(@PathVariable Long id,
                                                  @Valid @RequestBody ResultRequest req) {
        return ApiResponse.ok(invitationService.result(id, req));
    }

    // ---------- 寄信相關（掛在 invitation 下）----------

    @PostMapping("/invitations/{id}/trigger-mail")
    public ApiResponse<MailRecordResponse> triggerMail(@PathVariable Long id,
                                                       @Valid @RequestBody TriggerMailRequest req) {
        return ApiResponse.ok(mailService.triggerMail(id, req));
    }

    @PostMapping("/invitations/{id}/send-recommendation")
    public ApiResponse<Void> sendRecommendation(@PathVariable Long id) {
        mailService.sendRecommendation(id);
        return ApiResponse.ok(null);
    }
}
