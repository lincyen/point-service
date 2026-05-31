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

    private PointPolicyProperties properties() {
        return new PointPolicyProperties(
                new PointPolicyProperties.Earn(1, 100_000, "P365D", "P1D", "P5Y"),
                new PointPolicyProperties.Member(1_000_000),
                new PointPolicyProperties.Lock(Duration.ofSeconds(10))
        );
    }
}
