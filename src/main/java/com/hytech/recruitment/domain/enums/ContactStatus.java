package com.hytech.recruitment.domain.enums;

/**
 * 聯繫狀態（狀態機）。
 * 正常主線：EMAIL_CONTACT(藍) → SECOND_INVITE_PENDING(淺灰) → COMMUNICATING(綠)
 * → TIME_CONFIRMING(橘) → INTERVIEW_CONFIRMED(紅) → 面試結果。
 * <p>面試結果：確認面試後 → OFFER_EXTENDED(錄取人選) / THANKS_LETTER(感謝函) / BLACKLIST(黑名單，無故缺席)；
 * OFFER_EXTENDED → OFFER_ACCEPTED(接受聘約) / DECLINED(婉拒，人選拒絕聘約)。</p>
 * <p>終止狀態：OFFER_ACCEPTED / THANKS_LETTER / BLACKLIST / DECLINED。
 * OFFER_EXTENDED 非終止（尚待人選回覆）。HR 可於確認面試後於五個結果狀態間手動調整（編輯結果）。</p>
 */
public enum ContactStatus {
    EMAIL_CONTACT("信件聯繫"),
    SECOND_INVITE_PENDING("二次邀約未回覆"),
    COMMUNICATING("信件溝通中"),
    TIME_CONFIRMING("時間確認中"),
    INTERVIEW_CONFIRMED("確認面試"),
    OFFER_EXTENDED("錄取人選"),
    OFFER_ACCEPTED("接受聘約"),
    THANKS_LETTER("感謝函"),
    BLACKLIST("黑名單"),
    DECLINED("婉拒");

    private final String label;

    ContactStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 終止狀態不可再進入正常主線流轉（OFFER_EXTENDED 尚待回覆，非終止）。 */
    public boolean isTerminal() {
        return this == OFFER_ACCEPTED || this == THANKS_LETTER || this == BLACKLIST || this == DECLINED;
    }
}
