package com.payment.point.domain.use;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POINT.PNT_USE_ALLOC - 사용 Allocation 상세
 */
@Getter
@Entity
@Table(name = "PNT_USE_ALLOC", schema = "POINT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PntUseAlloc {

    /** 사용 상세번호, PK */
    @Id
    @Column(name = "USE_ALLOC_ID", length = 26, nullable = false)
    private String useAllocId;

    /** 사용 거래번호 */
    @Column(name = "PTXNO", length = 26, nullable = false)
    private String ptxno;

    /** 사용된 적립 거래번호 */
    @Column(name = "EARN_PTXNO", length = 26, nullable = false)
    private String earnPtxno;

    /** 회원아이디 */
    @Column(name = "MEMBER_ID", length = 32, nullable = false)
    private String memberId;

    /** 사용 차감 순서 */
    @Column(name = "PRIORITY", nullable = false)
    private Integer priority;

    /** 해당 적립건에서 사용한 금액 */
    @Column(name = "CNSM_AMT", nullable = false)
    private Long consumeAmount;

    /** 해당 사용분 중 취소된 금액 */
    @Column(name = "CNCL_AMT", nullable = false)
    private Long cancelAmount;

    /** 남은 취소 가능 금액 */
    @Column(name = "RMN_AMT", nullable = false)
    private Long remainingAmount;

    /** 사용 당시 적립건 만료일 */
    @Column(name = "EXP_DT", nullable = false)
    private LocalDate expireDate;

    /** 생성일시 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정일시 */
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public PntUseAlloc(String useAllocId, String ptxno, String earnPtxno, String memberId, Integer priority,
            Long consumeAmount, LocalDate expireDate) {
        this.useAllocId = useAllocId;
        this.ptxno = ptxno;
        this.earnPtxno = earnPtxno;
        this.memberId = memberId;
        this.priority = priority;
        this.consumeAmount = consumeAmount;
        this.cancelAmount = 0L;
        this.remainingAmount = consumeAmount;
        this.expireDate = expireDate;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel(long amount) {
        if (remainingAmount < amount) {
            throw new IllegalArgumentException("allocation cancel amount exceeds remaining amount");
        }
        this.cancelAmount += amount;
        this.remainingAmount -= amount;
    }
}
