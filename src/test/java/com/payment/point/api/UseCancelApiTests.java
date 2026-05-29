package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.use.UseCancelRequest;
import com.payment.point.api.use.UseCancelResponse;
import com.payment.point.api.use.UseRequest;
import com.payment.point.api.use.UseResponse;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.domain.use.PntUseAlloc;
import com.payment.point.domain.use.PntUseAllocRepository;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UseCancelApiTests extends PointApiTestSupport {

    @Autowired
    private PntUseAllocRepository pntUseAllocRepository;

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
}
