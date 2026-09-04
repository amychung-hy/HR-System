package com.hytech.recruitment.service;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.enums.ContactStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.hytech.recruitment.domain.enums.ContactStatus.COMMUNICATING;
import static com.hytech.recruitment.domain.enums.ContactStatus.DECLINED;
import static com.hytech.recruitment.domain.enums.ContactStatus.EMAIL_CONTACT;
import static com.hytech.recruitment.domain.enums.ContactStatus.INTERVIEW_CONFIRMED;
import static com.hytech.recruitment.domain.enums.ContactStatus.SECOND_INVITE_PENDING;
import static com.hytech.recruitment.domain.enums.ContactStatus.TIME_CONFIRMING;

/**
 * 聯繫狀態機：集中定義「主線」的合法流轉（至確認面試為止，以及中途婉拒），違反則丟 ILLEGAL_STATE_TRANSITION（422）。
 * <p>確認面試後的面試結果與 HR 編輯結果（OFFER_EXTENDED / OFFER_ACCEPTED / THANKS_LETTER / DECLINED / BLACKLIST
 * 之間的設定與互轉）不走本狀態機，改由 {@code InvitationService.applyResult} 以自身規則把關。</p>
 */
@Component
public class StatusMachine {

    private final Map<ContactStatus, Set<ContactStatus>> allowed = new EnumMap<>(ContactStatus.class);

    public StatusMachine() {
        allowed.put(EMAIL_CONTACT, EnumSet.of(SECOND_INVITE_PENDING, COMMUNICATING));
        allowed.put(SECOND_INVITE_PENDING, EnumSet.of(COMMUNICATING, DECLINED));
        allowed.put(COMMUNICATING, EnumSet.of(TIME_CONFIRMING, DECLINED));
        allowed.put(TIME_CONFIRMING, EnumSet.of(INTERVIEW_CONFIRMED, DECLINED));
        // INTERVIEW_CONFIRMED 之後的結果流轉由 applyResult 處理，本狀態機不再列出。
        allowed.put(INTERVIEW_CONFIRMED, EnumSet.noneOf(ContactStatus.class));
    }

    public boolean canTransition(ContactStatus from, ContactStatus to) {
        return allowed.getOrDefault(from, EnumSet.noneOf(ContactStatus.class)).contains(to);
    }

    /** 檢查並在非法時丟例外。 */
    public void assertTransition(ContactStatus from, ContactStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "不可由 " + from + "（" + from.getLabel() + "）轉為 "
                            + to + "（" + to.getLabel() + "）")
                    .with("from", from.name())
                    .with("to", to.name());
        }
    }
}
