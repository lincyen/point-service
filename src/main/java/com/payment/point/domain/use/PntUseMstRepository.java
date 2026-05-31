package com.payment.point.domain.use;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PntUseMstRepository extends JpaRepository<PntUseMst, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PntUseMst> findByPtxno(String ptxno);
}
