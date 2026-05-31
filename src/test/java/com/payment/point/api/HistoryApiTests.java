package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.history.HistoryResponse;
import com.payment.point.api.use.UseRequest;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.domain.transaction.TxType;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class HistoryApiTests extends PointApiTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getHistoriesRejectsWhenResultIsEmpty() {
        String memberId = memberId();
        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(1);

        ApiException exception = assertThrows(ApiException.class,
                () -> pointFacadeService.getHistories(memberId, startDate, endDate, null));

        assertEquals(ErrorCode.NO_HISTORY_RESULT, exception.getErrorCode());
    }

    @Test
    void getHistoriesReturnsLatestMemberTransactions() {
        String memberId = memberId();
        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(1);
        String earnOrderNo = orderNo("HISTORY-EARN");
        String useOrderNo = orderNo("HISTORY-USE");
        pointFacadeService.earn(memberId, new EarnRequest(earnOrderNo, null, EarnType.NORMAL, 1_000, "P10D"));
        pointFacadeService.use(memberId, new UseRequest(useOrderNo, null, 400));

        HistoryResponse response = pointFacadeService.getHistories(memberId, startDate, endDate, null);

        assertEquals(memberId, response.memberId());
        assertEquals(2, response.histories().size());
        assertEquals(TxType.USE, response.histories().get(0).txType());
        assertEquals(useOrderNo, response.histories().get(0).orderNo());
        assertEquals(400, response.histories().get(0).amount());
        assertEquals(600, response.histories().get(0).remainingAmount());
        assertEquals(TxType.EARN, response.histories().get(1).txType());
        assertEquals(earnOrderNo, response.histories().get(1).orderNo());
        assertEquals(1_000, response.histories().get(1).amount());
        assertEquals(1_000, response.histories().get(1).remainingAmount());
    }

    @Test
    void getHistoriesFiltersTxTypeWhenRequested() {
        String memberId = memberId();
        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(1);
        String earnOrderNo = orderNo("HISTORY-FILTER-EARN");
        String useOrderNo = orderNo("HISTORY-FILTER-USE");
        pointFacadeService.earn(memberId, new EarnRequest(earnOrderNo, null, EarnType.NORMAL, 1_000, "P10D"));
        pointFacadeService.use(memberId, new UseRequest(useOrderNo, null, 400));

        HistoryResponse response = pointFacadeService.getHistories(memberId, startDate, endDate, TxType.USE);

        assertEquals(1, response.histories().size());
        assertEquals(TxType.USE, response.histories().get(0).txType());
        assertEquals(useOrderNo, response.histories().get(0).orderNo());
    }

    @Test
    void getHistoriesRejectsWhenStartDateIsNotBeforeEndDate() {
        String memberId = memberId();
        LocalDate date = LocalDate.now();

        ApiException exception = assertThrows(ApiException.class,
                () -> pointFacadeService.getHistories(memberId, date, date, null));

        assertEquals(ErrorCode.INVALID_HISTORY_PERIOD, exception.getErrorCode());
    }

    @Test
    void getHistoriesRejectsWhenSearchPeriodExceedsThreeMonths() {
        String memberId = memberId();
        LocalDate startDate = LocalDate.now().minusMonths(4);
        LocalDate endDate = LocalDate.now();

        ApiException exception = assertThrows(ApiException.class,
                () -> pointFacadeService.getHistories(memberId, startDate, endDate, null));

        assertEquals(ErrorCode.HISTORY_PERIOD_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void getHistoriesApiRejectsInvalidDateFormat() throws Exception {
        mockMvc.perform(get("/v1/members/{memberId}/points/histories", memberId())
                        .param("startDate", "2026/05/01")
                        .param("endDate", "2026-05-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.name()));
    }

    @Test
    void getHistoriesApiRejectsMissingDateParameter() throws Exception {
        mockMvc.perform(get("/v1/members/{memberId}/points/histories", memberId())
                        .param("endDate", "2026-05-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.name()));
    }
}
