package com.hytech.recruitment.common;

import org.springframework.http.HttpStatus;

/** 統一錯誤碼與對應 HTTP 狀態。 */
public enum ErrorCode {

    DUPLICATE_INVITE_WITHIN_3M(HttpStatus.CONFLICT, "此人 90 天內已發送一面邀請"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "非法狀態轉移"),
    FIELD_NOT_WRITABLE_BY_ROLE(HttpStatus.FORBIDDEN, "此欄位不允許該角色寫入"),
    TEMPLATE_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "查無信件範本"),
    MAIL_REQUIRED_FIELDS_MISSING(HttpStatus.UNPROCESSABLE_ENTITY, "寄信必填欄位缺漏"),
    MANAGER_SELECTION_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "多位面試主管須先由 HR 選定一位"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "資源不存在"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "參數驗證失敗");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
