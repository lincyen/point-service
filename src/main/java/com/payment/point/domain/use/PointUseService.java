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

    /**
     * <b>사용 Allocation 저장</b>
     * @param pointTransactionNo 사용 거래번호
     * @param earnPointTransactionNo 사용된 적립 거래번호
     * @param memberId 회원아이디
     * @param priority 사용 차감 순서
     * @param consumeAmount 해당 사용분 중 취소된 금액
     * @param expireAt 사용 당시 적립건 만료일
     */
    public void createAllocation(String pointTransactionNo,
                                 String earnPointTransactionNo,
                                 String memberId,
                                 int priority,
                                 long consumeAmount,
                                 LocalDateTime expireAt) {
        pntUseAllocRepository.save(
                new PntUseAlloc(
                        pointIdGenerator.generateDetailId(),
                        pointTransactionNo,
                        earnPointTransactionNo,
                        memberId,
                        priority,
                        consumeAmount,
                        expireAt)
        );
    }

    /**
     * <b>사용 원장 등록<b/>
     * @param pointTransactionNo 사용 거래번호
     * @param memberId 회원아이디
     * @param orderNo 클라이언트 주문번호
     * @param amount 사용금액
     */
    public void createUse(String pointTransactionNo, String memberId, String orderNo, long amount) {
        pntUseMstRepository.save(new PntUseMst(pointTransactionNo, memberId, orderNo, amount));
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

    /**
     * <b>사용 거래번호 기반 잔액이 있는  사용 Allocation 목록 추출(priority order) </b>
     * @param usePointTransactionNo 사용 거래번호
     * @return 사용 Allocation 목록
     */
    public List<PntUseAlloc> findCancelableAllocations(String usePointTransactionNo) {
        return pntUseAllocRepository.findByPtxnoAndRemainingAmountGreaterThanOrderByPriorityAsc(usePointTransactionNo, 0L);
    }

    public int nextCancelSequence(String usePointTransactionNo) {
        return pntUseCancelHistRepository.findMaxCancelSequence(usePointTransactionNo) + 1;
    }

    /**
     * <b>사용취소 상세 이력 등록</b>
     * @param useCancelPointTransactionNo 사용취소 거래번호
     * @param usePointTransactionNo 원 사용 거래번호
     * @param useAllocId 원 사용 Allocation 번호
     * @param memberId 회원아이디
     * @param cancelSequence 원 사용건 내 취소 순번
     * @param originalEarnPointTransactionNo 원 적립 거래번호
     * @param restorePointTransactionNo 신규 RESTORE 적립 거래번호(nullable)
     * @param restoreType 복원 유형
     * @param cancelAmount 취소 금액
     */
    public void saveCancelHistory(String useCancelPointTransactionNo, String usePointTransactionNo,
                                  String useAllocId, String memberId,
                                  int cancelSequence, String originalEarnPointTransactionNo, String restorePointTransactionNo,
                                  RestoreType restoreType, long cancelAmount) {
        pntUseCancelHistRepository.save(new PntUseCancelHist(
                pointIdGenerator.generateDetailId(),
                useCancelPointTransactionNo,
                usePointTransactionNo,
                useAllocId,
                memberId,
                cancelSequence,
                originalEarnPointTransactionNo,
                restorePointTransactionNo,
                restoreType,
                cancelAmount
        ));
    }
}