package com.payment.point.support;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INVALID_PARAMETER("요청 파라미터가 올바르지 않습니다."),
    INVALID_USER("유효하지 않은 회원입니다."),
    NOT_ENOUGH_POINT("포인트 잔액이 부족합니다."),
    INCORRECT_POINT("포인트 금액이 올바르지 않습니다."),
    ALREADY_CANCELED("이미 취소된 거래입니다."),
    NO_POINT_HISTORY("포인트 거래 이력을 찾을 수 없습니다."),
    PARTIAL_CANCEL_FAIL("부분취소를 처리할 수 없습니다."),
    TIMEOUT("요청 시간이 초과되었습니다."),
    SYSTEM_ERROR("시스템 오류가 발생했습니다."),
    DUPLICATED_ORDER("이미 처리된 주문번호입니다."),
    NO_REMAIN_POINT("취소 가능한 잔여 포인트가 없습니다.");

    ErrorCode(String message) {
        this.message = message;
    }

    private final String message;
}
