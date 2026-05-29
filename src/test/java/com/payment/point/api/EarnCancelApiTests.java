package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.payment.point.api.earn.EarnCancelRequest;
import com.payment.point.api.earn.EarnCancelResponse;
import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.domain.earn.EarnType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EarnCancelApiTests extends PointApiTestSupport {

    @Test
    @DisplayName("성공-적립취소")
    void earnCancelCancelsRemainingPoint() {
        String memberId = memberId();
        EarnResponse earnResponse = pointFacadeService.earn(
                memberId,
                new EarnRequest(orderNo("EARN-CANCEL-API-EARN"), null, EarnType.MANUAL, 500, "P10D")
        );

        EarnCancelResponse cancelResponse = pointFacadeService.earnCancel(
                memberId,
                new EarnCancelRequest(orderNo("EARN-CANCEL-API"), null, earnResponse.ptxno(), 500)
        );

        assertPointId(cancelResponse.ptxno());
        assertEquals(memberId, cancelResponse.memberId());
        assertEquals(500, cancelResponse.amount());
        assertEquals(0, cancelResponse.remainingAmount());
    }
}
