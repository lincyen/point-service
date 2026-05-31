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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PointEarnServiceTests {

    private static final PointPolicyProperties POINT_POLICY_PROPERTIES = new PointPolicyProperties(
            new PointPolicyProperties.Earn(1, 100_000, "P365D", "P1D", "P5Y"),
            new PointPolicyProperties.Member(1_000_000),
            new PointPolicyProperties.Lock(Duration.ofSeconds(10))
    );

    @Test
    @DisplayName("실패-만료된 적립 원장 취소 조회, EXPIRED_POINT")
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
    @DisplayName("성공-최소 만료기간 P1D 적용")
    void resolveExpireDateAcceptsMinimumExpirePeriod() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDate now = LocalDate.of(2026, 5, 30);

        LocalDate expireDate = pointEarnService.resolveExpireDate("P1D", now);

        assertThat(expireDate).isEqualTo(now.plusDays(1));
    }

    @Test
    @DisplayName("성공-만료기간 미입력 시 기본 만료기간 적용")
    void resolveExpireDateUsesDefaultExpirePeriodWhenRequestIsNull() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDate now = LocalDate.of(2026, 5, 30);

        LocalDate expireDate = pointEarnService.resolveExpireDate(null, now);

        assertThat(expireDate).isEqualTo(now.plusDays(365));
    }

    @Test
    @DisplayName("실패-최대 만료기간 P5Y 요청, INVALID_PARAMETER")
    void resolveExpireDateRejectsFiveYearsBecauseMaxIsExclusive() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDate now = LocalDate.of(2026, 5, 30);

        assertThatThrownBy(() -> pointEarnService.resolveExpireDate("P5Y", now))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PARAMETER));
    }

    @Test
    @DisplayName("실패-최소 만료기간 미만 P0D 요청, INVALID_PARAMETER")
    void resolveExpireDateRejectsZeroExpirePeriodBecauseMinimumIsOneDay() {
        PointEarnService pointEarnService = new PointEarnService(null, POINT_POLICY_PROPERTIES, null);
        LocalDate now = LocalDate.of(2026, 5, 30);

        assertThatThrownBy(() -> pointEarnService.resolveExpireDate("P0D", now))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PARAMETER));
    }
}
