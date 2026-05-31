package com.payment.point.api.use;

/**
 * 포인트 사용 응답
 *
 * @param pointTransactionNo 생성된 포인트 사용 거래번호
 * @param memberId 회원아이디
 * @param amount 사용 금액
 * @param remainingAmount 사용 후 회원 총 잔액
 */
public record UseResponse(
        String pointTransactionNo,
        String memberId,
        long amount,
        long remainingAmount
) {
}
