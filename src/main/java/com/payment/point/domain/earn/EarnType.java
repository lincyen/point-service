package com.payment.point.domain.earn;

/**
 * 포인트 적립 유형.
 */
public enum EarnType {
    /** 일반 적립 포인트 */
    NORMAL,
    /** 관리자 수기 지급 포인트 */
    MANUAL,
    /** 만료된 원 적립 건의 사용취소로 새로 생성된 복원 포인트 */
    RESTORE
}
