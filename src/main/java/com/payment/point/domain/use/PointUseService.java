package com.payment.point.domain.use;

import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import com.payment.point.support.PointIdGenerator;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class PointUseService {

    private final PntUseMstRepository pntUseMstRepository;
    private final PntUseAllocRepository pntUseAllocRepository;
    private final PntUseCancelHistRepository pntUseCancelHistRepository;
    private final PointIdGenerator pointIdGenerator;

    public PntUseAlloc createAllocation(String usePtxno, String earnPtxno, String memberId, int priority,
            long consumeAmount, LocalDateTime expireAt) {
        return pntUseAllocRepository.save(new PntUseAlloc(
                pointIdGenerator.generateDetailId(),
                usePtxno,
                earnPtxno,
                memberId,
                priority,
                consumeAmount,
                expireAt
        ));
    }

    public PntUseMst createUse(String ptxno, String memberId, String orderNo, long amount) {
        return pntUseMstRepository.save(new PntUseMst(ptxno, memberId, orderNo, amount));
    }

    /**
     * <b>사용취소를 위한 원장 조회</b>
     * @param memberId 회원아이디
     * @param originalPointTransactionNo 취소 대상 사용 거래번호
     * @param cancelAmount 사용취소금액
     * @return 사용 원장
     */
    public PntUseMst findUseForCancel(String memberId, String originalPointTransactionNo, long cancelAmount) {
        PntUseMst useMst = pntUseMstRepository.findByPtxno(originalPointTransactionNo)
                .filter(value -> value.getMemberId().equals(memberId))
                .orElseThrow(() -> new ApiException(ErrorCode.NO_POINT_HISTORY));

        if (useMst.getRemainingAmount() <= 0) {
            throw new ApiException(ErrorCode.NO_REMAIN_POINT);
        }
        if (useMst.getRemainingAmount() < cancelAmount) {
            throw new ApiException(ErrorCode.CANCEL_AMOUNT_EXCEEDED);
        }
        return useMst;
    }

    public List<PntUseAlloc> findCancelableAllocations(String usePtxno) {
        return pntUseAllocRepository.findByPtxnoAndRemainingAmountGreaterThanOrderByPriorityAsc(usePtxno, 0L);
    }

    public int nextCancelSequence(String usePtxno) {
        return pntUseCancelHistRepository.findMaxCancelSequence(usePtxno) + 1;
    }

    public void saveCancelHistory(String useCancelPtxno, String usePtxno, String useAllocId, String memberId,
            int cancelSequence, String originalEarnPtxno, String restorePtxno, RestoreType restoreType,
            long cancelAmount) {
        pntUseCancelHistRepository.save(new PntUseCancelHist(
                pointIdGenerator.generateDetailId(),
                useCancelPtxno,
                usePtxno,
                useAllocId,
                memberId,
                cancelSequence,
                originalEarnPtxno,
                restorePtxno,
                restoreType,
                cancelAmount
        ));
    }
}
