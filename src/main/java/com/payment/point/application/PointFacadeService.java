package com.payment.point.application;

import com.payment.point.api.balance.BalanceResponse;
import com.payment.point.api.common.ExpireRequest;
import com.payment.point.api.common.ExpireResponse;
import com.payment.point.api.earn.EarnCancelRequest;
import com.payment.point.api.earn.EarnCancelResponse;
import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.api.history.HistoryResponse;
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
import com.payment.point.support.MemberPointLocked;
import com.payment.point.support.PointIdGenerator;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 API Facade 서비스.
 *
 * <p>트랜잭션 경계를 관리하고 도메인 서비스를 조합한다. 현재 신규 Facade는 적립과 적립취소 API를 우선 제공한다.</p>
 */
@Service
@AllArgsConstructor
public class PointFacadeService {

    private final PointBalanceService pointBalanceService;
    private final PointEarnService pointEarnService;
    private final PointTransactionService pointTransactionService;
    private final PointIdGenerator pointIdGenerator;

    /**
     * 포인트 적립을 처리한다.
     *
     * <p>동일 회원 요청 락은 AOP로 적용되고, 본 메서드는 단일 DB 트랜잭션 안에서
     * 잔액 생성/증가, 적립 원장 생성, 거래 이력 생성을 수행한다.</p>
     *
     * @param memberId 회원 식별자
     * @param request 적립 요청
     * @return 적립 응답
     */
    @MemberPointLocked
    @Transactional
    public EarnResponse earn(String memberId, EarnRequest request) {
        pointEarnService.validateAmountPolicy(request.amount());
        pointTransactionService.validateDuplicateOrder(memberId, request.orderNo());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = pointEarnService.resolveExpireAt(request.expirePeriod(), now);
        String ptxno = pointIdGenerator.generatePtxno();

        PntMemberBal balance = pointBalanceService.getOrCreateBalance(memberId);
        pointBalanceService.increaseBalance(balance, request.earnType(), request.amount());
        pointBalanceService.validateMaxBalance(balance);

        pointEarnService.createEarn(ptxno, memberId, request.earnType(), request.amount(), expireAt);
        pointTransactionService.appendTransaction(ptxno, ptxno, memberId, request.orderNo(), request.orderDtm(),
                TxType.EARN, request.amount(), balance.getTotalAmount(), expireAt);

        return new EarnResponse(ptxno, memberId, request.amount(), balance.getTotalAmount());
    }

    /**
     * 포인트 적립취소를 처리한다.
     *
     * <p>동일 회원 요청 락은 AOP로 적용되고, 본 메서드는 단일 DB 트랜잭션 안에서
     * 원 적립 원장 취소, 잔액 감소, 거래 이력 생성을 수행한다.</p>
     *
     * @param memberId 회원 식별자
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
        PntEarnMst earn = pointEarnService.findEarnForCancel(memberId, request.originalPtxno(), request.amount(), now);

        long cancelAmount = earn.getRemainingAmount();
        pointBalanceService.decreaseBalance(balance, earn.getEarnType(), cancelAmount);
        earn.cancelEarn();

        String ptxno = pointIdGenerator.generatePtxno();
        pointTransactionService.appendTransaction(ptxno, earn.getPtxno(), memberId, request.orderNo(),
                request.orderDtm(), TxType.EARN_CNCL, cancelAmount, balance.getTotalAmount(), null);

        return new EarnCancelResponse(ptxno, memberId, cancelAmount, balance.getTotalAmount());
    }

    @Transactional
    public UseResponse use(String memberId, UseRequest request) {
        throw new UnsupportedOperationException("TODO: migrate use from OLD_PointFacadeService");
    }

    @Transactional
    public UseCancelResponse cancelUse(String memberId, UseCancelRequest request) {
        throw new UnsupportedOperationException("TODO: migrate use cancel from OLD_PointFacadeService");
    }

    @Transactional
    public ExpireResponse expire(ExpireRequest request) {
        throw new UnsupportedOperationException("TODO: migrate expire from OLD_PointFacadeService");
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String memberId) {
        throw new UnsupportedOperationException("TODO: migrate balance query from OLD_PointFacadeService");
    }

    @Transactional(readOnly = true)
    public HistoryResponse getHistories(String memberId) {
        throw new UnsupportedOperationException("TODO: migrate history query from OLD_PointFacadeService");
    }
}
