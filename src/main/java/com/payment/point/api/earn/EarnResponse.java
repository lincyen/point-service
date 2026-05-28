package com.payment.point.api.earn;

/**
 * 포인트 적립 응답 DTO.
 *
 * @param ptxno 생성된 포인트 적립 거래번호
 * @param memberId 회원 식별자
 * @param amount 적립 금액
 * @param remainingAmount 적립 후 회원 총 잔액
 */
public record EarnResponse(
        String ptxno,
        String memberId,
        long amount,
        long remainingAmount
) {
}
