package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payment.point.api.earn.EarnResponse;
import com.payment.point.api.history.TransactionLookupResponse;
import com.payment.point.api.use.UseResponse;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.domain.transaction.TxType;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TransactionLookupApiTests extends PointApiTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("성공-주문번호 기반 적립 거래 조회")
    void getTransactionByOrderReturnsEarnTransaction() {
        String memberId = memberId();
        String orderNo = orderNo("LOOKUP-EARN");
        EarnResponse earnResponse = givenEarnByOrderNo(memberId, orderNo, EarnType.NORMAL, 1_000, "P10D");

        TransactionLookupResponse response = pointFacadeService.getTransactionByOrder(memberId, orderNo, TxType.EARN);

        assertTrue(response.exists());
        assertEquals(memberId, response.memberId());
        assertEquals(orderNo, response.orderNo());
        assertEquals(TxType.EARN, response.txType());
        assertNotNull(response.transaction());
        assertEquals(earnResponse.pointTransactionNo(), response.transaction().pointTransactionNo());
        assertEquals(TxType.EARN, response.transaction().txType());
        assertEquals(1_000, response.transaction().amount());
        assertEquals(1_000, response.transaction().remainingAmount());
    }

    @Test
    @DisplayName("성공-동일 회원과 주문번호라도 거래 유형이 다르면 별도 거래 처리")
    void transactionAllowsSameMemberAndOrderNoForDifferentTxTypes() {
        String memberId = memberId();
        String orderNo = orderNo("LOOKUP-SAME-ORDER");
        EarnResponse earnResponse = givenEarnByOrderNo(memberId, orderNo, EarnType.NORMAL, 1_000, "P10D");
        UseResponse useResponse = givenUseByOrderNo(memberId, orderNo, 400);

        TransactionLookupResponse earnLookup = pointFacadeService.getTransactionByOrder(memberId, orderNo, TxType.EARN);
        TransactionLookupResponse useLookup = pointFacadeService.getTransactionByOrder(memberId, orderNo, TxType.USE);

        assertTrue(earnLookup.exists());
        assertEquals(earnResponse.pointTransactionNo(), earnLookup.transaction().pointTransactionNo());
        assertEquals(TxType.EARN, earnLookup.transaction().txType());
        assertTrue(useLookup.exists());
        assertEquals(useResponse.pointTransactionNo(), useLookup.transaction().pointTransactionNo());
        assertEquals(TxType.USE, useLookup.transaction().txType());
    }

    @Test
    @DisplayName("성공-거래 유형으로 주문번호 기반 거래 조회 필터링")
    void getTransactionByOrderFiltersTxTypeWhenRequested() {
        String memberId = memberId();
        String earnOrderNo = orderNo("LOOKUP-USE-EARN");
        String useOrderNo = orderNo("LOOKUP-USE");
        givenEarnByOrderNo(memberId, earnOrderNo, EarnType.NORMAL, 1_000, "P10D");
        UseResponse useResponse = givenUseByOrderNo(memberId, useOrderNo, 400);

        TransactionLookupResponse response = pointFacadeService.getTransactionByOrder(memberId, useOrderNo, TxType.USE);

        assertTrue(response.exists());
        assertEquals(TxType.USE, response.txType());
        assertNotNull(response.transaction());
        assertEquals(useResponse.pointTransactionNo(), response.transaction().pointTransactionNo());
        assertEquals(400, response.transaction().amount());
        assertEquals(600, response.transaction().remainingAmount());
    }

    @Test
    @DisplayName("성공-거래 유형이 일치하지 않는 주문번호 기반 조회 시 존재하지 않음 응답")
    void getTransactionByOrderReturnsNotExistsWhenTxTypeDoesNotMatch() {
        String memberId = memberId();
        String orderNo = orderNo("LOOKUP-MISMATCH");
        givenEarnByOrderNo(memberId, orderNo, EarnType.NORMAL, 1_000, "P10D");

        TransactionLookupResponse response = pointFacadeService.getTransactionByOrder(memberId, orderNo, TxType.USE);

        assertFalse(response.exists());
        assertEquals(memberId, response.memberId());
        assertEquals(orderNo, response.orderNo());
        assertEquals(TxType.USE, response.txType());
        assertNull(response.transaction());
    }

    @Test
    @DisplayName("성공-유효 회원의 주문번호가 존재하지 않으면 존재하지 않음 응답")
    void getTransactionByOrderReturnsNotExistsWhenOrderDoesNotExist() {
        String memberId = memberId();
        givenEarn(memberId, "LOOKUP-EXISTING-EARN", EarnType.NORMAL, 100, "P10D");
        String orderNo = orderNo("LOOKUP-MISSING");

        TransactionLookupResponse response = pointFacadeService.getTransactionByOrder(memberId, orderNo, TxType.EARN);

        assertFalse(response.exists());
        assertEquals(memberId, response.memberId());
        assertEquals(orderNo, response.orderNo());
        assertEquals(TxType.EARN, response.txType());
        assertNull(response.transaction());
    }

    @Test
    @DisplayName("실패-존재하지 않는 회원의 주문번호 기반 조회, INVALID_USER")
    void getTransactionByOrderRejectsUnknownMember() {
        String memberId = memberId();

        ApiException exception = assertThrows(ApiException.class,
                () -> pointFacadeService.getTransactionByOrder(memberId, orderNo("LOOKUP-UNKNOWN"), TxType.EARN));

        assertEquals(ErrorCode.INVALID_USER, exception.getErrorCode());
    }

    @Test
    @DisplayName("실패-거래 유형이 누락된 주문번호 기반 조회, INVALID_PARAMETER")
    void getTransactionByOrderApiRejectsMissingTxType() throws Exception {
        mockMvc.perform(get("/v1/members/{memberId}/points/transactions/by-order", memberId())
                        .param("orderNo", orderNo("LOOKUP-MISSING-TYPE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.name()));
    }

    @Test
    @DisplayName("실패-주문번호가 누락된 주문번호 기반 조회, INVALID_PARAMETER")
    void getTransactionByOrderApiRejectsMissingOrderNo() throws Exception {
        mockMvc.perform(get("/v1/members/{memberId}/points/transactions/by-order", memberId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.name()));
    }

    @Test
    @DisplayName("실패-빈 주문번호 기반 조회, INVALID_PARAMETER")
    void getTransactionByOrderApiRejectsEmptyOrderNo() throws Exception {
        mockMvc.perform(get("/v1/members/{memberId}/points/transactions/by-order", memberId())
                        .param("orderNo", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.name()));
    }

    @Test
    @DisplayName("실패-공백 주문번호 기반 조회, INVALID_PARAMETER")
    void getTransactionByOrderApiRejectsBlankOrderNo() throws Exception {
        mockMvc.perform(get("/v1/members/{memberId}/points/transactions/by-order", memberId())
                        .param("orderNo", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.name()));
    }

    @Test
    @DisplayName("실패-40자를 초과한 주문번호 기반 조회, INVALID_PARAMETER")
    void getTransactionByOrderApiRejectsTooLongOrderNo() throws Exception {
        mockMvc.perform(get("/v1/members/{memberId}/points/transactions/by-order", memberId())
                        .param("orderNo", "A".repeat(41)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.name()));
    }
}
