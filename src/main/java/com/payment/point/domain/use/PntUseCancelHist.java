package com.payment.point.domain.use;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POINT.PNT_USE_CANCEL_HIST - 사용취소 상세 이력
 */
@Getter
@Entity
@Table(name = "PNT_USE_CANCEL_HIST", schema = "POINT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PntUseCancelHist {

    /** 사용취소 상세 이력번호, PK */
    @Id
    @Column(name = "USE_CNCL_HIST_ID", length = 26, nullable = false)
    private String useCancelHistId;

    /** 사용취소 거래번호 */
    @Column(name = "USE_CNCL_PTXNO", length = 26, nullable = false)
    private String useCancelPtxno;

    /** 원 사용 거래번호 */
    @Column(name = "USE_PTXNO", length = 26, nullable = false)
    private String usePtxno;

    /** 원 사용 Allocation 번호 */
    @Column(name = "USE_ALLOC_ID", length = 26, nullable = false)
    private String useAllocId;

    /** 회원아이디 */
    @Column(name = "MEMBER_ID", length = 32, nullable = false)
    private String memberId;

    /** 원 사용건 내 취소 순번 */
    @Column(name = "CNCL_SEQ", nullable = false)
    private Integer cancelSequence;

    /** 원 적립 거래번호 */
    @Column(name = "ORG_EARN_PTXNO", length = 26, nullable = false)
    private String originalEarnPtxno;

    /** 신규 RESTORE 적립 거래번호 nullable */
    @Column(name = "RESTORE_PTXNO", length = 26)
    private String restorePtxno;

    /** 복원 유형 */
    @Enumerated(EnumType.STRING)
    @Column(name = "RESTORE_TYPE", length = 30, nullable = false)
    private RestoreType restoreType;

    /** 취소 금액 */
    @Column(name = "CNCL_AMT", nullable = false)
    private Long cancelAmount;

    /** 생성일시 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PntUseCancelHist(String useCancelHistId, String useCancelPtxno, String usePtxno, String useAllocId,
            String memberId, Integer cancelSequence, String originalEarnPtxno, String restorePtxno,
            RestoreType restoreType, Long cancelAmount) {
        this.useCancelHistId = useCancelHistId;
        this.useCancelPtxno = useCancelPtxno;
        this.usePtxno = usePtxno;
        this.useAllocId = useAllocId;
        this.memberId = memberId;
        this.cancelSequence = cancelSequence;
        this.originalEarnPtxno = originalEarnPtxno;
        this.restorePtxno = restorePtxno;
        this.restoreType = restoreType;
        this.cancelAmount = cancelAmount;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
