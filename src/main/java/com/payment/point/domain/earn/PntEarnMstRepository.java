package com.payment.point.domain.earn;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PntEarnMstRepository extends JpaRepository<PntEarnMst, String> {

    @Query("""
            select e
            from PntEarnMst e
            where e.memberId = :memberId
              and e.status = com.payment.point.domain.earn.EarnStatus.ACTIVE
              and e.remainingAmount > 0
              and e.expireAt > :baseDtm
            order by
              case when e.earnType = com.payment.point.domain.earn.EarnType.MANUAL then 0 else 1 end,
              e.expireAt asc,
              e.createdAt asc
            """)
    List<PntEarnMst> findUsableEarns(@Param("memberId") String memberId, @Param("baseDtm") LocalDateTime baseDtm);

    List<PntEarnMst> findByMemberIdOrderByExpireAtAsc(String memberId);

    @Query("""
            select e
            from PntEarnMst e
            where e.status = com.payment.point.domain.earn.EarnStatus.ACTIVE
              and e.remainingAmount > 0
              and e.expireAt <= :baseDtm
            order by e.memberId asc, e.expireAt asc, e.createdAt asc
            """)
    List<PntEarnMst> findExpirableEarns(@Param("baseDtm") LocalDateTime baseDtm);
}
