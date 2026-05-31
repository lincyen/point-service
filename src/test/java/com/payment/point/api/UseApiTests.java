package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.api.use.UseRequest;
import com.payment.point.api.use.UseResponse;
import com.payment.point.domain.balance.PntMemberBal;
import com.payment.point.domain.balance.PntMemberBalRepository;
import com.payment.point.domain.earn.PntEarnMst;
import com.payment.point.domain.earn.PntEarnMstRepository;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.domain.earn.EarnStatus;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UseApiTests extends PointApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PntMemberBalRepository pntMemberBalRepository;

    @Autowired
    private PntEarnMstRepository pntEarnMstRepository;

    @Test
    @DisplayName("성공-포인트 사용")
    void useConsumesPoint() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-API-EARN"), null, EarnType.NORMAL, 1_000, "P10D"));

        UseResponse response = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-API"), null, 400));

        assertPointId(response.pointTransactionNo());
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

    @Test
    void useExpiresMemberEarnsOnlyWhenNextExpireDateIsDue() {
        String memberId = memberId();
        EarnResponse expiredEarn = pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-EXPIRE-DUE"), null,
                EarnType.MANUAL, 100, "P10D"));
        EarnResponse usableEarn = pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-EXPIRE-USABLE"), null,
                EarnType.NORMAL, 100, "P20D"));
        LocalDate yesterday = LocalDate.now().minusDays(1);
        jdbcTemplate.update("update POINT.PNT_EARN_MST set EXP_DT = ? where PTXNO = ?", yesterday,
                expiredEarn.pointTransactionNo());
        jdbcTemplate.update("update POINT.PNT_MEMBER_BAL set NEXT_EXP_DT = ? where MEMBER_ID = ?", yesterday, memberId);

        UseResponse response = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-EXPIRE"), null, 50));

        PntMemberBal balance = pntMemberBalRepository.findById(memberId).orElseThrow();
        PntEarnMst expiredEarnMst = pntEarnMstRepository.findById(expiredEarn.pointTransactionNo()).orElseThrow();
        PntEarnMst usableEarnMst = pntEarnMstRepository.findById(usableEarn.pointTransactionNo()).orElseThrow();
        assertEquals(50, response.remainingAmount());
        assertEquals(100, balance.getExpiredAmount());
        assertEquals(LocalDate.now().plusDays(20), balance.getNextExpireDate());
        assertEquals(EarnStatus.EXPIRED, expiredEarnMst.getStatus());
        assertEquals(50, usableEarnMst.getRemainingAmount());
    }
}
