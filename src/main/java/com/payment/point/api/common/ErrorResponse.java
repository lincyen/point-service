package com.payment.point.api.common;

import com.payment.point.support.ErrorCode;

/**
 * API 오류 응답 DTO.
 *
 * @param code 오류 코드
 * @param message 오류 메시지
 */
public record ErrorResponse(
        ErrorCode code,
        String message
) {

    public ErrorResponse(ErrorCode code) {
        this(code, code.getMessage());
    }
}
