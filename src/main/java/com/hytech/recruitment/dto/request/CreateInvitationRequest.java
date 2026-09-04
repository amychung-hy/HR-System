package com.hytech.recruitment.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 建立邀約紀錄。狀態初始為 EMAIL_CONTACT。
 * 性別／上機試題類型／面試地點屬「基本資料」，已移置面試安排由 HR 手動填，故不在此。
 * inviter＝邀請人（英文名.英文姓氏），由 HR 於新增求職者時自行填寫；未做登入，留空則帶預設值。
 */
public record CreateInvitationRequest(
        @NotBlank(message = "姓名必填") String name,
        @NotBlank(message = "Email 必填") @Email(message = "Email 格式錯誤") String email,
        String phone,
        String resumeNo,
        @NotBlank(message = "招募管道必填") String channel,
        @NotBlank(message = "職位名稱必填") String jobTitle,
        String inviter
) {
}
