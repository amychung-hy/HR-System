package com.hytech.recruitment.dto.request;

/**
 * 觸發面試通知信參數。
 * 性別／上機試題類型（examType）／面試地點（location）／職稱皆取自「面試安排」（HR 先填），
 * 故此處只餘可選的 formLink（{{面試前資料}} 連結；未給則帶入面試安排的基本資料表連結）。
 */
public record TriggerMailRequest(
        String formLink
) {
}
