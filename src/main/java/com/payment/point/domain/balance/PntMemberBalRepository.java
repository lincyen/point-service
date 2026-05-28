package com.payment.point.domain.balance;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PntMemberBalRepository extends JpaRepository<PntMemberBal, String> {

    Optional<PntMemberBal> findByMemberId(String memberId);
}
