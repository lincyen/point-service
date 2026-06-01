package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.payment.point.api.expire.ExpireRequest;
import com.payment.point.api.expire.ExpireResponse;
import com.payment.point.api.balance.BalanceResponse;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ExpireApiTests extends PointApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("성공-적립 원장 잔액 만료 처리")
    void expireRemainingEarnPoints() {
        String memberId = memberId();
        EarnResponse earnResponse = givenEarn(memberId, "EXPIRE-API-EARN", EarnType.NORMAL, 300, "P2D");
        expireEarn(earnResponse.pointTransactionNo());

        ExpireResponse response = pointFacadeService.expire(memberId, new ExpireRequest(LocalDate.now()));

        assertEquals(1, response.expiredCount());
        assertEquals(300, response.expiredAmount());

        BalanceResponse balance = pointFacadeService.getBalance(memberId);
        assertEquals(0, balance.normalAmount());
        assertEquals(300, balance.expiredAmount());
        assertEquals(0, balance.totalAmount());
    }

    @Test
    @DisplayName("성공-요청 회원의 적립 원장만 만료 처리")
    void expireProcessesOnlyRequestedMemberPoints() {
        String targetMemberId = memberId();
        String otherMemberId = memberId();
        EarnResponse targetEarn = givenEarn(targetMemberId, "EXPIRE-API-TARGET", EarnType.NORMAL, 300, "P2D");
        givenEarn(otherMemberId, "EXPIRE-API-OTHER", EarnType.NORMAL, 500, "P2D");
        expireEarn(targetEarn.pointTransactionNo());

        ExpireResponse response = pointFacadeService.expire(targetMemberId, new ExpireRequest(LocalDate.now()));

        BalanceResponse targetBalance = pointFacadeService.getBalance(targetMemberId);
        BalanceResponse otherBalance = pointFacadeService.getBalance(otherMemberId);
        assertEquals(1, response.expiredCount());
        assertEquals(300, response.expiredAmount());
        assertEquals(0, targetBalance.totalAmount());
        assertEquals(500, otherBalance.totalAmount());
    }

    @Test
    @DisplayName("실패-오늘 이후 기준일의 만료 요청, INVALID_PARAMETER")
    void expireRejectsFutureBaseDate() {
        String memberId = memberId();
        givenEarn(memberId, "EXPIRE-API-FUTURE", EarnType.NORMAL, 300, "P2D");

        ApiException exception = assertThrows(ApiException.class,
                () -> pointFacadeService.expire(memberId, new ExpireRequest(LocalDate.now().plusDays(1))));

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        assertEquals(300, pointFacadeService.getBalance(memberId).totalAmount());
    }

    private void expireEarn(String pointTransactionNo) {
        jdbcTemplate.update(
                "update POINT.PNT_EARN_MST set EXP_DT = ? where PTXNO = ?",
                LocalDate.now().minusDays(1),
                pointTransactionNo
        );
    }
}
