package com.payment.point.domain.earn;

import com.payment.point.config.PointPolicyProperties;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import com.payment.point.support.PointIdGenerator;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 포인트 적립 원장 도메인 서비스.
 *
 * <p>적립 정책 검증, 적립 원장 생성/조회, 만료 대상 조회, 사용취소 시 RESTORE 적립 생성을 담당한다.</p>
 */
@Service
public class PointEarnService {

    private final PntEarnMstRepository pntEarnMstRepository;
    private final PointPolicyProperties pointPolicyProperties;
    private final PointIdGenerator pointIdGenerator;

    public PointEarnService(
            PntEarnMstRepository pntEarnMstRepository,
            PointPolicyProperties pointPolicyProperties,
            PointIdGenerator pointIdGenerator
    ) {
        this.pntEarnMstRepository = pntEarnMstRepository;
        this.pointPolicyProperties = pointPolicyProperties;
        this.pointIdGenerator = pointIdGenerator;
    }

    /**
     * 적립 금액이 정책상 허용 범위에 있는지 검증한다.
     *
     * @param amount 적립 금액
     */
    public void validateAmountPolicy(long amount) {
        validatePositive(amount);
        PointPolicyProperties.Earn earnPolicy = pointPolicyProperties.earn();
        if (amount < earnPolicy.minAmount() || amount > earnPolicy.maxAmount()) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
    }

    /**
     * 금액이 양수인지 검증한다.
     *
     * @param amount 검증 대상 금액
     */
    public void validatePositive(long amount) {
        if (amount <= 0) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
    }

    /**
     * 요청 만료 기간과 적립 정책을 기준으로 실제 만료일시를 계산한다.
     *
     * @param requestedExpirePeriod ISO-8601 period 형식의 요청 만료 기간
     * @param now 적립 처리 기준 시각
     * @return 계산된 포인트 만료일시
     */
    public LocalDateTime resolveExpireAt(String requestedExpirePeriod, LocalDateTime now) {
        PointPolicyProperties.Earn earnPolicy = pointPolicyProperties.earn();
        Period expirePeriod = parseExpirePeriod(
                requestedExpirePeriod == null ? earnPolicy.defaultExpirePeriod() : requestedExpirePeriod
        );
        LocalDateTime expireAt = now.plus(expirePeriod);
        LocalDateTime minExpireAt = now.plus(parseExpirePeriod(earnPolicy.minExpirePeriod()));
        LocalDateTime maxExpireAt = now.plus(parseExpirePeriod(earnPolicy.maxExpirePeriod()));

        if (expireAt.isBefore(minExpireAt) || !expireAt.isBefore(maxExpireAt)) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
        return expireAt;
    }

    /**
     * 신규 적립 원장을 생성한다.
     *
     * @param ptxno 적립 거래번호
     * @param memberId 회원 식별자
     * @param earnType 적립 유형
     * @param amount 적립 금액
     * @param expireAt 만료일시
     * @return 저장된 적립 원장
     */
    public PntEarnMst createEarn(String ptxno, String memberId, EarnType earnType, long amount,
            LocalDateTime expireAt) {
        PntEarnMst earn = new PntEarnMst(ptxno, memberId, earnType, amount, expireAt);
        return pntEarnMstRepository.save(earn);
    }

    /**
     * 적립취소 가능한 원 적립 원장을 조회하고 취소 가능 여부를 검증한다.
     *
     * @param memberId 회원 식별자
     * @param originalPtxno 원 적립 거래번호
     * @param requestAmount 요청 적립취소 금액
     * @return 적립취소 대상 원장
     */
    public PntEarnMst findEarnForCancel(String memberId, String originalPtxno, long requestAmount) {
        PntEarnMst earn = pntEarnMstRepository.findById(originalPtxno)
                .filter(value -> value.getMemberId().equals(memberId))
                .orElseThrow(() -> new ApiException(ErrorCode.NO_POINT_HISTORY));

        if (earn.getUseAmount() > 0) {
            throw new ApiException(ErrorCode.INCORRECT_POINT);
        }
        if (earn.getCancelAmount() > 0) {
            throw new ApiException(ErrorCode.ALREADY_CANCELED);
        }
        if (earn.getRemainingAmount() != requestAmount) {
            throw new ApiException(ErrorCode.PARTIAL_CANCEL_FAIL);
        }
        return earn;
    }

    /**
     * 사용 가능한 적립 원장을 사용 우선순위대로 조회한다.
     *
     * @param memberId 회원 식별자
     * @param baseDtm 사용 가능 여부 판단 기준 시각
     * @return 사용 가능한 적립 원장 목록
     */
    public List<PntEarnMst> findUsableEarns(String memberId, LocalDateTime baseDtm) {
        return pntEarnMstRepository.findUsableEarns(memberId, baseDtm);
    }

    /**
     * 만료 처리 대상 적립 원장을 조회한다.
     *
     * @param baseDtm 만료 기준 시각
     * @return 만료 대상 적립 원장 목록
     */
    public List<PntEarnMst> findExpirableEarns(LocalDateTime baseDtm) {
        return pntEarnMstRepository.findExpirableEarns(baseDtm);
    }

    /**
     * 사용 Allocation에 연결된 원 적립 원장을 조회한다.
     *
     * @param earnPtxno 원 적립 거래번호
     * @return 원 적립 원장
     */
    public PntEarnMst findOriginalEarn(String earnPtxno) {
        return pntEarnMstRepository.findById(earnPtxno)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_POINT_HISTORY));
    }

    /**
     * 만료된 원 적립건의 사용취소 금액을 신규 RESTORE 적립으로 생성한다.
     *
     * @param memberId 회원 식별자
     * @param amount 복원 금액
     * @param now RESTORE 적립 생성 기준 시각
     * @return 생성된 RESTORE 적립 거래번호
     */
    public String createRestoreEarn(String memberId, long amount, LocalDateTime now) {
        String restorePtxno = pointIdGenerator.generatePtxno();
        LocalDateTime restoreExpireAt = now.plus(parseExpirePeriod(pointPolicyProperties.earn().defaultExpirePeriod()));
        pntEarnMstRepository.save(new PntEarnMst(restorePtxno, memberId, EarnType.RESTORE, amount, restoreExpireAt));
        return restorePtxno;
    }

    /**
     * ISO-8601 period 문자열을 Java {@link Period}로 파싱한다.
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
