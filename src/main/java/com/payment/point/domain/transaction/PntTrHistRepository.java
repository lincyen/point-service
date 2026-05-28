package com.payment.point.domain.transaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PntTrHistRepository extends JpaRepository<PntTrHist, String> {

    List<PntTrHist> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<PntTrHist> findByMemberIdAndOrderNo(String memberId, String orderNo);

    List<PntTrHist> findByMemberIdAndOptxno(String memberId, String optxno);

    boolean existsByMemberIdAndOrderNo(String memberId, String orderNo);
}
