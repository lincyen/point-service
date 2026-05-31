package com.payment.point.domain.earn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.payment.point.config.PointPolicyProperties;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.Duration;
import java.time.LocalDate;
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

        LocalDate now = LocalDate.now();
        PntEarnMst earn = new PntEarnMst("20260528100000001000000009", "550e8400e29b41d4a716446655440000",
                EarnType.NORMAL, 1_000L, now.minusDays(1));
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
    void resolveExpireDateAcceptsMinimumExpirePeriod() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDate now = LocalDate.of(2026, 5, 30);

        LocalDate expireDate = pointEarnService.resolveExpireDate("P1D", now);

        assertThat(expireDate).isEqualTo(now.plusDays(1));
    }

    @Test
    void resolveExpireDateUsesDefaultExpirePeriodWhenRequestIsNull() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDate now = LocalDate.of(2026, 5, 30);

        LocalDate expireDate = pointEarnService.resolveExpireDate(null, now);

        assertThat(expireDate).isEqualTo(now.plusDays(365));
    }

    @Test
    void resolveExpireDateRejectsFiveYearsBecauseMaxIsExclusive() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDate now = LocalDate.of(2026, 5, 30);

        assertThatThrownBy(() -> pointEarnService.resolveExpireDate("P5Y", now))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PARAMETER));
    }

    @Test
    void resolveExpireDateRejectsZeroExpirePeriodBecauseMinimumIsOneDay() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDate now = LocalDate.of(2026, 5, 30);

        assertThatThrownBy(() -> pointEarnService.resolveExpireDate("P0D", now))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PARAMETER));
    }
}
