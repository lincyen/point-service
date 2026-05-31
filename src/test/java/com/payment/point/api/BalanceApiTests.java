package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payment.point.api.balance.BalanceResponse;
import com.payment.point.api.earn.EarnRequest;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class BalanceApiTests extends PointApiTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("실패-존재하지 않는 회원 잔액 조회, INVALID_USER")
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

    @Test
    @DisplayName("성공-영문자와 숫자로 구성된 회원아이디 요청 허용")
    void getBalanceApiAcceptsAlphanumericMemberId() throws Exception {
        mockMvc.perform(get("/v1/members/{memberId}/points/balance", "Member123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_USER.name()));
    }

    @Test
    @DisplayName("실패-특수문자가 포함된 회원아이디 요청, INVALID_PARAMETER")
    void getBalanceApiRejectsMemberIdContainingSpecialCharacter() throws Exception {
        mockMvc.perform(get("/v1/members/{memberId}/points/balance", "member-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.name()));
    }

    @Test
    @DisplayName("실패-32자를 초과한 회원아이디 요청, INVALID_PARAMETER")
    void getBalanceApiRejectsTooLongMemberId() throws Exception {
        mockMvc.perform(get("/v1/members/{memberId}/points/balance", "A".repeat(33)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.name()));
    }
}
