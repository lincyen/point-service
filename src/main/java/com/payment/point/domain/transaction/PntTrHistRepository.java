package com.payment.point.domain.transaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    Optional<PntTrHist> findByMemberIdAndOrderNoAndTxType(String memberId, String orderNo, TxType txType);

    boolean existsByMemberIdAndOrderNoAndTxType(String memberId, String orderNo, TxType txType);
}
