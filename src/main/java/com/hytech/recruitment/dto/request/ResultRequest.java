package com.hytech.recruitment.dto.request;

import com.hytech.recruitment.domain.enums.ContactStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 設定面試結果／HR 編輯結果。
 * result 僅能為五個結果狀態之一：OFFER_EXTENDED(錄取人選)／OFFER_ACCEPTED(接受聘約)／
 * THANKS_LETTER(感謝函)／DECLINED(婉拒)／BLACKLIST(黑名單)。
 * reason 為選填：僅於 DECLINED／BLACKLIST 時採用，寫入婉拒原因欄。
 */
public record ResultRequest(
        @NotNull(message = "result 必填（錄取人選／接受聘約／感謝函／婉拒／黑名單）") ContactStatus result,
        String reason
) {
}
