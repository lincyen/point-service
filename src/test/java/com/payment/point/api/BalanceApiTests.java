package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.payment.point.api.balance.BalanceResponse;
import com.payment.point.api.earn.EarnRequest;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BalanceApiTests extends PointApiTestSupport {

    @Test
    @DisplayName("실패-사용자가 없는 잔액 조회,INVALID_USER")
    void getBalanceRejectsMemberWithoutEarnBalance() {
        String memberId = memberId();

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.getBalance(memberId));

        assertEquals(ErrorCode.INVALID_USER, exception.getErrorCode());
    }

    @Test
    @DisplayName("성공-현재 잔액 조회")
    void getBalanceReturnsCurrentPointAmounts() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("BALANCE-EARN-NORMAL"), null,
                EarnType.NORMAL, 300, "P10D"));
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("BALANCE-EARN-MANUAL"), null,
                EarnType.MANUAL, 200, "P10D"));

        BalanceResponse response = pointFacadeService.getBalance(memberId);

        assertEquals(memberId, response.memberId());
        assertEquals(300, response.normalAmount());
        assertEquals(200, response.manualAmount());
        assertEquals(0, response.expiredAmount());
        assertEquals(500, response.totalAmount());
    }
}
