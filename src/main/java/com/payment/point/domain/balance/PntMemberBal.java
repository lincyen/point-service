package com.payment.point.domain.balance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POINT.PNT_MEMBER_BAL - 회원별 포인트 잔액
 */
@Getter
@Entity
@Table(name = "PNT_MEMBER_BAL", schema = "POINT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PntMemberBal {

    /** 회원 식별자 (ULID) */
    @Id
    @Column(name = "MEMBER_ID", length = 26, nullable = false)
    private String memberId;

    /** 일반 적립 포인트 잔액 */
    @Column(name = "NORMAL_AMT", nullable = false)
    private Long normalAmount;

    /** 관리자 수기 지급 포인트 잔액 */
    @Column(name = "MANUAL_AMT", nullable = false)
    private Long manualAmount;

    /** 누적 만료 포인트 금액 */
    @Column(name = "EXPIRED_AMT", nullable = false)
    private Long expiredAmount;

    /** 낙관적 락 버전 */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    /** 생성일시 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정일시 */
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public PntMemberBal(String memberId) {
        this.memberId = memberId;
        this.normalAmount = 0L;
        this.manualAmount = 0L;
        this.expiredAmount = 0L;
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

    public long getTotalAmount() {
        return normalAmount + manualAmount;
    }

    public void increaseNormal(long amount) {
        this.normalAmount += amount;
    }

    public void increaseManual(long amount) {
        this.manualAmount += amount;
    }

    public void decreaseNormal(long amount) {
        if (this.normalAmount < amount) {
            throw new IllegalArgumentException("normal point balance is not enough");
        }
        this.normalAmount -= amount;
    }

    public void decreaseManual(long amount) {
        if (this.manualAmount < amount) {
            throw new IllegalArgumentException("manual point balance is not enough");
        }
        this.manualAmount -= amount;
    }

    public void increaseExpired(long amount) {
        this.expiredAmount += amount;
    }
}
