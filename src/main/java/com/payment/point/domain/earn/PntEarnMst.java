package com.payment.point.domain.earn;

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
 * POINT.PNT_EARN_MST - 적립 원장
 */
@Getter
@Entity
@Table(name = "PNT_EARN_MST", schema = "POINT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PntEarnMst {

    /** 적립 거래번호, PK */
    @Id
    @Column(name = "PTXNO", length = 26, nullable = false)
    private String ptxno;

    /** 회원아이디 */
    @Column(name = "MEMBER_ID", length = 32, nullable = false)
    private String memberId;

    /** 적립 유형 */
    @Enumerated(EnumType.STRING)
    @Column(name = "EARN_TYPE", length = 20, nullable = false)
    private EarnType earnType;

    /** 최초 적립 금액 */
    @Column(name = "EARN_AMT", nullable = false)
    private Long earnAmount;

    /** 현재 사용 가능 잔액 */
    @Column(name = "RMN_AMT", nullable = false)
    private Long remainingAmount;

    /** 누적 사용 금액 */
    @Column(name = "USE_AMT", nullable = false)
    private Long useAmount;

    /** 적립취소 금액 */
    @Column(name = "EARN_CNCL_AMT", nullable = false)
    private Long earnCancelAmount;

    /** 만료 금액 */
    @Column(name = "EXPIRED_AMT", nullable = false)
    private Long expiredAmount;

    /** 최초 부여 만료일 */
    @Column(name = "FIRST_EXP_DT", nullable = false)
    private LocalDateTime firstExpireAt;

    /** 현재 유효 만료일 */
    @Column(name = "EXP_DT", nullable = false)
    private LocalDateTime expireAt;

    /** 현재 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private EarnStatus status;

    /** 생성일시 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정일시 */
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public PntEarnMst(String ptxno, String memberId, EarnType earnType, Long earnAmount, LocalDateTime expireAt) {
        this.ptxno = ptxno;
        this.memberId = memberId;
        this.earnType = earnType;
        this.earnAmount = earnAmount;
        this.remainingAmount = earnAmount;
        this.useAmount = 0L;
        this.earnCancelAmount = 0L;
        this.expiredAmount = 0L;
        this.firstExpireAt = expireAt;
        this.expireAt = expireAt;
        this.status = EarnStatus.ACTIVE;
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

    public boolean isManual() {
        return earnType == EarnType.MANUAL;
    }

    public boolean isExpiredAt(LocalDateTime baseDtm) {
        return !expireAt.isAfter(baseDtm);
    }

    /**
     * <b>포인트 사용</b>
     * @param amount 사용금액
     */
    public void use(long amount) {
        if (status != EarnStatus.ACTIVE || remainingAmount < amount) {
            throw new IllegalArgumentException("earn point is not usable");
        }
        this.remainingAmount -= amount;
        this.useAmount += amount;
        if (this.remainingAmount == 0) {
            this.status = EarnStatus.USED_UP;
        }
    }

    /**
     * <b>적립취소 설정</b>
     */
    public void cancelEarn() {
        if (useAmount > 0 || status == EarnStatus.CNCL) {
            throw new IllegalArgumentException("earn point cannot be canceled");
        }
        this.earnCancelAmount = earnAmount;
        this.remainingAmount = 0L;
        this.status = EarnStatus.CNCL;
    }

    public void restoreUse(long amount) {
        if (expiredAmount > 0 || status == EarnStatus.EXPIRED) {
            throw new IllegalArgumentException("expired earn point cannot be restored");
        }
        if (useAmount < amount) {
            throw new IllegalArgumentException("restore amount exceeds used amount");
        }
        this.useAmount -= amount;
        this.remainingAmount += amount;
        this.status = EarnStatus.ACTIVE;
    }

    public long expireAll() {
        if (remainingAmount <= 0 || status != EarnStatus.ACTIVE) {
            return 0L;
        }
        long expired = remainingAmount;
        this.expiredAmount += expired;
        this.remainingAmount = 0L;
        this.status = EarnStatus.EXPIRED;
        return expired;
    }
}
