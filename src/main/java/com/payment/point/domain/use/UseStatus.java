package com.payment.point.domain.use;

/**
 * 사용 원장 상태.
 */
public enum UseStatus {
    /** 취소 가능한 금액이 남아 있는 사용 거래 상태 */
    ACTIVE,
    /** 일부 사용취소가 처리된 상태 */
    PARTIAL_CNCL,
    /** 전체 사용취소가 완료된 상태 */
    CNCL
}
