package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.use.UseRequest;
import com.payment.point.api.use.UseResponse;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UseApiTests extends PointApiTestSupport {

    @Test
    @DisplayName("성공-포인트 사용")
    void useConsumesPoint() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-API-EARN"), null, EarnType.NORMAL, 1_000, "P10D"));

        UseResponse response = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-API"), null, 400));

        assertPointId(response.ptxno());
        assertEquals(memberId, response.memberId());
        assertEquals(400, response.amount());
        assertEquals(600, response.remainingAmount());
    }

    @Test
    @DisplayName("실패-포인트 잔액 부족, NOT_ENOUGH_POINT")
    void useRejectsNotEnoughPoint() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-API-EARN"), null, EarnType.NORMAL, 100, "P10D"));

        ApiException exception = assertThrows(ApiException.class,
                () -> pointFacadeService.use(memberId, new UseRequest(orderNo("USE-API"), null, 200)));

        assertEquals(ErrorCode.NOT_ENOUGH_POINT, exception.getErrorCode());
    }
}
