package com.payment.point.domain.balance;

import com.payment.point.api.balance.BalanceResponse;
import com.payment.point.config.PointPolicyProperties;
import com.payment.point.domain.earn.EarnType;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 회원별 포인트 잔액 도메인 서비스.
 *
 * <p>회원 잔액 row 생성/조회, 잔액 증감, 최대 보유 가능 포인트 검증을 담당한다.</p>
 */
@Service
public class PointBalanceService {

    private final PntMemberBalRepository pntMemberBalRepository;
    private final PointPolicyProperties pointPolicyProperties;

    public PointBalanceService(
            PntMemberBalRepository pntMemberBalRepository,
            PointPolicyProperties pointPolicyProperties
    ) {
        this.pntMemberBalRepository = pntMemberBalRepository;
        this.pointPolicyProperties = pointPolicyProperties;
    }

    /**
     * 회원 잔액을 조회하고, 없으면 신규 생성한다.
     *
     * @param memberId 회원 식별자
     * @return 조회되었거나 신규 생성된 회원 잔액
     */
    public PntMemberBal getOrCreateBalance(String memberId) {
        return pntMemberBalRepository.findById(memberId)
                .orElseGet(() -> pntMemberBalRepository.saveAndFlush(new PntMemberBal(memberId)));
    }

    /**
     * 회원 잔액을 조회한다. 회원 row 가 없는 경우 "유효하지 않은 회원입니다." 처리
     *
     * @param memberId 회원 식별자
     * @return 회원 잔액
     */
    public PntMemberBal findBalance(String memberId) {
        return pntMemberBalRepository.findById(memberId).orElseThrow(() -> new ApiException(ErrorCode.INVALID_USER));
    }

    /**
     * 회원 잔액 조회 응답을 생성한다.
     *
     * @param memberId 회원 식별자
     * @return 회원 잔액 응답 DTO
     */
    public BalanceResponse getBalance(String memberId) {
        PntMemberBal balance = findBalance(memberId);

        return new BalanceResponse(
                balance.getMemberId(),
                balance.getNormalAmount(),
                balance.getManualAmount(),
                balance.getExpiredAmount(),
                balance.getTotalAmount()
        );
    }

    /**
     * 적립 유형에 맞는 잔액 항목을 증가시킨다.
     *
     * @param balance 회원 잔액
     * @param earnType 적립 유형
     * @param amount 증가 금액
     */
    public void increaseBalance(PntMemberBal balance, EarnType earnType, long amount) {
        if (earnType == EarnType.MANUAL) {
            balance.increaseManual(amount);
            return;
        }
        balance.increaseNormal(amount);
    }

    /**
     * 적립 유형에 맞는 잔액 항목을 감소시킨다.
     *
     * @param balance 회원 잔액
     * @param earnType 적립 유형
     * @param amount 감소 금액
     */
    public void decreaseBalance(PntMemberBal balance, EarnType earnType, long amount) {
        try {
            if (earnType == EarnType.MANUAL) {
                balance.decreaseManual(amount);
                return;
            }
            balance.decreaseNormal(amount);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INCORRECT_POINT);
        }
    }

    /**
     * 회원의 총 사용 가능 잔액이 정책상 최대 보유 금액을 넘지 않는지 검증한다.
     *
     * @param balance 회원 잔액
     */
    public void validateMaxBalance(PntMemberBal balance) {
        if (balance.getTotalAmount() > pointPolicyProperties.member().maxBalanceAmount()) {
            throw new ApiException(ErrorCode.INCORRECT_POINT);
        }
    }

}
