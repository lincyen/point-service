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
 * <pre>
 *     주문번호 중복 검증, 거래 이력 append, 회원별 거래 이력 조회를 담당한다.
 * </pre>
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
     * <b>적립 거래 이력 저장</b>
     *
     * @param earnMst {@link PntEarnMst 적립 원장}
     * @param orderNo 클라이언트 주문번호
     * @param orderDtm 클라이언트 주문/요청 시각
     * @param remainingAmount 거래 후 회원 총 잔액
     */
    public void appendEarnTransaction(PntEarnMst earnMst, String orderNo, LocalDateTime orderDtm, long remainingAmount) {
        appendTransaction(
                earnMst.getPtxno(),
                earnMst.getPtxno(),
                earnMst.getMemberId(),
                orderNo,
                orderDtm,
                TxType.EARN,
                earnMst.getEarnAmount(),
                remainingAmount,
                earnMst.getExpireDate()
        );
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
        appendTransaction(
                pointTransactionNo,
                earnMst.getPtxno(),
                earnMst.getMemberId(),
                orderNo,
                orderDtm,
                TxType.EARN_CNCL,
                earnMst.getEarnCancelAmount(),
                remainingAmount,
                null
        );
    }

    /**
     * <b>사용 거래 이력 저장</b>
     *
     * @param pointTransactionNo 사용 거래번호
     * @param memberId 회원아이디
     * @param orderNo 클라이언트 주문번호
     * @param orderDtm 클라이언트 주문/요청 시각
     * @param useAmount 사용 금액
     * @param remainingAmount 거래 후 회원 총 잔액
     */
    public void appendUseTransaction(String pointTransactionNo, String memberId, String orderNo,
            LocalDateTime orderDtm, long useAmount, long remainingAmount) {
        appendTransaction(
                pointTransactionNo,
                pointTransactionNo,
                memberId,
                orderNo,
                orderDtm,
                TxType.USE,
                useAmount,
                remainingAmount,
                null
        );
    }

    /**
     * <b>사용취소 거래 이력 저장</b>
     *
     * @param useCancelPtxno 사용취소 거래번호
     * @param originalUsePtxno 원 사용 거래번호
     * @param memberId 회원아이디
     * @param orderNo 클라이언트 주문번호
     * @param orderDtm 클라이언트 주문/요청 시각
     * @param cancelAmount 사용취소 금액
     * @param remainingAmount 거래 후 회원 총 잔액
     */
    public void appendUseCancelTransaction(String useCancelPtxno, String originalUsePtxno, String memberId,
            String orderNo, LocalDateTime orderDtm, long cancelAmount, long remainingAmount) {
        appendTransaction(
                useCancelPtxno,
                originalUsePtxno,
                memberId,
                orderNo,
                orderDtm,
                TxType.USE_CNCL,
                cancelAmount,
                remainingAmount,
                null
        );
    }

    /**
     * <b>만료 거래 이력 저장</b>
     *
     * @param pointTransactionNo 만료 거래번호
     * @param earnMst 만료 처리된 적립 원장
     * @param expiredAmount 만료 금액
     * @param remainingAmount 거래 후 회원 총 잔액
     */
    public void appendExpireTransaction(String pointTransactionNo, PntEarnMst earnMst, long expiredAmount, long remainingAmount) {
        appendTransaction(
                pointTransactionNo,
                earnMst.getPtxno(),
                earnMst.getMemberId(),
                null,
                LocalDateTime.now(),
                TxType.EXPIRE,
                expiredAmount,
                remainingAmount,
                earnMst.getExpireDate()
        );
    }

    /**
     * <b>거래 이력 저장</b>
     * @param pointTransactionNo 거래번호
     * @param originalPointTransactionNo 원 거래번호
     * @param memberId 회원아이디
     * @param orderNo 클라이언트 주문번호
     * @param orderDtm 클라이언트 주문/요청 시각
     * @param txType 거래 유형
     * @param txAmount 거래 금액
     * @param remainingAmount 거래 후 총 잔액
     * @param expireDate 거래 당시 만료일
     */
    private void appendTransaction(String pointTransactionNo, String originalPointTransactionNo, String memberId, String orderNo,
            LocalDateTime orderDtm, TxType txType, long txAmount, long remainingAmount, LocalDate expireDate) {
        pntTrHistRepository.save(new PntTrHist(
                pointTransactionNo,
                originalPointTransactionNo,
                memberId,
                orderNo,
                orderDtm == null ? LocalDateTime.now() : orderDtm,
                txType,
                txAmount,
                remainingAmount,
                expireDate
        ));
    }

    /**
     * <b>포인트 거래 이력 조회</b>
     * <pre>
     *     Facade에서 회원 잔액 row 존재 여부를 검증한 뒤 호출
     *     유효 회원의 조회 결과가 없으면 NO_HISTORY_RESULT 처리
     * </pre>
     *
     * @param memberId 회원아이디
     * @param startDate 조회 시작일
     * @param endDate 조회 종료일
     * @param txType 거래 유형 (optional)
     * @return 포인트 거래 이력 응답
     */
    public HistoryResponse getHistories(String memberId, LocalDate startDate, LocalDate endDate, TxType txType) {
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
                        history.getExpireDate(),
                        history.getCreatedAt()
                ))
                .toList();

        if (histories.isEmpty()) {
            throw new ApiException(ErrorCode.NO_HISTORY_RESULT);
        }

        return new HistoryResponse(memberId, histories);
    }

    /**
     * <b>이력 조회 기간 valid</b>
     * <pre>
     *     startDate 또는 endDate 가 없는 경우 INVALID_PARAMETER
     *     startDate 가 endDate 보다 이후인 경우 INVALID_HISTORY_PERIOD
     *     조회기간이 3개월 이상인 경우 HISTORY_PERIOD_EXCEEDED
     * </pre>
     * @param startDate 조회 시작일
     * @param endDate 조회 종료일
     */
    public void validateHistorySearchPeriod(LocalDate startDate, LocalDate endDate) {
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
     * <b>회원아이디와 주문번호로 거래정보 조회</b>
     *
     * <pre>
     *     네트워크 유실 등으로 처리 응답을 받지 못했을 경우, 요청 건이 처리되었는지 확인하기 위해 사용
     *     Facade에서 회원 잔액 row 존재 여부를 검증한 뒤 호출한다.
     *     유효 회원의 주문번호에 해당하는 거래가 없으면 exists=false 응답을 반환한다.
     * </pre>
     *
     * @param memberId 회원아이디
     * @param orderNo 클라이언트 주문번호
     * @param txType 거래 유형 (optional)
     * @return 거래 조회 응답
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
                                history.getExpireDate(),
                                history.getCreatedAt()
                        )
                ))
                .orElseGet(() -> new TransactionLookupResponse(memberId, orderNo, txType, false, null));
    }
}
