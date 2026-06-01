package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.payment.point.api.earn.EarnCancelRequest;
import com.payment.point.api.earn.EarnCancelResponse;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EarnCancelApiTests extends PointApiTestSupport {

    @Test
    @DisplayName("성공-적립취소")
    void earnCancelCancelsRemainingPoint() {
        String memberId = memberId();
        EarnResponse earnResponse = givenEarn(memberId, "EARN-CANCEL-API-EARN", EarnType.MANUAL, 500, "P10D");

        EarnCancelResponse cancelResponse = pointFacadeService.earnCancel(
                memberId,
                new EarnCancelRequest(orderNo("EARN-CANCEL-API"), null, earnResponse.pointTransactionNo(), 500)
        );

        assertPointId(cancelResponse.pointTransactionNo());
        assertEquals(memberId, cancelResponse.memberId());
        assertEquals(500, cancelResponse.amount());
        assertEquals(0, cancelResponse.remainingAmount());
    }

    @Test
    @DisplayName("실패-일부 사용된 적립 원장 취소 요청, INCORRECT_POINT")
    void earnCancelRejectsUsedEarn() {
        String memberId = memberId();
        EarnResponse earnResponse = givenEarn(memberId, "EARN-CANCEL-USED-EARN", EarnType.NORMAL, 500, "P10D");
        givenUse(memberId, "EARN-CANCEL-USED-USE", 100);

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.earnCancel(
                memberId,
                new EarnCancelRequest(orderNo("EARN-CANCEL-USED"), null, earnResponse.pointTransactionNo(), 500)
        ));

        assertEquals(ErrorCode.INCORRECT_POINT, exception.getErrorCode());
    }

    @Test
    @DisplayName("실패-동일 주문번호 적립취소 재호출, DUPLICATED_ORDER")
    void earnCancelRejectsDuplicatedOrderNoWithoutAdditionalBalanceChange() {
        String memberId = memberId();
        EarnResponse earnResponse = givenEarn(memberId, "EARN-CANCEL-DUPLICATE-EARN", EarnType.NORMAL, 500, "P10D");
        EarnCancelRequest request = new EarnCancelRequest(
                orderNo("EARN-CANCEL-DUPLICATE"),
                null,
                earnResponse.pointTransactionNo(),
                500
        );

        pointFacadeService.earnCancel(memberId, request);

        ApiException exception = assertThrows(ApiException.class,
                () -> pointFacadeService.earnCancel(memberId, request));

        assertEquals(ErrorCode.DUPLICATED_ORDER, exception.getErrorCode());
        assertEquals(0, pointFacadeService.getBalance(memberId).totalAmount());
    }
}
