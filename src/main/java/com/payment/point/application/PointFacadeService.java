package com.payment.point.application;

import com.payment.point.api.balance.BalanceResponse;
import com.payment.point.api.expire.ExpireRequest;
import com.payment.point.api.expire.ExpireResponse;
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
import com.payment.point.domain.expire.PointExpireService;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 API Facade 서비스
 * <pre>
 *     공통: 포인트 변경 기능은 MemberPointLocked AOP를 통해 회원아이디 기반 락을 수행한다.
 *     트랜잭션 경계를 관리하고 적립, 적립취소, 사용, 사용취소, 만료, 조회 API에 필요한 도메인 서비스를 조합한다.
 * </pre>
 */
@Service
@AllArgsConstructor
public class PointFacadeService {

    private final PointBalanceService pointBalanceService;
    private final PointEarnService pointEarnService;
    private final PointExpireService pointExpireService;
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
     *     6. 적립 원장 생성
     *     7. 회원별 다음 만료 예정일 갱신
     *     8. 적립 거래 이력 등록
     * </pre>
     * @param memberId 회원아이디
     * @param request {@link EarnRequest 포인트 적립 요청}
     * @return 포인트 적립 응답
     */
    @MemberPointLocked
    @Transactional
    public EarnResponse earn(String memberId, EarnRequest request) {
        pointEarnService.validateRequestEarnType(request.earnType());
        pointEarnService.validateAmountPolicy(request.amount());
        pointEarnService.validatePositive(request.amount());
        pointTransactionService.validateDuplicateOrder(memberId, request.orderNo(), TxType.EARN);

        LocalDateTime now = LocalDateTime.now();
        LocalDate expireDate = pointEarnService.resolveExpireDate(request.expirePeriod(), now.toLocalDate());
        String pointTransactionNo = pointIdGenerator.generatePointTransactionNo();

        PntMemberBal balance = pointBalanceService.getOrCreateBalance(memberId);
        pointBalanceService.increaseBalance(balance, request.earnType(), request.amount());
        pointBalanceService.validateMaxBalance(balance);

        PntEarnMst earn = pointEarnService.createEarn(pointTransactionNo, memberId, request.earnType(), request.amount(), expireDate);
        pointExpireService.updateNextExpireDateAfterEarn(balance, expireDate);
        pointTransactionService.appendEarnTransaction(earn, request.orderNo(), request.orderDtm(), balance.getTotalAmount());

        return new EarnResponse(pointTransactionNo, memberId, request.amount(), balance.getTotalAmount());
    }

    /**
     * <b>포인트 적립 취소 요청</b>
     * <pre>
     *     1. 금액 양수 체크, 주문번호 중복체크
     *     2. 회원 잔액 조회
     *     3. 적립취소 가능 여부 조회 및 valid
     *     4. 회원별 포인트 잔액 감소
     *     5. 적립 원장 취소 처리(JPA dirty checking)
     *     6. 회원별 다음 만료 예정일 재계산
     *     7. 적립취소 거래번호 생성 및 거래 이력 등록
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
        pointTransactionService.validateDuplicateOrder(memberId, request.orderNo(), TxType.EARN_CNCL);

        LocalDate now = LocalDate.now();
        PntMemberBal balance = pointBalanceService.findBalance(memberId);
        PntEarnMst earn = pointEarnService.findEarnForCancel(memberId, request.pointTransactionNo(), request.amount(), now);

        long cancelAmount = earn.getRemainingAmount();
        pointBalanceService.decreaseBalance(balance, earn.getEarnType(), cancelAmount);
        earn.cancelEarn();
        pointExpireService.updateNextExpireDateAfterEarnCancel(balance, earn);

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
     *     2. 회원 잔액 조회
     *     3. 다음 만료 예정일이 현재일 이전이거나 같으면 회원별 만료 선처리
     *     4. 회원 잔액을 MANUAL 우선으로 차감
     *     5. 포인트 거래번호 생성
     *     6. 사용 원장 등록
     *     7. 적립 원장 차감 및 사용 Allocation 생성
     *     8. 회원별 다음 만료 예정일 재계산
     *     9. 사용 거래 이력 등록
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
        pointTransactionService.validateDuplicateOrder(memberId, request.orderNo(), TxType.USE);

        LocalDate now = LocalDate.now();

        PntMemberBal balance = pointBalanceService.findBalance(memberId);
        pointExpireService.expireMemberBeforeUseIfRequired(memberId, balance, now);
        pointBalanceService.decreaseUseBalance(balance, request.amount());
        String pointTransactionNo = pointIdGenerator.generatePointTransactionNo();
        pointUseService.createUse(pointTransactionNo, memberId, request.orderNo(), request.amount());
        organizeUseLedger(pointTransactionNo, memberId, request.amount(), now);
        pointExpireService.updateNextExpireDateAfterUse(balance);

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
     * <b>적립원장 차감 및 사용 원장 정리</b>
     * <pre>
     *     1. 사용 가능한 적립 원장을 우선순위 순서로 조회
     *     2. 요청 금액이 충족될 때까지 적립 원장을 차감
     *     3. 적립 원장별 사용 Allocation 생성
     *     4. 적립 원장 합계가 요청 금액보다 부족하면 INCORRECT_POINT
     * </pre>
     *
     * @param pointTransactionNo 사용 거래번호
     * @param memberId 회원아이디
     * @param useAmount 사용 금액
     * @param baseDate 사용 가능 적립 원장 판단 기준일
     */
    private void organizeUseLedger(String pointTransactionNo, String memberId, long useAmount, LocalDate baseDate) {
        List<PntEarnMst> earnMasters = pointEarnService.findUsableEarns(memberId, baseDate);
        long remainingUseAmount = useAmount;
        int priority = 1;
        for (PntEarnMst earnMst : earnMasters) {
            if (remainingUseAmount == 0) break;
            long consumeAmount = Math.min(earnMst.getRemainingAmount(), remainingUseAmount);
            earnMst.use(consumeAmount);

            pointUseService.createAllocation(pointTransactionNo, earnMst.getPtxno(), memberId, priority++, consumeAmount, earnMst.getExpireDate());
            remainingUseAmount -= consumeAmount;
        }

        if (remainingUseAmount > 0) throw new ApiException(ErrorCode.INCORRECT_POINT);
    }

    /**
     * <b>포인트 사용취소 요청</b>
     * <pre>
     *     1. 금액 양수 체크, 주문번호 중복체크
     *     2. 회원 잔액 조회
     *     3. 사용취소 가능 여부 조회
     *     4. 사용취소 거래번호 생성
     *     5. 사용취소 금액만큼 회원 잔액 및 사용 Allocation 복원
     *     6. 복원된 적립 원장을 기준으로 회원별 다음 만료 예정일 갱신
     *     7. 사용 원장 취소 처리(JPA dirty checking) 및 회원별 최대 잔액 valid
     *     8. 사용취소 거래 이력 등록
     * </pre>
     *
     * @param memberId 회원아이디
     * @param request 사용취소 요청
     * @return 사용취소 응답
     */
    @MemberPointLocked
    @Transactional
    public UseCancelResponse useCancel(String memberId, UseCancelRequest request) {
        pointEarnService.validatePositive(request.amount());
        pointTransactionService.validateDuplicateOrder(memberId, request.orderNo(), TxType.USE_CNCL);

        LocalDate now = LocalDate.now();
        PntMemberBal balance = pointBalanceService.findBalance(memberId);
        PntUseMst useMst = pointUseService.findUseForCancel(memberId, request.pointTransactionNo(), request.amount());

        String useCancelPointTransactionNo = pointIdGenerator.generatePointTransactionNo();
        LocalDate restoredExpireDate = restoreUseBalanceAndAllocation(
                useCancelPointTransactionNo, memberId, useMst, request.amount(), balance, now
        );
        pointExpireService.updateNextExpireDateAfterUseCancel(balance, restoredExpireDate);

        useMst.cancel(request.amount());
        pointBalanceService.validateMaxBalance(balance);
        pointTransactionService.appendUseCancelTransaction(
                useCancelPointTransactionNo,
                useMst.getPtxno(),
                memberId,
                request.orderNo(),
                request.orderDtm(),
                request.amount(),
                balance.getTotalAmount()
        );

        return new UseCancelResponse(useCancelPointTransactionNo, memberId, request.amount(), balance.getTotalAmount());
    }

    /**
     * <b>사용취소 잔액 및 Allocation 복원</b>
     *
     * <pre>
     *     1. 사용 거래번호 기반 잔액이 있는 사용 Allocation 목록 추출(priority order)
     *     2. 사용 Allocation과 연결된 원 적립 원장을 일괄 조회
     *     3. 취소 순번 획득
     *     4-1. 원 적립건이 만료되었으면 신규 RESTORE 적립으로 잔액을 복원
     *     4-2. 원 적립건이 만료 전이면 원적립건을 복원
     *     5. 사용 Allocation 취소 처리 및 사용취소 상세 이력 등록
     *     6. 복원된 적립 원장 중 가장 빠른 만료일 반환
     * </pre>
     *
     * @param useCancelPointTransactionNo 사용취소 거래번호
     * @param memberId 회원아이디
     * @param useMst 원 사용 원장
     * @param cancelAmount 사용취소 금액
     * @param balance 회원 잔액
     * @param baseDate 만료 여부 판단 기준일
     * @return 복원된 적립 원장 중 가장 빠른 만료일
     */
    private LocalDate restoreUseBalanceAndAllocation(String useCancelPointTransactionNo, String memberId, PntUseMst useMst,
            long cancelAmount, PntMemberBal balance, LocalDate baseDate) {
        List<PntUseAlloc> allocations = pointUseService.findCancelableAllocations(useMst.getPtxno());
        Map<String, PntEarnMst> originalEarnMap = pointEarnService.findAllOriginalEarns(
                        allocations.stream()
                                .map(PntUseAlloc::getEarnPtxno)
                                .distinct()
                                .toList()
                ).stream()
                .collect(Collectors.toMap(PntEarnMst::getPtxno, Function.identity()));
        long remainingCancelAmount = cancelAmount;
        int cancelSequence = pointUseService.nextCancelSequence(useMst.getPtxno()); //취소 순번
        LocalDate restoredExpireDate = null;

        for (PntUseAlloc allocation : allocations) {
            if (remainingCancelAmount == 0) {
                break;
            }

            long allocationCancelAmount = Math.min(allocation.getRemainingAmount(), remainingCancelAmount);
            PntEarnMst originalEarn = originalEarnMap.get(allocation.getEarnPtxno());
            if (originalEarn == null) {
                throw new ApiException(ErrorCode.NO_POINT_HISTORY);
            }

            String restorePtxno = null;
            RestoreType restoreType;
            if (originalEarn.isExpiredOn(baseDate)) {
                PntEarnMst restoreEarn = pointEarnService.createRestoreEarn(memberId, allocationCancelAmount, baseDate);
                restorePtxno = restoreEarn.getPtxno();
                balance.increaseNormal(allocationCancelAmount);
                restoreType = RestoreType.NEW_EARN;
                restoredExpireDate = earlierOf(restoredExpireDate, restoreEarn.getExpireDate());
            } else {
                originalEarn.restoreUse(allocationCancelAmount);
                pointBalanceService.increaseBalance(balance, originalEarn.getEarnType(), allocationCancelAmount);
                restoreType = RestoreType.ORIGINAL_RESTORE;
                restoredExpireDate = earlierOf(restoredExpireDate, originalEarn.getExpireDate());
            }

            allocation.cancel(allocationCancelAmount);
            pointUseService.saveCancelHistory(
                    useCancelPointTransactionNo,
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
        return restoredExpireDate;
    }

    private LocalDate earlierOf(LocalDate left, LocalDate right) {
        return left == null || right.isBefore(left) ? right : left;
    }

    /**
     * <b>포인트 만료 요청</b>
     * <pre>
     *     1. 회원 잔액 조회
     *     2. 회원별 만료 처리 대상 조회
     *     3. 적립 원장별 잔여 금액만큼 회원 잔액 감소 및 누적 만료 금액 증가
     *     4. 적립 원장 만료 처리(JPA dirty checking)
     *     5. 만료 거래번호 생성 및 만료 거래 이력 등록
     *     6. 회원별 다음 만료 예정일 재계산
     *     7. 만료 처리 건수와 만료 금액 합계 응답
     * </pre>
     *
     * @param memberId 회원아이디
     * @param request 만료 요청
     * @return 만료 응답
     */
    @MemberPointLocked
    @Transactional
    public ExpireResponse expire(String memberId, ExpireRequest request) {
        LocalDate baseDate = request.baseDate() == null ? LocalDate.now() : request.baseDate();
        PntMemberBal balance = pointBalanceService.findBalance(memberId);
        return pointExpireService.expireMember(memberId, balance, baseDate);
    }

    /**
     * <b>회원 잔액 조회</b>
     * @param memberId 회원아이디
     * @return 포인트 잔액 조회 응답
     */
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String memberId) {
        return pointBalanceService.getBalance(memberId);
    }

    /**
     * <b>포인트 거래 이력 조회</b>
     * <pre>
     *     1. 이력 조회 기간 valid
     *     2. 회원 잔액 row 존재 여부 valid
     *     3. 회원아이디, 조회 기간, 거래 유형을 기준으로 거래 이력 조회
     *     4. 조회 결과가 없으면 NO_HISTORY_RESULT
     * </pre>
     * @param memberId 회원아이디
     * @param startDate 조회 시작일
     * @param endDate 조회 종료일
     * @param txType {@link TxType 포인트 거래 유형} (optional)
     * @return 포인트 거래 이력 조회 응답
     */
    @Transactional(readOnly = true)
    public HistoryResponse getHistories(String memberId, LocalDate startDate, LocalDate endDate, TxType txType) {
        pointTransactionService.validateHistorySearchPeriod(startDate, endDate);
        pointBalanceService.validateMember(memberId);
        return pointTransactionService.getHistories(memberId, startDate, endDate, txType);
    }

    /**
     * <b>주문번호 기반 포인트 거래 조회</b>
     * <pre>
     *     1. 회원 잔액 row 존재 여부 valid
     *     2. 회원아이디, 주문번호, 거래 유형으로 거래 이력 조회
     *     3. 거래가 없으면 exists=false 응답
     * </pre>
     *
     * @param memberId 회원아이디
     * @param orderNo 주문번호
     * @param txType {@link TxType 포인트 거래 유형}
     * @return 주문번호 기반 포인트 거래 조회 응답
     */
    @Transactional(readOnly = true)
    public TransactionLookupResponse getTransactionByOrder(String memberId, String orderNo, TxType txType) {
        pointBalanceService.validateMember(memberId);
        return pointTransactionService.getTransactionByOrder(memberId, orderNo, txType);
    }
}
