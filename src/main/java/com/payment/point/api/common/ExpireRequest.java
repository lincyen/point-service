package com.payment.point.api.common;

import java.time.LocalDateTime;

/**
 * 포인트 만료 처리 요청 DTO.
 *
 * @param baseDtm 만료 기준 시각, null이면 서버 현재 시각 사용
 */
public record ExpireRequest(
        LocalDateTime baseDtm
) {
}
