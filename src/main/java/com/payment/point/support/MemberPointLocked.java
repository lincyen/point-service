package com.payment.point.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 회원 단위 포인트 거래 요청 락 적용 어노테이션.
 *
 * <p>첫 번째 메서드 인자가 {@code memberId}인 public 메서드에 적용한다.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MemberPointLocked {
}
