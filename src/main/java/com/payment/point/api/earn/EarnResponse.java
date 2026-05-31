package com.payment.point.api.earn;

/**
 * 포인트 적립 응답
 *
 * @param pointTransactionNo 생성된 포인트 적립 거래번호
 * @param memberId 회원아이디
 * @param amount 적립 금액
 * @param remainingAmount 적립 후 회원 총 잔액
 */
public record EarnResponse(
        String pointTransactionNo,
        String memberId,
        long amount,
        long remainingAmount
) {
}
