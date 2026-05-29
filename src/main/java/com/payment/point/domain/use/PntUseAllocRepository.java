package com.payment.point.domain.use;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PntUseAllocRepository extends JpaRepository<PntUseAlloc, String> {

    List<PntUseAlloc> findByPtxnoOrderByPriorityAsc(String ptxno);

    List<PntUseAlloc> findByPtxnoAndRemainingAmountGreaterThanOrderByPriorityAsc(String ptxno, Long remainingAmount);

    List<PntUseAlloc> findByEarnPtxno(String earnPtxno);
}
