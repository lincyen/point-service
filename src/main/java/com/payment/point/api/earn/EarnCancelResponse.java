package com.payment.point.api.earn;

/**
 * 포인트 적립취소 응답
 *
 * @param ptxno 생성된 포인트 적립취소 거래번호
 * @param memberId 회원아이디
 * @param amount 적립취소 금액
 * @param remainingAmount 적립취소 후 회원 총 잔액
 */
public record EarnCancelResponse(
        String ptxno,
        String memberId,
        long amount,
        long remainingAmount
) {
}
