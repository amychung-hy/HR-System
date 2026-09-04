package com.hytech.recruitment.service;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.enums.ContactStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.hytech.recruitment.domain.enums.ContactStatus.COMMUNICATING;
import static com.hytech.recruitment.domain.enums.ContactStatus.DECLINED;
import static com.hytech.recruitment.domain.enums.ContactStatus.EMAIL_CONTACT;
import static com.hytech.recruitment.domain.enums.ContactStatus.INTERVIEW_CONFIRMED;
import static com.hytech.recruitment.domain.enums.ContactStatus.OFFER_EXTENDED;
import static com.hytech.recruitment.domain.enums.ContactStatus.SECOND_INVITE_PENDING;
import static com.hytech.recruitment.domain.enums.ContactStatus.TIME_CONFIRMING;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 聯繫狀態機主線流轉單元測試。 */
@DisplayName("聯繫狀態機主線流轉")
class StatusMachineTest {

    private final StatusMachine machine = new StatusMachine();

    @Test
    @DisplayName("合法主線流轉皆可通過")
    void legalMainlineTransitions_allPass() {
        assertTrue(machine.canTransition(EMAIL_CONTACT, SECOND_INVITE_PENDING));
        assertTrue(machine.canTransition(EMAIL_CONTACT, COMMUNICATING));
        assertTrue(machine.canTransition(SECOND_INVITE_PENDING, COMMUNICATING));
        assertTrue(machine.canTransition(SECOND_INVITE_PENDING, DECLINED));
        assertTrue(machine.canTransition(COMMUNICATING, TIME_CONFIRMING));
        assertTrue(machine.canTransition(COMMUNICATING, DECLINED));
        assertTrue(machine.canTransition(TIME_CONFIRMING, INTERVIEW_CONFIRMED));
        assertTrue(machine.canTransition(TIME_CONFIRMING, DECLINED));
    }

    @Test
    @DisplayName("非法流轉回傳 false")
    void illegalTransitions_returnFalse() {
        assertFalse(machine.canTransition(EMAIL_CONTACT, INTERVIEW_CONFIRMED));
        assertFalse(machine.canTransition(EMAIL_CONTACT, TIME_CONFIRMING));
        assertFalse(machine.canTransition(INTERVIEW_CONFIRMED, OFFER_EXTENDED));
        assertFalse(machine.canTransition(COMMUNICATING, EMAIL_CONTACT));
    }

    @Test
    @DisplayName("確認面試後無主線可走")
    void afterConfirmed_noMainlineTransition() {
        for (ContactStatus to : ContactStatus.values()) {
            assertFalse(machine.canTransition(INTERVIEW_CONFIRMED, to),
                    "INTERVIEW_CONFIRMED 不應有任何主線流轉：" + to);
        }
    }

    @Test
    @DisplayName("assertTransition 合法時不丟例外")
    void assertTransition_legal_doesNotThrow() {
        assertDoesNotThrow(() -> machine.assertTransition(COMMUNICATING, TIME_CONFIRMING));
    }

    @Test
    @DisplayName("assertTransition 非法時丟 ILLEGAL_STATE_TRANSITION")
    void assertTransition_illegal_throwsIllegalState() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> machine.assertTransition(EMAIL_CONTACT, INTERVIEW_CONFIRMED));
        assertEquals(ErrorCode.ILLEGAL_STATE_TRANSITION, ex.getErrorCode());
        assertEquals(EMAIL_CONTACT.name(), ex.getExtras().get("from"));
        assertEquals(INTERVIEW_CONFIRMED.name(), ex.getExtras().get("to"));
    }
}
