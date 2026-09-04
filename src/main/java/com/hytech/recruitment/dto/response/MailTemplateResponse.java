package com.hytech.recruitment.dto.response;

import com.hytech.recruitment.domain.entity.MailTemplate;

/** 信件範本輸出。 */
public record MailTemplateResponse(
        Long id,
        String examType,
        String location,
        String jobTitle,
        String body
) {
    public static MailTemplateResponse from(MailTemplate t) {
        return new MailTemplateResponse(
                t.getId(), t.getExamType(), t.getLocation(), t.getJobTitle(), t.getBody());
    }
}
