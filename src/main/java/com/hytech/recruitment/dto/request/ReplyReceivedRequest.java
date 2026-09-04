package com.hytech.recruitment.dto.request;

/**
 * 人選回覆有意願／主動投遞 → COMMUNICATING。
 * 可帶履歷連結供主管檢視（建立面試安排時單向帶入）。
 */
public record ReplyReceivedRequest(
        String resumeLink
) {
}
