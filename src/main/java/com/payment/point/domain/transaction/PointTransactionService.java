package com.payment.point.domain.transaction;

import com.payment.point.api.history.HistoryResponse;
import com.payment.point.api.history.TransactionLookupResponse;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 포인트 거래 이력 도메인 서비스.
 *
 * <p>주문번호 중복 검증, 거래 이력 append, 회원별 거래 이력 조회를 담당한다.</p>
 */
@Service
public class PointTransactionService {

    private final PntTrHistRepository pntTrHistRepository;

    public PointTransactionService(PntTrHistRepository pntTrHistRepository) {
        this.pntTrHistRepository = pntTrHistRepository;
    }

    /**
     * 동일 회원의 주문번호가 이미 거래 이력에 존재하는지 검증한다.
     *
     * @param memberId 회원 식별자
     * @param orderNo 클라이언트 주문번호
     */
    public void validateDuplicateOrder(String memberId, String orderNo) {
        if (orderNo != null && pntTrHistRepository.existsByMemberIdAndOrderNo(memberId, orderNo)) {
            throw new ApiException(ErrorCode.DUPLICATED_ORDER);
        }
    }

    /**
     * 포인트 거래 이력을 append-only 방식으로 저장한다.
     *
     * @param ptxno 포인트 거래번호
     * @param optxno 원 포인트 거래번호
     * @param memberId 회원 식별자
     * @param orderNo 클라이언트 주문번호
     * @param orderDtm 클라이언트 주문/요청 시각
     * @param txType 거래 유형
     * @param txAmount 거래 금액
     * @param remainingAmount 거래 후 회원 총 잔액
     * @param expireAt 거래 당시 만료일 스냅샷
     */
    public void appendTransaction(String ptxno, String optxno, String memberId, String orderNo,
            LocalDateTime orderDtm, TxType txType, long txAmount, long remainingAmount, LocalDateTime expireAt) {
        pntTrHistRepository.save(new PntTrHist(
                ptxno,
                optxno,
                memberId,
                orderNo,
                orderDtm == null ? LocalDateTime.now() : orderDtm,
                txType,
                txAmount,
                remainingAmount,
                expireAt
        ));
    }

    /**
     * 회원의 포인트 거래 이력을 최신순으로 조회한다.
     *
     * @param memberId 회원 식별자
     * @return 포인트 거래 이력 응답 DTO
     */
    public HistoryResponse getHistories(String memberId) {
        List<HistoryResponse.Item> histories = pntTrHistRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(history -> new HistoryResponse.Item(
                        history.getPtxno(),
                        history.getOptxno(),
                        history.getOrderNo(),
                        history.getOrderDtm(),
                        history.getTxType(),
                        history.getTxAmount(),
                        history.getRemainingAmount(),
                        history.getExpireAt(),
                        history.getCreatedAt()
                ))
                .toList();

        return new HistoryResponse(memberId, histories);
    }

    /**
     * 회원과 주문번호로 거래 처리 여부를 조회한다.
     *
     * <p>현재 주문번호는 동일 회원 내 모든 포인트 거래 유형에서 중복을 허용하지 않으므로,
     * 거래 유형은 선택 필터로만 사용한다.</p>
     *
     * @param memberId 회원 식별자
     * @param orderNo 클라이언트 주문번호
     * @param txType 거래 유형 nullable
     * @return 거래 조회 응답 DTO
     */
    public TransactionLookupResponse getTransactionByOrder(String memberId, String orderNo, TxType txType) {
        Optional<PntTrHist> transaction = pntTrHistRepository.findByMemberIdAndOrderNo(memberId, orderNo)
                .stream()
                .filter(history -> txType == null || history.getTxType() == txType)
                .findFirst();

        return transaction
                .map(history -> new TransactionLookupResponse(
                        memberId,
                        orderNo,
                        txType,
                        true,
                        new TransactionLookupResponse.Item(
                                history.getPtxno(),
                                history.getOptxno(),
                                history.getOrderDtm(),
                                history.getTxType(),
                                history.getTxAmount(),
                                history.getRemainingAmount(),
                                history.getExpireAt(),
                                history.getCreatedAt()
                        )
                ))
                .orElseGet(() -> new TransactionLookupResponse(memberId, orderNo, txType, false, null));
    }
}
