package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.domain.balance.PntMemberBal;
import com.payment.point.domain.balance.PntMemberBalRepository;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.domain.earn.PntEarnMst;
import com.payment.point.domain.earn.PntEarnMstRepository;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EarnApiTests extends PointApiTestSupport {

    @Autowired
    private PntEarnMstRepository pntEarnMstRepository;

    @Autowired
    private PntMemberBalRepository pntMemberBalRepository;

    @Test
    @DisplayName("성공-적립 생성")
    void earnCreatesPoint() {
        String memberId = memberId();

        EarnResponse response = pointFacadeService.earn(
                memberId,
                new EarnRequest(orderNo("EARN-API"), null, EarnType.NORMAL, 1_000, "P10D")
        );

        assertPointId(response.pointTransactionNo());
        assertEquals(memberId, response.memberId());
        assertEquals(1_000, response.amount());
        assertEquals(1_000, response.remainingAmount());
    }

    @Test
    @DisplayName("실패-동일 주문번호 중복 요청, DUPLICATED_ORDER")
    void earnRejectsDuplicatedOrderNo() {
        String memberId = memberId();
        EarnRequest request = new EarnRequest(orderNo("EARN-API"), null, EarnType.NORMAL, 100, "P10D");

        pointFacadeService.earn(memberId, request);

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.earn(memberId, request));
        assertEquals(ErrorCode.DUPLICATED_ORDER, exception.getErrorCode());
    }

    @Test
    @DisplayName("실패-최대 만료기간 P5Y 요청, INVALID_PARAMETER")
    void earnRejectsFiveYearExpirePeriod() {
        String memberId = memberId();
        EarnRequest request = new EarnRequest(orderNo("EARN-API"), null, EarnType.NORMAL, 100, "P5Y");

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.earn(memberId, request));

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
    }

    @Test
    @DisplayName("실패-외부 적립 요청의 RESTORE 유형, INVALID_PARAMETER")
    void earnRejectsRestoreEarnType() {
        String memberId = memberId();
        EarnRequest request = new EarnRequest(orderNo("EARN-API"), null, EarnType.RESTORE, 100, "P10D");

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.earn(memberId, request));

        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
    }

    @Test
    @DisplayName("성공-expirePeriod 미입력 시 P365D 기본 만료기간 적용")
    void earnUsesDefaultExpirePeriodWhenExpirePeriodIsNull() {
        String memberId = memberId();
        LocalDate beforeEarn = LocalDate.now();

        EarnResponse response = pointFacadeService.earn(
                memberId,
                new EarnRequest(orderNo("EARN-API"), null, EarnType.NORMAL, 100, null)
        );

        PntEarnMst earn = pntEarnMstRepository.findById(response.pointTransactionNo()).orElseThrow();
        LocalDate expectedExpireDate = beforeEarn.plusDays(365);

        assertEquals(memberId, earn.getMemberId());
        assertEquals(expectedExpireDate, earn.getExpireDate());
    }

    @Test
    @DisplayName("성공-더 빠른 만료일의 적립 생성 시 회원 다음 만료 예정일 갱신")
    void earnUpdatesMemberNextExpireDateWhenEarlierEarnIsCreated() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("EARN-NEXT-LATE"), null,
                EarnType.NORMAL, 100, "P10D"));
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("EARN-NEXT-EARLY"), null,
                EarnType.NORMAL, 100, "P2D"));

        PntMemberBal balance = pntMemberBalRepository.findById(memberId).orElseThrow();

        assertEquals(LocalDate.now().plusDays(2), balance.getNextExpireDate());
    }
}
