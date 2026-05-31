package com.payment.point.domain.earn;

import java.util.List;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PntEarnMstRepository extends JpaRepository<PntEarnMst, String> {

    /**
     * <b>사용 가능한 적립 거래 조회 (JPQL)</b>
     * <pre>
     *     해당 회원의 적립 상태가 ACTIVE 이고 잔액이 있는 거래건 중 만료일이 지나지 않은 건만 조회
     *     정책에 따라
     *     1. 관리자 수기 지급 포인트
     *     2. 만료일 짧은 순
     *     3. 생성일 짧은 순
     *     4. 포인트 거래번호 짧은 순
     * </pre>
     * @param memberId 회원아이디
     * @param baseDate 사용 가능 여부 판단 기준일
     * @return 적립원장(sort 순)
     */
    @Query(value = """
            select e
            from PntEarnMst e
            where e.memberId = :memberId
              and e.status = com.payment.point.domain.earn.EarnStatus.ACTIVE
              and e.remainingAmount > 0
              and e.expireDate > :baseDate
            order by
              case when e.earnType = com.payment.point.domain.earn.EarnType.MANUAL then 0 else 1 end,
              e.expireDate asc,
              e.createdAt asc,
              e.ptxno asc
            """)
    List<PntEarnMst> findUsableEarns(@Param("memberId") String memberId, @Param("baseDate") LocalDate baseDate);

    @Query("""
            select e
            from PntEarnMst e
            where e.memberId = :memberId
              and e.status = com.payment.point.domain.earn.EarnStatus.ACTIVE
              and e.remainingAmount > 0
              and e.expireDate <= :baseDate
            order by e.expireDate asc, e.createdAt asc
            """)
    List<PntEarnMst> findExpirableEarns(
            @Param("memberId") String memberId,
            @Param("baseDate") LocalDate baseDate
    );

    @Query("""
            select min(e.expireDate)
            from PntEarnMst e
            where e.memberId = :memberId
              and e.status = com.payment.point.domain.earn.EarnStatus.ACTIVE
              and e.remainingAmount > 0
            """)
    LocalDate findNextExpireDate(@Param("memberId") String memberId);
}
