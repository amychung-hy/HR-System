package com.hytech.recruitment.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 統一回應信封。
 * 成功：{ "success": true, "data": ... }
 * 失敗：{ "success": false, "error": { "code", "message", ...extras } }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorBody error;

    private ApiResponse(boolean success, T data, ErrorBody error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(String code, String message, Map<String, Object> extras) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, extras));
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public ErrorBody getError() { return error; }

    /** 錯誤內容；extras 以 @JsonAnyGetter 平鋪到 error 物件下。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorBody {
        private final String code;
        private final String message;
        private final Map<String, Object> extras;

        public ErrorBody(String code, String message, Map<String, Object> extras) {
            this.code = code;
            this.message = message;
            this.extras = extras == null ? new LinkedHashMap<>() : extras;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }

        @com.fasterxml.jackson.annotation.JsonAnyGetter
        public Map<String, Object> getExtras() { return extras; }
    }
}
