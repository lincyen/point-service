package com.payment.point.support;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 회원 단위 포인트 거래 요청 락 Aspect.
 *
 * <p>트랜잭션 Aspect보다 먼저 실행되어 memberId 기준 로컬 락을 획득한 뒤 비즈니스 메서드를 진행한다.</p>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MemberPointLockAspect {

    private final MemberPointLock memberPointLock;

    public MemberPointLockAspect(MemberPointLock memberPointLock) {
        this.memberPointLock = memberPointLock;
    }

    /**
     * {@link MemberPointLocked}가 선언된 메서드에 회원 단위 락을 적용한다.
     *
     * @param joinPoint 대상 메서드 join point
     * @return 대상 메서드 실행 결과
     * @throws Throwable 대상 메서드 또는 락 처리 중 발생한 예외
     */
    @Around("@annotation(com.payment.point.support.MemberPointLocked)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        String memberId = (String) joinPoint.getArgs()[0];
        try (MemberPointLock.LockHandle ignored = memberPointLock.acquire(memberId)) {
            return joinPoint.proceed();
        }
    }
}
