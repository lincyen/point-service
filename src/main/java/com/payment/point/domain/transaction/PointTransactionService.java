package com.payment.point.domain.transaction;

import com.payment.point.api.history.HistoryResponse;
import com.payment.point.api.history.TransactionLookupResponse;
import com.payment.point.domain.earn.PntEarnMst;
import com.payment.point.support.ApiException;
import com.payment.point.support.ErrorCode;
import java.time.LocalDate;
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

    private static final int MAX_HISTORY_SEARCH_MONTHS = 3;

    private final PntTrHistRepository pntTrHistRepository;

    public PointTransactionService(PntTrHistRepository pntTrHistRepository) {
        this.pntTrHistRepository = pntTrHistRepository;
    }

    /**
     * <b>동일 회원, 동일 주문번호 요청 시 중복체크 valid</b>
     *
     * @param memberId 회원아이디
     * @param orderNo 클라이언트 주문번호
     */
    public void validateDuplicateOrder(String memberId, String orderNo) {
        if (memberId == null || memberId.isBlank() || orderNo == null || orderNo.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
        if (pntTrHistRepository.existsByMemberIdAndOrderNo(memberId, orderNo)) {
            throw new ApiException(ErrorCode.DUPLICATED_ORDER);
        }
    }

    /**
     * <b>거래 이력 저장</b>
     *
     * @param pointTransactionNo 포인트 거래번호
     * @param originalPointTransactionNo 원 포인트 거래번호
     * @param memberId 회원아이디
     * @param orderNo 클라이언트 주문번호
     * @param orderDtm 클라이언트 주문/요청 시각
     * @param txType 거래 유형
     * @param txAmount 거래 금액
     * @param remainingAmount 거래 후 회원 총 잔액
     * @param expireAt 거래 당시 만료일 스냅샷
     */
    public void appendTransaction(String pointTransactionNo, String originalPointTransactionNo, String memberId, String orderNo,
            LocalDateTime orderDtm, TxType txType, long txAmount, long remainingAmount, LocalDateTime expireAt) {
        pntTrHistRepository.save(new PntTrHist(
                pointTransactionNo,
                originalPointTransactionNo,
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
     * <b>적립 거래 이력 저장</b>
     *
     * @param earnMst {@link PntEarnMst 적립 원장}
     * @param orderNo 클라이언트 주문번호
     * @param orderDtm 클라이언트 주문/요청 시각
     * @param remainingAmount 거래 후 회원 총 잔액
     */
    public void appendEarnTransaction(PntEarnMst earnMst, String orderNo, LocalDateTime orderDtm, long remainingAmount) {
        pntTrHistRepository.save(new PntTrHist(
                earnMst.getPtxno(),
                earnMst.getPtxno(),
                earnMst.getMemberId(),
                orderNo,
                orderDtm == null ? LocalDateTime.now() : orderDtm,
                TxType.EARN,
                earnMst.getEarnAmount(),
                remainingAmount,
                earnMst.getExpireAt()
        ));
    }

    /**
     * <b>적립취소 거래 이력 저장</b>
     *
     * @param pointTransactionNo 적립취소 거래번호
     * @param earnMst 원 적립 원장
     * @param orderNo 클라이언트 주문번호
     * @param orderDtm 클라이언트 주문/요청 시각
     * @param remainingAmount 거래 후 회원 총 잔액
     */
    public void appendEarnCancelTransaction(String pointTransactionNo, PntEarnMst earnMst, String orderNo, LocalDateTime orderDtm, long remainingAmount) {
        pntTrHistRepository.save(new PntTrHist(
                pointTransactionNo,
                earnMst.getPtxno(),
                earnMst.getMemberId(),
                orderNo,
                orderDtm == null ? LocalDateTime.now() : orderDtm,
                TxType.EARN_CNCL,
                earnMst.getEarnCancelAmount(),
                remainingAmount,
                null
        ));
    }

    /**
     * 회원의 포인트 거래 이력을 최신순으로 조회한다.
     *
     * @param memberId 회원아이디
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
     * 회원의 포인트 거래 이력을 조건에 맞게 최신순으로 조회한다.
     *
     * @param memberId 회원아이디
     * @param startDate 조회 시작일
     * @param endDate 조회 종료일
     * @param txType 거래 유형 nullable
     * @return 포인트 거래 이력 응답 DTO
     */
    public HistoryResponse getHistories(String memberId, LocalDate startDate, LocalDate endDate, TxType txType) {
        validateHistorySearchPeriod(startDate, endDate);

        LocalDateTime startAt = startDate.atStartOfDay();
        LocalDateTime endAt = endDate.plusDays(1).atStartOfDay();
        List<HistoryResponse.Item> histories = pntTrHistRepository.findHistories(memberId, startAt, endAt, txType)
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

        if (histories.isEmpty()) {
            throw new ApiException(ErrorCode.NO_HISTORY_RESULT);
        }

        return new HistoryResponse(memberId, histories);
    }

    private void validateHistorySearchPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER);
        }
        if (!startDate.isBefore(endDate)) {
            throw new ApiException(ErrorCode.INVALID_HISTORY_PERIOD);
        }
        if (endDate.isAfter(startDate.plusMonths(MAX_HISTORY_SEARCH_MONTHS))) {
            throw new ApiException(ErrorCode.HISTORY_PERIOD_EXCEEDED);
        }
    }

    /**
     * 회원과 주문번호로 거래 처리 여부를 조회한다.
     *
     * <p>현재 주문번호는 동일 회원 내 모든 포인트 거래 유형에서 중복을 허용하지 않으므로,
     * 거래 유형은 선택 필터로만 사용한다.</p>
     *
     * @param memberId 회원아이디
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
