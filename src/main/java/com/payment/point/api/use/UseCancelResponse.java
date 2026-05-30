package com.payment.point.api.use;

/**
 * 포인트 사용취소 응답
 *
 * @param ptxno 생성된 포인트 사용취소 거래번호
 * @param memberId 회원아이디
 * @param amount 사용취소 금액
 * @param remainingAmount 사용취소 후 회원 총 잔액
 */
public record UseCancelResponse(
        String ptxno,
        String memberId,
        long amount,
        long remainingAmount
) {
}
