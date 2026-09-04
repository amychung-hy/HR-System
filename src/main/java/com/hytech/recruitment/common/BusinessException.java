package com.hytech.recruitment.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商業規則例外，攜帶 {@link ErrorCode} 與可選附加欄位
 * （如 existingInvitationId），由 GlobalExceptionHandler 轉為統一錯誤信封。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> extras = new LinkedHashMap<>();

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getExtras() {
        return extras;
    }

    /** 鏈式加入附加欄位。 */
    public BusinessException with(String key, Object value) {
        this.extras.put(key, value);
        return this;
    }

    public static BusinessException notFound(String resource, Object id) {
        return new BusinessException(ErrorCode.NOT_FOUND, resource + " 不存在：id=" + id);
    }
}
