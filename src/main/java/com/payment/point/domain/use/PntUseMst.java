package com.payment.point.domain.use;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POINT.PNT_USE_MST - 사용 원장
 */
@Getter
@Entity
@Table(name = "PNT_USE_MST", schema = "POINT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PntUseMst {

    /** 사용 거래번호, PK */
    @Id
    @Column(name = "PTXNO", length = 26, nullable = false)
    private String ptxno;

    /** 회원 식별자 */
    @Column(name = "MEMBER_ID", length = 32, nullable = false)
    private String memberId;

    /** 클라이언트 주문번호 */
    @Column(name = "ORDER_NO", length = 100, nullable = false)
    private String orderNo;

    /** 최초 사용 금액 */
    @Column(name = "USE_AMT", nullable = false)
    private Long useAmount;

    /** 누적 사용취소 금액 */
    @Column(name = "CNCL_AMT", nullable = false)
    private Long cancelAmount;

    /** 남은 취소 가능 금액 */
    @Column(name = "RMN_AMT", nullable = false)
    private Long remainingAmount;

    /** 사용 거래 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private UseStatus status;

    /** 생성일시 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정일시 */
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public PntUseMst(String ptxno, String memberId, String orderNo, Long useAmount) {
        this.ptxno = ptxno;
        this.memberId = memberId;
        this.orderNo = orderNo;
        this.useAmount = useAmount;
        this.cancelAmount = 0L;
        this.remainingAmount = useAmount;
        this.status = UseStatus.ACTIVE;
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
            throw new IllegalArgumentException("use cancel amount exceeds remaining amount");
        }
        this.cancelAmount += amount;
        this.remainingAmount -= amount;
        this.status = this.remainingAmount == 0 ? UseStatus.CNCL : UseStatus.PARTIAL_CNCL;
    }
}
