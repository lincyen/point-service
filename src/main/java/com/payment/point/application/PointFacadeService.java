package com.payment.point.application;

import com.payment.point.api.balance.BalanceResponse;
import com.payment.point.api.common.ExpireRequest;
import com.payment.point.api.common.ExpireResponse;
import com.payment.point.api.earn.EarnCancelRequest;
import com.payment.point.api.earn.EarnCancelResponse;
import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.api.history.HistoryResponse;
import com.payment.point.api.history.TransactionLookupResponse;
import com.payment.point.api.use.UseCancelRequest;
import com.payment.point.api.use.UseCancelResponse;
import com.payment.point.api.use.UseRequest;
import com.payment.point.api.use.UseResponse;
import com.payment.point.domain.balance.PntMemberBal;
import com.payment.point.domain.balance.PointBalanceService;
import com.payment.point.domain.earn.PntEarnMst;
import com.payment.point.domain.earn.PointEarnService;
import com.payment.point.domain.transaction.PointTransactionService;
import com.payment.point.domain.transaction.TxType;
import com.payment.point.domain.use.PntUseAlloc;
import com.payment.point.domain.use.PntUseMst;
import com.payment.point.domain.use.PointUseService;
import com.payment.point.domain.use.RestoreType;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import com.payment.point.support.MemberPointLocked;
import com.payment.point.support.PointIdGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 API Facade 서비스
 * <pre>
 *     공통 : 포인트 변경 기능의 경우 MemberPointLocked AOP 를 통해 회원 기반 락을 수행한다
 *     트랜잭션 경계를 관리하고 적립, 적립취소, 사용, 사용취소, 만료, 조회 API에 필요한 도메인 서비스를 조합한다.
 * </pre>
 */
@Service
@AllArgsConstructor
public class PointFacadeService {

    private final PointBalanceService pointBalanceService;
    private final PointEarnService pointEarnService;
    private final PointUseService pointUseService;
    private final PointTransactionService pointTransactionService;
    private final PointIdGenerator pointIdGenerator;

    /**
     * <b>포인트 적립 요청</b>
     * <pre>
     *     1. 적립 요청 타입 검증(null, RESTORE 비허용)
     *     2. 1회 적립 요청 금액 정책 valid
     *     3. 금액 양수 체크, 주문번호 중복체크
     *     4. 만료일 생성 및 포인트 거래번호 생성
     *     5. 잔액 조회 및 없을 경우 신규 생성, 회원별 포인트 잔액 증가, 회원별 최대 잔액 valid
     *     6. 적립 원장 생성 및 거래이력 등록
     * </pre>
     * @param memberId 회원번호
     * @param request {@link EarnRequest 포인트 적립 요청}
     * @return 포인트 적립 응답
     */
    @MemberPointLocked
    @Transactional
    public EarnResponse earn(String memberId, EarnRequest request) {
        pointEarnService.validateRequestEarnType(request.earnType());
        pointEarnService.validateAmountPolicy(request.amount());
        pointEarnService.validatePositive(request.amount());
        pointTransactionService.validateDuplicateOrder(memberId, request.orderNo());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = pointEarnService.resolveExpireAt(request.expirePeriod(), now);
        String pointTransactionNo = pointIdGenerator.generatePointTransactionNo();

        PntMemberBal balance = pointBalanceService.getOrCreateBalance(memberId);
        pointBalanceService.increaseBalance(balance, request.earnType(), request.amount());
        pointBalanceService.validateMaxBalance(balance);

        PntEarnMst earn = pointEarnService.createEarn(pointTransactionNo, memberId, request.earnType(), request.amount(), expireAt);
        pointTransactionService.appendEarnTransaction(earn, request.orderNo(), request.orderDtm(), balance.getTotalAmount());

        return new EarnResponse(pointTransactionNo, memberId, request.amount(), balance.getTotalAmount());
    }

    /**
     * <b>포인트 적립 취소 요청</b>
     * <pre>
     *     1. 금액 양수 체크, 주문번호 중복체크
     *     2. 회원 잔액 조회
     *     3. 적립취소 가능 여부 조회
     *     4. 회원별 포인트 잔액 감소
     *     5. 적립 원장 업데이트(JPA dirty checking) 및 거래이력 등록
     * </pre>
     *
     * @param memberId 회원아이디
     * @param request 적립취소 요청
     * @return 적립취소 응답
     */
    @MemberPointLocked
    @Transactional
    public EarnCancelResponse earnCancel(String memberId, EarnCancelRequest request) {
        pointEarnService.validatePositive(request.amount());
        pointTransactionService.validateDuplicateOrder(memberId, request.orderNo());

        LocalDateTime now = LocalDateTime.now();
        PntMemberBal balance = pointBalanceService.findBalance(memberId);
        PntEarnMst earn = pointEarnService.findEarnForCancel(memberId, request.pointTransactionNo(), request.amount(), now);

        long cancelAmount = earn.getRemainingAmount();
        pointBalanceService.decreaseBalance(balance, earn.getEarnType(), cancelAmount);
        earn.cancelEarn();

        String pointTransactionNo = pointIdGenerator.generatePointTransactionNo();
        pointTransactionService.appendEarnCancelTransaction(
                pointTransactionNo,
                earn,
                request.orderNo(),
                request.orderDtm(),
                balance.getTotalAmount()
        );

        return new EarnCancelResponse(pointTransactionNo, memberId, cancelAmount, balance.getTotalAmount());
    }

    /**
     * <b>포인트 사용 요청</b>
     * <pre>
     *     1. 금액 양수 체크, 주문번호 중복체크
     *     2. 회원 잔액을 MANUAL 우선으로 차감
     *     3. 적립 원장 차감 및 사용 Allocation 생성
     *     4. 사용 원장 및 거래 이력 등록
     * </pre>
     *
     * @param memberId 회원아이디
     * @param request 사용 요청
     * @return 사용 응답
     */
    @MemberPointLocked
    @Transactional
    public UseResponse use(String memberId, UseRequest request) {
        pointEarnService.validatePositive(request.amount());
        pointTransactionService.validateDuplicateOrder(memberId, request.orderNo());

        LocalDateTime now = LocalDateTime.now();

        PntMemberBal balance = usePointBalance(memberId, request.amount());
        String pointTransactionNo = pointIdGenerator.generatePointTransactionNo();
        organizeUseLedger(pointTransactionNo, memberId, request.amount(), now);

        pointUseService.createUse(pointTransactionNo, memberId, request.orderNo(), request.amount());
        pointTransactionService.appendUseTransaction(
                pointTransactionNo,
                memberId,
                request.orderNo(),
                request.orderDtm(),
                request.amount(),
                balance.getTotalAmount()
        );

        return new UseResponse(pointTransactionNo, memberId, request.amount(), balance.getTotalAmount());
    }

    /**
     * <b>회원 잔액 사용 처리</b>
     * <pre>
     *     회원 잔액을 조회한 뒤 사용 정책에 따라 MANUAL 우선으로 차감
     * </pre>
     *
     * @param memberId 회원아이디
     * @param useAmount 사용 금액
     * @return 차감 후 회원 잔액
     */
    private PntMemberBal usePointBalance(String memberId, long useAmount) {
        PntMemberBal balance = pointBalanceService.findBalance(memberId);
        pointBalanceService.decreaseUseBalance(balance, useAmount);
        return balance;
    }

    /**
     * <b>사용 원장 정리</b>
     * <pre>
     *    사용 우선순위에 따라 적립 원장을 차감하고, 적립 원장별 사용 Allocation을 생성
     * </pre>
     *
     * @param pointTransactionNo 사용 거래번호
     * @param memberId 회원아이디
     * @param useAmount 사용 금액
     * @param baseDtm 사용 가능 적립 원장 판단 기준 시각
     */
    private void organizeUseLedger(String pointTransactionNo, String memberId, long useAmount, LocalDateTime baseDtm) {
        List<PntEarnMst> earns = pointEarnService.findUsableEarns(memberId, baseDtm);
        long remainingUseAmount = useAmount;
        int priority = 1;
        for (PntEarnMst earn : earns) {
            if (remainingUseAmount == 0) break;
            long consumeAmount = Math.min(earn.getRemainingAmount(), remainingUseAmount);
            earn.use(consumeAmount);

            pointUseService.createAllocation(pointTransactionNo, earn.getPtxno(), memberId, priority++, consumeAmount, earn.getExpireAt());
            remainingUseAmount -= consumeAmount;
        }

        if (remainingUseAmount > 0) throw new ApiException(ErrorCode.INCORRECT_POINT);
    }

    /**
     * 포인트 사용취소를 처리한다.
     *
     * <p>동일 회원 요청 락은 AOP로 적용되고, 본 메서드는 단일 DB 트랜잭션 안에서
     * 사용 Allocation을 차감 순서대로 복원한다. 원 적립건이 만료된 경우 신규 RESTORE 적립을 생성한다.</p>
     *
     * @param memberId 회원아이디
     * @param request 사용취소 요청
     * @return 사용취소 응답
     */
    @MemberPointLocked
    @Transactional
    public UseCancelResponse cancelUse(String memberId, UseCancelRequest request) {
        pointEarnService.validatePositive(request.amount());
        pointTransactionService.validateDuplicateOrder(memberId, request.orderNo());

        LocalDateTime now = LocalDateTime.now();
        PntMemberBal balance = pointBalanceService.findBalance(memberId);
        PntUseMst useMst = pointUseService.findUseForCancel(memberId, request.originalUsePtxno(), request.amount());

        String useCancelPtxno = pointIdGenerator.generatePointTransactionNo();
        restoreUseBalanceAndAllocation(useCancelPtxno, memberId, useMst, request.amount(), balance, now);

        useMst.cancel(request.amount());
        pointBalanceService.validateMaxBalance(balance);
        pointTransactionService.appendUseCancelTransaction(
                useCancelPtxno,
                useMst.getPtxno(),
                memberId,
                request.orderNo(),
                request.orderDtm(),
                request.amount(),
                balance.getTotalAmount()
        );

        return new UseCancelResponse(useCancelPtxno, memberId, request.amount(), balance.getTotalAmount());
    }

    /**
     * <b>사용취소 잔액 및 Allocation 복원</b>
     *
     * <p>원 적립건이 만료되었으면 신규 RESTORE 적립으로 잔액을 복원하고, 만료 전이면 원 적립건을 복원한다.</p>
     *
     * @param useCancelPtxno 사용취소 거래번호
     * @param memberId 회원아이디
     * @param useMst 원 사용 원장
     * @param cancelAmount 사용취소 금액
     * @param balance 회원 잔액
     * @param baseDtm 만료 여부 판단 기준 시각
     */
    private void restoreUseBalanceAndAllocation(String useCancelPtxno, String memberId, PntUseMst useMst,
            long cancelAmount, PntMemberBal balance, LocalDateTime baseDtm) {
        List<PntUseAlloc> allocations = pointUseService.findCancelableAllocations(useMst.getPtxno());
        long remainingCancelAmount = cancelAmount;
        int cancelSequence = pointUseService.nextCancelSequence(useMst.getPtxno());

        for (PntUseAlloc allocation : allocations) {
            if (remainingCancelAmount == 0) {
                break;
            }

            long allocationCancelAmount = Math.min(allocation.getRemainingAmount(), remainingCancelAmount);
            PntEarnMst originalEarn = pointEarnService.findOriginalEarn(allocation.getEarnPtxno());

            String restorePtxno = null;
            RestoreType restoreType;
            if (originalEarn.isExpiredAt(baseDtm)) {
                restorePtxno = pointEarnService.createRestoreEarn(memberId, allocationCancelAmount, baseDtm);
                balance.increaseNormal(allocationCancelAmount);
                restoreType = RestoreType.NEW_EARN;
            } else {
                originalEarn.restoreUse(allocationCancelAmount);
                pointBalanceService.increaseBalance(balance, originalEarn.getEarnType(), allocationCancelAmount);
                restoreType = RestoreType.ORIGINAL_RESTORE;
            }

            allocation.cancel(allocationCancelAmount);
            pointUseService.saveCancelHistory(
                    useCancelPtxno,
                    useMst.getPtxno(),
                    allocation.getUseAllocId(),
                    memberId,
                    cancelSequence,
                    originalEarn.getPtxno(),
                    restorePtxno,
                    restoreType,
                    allocationCancelAmount
            );

            remainingCancelAmount -= allocationCancelAmount;
        }

        if (remainingCancelAmount > 0) {
            throw new ApiException(ErrorCode.INCORRECT_POINT);
        }
    }

    /**
     * 회원별 포인트 만료를 처리한다.
     *
     * <p>동일 회원 요청 락은 AOP로 적용되고, 기준 시각까지 만료된 회원의 적립 원장을 단일 DB 트랜잭션 안에서 처리한다.</p>
     *
     * @param memberId 회원아이디
     * @param request 만료 요청
     * @return 만료 응답
     */
    @MemberPointLocked
    @Transactional
    public ExpireResponse expire(String memberId, ExpireRequest request) {
        LocalDateTime baseDtm = request.baseDtm() == null ? LocalDateTime.now() : request.baseDtm();
        PntMemberBal balance = pointBalanceService.findBalance(memberId);
        List<PntEarnMst> expirableEarns = pointEarnService.findExpirableEarns(memberId, baseDtm);

        long expiredCount = 0;
        long expiredAmountSum = 0;

        for (PntEarnMst earn : expirableEarns) {
            long expiredAmount = earn.getRemainingAmount();
            pointBalanceService.decreaseBalance(balance, earn.getEarnType(), expiredAmount);
            balance.increaseExpired(expiredAmount);
            earn.expireAll();

            String ptxno = pointIdGenerator.generatePointTransactionNo();
            pointTransactionService.appendExpireTransaction(ptxno, earn, expiredAmount, balance.getTotalAmount());

            expiredCount++;
            expiredAmountSum += expiredAmount;
        }

        return new ExpireResponse(expiredCount, expiredAmountSum);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String memberId) {
        return pointBalanceService.getBalance(memberId);
    }

    @Transactional(readOnly = true)
    public HistoryResponse getHistories(String memberId, LocalDate startDate, LocalDate endDate, TxType txType) {
        return pointTransactionService.getHistories(memberId, startDate, endDate, txType);
    }

    @Transactional(readOnly = true)
    public TransactionLookupResponse getTransactionByOrder(String memberId, String orderNo, TxType txType) {
        return pointTransactionService.getTransactionByOrder(memberId, orderNo, txType);
    }
}
