package com.payment.point.api.balance;

/**
 * 포인트 잔액 조회 응답
 *
 * @param memberId 회원아이디
 * @param normalAmount 일반 적립 포인트 잔액
 * @param manualAmount 관리자 수기 지급 포인트 잔액
 * @param expiredAmount 누적 만료 포인트 금액
 * @param totalAmount 사용 가능한 총 포인트 잔액
 */
public record BalanceResponse(
        String memberId,
        long normalAmount,
        long manualAmount,
        long expiredAmount,
        long totalAmount
) {
}
