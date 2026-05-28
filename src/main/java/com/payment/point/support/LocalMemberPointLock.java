package com.payment.point.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.payment.point.config.PointPolicyProperties;
import org.springframework.stereotype.Component;

/**
 * Caffeine 로컬 캐시 기반 회원 단위 포인트 거래 요청 락.
 *
 * <p>단일 애플리케이션 인스턴스에서만 유효하며, TTL을 통해 비정상 상황의 영구 락을 방지한다.
 * 다중 인스턴스 환경에서는 Redis 등 분산 락으로 교체해야 한다.</p>
 */
@Component
public class LocalMemberPointLock implements MemberPointLock {

    private final Cache<String, Boolean> processingMemberIds;

    public LocalMemberPointLock(PointPolicyProperties pointPolicyProperties) {
        this.processingMemberIds = Caffeine.newBuilder()
                .expireAfterWrite(pointPolicyProperties.lock().ttl())
                .build();
    }

    /**현
     * 회원 단위 락을 획득한다.
     *
     * @param memberId 회원 식별자
     * @return 락 해제 handle
     */
    @Override
    public LockHandle acquire(String memberId) {
        Boolean previous = processingMemberIds.asMap().putIfAbsent(memberId, Boolean.TRUE);
        if (previous != null) {
            throw new ApiException(ErrorCode.POINT_PROCESSING);
        }
        return () -> processingMemberIds.invalidate(memberId);
    }
}
