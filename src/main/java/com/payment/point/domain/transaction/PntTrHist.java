package com.payment.point.domain.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * POINT.PNT_TR_HIST - 포인트 거래 이력
 */
@Entity
@Table(name = "PNT_TR_HIST", schema = "POINT")
public class PntTrHist {

    /** 거래번호 */
    @Id
    @Column(name = "PTXNO", length = 26, nullable = false, updatable = false)
    private String ptxno;

    /** 원 거래번호 */
    @Column(name = "OPTXNO", length = 26, nullable = false, updatable = false)
    private String optxno;

    /** 회원 식별자 */
    @Column(name = "MEMBER_ID", length = 26, nullable = false, updatable = false)
    private String memberId;

    /** 클라이언트 주문번호 */
    @Column(name = "ORDER_NO", length = 100, updatable = false)
    private String orderNo;

    /** 클라이언트 주문/요청 시각 */
    @Column(name = "ORDER_DTM", updatable = false)
    private LocalDateTime orderDtm;

    /** 거래 유형 */
    @Enumerated(EnumType.STRING)
    @Column(name = "TX_TYPE", length = 20, nullable = false, updatable = false)
    private TxType txType;

    /** 거래 금액 */
    @Column(name = "TX_AMT", nullable = false, updatable = false)
    private Long txAmount;

    /** 거래 후 총 잔액 */
    @Column(name = "RMN_AMT", nullable = false, updatable = false)
    private Long remainingAmount;

    /** 거래 당시 만료일 스냅샷 */
    @Column(name = "EXP_DT", updatable = false)
    private LocalDateTime expireAt;

    /** 서버 생성 시각 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PntTrHist() {
    }

    public PntTrHist(String ptxno, String optxno, String memberId, String orderNo, LocalDateTime orderDtm,
            TxType txType, Long txAmount, Long remainingAmount, LocalDateTime expireAt) {
        this.ptxno = ptxno;
        this.optxno = optxno;
        this.memberId = memberId;
        this.orderNo = orderNo;
        this.orderDtm = orderDtm;
        this.txType = txType;
        this.txAmount = txAmount;
        this.remainingAmount = remainingAmount;
        this.expireAt = expireAt;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public String getPtxno() {
        return ptxno;
    }

    public String getOptxno() {
        return optxno;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public LocalDateTime getOrderDtm() {
        return orderDtm;
    }

    public TxType getTxType() {
        return txType;
    }

    public Long getTxAmount() {
        return txAmount;
    }

    public Long getRemainingAmount() {
        return remainingAmount;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
