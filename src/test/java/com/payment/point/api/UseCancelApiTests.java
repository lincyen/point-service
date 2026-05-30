package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import com.payment.point.domain.use.RestoreType;
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
    private PntEarnMstRepository pntEarnMstRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("성공-사용취소 복원")
    void useCancelRestoresPoint() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-API-EARN"), null, EarnType.NORMAL, 1_000, "P10D"));
        UseResponse useResponse = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-CANCEL-API-USE"), null, 400));

        UseCancelResponse cancelResponse = pointFacadeService.cancelUse(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-API"), null, useResponse.ptxno(), 150)
        );

        assertPointId(cancelResponse.ptxno());
        assertEquals(memberId, cancelResponse.memberId());
        assertEquals(150, cancelResponse.amount());
        assertEquals(750, cancelResponse.remainingAmount());
    }

    @Test
    void useCancelRestoresAllocationsInUsePriorityOrder() {
        String memberId = memberId();
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-ASC-EARN-A"), null,
                EarnType.NORMAL, 1_000, "P10D"));
        pointFacadeService.earn(memberId, new EarnRequest(orderNo("USE-CANCEL-ASC-EARN-B"), null,
                EarnType.NORMAL, 500, "P10D"));
        UseResponse useResponse = pointFacadeService.use(memberId, new UseRequest(orderNo("USE-CANCEL-ASC-USE"), null, 1_200));

        UseCancelResponse cancelResponse = pointFacadeService.cancelUse(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-ASC"), null, useResponse.ptxno(), 1_100)
        );

        List<PntUseAlloc> allocations = pntUseAllocRepository.findByPtxnoOrderByPriorityAsc(useResponse.ptxno());

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
        expireEarn(earnA.ptxno());

        UseCancelResponse cancelResponse = pointFacadeService.cancelUse(
                memberId,
                new UseCancelRequest(orderNo("USE-CANCEL-EXPIRED"), null, useResponse.ptxno(), 1_100)
        );

        List<PntUseAlloc> allocations = pntUseAllocRepository.findByPtxnoOrderByPriorityAsc(useResponse.ptxno());
        List<PntUseCancelHist> histories = pntUseCancelHistRepository.findByUseCancelPtxno(cancelResponse.ptxno());
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
