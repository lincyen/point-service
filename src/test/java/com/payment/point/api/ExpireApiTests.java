package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.payment.point.api.expire.ExpireRequest;
import com.payment.point.api.expire.ExpireResponse;
import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.balance.BalanceResponse;
import com.payment.point.domain.earn.EarnType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpireApiTests extends PointApiTestSupport {

    @Test
    @DisplayName("성공-적립 원장 잔액 만료 처리")
    void expireRemainingEarnPoints() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("EXPIRE-API-EARN"), null, EarnType.NORMAL, 300, "P2D"));

        ExpireResponse response = pointFacadeService.expire(memberId, new ExpireRequest(LocalDate.now().plusDays(3)));

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
        pointFacadeService.earn(targetMemberId, new EarnRequest(orderNo("EXPIRE-API-TARGET"), null, EarnType.NORMAL, 300, "P2D"));
        pointFacadeService.earn(otherMemberId, new EarnRequest(orderNo("EXPIRE-API-OTHER"), null, EarnType.NORMAL, 500, "P2D"));

        ExpireResponse response = pointFacadeService.expire(targetMemberId, new ExpireRequest(LocalDate.now().plusDays(3)));

        BalanceResponse targetBalance = pointFacadeService.getBalance(targetMemberId);
        BalanceResponse otherBalance = pointFacadeService.getBalance(otherMemberId);
        assertEquals(1, response.expiredCount());
        assertEquals(300, response.expiredAmount());
        assertEquals(0, targetBalance.totalAmount());
        assertEquals(500, otherBalance.totalAmount());
    }
}
