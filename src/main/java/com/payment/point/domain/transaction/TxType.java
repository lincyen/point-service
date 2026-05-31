package com.payment.point.domain.transaction;

/**
 * 포인트 거래 유형
 */
public enum TxType {
    /** 포인트 적립 거래 */
    EARN,
    /** 포인트 적립취소 거래 */
    EARN_CNCL,
    /** 포인트 사용 거래 */
    USE,
    /** 포인트 사용취소 거래 */
    USE_CNCL,
    /** 포인트 만료 거래 */
    EXPIRE
}
