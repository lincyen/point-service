package com.payment.point.domain.earn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.payment.point.config.PointPolicyProperties;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PointEarnServiceTests {

    private static final PointPolicyProperties POINT_POLICY_PROPERTIES = new PointPolicyProperties(
            new PointPolicyProperties.Earn(1, 100_000, "P365D", "P1D", "P5Y"),
            new PointPolicyProperties.Member(1_000_000),
            new PointPolicyProperties.Lock(Duration.ofSeconds(10))
    );

    @Test
    void findEarnForCancelRejectsExpiredEarn() {
        PntEarnMstRepository pntEarnMstRepository = mock(PntEarnMstRepository.class);
        PointEarnService pointEarnService = new PointEarnService(pntEarnMstRepository, null, null);

        LocalDateTime now = LocalDateTime.now();
        PntEarnMst earn = new PntEarnMst("20260528100000001000000009", "550e8400e29b41d4a716446655440000",
                EarnType.NORMAL, 1_000L, now.minusSeconds(1));
        when(pntEarnMstRepository.findById(earn.getPtxno())).thenReturn(Optional.of(earn));

        assertThatThrownBy(() -> pointEarnService.findEarnForCancel(
                earn.getMemberId(),
                earn.getPtxno(),
                earn.getRemainingAmount(),
                now
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.EXPIRED_POINT));
    }

    @Test
    void resolveExpireAtAcceptsMinimumExpirePeriod() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDateTime now = LocalDateTime.of(2026, 5, 30, 10, 0);

        LocalDateTime expireAt = pointEarnService.resolveExpireAt("P1D", now);

        assertThat(expireAt).isEqualTo(now.plusDays(1));
    }

    @Test
    void resolveExpireAtUsesDefaultExpirePeriodWhenRequestIsNull() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDateTime now = LocalDateTime.of(2026, 5, 30, 10, 0);

        LocalDateTime expireAt = pointEarnService.resolveExpireAt(null, now);

        assertThat(expireAt).isEqualTo(now.plusDays(365));
    }

    @Test
    void resolveExpireAtRejectsFiveYearsBecauseMaxIsExclusive() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDateTime now = LocalDateTime.of(2026, 5, 30, 10, 0);

        assertThatThrownBy(() -> pointEarnService.resolveExpireAt("P5Y", now))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PARAMETER));
    }

    @Test
    void resolveExpireAtRejectsZeroExpirePeriodBecauseMinimumIsOneDay() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDateTime now = LocalDateTime.of(2026, 5, 30, 10, 0);

        assertThatThrownBy(() -> pointEarnService.resolveExpireAt("P0D", now))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PARAMETER));
    }
}
