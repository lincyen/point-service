package com.payment.point.api.history;

import com.payment.point.domain.transaction.TxType;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주문번호 기반 포인트 거래 조회 응답
 *
 * @param memberId 회원아이디
 * @param orderNo 클라이언트 주문번호
 * @param txType 조회 요청 거래 유형 nullable
 * @param exists 거래 존재 여부
 * @param transaction 거래 이력 nullable
 */
public record TransactionLookupResponse(
        String memberId,
        String orderNo,
        TxType txType,
        boolean exists,
        Item transaction
) {

    /**
     * 포인트 거래 이력
     *
     * @param pointTransactionNo 포인트 거래번호
     * @param orignalPointTransactionNo 원 포인트 거래번호
     * @param orderDtm 클라이언트 주문/요청 시각
     * @param txType 거래 유형
     * @param amount 거래 금액
     * @param remainingAmount 거래 후 회원 총 잔액
     * @param expireDate 거래 당시 만료일 스냅샷
     * @param createdAt 서버 생성 시각
     */
    public record Item(
            String pointTransactionNo,
            String orignalPointTransactionNo,
            LocalDateTime orderDtm,
            TxType txType,
            long amount,
            long remainingAmount,
            LocalDate expireDate,
            LocalDateTime createdAt
    ) {
    }
}
