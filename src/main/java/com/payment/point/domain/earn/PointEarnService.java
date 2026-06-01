package com.payment.point.domain.earn;

import com.payment.point.config.PointPolicyProperties;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import com.payment.point.support.PointIdGenerator;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.List;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 포인트 적립 원장 도메인 서비스.
 *
 * <pre>
 *     적립 정책 검증, 적립 원장 생성/조회, 만료 대상 조회, 사용취소 시 RESTORE 적립 생성을 담당한다.
 * </pre>
 */
@Service
@AllArgsConstructor
public class PointEarnService {
    private final PntEarnMstRepository pntEarnMstRepository;
    private final PointPolicyProperties pointPolicyProperties;
    private final PointIdGenerator pointIdGenerator;

    /**
     * <b>1회 적립 요청 금액 valid</b>
     * <pre>
     *     적립 요청 금액은 정책 상 1회 최소/최대 한도 제한이 존재
     * </pre>
     *
     * @param amount 금액
     */
    public void validateAmountPolicy(long amount) {
        PointPolicyProperties.Earn earnPolicy = pointPolicyProperties.earn();
        if (amount < earnPolicy.minAmount() || amount > earnPolicy.maxAmount()) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
    }

    /**
     * <b>양수 금액 valid</b>
     * <pre>
     *     모든 요청은 양수로 처리하며, 적립/적립취소/사용/사용취소에 따라 연산이 정해진다
     * </pre>
     *
     * @param amount 금액
     */
    public void validatePositive(long amount) {
        if (amount <= 0) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
    }

    /**
     * <b>적립 요청 타입 검증(null, RESTORE 비허용)</b>
     *
     * @param earnType 적립 유형
     */
    public void validateRequestEarnType(EarnType earnType) {
        if (earnType == null || earnType == EarnType.RESTORE) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
    }

    /**
     * <b>만료일 계산</b>
     * <pre>
     *     요청 만료 기간과 처리 기준일을 기준으로 LocalDate 만료일을 계산
     *     정책 상 최소/최대 만료일에서 벗어나는 경우 Exception 처리
     * </pre>
     *
     * @param requestedExpirePeriod ISO-8601 period 형식의 요청 만료 기간
     * @param baseDate 처리 기준일
     * @return 계산된 포인트 만료일
     */
    public LocalDate resolveExpireDate(String requestedExpirePeriod, LocalDate baseDate) {
        PointPolicyProperties.Earn earnPolicy = pointPolicyProperties.earn();
        Period expirePeriod = parseExpirePeriod(
                requestedExpirePeriod == null ? earnPolicy.defaultExpirePeriod() : requestedExpirePeriod
        );
        LocalDate expireDate = baseDate.plus(expirePeriod);
        LocalDate minExpireDate = baseDate.plus(parseExpirePeriod(earnPolicy.minExpirePeriod()));
        LocalDate maxExpireDate = baseDate.plus(parseExpirePeriod(earnPolicy.maxExpirePeriod()));

        if (expireDate.isBefore(minExpireDate) || !expireDate.isBefore(maxExpireDate)) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
        return expireDate;
    }

    /**
     * <b>신규 적립 수행</b>
     *
     * @param pointTransactionNo 적립 거래번호
     * @param memberId 회원아이디
     * @param earnType 적립 유형
     * @param amount 적립 금액
     * @param expireDate 만료일
     * @return 저장된 적립 원장
     */
    public PntEarnMst createEarn(String pointTransactionNo, String memberId, EarnType earnType, long amount,
            LocalDate expireDate) {
        PntEarnMst earn = new PntEarnMst(pointTransactionNo, memberId, earnType, amount, expireDate);
        return pntEarnMstRepository.save(earn);
    }

    /**
     * <b>적립취소 가능 여부 조회</b>
     * <pre>
     *     거래정보가 존재하지 않으면 NO_POINT_HISTORY
     *     사용포인트가 존재하면 INCORRECT_POINT
     *     적립 상태가 취소이면 ALREADY_CANCELED
     *     기준일 기준 만료일을 지났거나, 만료 금액이 있거나, 적립상태가 만료이면 EXPIRED_POINT
     *     요청금액과 잔액이 다르면 PARTIAL_CANCEL_FAIL
     * </pre>
     *
     * @param memberId 회원아이디
     * @param pointTransactionNo 적립 거래번호
     * @param requestAmount 요청 적립취소 금액
     * @param baseDate 만료 여부 판단 기준일
     * @return 적립취소 대상 원장
     */
    public PntEarnMst findEarnForCancel(String memberId, String pointTransactionNo, long requestAmount, LocalDate baseDate) {
        PntEarnMst earn = pntEarnMstRepository.findById(pointTransactionNo)
                .filter(value -> value.getMemberId().equals(memberId))
                .orElseThrow(() -> new ApiException(ErrorCode.NO_POINT_HISTORY));

        if (earn.getUseAmount() > 0) {
            throw new ApiException(ErrorCode.INCORRECT_POINT);
        }
        if (earn.getStatus() == EarnStatus.CNCL) {
            throw new ApiException(ErrorCode.ALREADY_CANCELED);
        }
        if (earn.isExpiredOn(baseDate) || earn.getExpiredAmount() > 0 || earn.getStatus() == EarnStatus.EXPIRED) {
            throw new ApiException(ErrorCode.EXPIRED_POINT);
        }
        if (earn.getRemainingAmount() != requestAmount) {
            throw new ApiException(ErrorCode.PARTIAL_CANCEL_FAIL);
        }
        return earn;
    }

    /**
     * <b>사용 가능 적립 거래 조회</b>
     * <pre>
     *     정책에 따라 사용 순서를 정의, 정의된 순서로 응답
     * </pre>
     *
     * @param memberId 회원아이디
     * @param baseDate 사용 가능 여부 판단 기준일
     * @return 사용 가능한 적립 원장 목록
     */
    public List<PntEarnMst> findUsableEarns(String memberId, LocalDate baseDate) {
        return pntEarnMstRepository.findUsableEarns(memberId, baseDate);
    }

    /**
     * <b>회원별 만료 처리 대상 조회</b>
     *
     * @param memberId 회원아이디
     * @param baseDate 만료 기준일
     * @return 회원별 만료 대상 적립 원장 목록
     */
    public List<PntEarnMst> findExpirableEarns(String memberId, LocalDate baseDate) {
        return pntEarnMstRepository.findExpirableEarns(memberId, baseDate);
    }

    public LocalDate findNextExpireDate(String memberId) {
        return pntEarnMstRepository.findNextExpireDate(memberId);
    }

    /**
     * <b>사용 Allocation에 연결된 원 적립 원장 일괄 조회</b>
     *
     * @param earnPointTransactionNos 원 적립 거래번호 목록
     * @return 원 적립 원장 목록
     */
    public List<PntEarnMst> findAllOriginalEarns(List<String> earnPointTransactionNos) {
        return pntEarnMstRepository.findAllById(earnPointTransactionNos);
    }

    /**
     * <b>만료된 원 적립 건의 사용취소 금액을 신규 RESTORE 적립으로 생성</b>
     * <pre>
     *     신규 적립 건의 만료일은 기본 만료일로 설정한다.
     * </pre>
     * @param memberId 회원아이디
     * @param amount 복원 금액
     * @param baseDate RESTORE 적립 생성 기준일
     * @return 생성된 RESTORE 적립 원장
     */
    public PntEarnMst createRestoreEarn(String memberId, long amount, LocalDate baseDate) {
        String restorePointTransactionNo = pointIdGenerator.generatePointTransactionNo();
        LocalDate restoreExpireDate = baseDate.plus(parseExpirePeriod(pointPolicyProperties.earn().defaultExpirePeriod()));
        return pntEarnMstRepository.save(new PntEarnMst(restorePointTransactionNo, memberId, EarnType.RESTORE, amount, restoreExpireDate));
    }

    /**
     * <b>ISO-8601 period 문자열을 Java {@link Period}로 parse</b>
     *
     * @param expirePeriod ISO-8601 period 문자열
     * @return 파싱된 만료 기간
     */
    private Period parseExpirePeriod(String expirePeriod) {
        try {
            Period parsed = Period.parse(expirePeriod);
            if (parsed.isZero() || parsed.isNegative()) {
                throw new ApiException(ErrorCode.INVALID_PARAMETER);
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
    }
}
