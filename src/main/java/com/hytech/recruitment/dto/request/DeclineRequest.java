package com.hytech.recruitment.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 婉拒 → DECLINED，帶婉拒原因。 */
public record DeclineRequest(
        @NotBlank(message = "婉拒原因必填") String declineReason
) {
}
