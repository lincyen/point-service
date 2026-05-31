package com.payment.point.api.expire;

import java.time.LocalDate;

/**
 * 포인트 만료 처리 요청
 *
 * @param baseDate 만료 기준일, null이면 서버 현재일 사용
 */
public record ExpireRequest(
        LocalDate baseDate
) {
}
