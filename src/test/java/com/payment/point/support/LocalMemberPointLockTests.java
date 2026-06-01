package com.payment.point.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.payment.point.config.PointPolicyProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalMemberPointLockTests {

    @Test
    @DisplayName("실패-동일 회원 락 중복 획득 요청, POINT_PROCESSING")
    void acquireRejectsDuplicatedMemberLock() {
        LocalMemberPointLock lock = new LocalMemberPointLock(properties());

        try (MemberPointLock.LockHandle ignored = lock.acquire("Member123")) {
            ApiException exception = assertThrows(ApiException.class, () -> lock.acquire("Member123"));

            assertEquals(ErrorCode.POINT_PROCESSING, exception.getErrorCode());
        }
    }

    @Test
    @DisplayName("성공-회원 락 명시적 해제 후 재획득")
    void acquireAllowsMemberLockAfterExplicitRelease() {
        LocalMemberPointLock lock = new LocalMemberPointLock(properties());

        lock.acquire("Member123").close();

        assertDoesNotThrow(() -> lock.acquire("Member123").close());
    }

    @Test
    @DisplayName("성공-TTL 만료 후 이전 요청의 해제가 신규 요청 락을 제거하지 않음")
    void expiredLockHandleDoesNotReleaseNewMemberLock() throws InterruptedException {
        LocalMemberPointLock lock = new LocalMemberPointLock(properties(Duration.ofMillis(10)));
        MemberPointLock.LockHandle expiredHandle = lock.acquire("Member123");

        Thread.sleep(50);

        try (MemberPointLock.LockHandle ignored = lock.acquire("Member123")) {
            expiredHandle.close();

            ApiException exception = assertThrows(ApiException.class, () -> lock.acquire("Member123"));
            assertEquals(ErrorCode.POINT_PROCESSING, exception.getErrorCode());
        }
    }

    private PointPolicyProperties properties() {
        return properties(Duration.ofSeconds(10));
    }

    private PointPolicyProperties properties(Duration ttl) {
        return new PointPolicyProperties(
                new PointPolicyProperties.Earn(1, 100_000, "P365D", "P1D", "P5Y"),
                new PointPolicyProperties.Member(1_000_000),
                new PointPolicyProperties.Lock(ttl)
        );
    }
}
