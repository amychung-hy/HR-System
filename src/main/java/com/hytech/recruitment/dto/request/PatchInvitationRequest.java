package com.hytech.recruitment.dto.request;

/**
 * 邀約紀錄欄位級更新：只更新「非 null」的傳入欄位，不整列覆寫。
 * contactStatus 不在此更新（走動作型端點）。
 * 性別／上機試題類型／面試地點已移置面試安排，故不在此。
 */
public record PatchInvitationRequest(
        String name,
        String email,
        String phone,
        String resumeNo,
        String channel,
        String jobTitle
) {
}
