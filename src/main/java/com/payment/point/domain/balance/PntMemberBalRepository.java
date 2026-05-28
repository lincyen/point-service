package com.payment.point.domain.balance;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PntMemberBalRepository extends JpaRepository<PntMemberBal, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PntMemberBal> findByMemberId(String memberId);
}
