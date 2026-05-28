package com.payment.point.domain.earn;

/**
 * 적립 원장 상태.
 */
public enum EarnStatus {
    /** 사용 가능한 적립 원장 상태 */
    ACTIVE,
    /** 전액 사용되어 남은 금액이 없는 상태 */
    USED_UP,
    /** 적립취소가 완료된 상태 */
    CNCL,
    /** 남은 금액이 만료 처리된 상태 */
    EXPIRED
}
