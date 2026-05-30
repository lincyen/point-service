package com.payment.point.support;

/**
 * 회원 단위 포인트 거래 요청 락.
 *
 * <p>동일 회원의 포인트 금액 변경 요청이 동시에 처리되지 않도록 진입 지점을 제한한다.</p>
 */
public interface MemberPointLock {

    /**
     * 회원 단위 락을 획득한다.
     *
     * @param memberId 회원아이디
     * @return 락 해제를 담당하는 handle
     */
    LockHandle acquire(String memberId);

    /**
     * 획득한 락을 해제하는 handle.
     */
    interface LockHandle extends AutoCloseable {

        @Override
        void close();
    }
}
