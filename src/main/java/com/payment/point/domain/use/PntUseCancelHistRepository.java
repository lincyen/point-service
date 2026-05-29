package com.payment.point.domain.use;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PntUseCancelHistRepository extends JpaRepository<PntUseCancelHist, String> {

    List<PntUseCancelHist> findByUsePtxnoOrderByCancelSequenceAsc(String usePtxno);

    List<PntUseCancelHist> findByUseCancelPtxno(String useCancelPtxno);

    @Query("select coalesce(max(h.cancelSequence), 0) from PntUseCancelHist h where h.usePtxno = :usePtxno")
    int findMaxCancelSequence(@Param("usePtxno") String usePtxno);
}
