package com.payment.point.api.history;

import com.payment.point.domain.transaction.TxType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 포인트 거래 이력 조회 응답
 *
 * @param memberId 회원아이디
 * @param histories 거래 이력 목록
 */
public record HistoryResponse(
        String memberId,
        List<Item> histories
) {

    /**
     * 포인트 거래 이력
     *
     * @param pointTransactionNo 포인트 거래번호
     * @param orignalPointTransactionNo 원 포인트 거래번호
     * @param orderNo 클라이언트 주문번호
     * @param orderDtm 클라이언트 주문/요청 시각
     * @param txType 거래 유형
     * @param amount 거래 금액
     * @param remainingAmount 거래 후 회원 총 잔액
     * @param expireAt 거래 당시 만료일 스냅샷
     * @param createdAt 서버 생성 시각
     */
    public record Item(
            String pointTransactionNo,
            String orignalPointTransactionNo,
            String orderNo,
            LocalDateTime orderDtm,
            TxType txType,
            long amount,
            long remainingAmount,
            LocalDateTime expireAt,
            LocalDateTime createdAt
    ) {
    }
}
