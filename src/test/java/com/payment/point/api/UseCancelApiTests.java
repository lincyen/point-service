package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.api.use.UseCancelRequest;
import com.payment.point.api.use.UseCancelResponse;
import com.payment.point.api.use.UseRequest;
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
import java.time.LocalDateTime;
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
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-API-EARN"), null, EarnType.NORMAL, 1_000, "P10D"));
        UseResponse useResponse = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-CANCEL-API-USE"), null, 400));

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
    void useCancelAllowsMultiplePartialCancelsUntilRemainingAmount() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-PARTIAL-EARN"), null,
                EarnType.NORMAL, 1_000, "P10D"));
        UseResponse useResponse = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-CANCEL-PARTIAL-USE"), null, 600));

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
    void useCancelUpdatesUseMasterAsPartialCancel() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-MST-EARN"), null,
                EarnType.NORMAL, 1_000, "P10D"));
        UseResponse useResponse = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-CANCEL-MST-USE"), null, 600));

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
    void useCancelRejectsAmountGreaterThanRemainingAmountAfterPartialCancel() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-REMAIN-EARN"), null,
                EarnType.NORMAL, 1_000, "P10D"));
        UseResponse useResponse = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-CANCEL-REMAIN-USE"), null, 300));
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
    void useCancelRejectsAlreadyFullyCanceledUseMaster() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-FULL-EARN"), null,
                EarnType.NORMAL, 1_000, "P10D"));
        UseResponse useResponse = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-CANCEL-FULL-USE"), null, 300));
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
    void useCancelRejectsAmountGreaterThanCancelableRemainingAmount() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-EXCEED-EARN"), null,
                EarnType.NORMAL, 1_000, "P10D"));
        UseResponse useResponse = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-CANCEL-EXCEED-USE"), null, 300));

        ApiException exception = assertThrows(ApiException.class, () -> pointFacadeService.useCancel(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-EXCEED"), null, useResponse.pointTransactionNo(), 301)
        ));

        assertEquals(ErrorCode.CANCEL_AMOUNT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void useCancelRestoresAllocationsInUsePriorityOrder() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-ASC-EARN-A"), null,
                EarnType.NORMAL, 1_000, "P10D"));
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-ASC-EARN-B"), null,
                EarnType.NORMAL, 500, "P10D"));
        UseResponse useResponse = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-CANCEL-ASC-USE"), null, 1_200));

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
    void useCancelCreatesRestoreEarnWhenOriginalEarnIsExpired() {
        String memberId = memberId();
        EarnResponse earnA = pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-EXPIRED-EARN-A"),
                null, EarnType.NORMAL, 1_000, "P10D"));
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-EXPIRED-EARN-B"),
                null, EarnType.NORMAL, 500, "P10D"));
        UseResponse useResponse = pointFacadeService.use(
                memberId,
                new UseRequest(orderNo("USE-CANCEL-EXPIRED-USE"), null, 1_200)
        );
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

    private void expireEarn(String ptxno) {
        jdbcTemplate.update(
                "update POINT.PNT_EARN_MST set EXP_DT = ? where PTXNO = ?",
                LocalDateTime.now().minusSeconds(1),
                ptxno
        );
    }
}
