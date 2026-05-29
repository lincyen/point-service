package com.payment.point.domain.use;

/**
 * 사용취소 시 포인트 복원 유형.
 */
public enum RestoreType {
    /** 만료 전 원 적립건으로 복원 */
    ORIGINAL_RESTORE,
    /** 만료 후 신규 RESTORE 적립 생성 */
    NEW_EARN
}
