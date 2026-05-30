package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.domain.earn.PntEarnMst;
import com.payment.point.domain.earn.PntEarnMstRepository;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EarnApiTests extends PointApiTestSupport {

    @Autowired
    private PntEarnMstRepository pntEarnMstRepository;

    @Test
    @DisplayName("성공-적립 생성")
    void earnCreatesPoint() {
        String memberId = memberId();

        EarnResponse response = pointFacadeService.earn(
                memberId,
                new EarnRequest(orderNo("EARN-API"), null, EarnType.NORMAL, 1_000, "P10D")
        );

        assertPointId(response.ptxno());
        assertEquals(memberId, response.memberId());
        assertEquals(1_000, response.amount());
        assertEquals(1_000, response.remainingAmount());
    }

    @Test
    @DisplayName("실패-동일 번호 요청, DUPLICATED_ORDER")
    void earnRejectsDuplicatedOrderNo() {
        String memberId = memberId();
        EarnRequest request = new EarnRequest(orderNo("EARN-API"), null, EarnType.NORMAL, 100, "P10D");

        pointFacadeService.earn(memberId, request);

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.earn(memberId, request));
        assertEquals(ErrorCode.DUPLICATED_ORDER, exception.getErrorCode());
    }

    @Test
    @DisplayName("실패-만료기간이 P5Y 이면 INVALID_PARAMETER")
    void earnRejectsFiveYearExpirePeriod() {
        String memberId = memberId();
        EarnRequest request = new EarnRequest(orderNo("EARN-API"), null, EarnType.NORMAL, 100, "P5Y");

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.earn(memberId, request));

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
    }

    @Test
    @DisplayName("실패-외부 적립 요청의 RESTORE 유형은 INVALID_PARAMETER")
    void earnRejectsRestoreEarnType() {
        String memberId = memberId();
        EarnRequest request = new EarnRequest(orderNo("EARN-API"), null, EarnType.RESTORE, 100, "P10D");

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.earn(memberId, request));

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
    }

    @Test
    @DisplayName("성공-expirePeriod 미입력 시 P365D 기본 만료기간 적용")
    void earnUsesDefaultExpirePeriodWhenExpirePeriodIsNull() {
        String memberId = memberId();
        LocalDateTime beforeEarn = LocalDateTime.now();

        EarnResponse response = pointFacadeService.earn(
                memberId,
                new EarnRequest(orderNo("EARN-API"), null, EarnType.NORMAL, 100, null)
        );

        PntEarnMst earn = pntEarnMstRepository.findById(response.ptxno()).orElseThrow();
        LocalDateTime expectedExpireAt = beforeEarn.plusDays(365);
        Duration difference = Duration.between(expectedExpireAt, earn.getExpireAt()).abs();

        assertEquals(memberId, earn.getMemberId());
        assertTrue(difference.getSeconds() < 5);
    }
}
