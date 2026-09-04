package com.hytech.recruitment.dto.request;

import com.hytech.recruitment.domain.enums.Gender;
import jakarta.validation.constraints.Future;

import java.time.LocalDateTime;

/**
 * HR 「排面試時間」單一動作（面試者與主管配對成功後）。
 * <p>一併帶入基本資料與選定主管，作為觸發寄信（CC 該主管、發送範本信）的資料來源：</p>
 * <ul>
 *   <li>{@code interviewTime}：一面時間，ISO-8601（例 2026-09-08T10:00:00）；純「儲存」可留空（欄位級更新，
 *       留空不覆寫原值），有值時不可為過去時間；「儲存並發信」前由前端確保已填。</li>
 *   <li>{@code gender}／{@code examType}／{@code location}：基本資料，皆下拉；欄位級更新（只寫非 null 者），
 *       examType 僅「基本／AI」、location 僅「南港／板橋」，非法值 400。</li>
 *   <li>{@code selectedManager}：多位面試主管時 HR 選定的一位（寄信只 CC 該位）；須在面試主管清單內，否則 422。
 *       傳空字串代表「不選定」→ 清除既有選定，寄信一起 CC 全部主管；{@code null} 則不動既有值。</li>
 * </ul>
 * <p>備註改為「主管備註」，由主管於 manager-fields 填寫，HR 不於此帶入。</p>
 */
public record InterviewTimeRequest(
        @Future(message = "interviewTime 不可為過去時間") LocalDateTime interviewTime,
        Gender gender,
        String examType,
        String location,
        String selectedManager
) {
}
