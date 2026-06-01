package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.payment.point.api.earn.EarnResponse;
import com.payment.point.api.use.UseCancelRequest;
import com.payment.point.api.use.UseCancelResponse;
import com.payment.point.api.use.UseResponse;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.domain.earn.PntEarnMst;
import com.payment.point.domain.earn.PntEarnMstRepository;
import com.payment.point.domain.use.PntUseAlloc;
import com.payment.point.domain.use.PntUseAllocRepository;
import com.payment.point.domain.use.PntUseCancelHist;
import com.payment.point.domain.use.PntUseCancelHistRepository;
import com.payment.point.domain.use.PntUseMst;
import com.payment.point.domain.use.PntUseMstRepository;
import com.payment.point.domain.use.RestoreType;
import com.payment.point.domain.use.UseStatus;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UseCancelApiTests extends PointApiTestSupport {

    @Autowired
    private PntUseAllocRepository pntUseAllocRepository;

    @Autowired
    private PntUseCancelHistRepository pntUseCancelHistRepository;

    @Autowired
    private PntUseMstRepository pntUseMstRepository;

    @Autowired
    private PntEarnMstRepository pntEarnMstRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("성공-사용취소 복원")
    void useCancelRestoresPoint() {
        String memberId = memberId();
        givenEarn(memberId, "USE-CANCEL-API-EARN", EarnType.NORMAL, 1_000, "P10D");
        UseResponse useResponse = givenUse(memberId, "USE-CANCEL-API-USE", 400);

        UseCancelResponse cancelResponse = pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-API"), null, useResponse.pointTransactionNo(), 150)
        );

        assertPointId(cancelResponse.pointTransactionNo());
        assertEquals(memberId, cancelResponse.memberId());
        assertEquals(150, cancelResponse.amount());
        assertEquals(750, cancelResponse.remainingAmount());
    }

    @Test
    @DisplayName("실패-동일 주문번호 사용취소 재호출, DUPLICATED_ORDER")
    void useCancelRejectsDuplicatedOrderNoWithoutAdditionalBalanceChange() {
        String memberId = memberId();
        givenEarn(memberId, "USE-CANCEL-DUPLICATE-EARN", EarnType.NORMAL, 1_000, "P10D");
        UseResponse useResponse = givenUse(memberId, "USE-CANCEL-DUPLICATE-USE", 400);
        UseCancelRequest request = new UseCancelRequest(
                orderNo("USE-CANCEL-DUPLICATE"),
                null,
                useResponse.pointTransactionNo(),
                150
        );

        pointFacadeService.useCancel(memberId, request);

        ApiException exception = assertThrows(ApiException.class,
                () -> pointFacadeService.useCancel(memberId, request));
        PntUseMst useMst = pntUseMstRepository.findById(useResponse.pointTransactionNo()).orElseThrow();

        assertEquals(ErrorCode.DUPLICATED_ORDER, exception.getErrorCode());
        assertEquals(750, pointFacadeService.getBalance(memberId).totalAmount());
        assertEquals(150, useMst.getCancelAmount());
    }

    @Test
    @DisplayName("성공-취소 가능 잔액 범위에서 여러 차례 부분 사용취소")
    void useCancelAllowsMultiplePartialCancelsUntilRemainingAmount() {
        String memberId = memberId();
        givenEarn(memberId, "USE-CANCEL-PARTIAL-EARN", EarnType.NORMAL, 1_000, "P10D");
        UseResponse useResponse = givenUse(memberId, "USE-CANCEL-PARTIAL-USE", 600);

        UseCancelResponse firstCancel = pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-PARTIAL-1"), null, useResponse.pointTransactionNo(), 100)
        );
        UseCancelResponse secondCancel = pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-PARTIAL-2"), null, useResponse.pointTransactionNo(), 200)
        );

        assertEquals(500, firstCancel.remainingAmount());
        assertEquals(700, secondCancel.remainingAmount());
    }

    @Test
    @DisplayName("성공-부분 사용취소 시 사용 원장 상태 갱신")
    void useCancelUpdatesUseMasterAsPartialCancel() {
        String memberId = memberId();
        givenEarn(memberId, "USE-CANCEL-MST-EARN", EarnType.NORMAL, 1_000, "P10D");
        UseResponse useResponse = givenUse(memberId, "USE-CANCEL-MST-USE", 600);

        pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-MST"), null, useResponse.pointTransactionNo(), 100)
        );

        PntUseMst useMst = pntUseMstRepository.findById(useResponse.pointTransactionNo()).orElseThrow();

        assertEquals(100, useMst.getCancelAmount());
        assertEquals(500, useMst.getRemainingAmount());
        assertEquals(UseStatus.PARTIAL_CNCL, useMst.getStatus());
    }

    @Test
    @DisplayName("실패-부분 사용취소 후 남은 금액을 초과한 취소 요청, CANCEL_AMOUNT_EXCEEDED")
    void useCancelRejectsAmountGreaterThanRemainingAmountAfterPartialCancel() {
        String memberId = memberId();
        givenEarn(memberId, "USE-CANCEL-REMAIN-EARN", EarnType.NORMAL, 1_000, "P10D");
        UseResponse useResponse = givenUse(memberId, "USE-CANCEL-REMAIN-USE", 300);
        pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-REMAIN-1"), null, useResponse.pointTransactionNo(), 200)
        );

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-REMAIN-2"), null, useResponse.pointTransactionNo(), 101)
        ));

        assertEquals(ErrorCode.CANCEL_AMOUNT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    @DisplayName("실패-이미 전체 취소된 사용 원장 취소 요청, NO_REMAIN_POINT")
    void useCancelRejectsAlreadyFullyCanceledUseMaster() {
        String memberId = memberId();
        givenEarn(memberId, "USE-CANCEL-FULL-EARN", EarnType.NORMAL, 1_000, "P10D");
        UseResponse useResponse = givenUse(memberId, "USE-CANCEL-FULL-USE", 300);
        pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-FULL-1"), null, useResponse.pointTransactionNo(), 300)
        );

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-FULL-2"), null, useResponse.pointTransactionNo(), 1)
        ));

        PntUseMst useMst = pntUseMstRepository.findById(useResponse.pointTransactionNo()).orElseThrow();

        assertEquals(ErrorCode.NO_REMAIN_POINT, exception.getErrorCode());
        assertEquals(UseStatus.CNCL, useMst.getStatus());
        assertEquals(0, useMst.getRemainingAmount());
    }

    @Test
    @DisplayName("실패-취소 가능 금액을 초과한 사용취소 요청, CANCEL_AMOUNT_EXCEEDED")
    void useCancelRejectsAmountGreaterThanCancelableRemainingAmount() {
        String memberId = memberId();
        givenEarn(memberId, "USE-CANCEL-EXCEED-EARN", EarnType.NORMAL, 1_000, "P10D");
        UseResponse useResponse = givenUse(memberId, "USE-CANCEL-EXCEED-USE", 300);

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-EXCEED"), null, useResponse.pointTransactionNo(), 301)
        ));

        assertEquals(ErrorCode.CANCEL_AMOUNT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    @DisplayName("성공-사용 Allocation 우선순서에 따른 사용취소 복원")
    void useCancelRestoresAllocationsInUsePriorityOrder() {
        String memberId = memberId();
        givenEarn(memberId, "USE-CANCEL-ASC-EARN-A", EarnType.NORMAL, 1_000, "P10D");
        givenEarn(memberId, "USE-CANCEL-ASC-EARN-B", EarnType.NORMAL, 500, "P10D");
        UseResponse useResponse = givenUse(memberId, "USE-CANCEL-ASC-USE", 1_200);

        UseCancelResponse cancelResponse = pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-ASC"), null, useResponse.pointTransactionNo(), 1_100)
        );

        List<PntUseAlloc> allocations = pntUseAllocRepository.findByPtxnoOrderByPriorityAsc(useResponse.pointTransactionNo());

        assertEquals(1_400, cancelResponse.remainingAmount());
        assertEquals(2, allocations.size());
        assertEquals(0, allocations.get(0).getRemainingAmount());
        assertEquals(100, allocations.get(1).getRemainingAmount());
    }

    @Test
    @DisplayName("성공-원 적립 건 만료 시 RESTORE 신규 적립 생성")
    void useCancelCreatesRestoreEarnWhenOriginalEarnIsExpired() {
        String memberId = memberId();
        EarnResponse earnA = givenEarn(memberId, "USE-CANCEL-EXPIRED-EARN-A", EarnType.NORMAL, 1_000, "P10D");
        givenEarn(memberId, "USE-CANCEL-EXPIRED-EARN-B", EarnType.NORMAL, 500, "P10D");
        UseResponse useResponse = givenUse(memberId, "USE-CANCEL-EXPIRED-USE", 1_200);
        expireEarn(earnA.pointTransactionNo());

        UseCancelResponse cancelResponse = pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-EXPIRED"), null, useResponse.pointTransactionNo(), 1_100)
        );

        List<PntUseAlloc> allocations = pntUseAllocRepository.findByPtxnoOrderByPriorityAsc(useResponse.pointTransactionNo());
        List<PntUseCancelHist> histories = pntUseCancelHistRepository.findByUseCancelPtxno(cancelResponse.pointTransactionNo());
        PntUseCancelHist expiredRestoreHistory = histories.stream()
                .filter(history -> history.getRestoreType() == RestoreType.NEW_EARN)
                .findFirst()
                .orElseThrow();
        PntUseCancelHist originalRestoreHistory = histories.stream()
                .filter(history -> history.getRestoreType() == RestoreType.ORIGINAL_RESTORE)
                .findFirst()
                .orElseThrow();
        PntEarnMst restoreEarn = pntEarnMstRepository.findById(expiredRestoreHistory.getRestorePtxno())
                .orElseThrow();

        assertEquals(1_400, cancelResponse.remainingAmount());
        assertEquals(2, allocations.size());
        assertEquals(0, allocations.get(0).getRemainingAmount());
        assertEquals(100, allocations.get(1).getRemainingAmount());
        assertEquals(1_000, expiredRestoreHistory.getCancelAmount());
        assertNotNull(expiredRestoreHistory.getRestorePtxno());
        assertEquals(100, originalRestoreHistory.getCancelAmount());
        assertNull(originalRestoreHistory.getRestorePtxno());
        assertEquals(EarnType.RESTORE, restoreEarn.getEarnType());
        assertEquals(1_000, restoreEarn.getRemainingAmount());
    }

    private void expireEarn(String pointTransactionNo) {
        jdbcTemplate.update(
                "update POINT.PNT_EARN_MST set EXP_DT = ? where PTXNO = ?",
                LocalDate.now().minusDays(1),
                pointTransactionNo
        );
    }
}
