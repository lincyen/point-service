package com.payment.point.domain.expire;

import com.payment.point.api.expire.ExpireResponse;
import com.payment.point.domain.balance.PntMemberBal;
import com.payment.point.domain.balance.PointBalanceService;
import com.payment.point.domain.earn.PntEarnMst;
import com.payment.point.domain.earn.PointEarnService;
import com.payment.point.domain.transaction.PointTransactionService;
import com.payment.point.support.PointIdGenerator;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 포인트 만료 도메인 서비스
 *
 * <pre>
 *     회원별 다음 만료 예정일 Snapshot을 관리하고, 만료 대상 적립 원장을 정리한다.
 * </pre>
 */
@Service
@AllArgsConstructor
public class PointExpireService {

    private final PointEarnService pointEarnService;
    private final PointBalanceService pointBalanceService;
    private final PointTransactionService pointTransactionService;
    private final PointIdGenerator pointIdGenerator;

    /**
     * <b>적립 후 다음 만료 예정일 설정</b>
     * @param balance 잔액정보
     * @param earnedExpireDate 적립건 만료 예정일
     */
    public void updateNextExpireDateAfterEarn(PntMemberBal balance, LocalDate earnedExpireDate) {
        updateNextExpireDateIfEarlier(balance, earnedExpireDate);
    }

    /**
     * <b>적립 취소 후 다음 만료 예정일 설정</b>
     * @param balance 잔액정보
     * @param canceledEarn 적립취소 대상 적립원장
     */
    public void updateNextExpireDateAfterEarnCancel(PntMemberBal balance, PntEarnMst canceledEarn) {
        if (canceledEarn.getExpireDate().equals(balance.getNextExpireDate())) {
            recalculateNextExpireDate(balance);
        }
    }

    /**
     * <b>사용 전 만료 예정일 도래 여부 확인 및 만료 처리</b>
     * @param memberId 회원아이디
     * @param balance 회원별 포인트 잔액
     * @param baseDate 기준일
     */
    public void expireMemberBeforeUseIfRequired(String memberId, PntMemberBal balance, LocalDate baseDate) {
        if (balance.isExpirationDueOn(baseDate)) {
            expireMember(memberId, balance, baseDate);
        }
    }

    /**
     * <b>사용 후 다음 만료 예정일 설정</b>
     * @param balance 잔액정보
     */
    public void updateNextExpireDateAfterUse(PntMemberBal balance) {
        recalculateNextExpireDate(balance);
    }

    /**
     * <b>사용취소 후 만료 예정일 복원</b>
     * @param balance 잔액정보
     * @param restoredExpireDate 복원대상 만료예정일
     */
    public void updateNextExpireDateAfterUseCancel(PntMemberBal balance, LocalDate restoredExpireDate) {
        updateNextExpireDateIfEarlier(balance, restoredExpireDate);
    }

    /**
     * <b>회원별 포인트 만료 처리</b>
     * <pre>
     *     1. 기준일까지 만료된 ACTIVE 적립 원장을 조회
     *     2. 적립 원장 잔액 전체를 만료 처리하고 만료 금액을 획득
     *     3. 적립 유형에 따라 회원 잔액을 차감하고 누적 만료 금액을 증가
     *     4. 적립 원장별 만료 거래번호 생성 및 거래 이력 등록
     *     5. 남은 ACTIVE 적립 원장을 기준으로 회원별 다음 만료 예정일 재계산
     *     6. 만료 처리 건수와 만료 금액 합계 응답
     * </pre>
     *
     * @param memberId 회원아이디
     * @param balance 회원별 포인트 잔액
     * @param baseDate 만료 처리 기준일
     * @return 만료 처리 건수와 만료 금액 합계
     */
    public ExpireResponse expireMember(String memberId, PntMemberBal balance, LocalDate baseDate) {
        List<PntEarnMst> expirableEarns = pointEarnService.findExpirableEarns(memberId, baseDate);
        long expiredCount = 0;
        long expiredAmountSum = 0;

        for (PntEarnMst earn : expirableEarns) {
            long expiredAmount = earn.expireAll();
            pointBalanceService.decreaseBalance(balance, earn.getEarnType(), expiredAmount);
            balance.increaseExpired(expiredAmount);

            String pointTransactionNo = pointIdGenerator.generatePointTransactionNo();
            pointTransactionService.appendExpireTransaction(pointTransactionNo, earn, expiredAmount, balance.getTotalAmount());

            expiredCount++;
            expiredAmountSum += expiredAmount;
        }

        recalculateNextExpireDate(balance);
        return new ExpireResponse(expiredCount, expiredAmountSum);
    }

    private void updateNextExpireDateIfEarlier(PntMemberBal balance, LocalDate candidateExpireDate) {
        if (candidateExpireDate == null) {
            return;
        }
        LocalDate nextExpireDate = balance.getNextExpireDate();
        if (nextExpireDate == null || candidateExpireDate.isBefore(nextExpireDate)) {
            balance.updateNextExpireDate(candidateExpireDate);
        }
    }

    private void recalculateNextExpireDate(PntMemberBal balance) {
        balance.updateNextExpireDate(pointEarnService.findNextExpireDate(balance.getMemberId()));
    }
}
