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
 * <pre>
 *     회원 잔액 row 생성/조회, 회원 존재 여부 검증, 잔액 증감, 최대 보유 가능 포인트 검증을 담당한다.
 * </pre>
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
     * <b>회원 잔액 조회</b>
     * <pre>
     *     회원 잔액을 조회하고, 없으면 신규 생성한다.
     * </pre>
     *
     * @param memberId 회원아이디
     * @return 조회되었거나 신규 생성된 회원 잔액
     */
    public PntMemberBal getOrCreateBalance(String memberId) {
        return pntMemberBalRepository.findById(memberId)
                .orElseGet(() -> pntMemberBalRepository.saveAndFlush(new PntMemberBal(memberId)));
    }

    /**
     * <b>회원 잔액 조회</b>
     * <pre>
     *     회원정보가 없으면 INVALID_USER 처리
     * </pre>
     *
     * @param memberId 회원아이디
     * @return 회원 잔액
     */
    public PntMemberBal findBalance(String memberId) {
        return pntMemberBalRepository.findById(memberId).orElseThrow(() -> new ApiException(ErrorCode.INVALID_USER));
    }

    /**
     * <b>회원 존재 여부 valid</b>
     * <pre>
     *     회원 잔액 row가 없으면 INVALID_USER 처리
     * </pre>
     *
     * @param memberId 회원아이디
     */
    public void validateMember(String memberId) {
        if (!pntMemberBalRepository.existsById(memberId)) {
            throw new ApiException(ErrorCode.INVALID_USER);
        }
    }

    /**
     * <b>회원 잔액 조회</b>
     *
     * @param memberId 회원아이디
     * @return 회원 잔액 응답
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
     * <b>적립 유형별 잔액 증가</b>
     *
     * @param balance 회원 잔액
     * @param earnType 적립 유형
     * @param amount 증가 금액
     */
    public void increaseBalance(PntMemberBal balance, EarnType earnType, long amount) {
        switch (earnType) {
            case MANUAL -> balance.increaseManual(amount);
            case NORMAL, RESTORE -> balance.increaseNormal(amount);
            case null -> throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
    }

    /**
     * <b>적립 유형별 잔액 감소</b>
     *
     * @param balance 회원 잔액
     * @param earnType 적립 유형
     * @param amount 감소 금액
     */
    public void decreaseBalance(PntMemberBal balance, EarnType earnType, long amount) {
        try {
            switch (earnType) {
                case MANUAL -> balance.decreaseManual(amount);
                case NORMAL, RESTORE -> balance.decreaseNormal(amount);
                case null -> throw new ApiException(ErrorCode.INVALID_PARAMETER);
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INCORRECT_POINT);
        }
    }

    /**
     * <b>사용 요청 잔액 차감</b>
     * <pre>
     *     사용 정책에 따라 관리자 수기 지급 포인트를 먼저 차감하고, 부족분은 일반 포인트에서 차감한다.
     * </pre>
     *
     * @param balance 회원 잔액
     * @param amount 사용 금액
     */
    public void decreaseUseBalance(PntMemberBal balance, long amount) {
        if (balance.getTotalAmount() < amount) throw new ApiException(ErrorCode.NOT_ENOUGH_POINT);

        long manualUseAmount = Math.min(balance.getManualAmount(), amount);
        long normalUseAmount = amount - manualUseAmount;

        try {
            if (manualUseAmount > 0) {
                balance.decreaseManual(manualUseAmount);
            }
            if (normalUseAmount > 0) {
                balance.decreaseNormal(normalUseAmount);
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INCORRECT_POINT);
        }
    }

    /**
     * <b>최대 보유 가능 잔액 valid</b>
     *
     * @param balance 회원별 포인트 잔액
     */
    public void validateMaxBalance(PntMemberBal balance) {
        if (balance.getTotalAmount() > pointPolicyProperties.member().maxBalanceAmount()) {
            throw new ApiException(ErrorCode.INCORRECT_POINT);
        }
    }

}
