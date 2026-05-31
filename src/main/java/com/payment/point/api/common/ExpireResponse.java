package com.payment.point.api.common;

/**
 * 포인트 만료 처리 응답
 *
 * @param expiredCount 만료 처리된 적립 원장 수
 * @param expiredAmount 만료 처리된 포인트 금액 합계
 */
public record ExpireResponse(
        long expiredCount,
        long expiredAmount
) {
}
