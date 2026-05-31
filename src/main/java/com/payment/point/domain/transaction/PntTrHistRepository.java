package com.payment.point.domain.transaction;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PntTrHistRepository extends JpaRepository<PntTrHist, String> {
    @Query("""
            select h
            from PntTrHist h
            where h.memberId = :memberId
              and h.createdAt >= :startAt
              and h.createdAt < :endAt
              and (:txType is null or h.txType = :txType)
            order by h.createdAt desc
            """)
    List<PntTrHist> findHistories(
            @Param("memberId") String memberId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("txType") TxType txType
    );

    List<PntTrHist> findByMemberIdAndOrderNo(String memberId, String orderNo);

    boolean existsByMemberIdAndOrderNo(String memberId, String orderNo);
}
