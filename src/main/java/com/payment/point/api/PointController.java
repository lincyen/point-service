package com.payment.point.api;

import com.payment.point.api.balance.BalanceResponse;
import com.payment.point.api.expire.ExpireRequest;
import com.payment.point.api.expire.ExpireResponse;
import com.payment.point.api.earn.EarnCancelRequest;
import com.payment.point.api.earn.EarnCancelResponse;
import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.api.history.HistoryResponse;
import com.payment.point.api.history.TransactionLookupResponse;
import com.payment.point.api.use.UseCancelRequest;
import com.payment.point.api.use.UseCancelResponse;
import com.payment.point.api.use.UseRequest;
import com.payment.point.api.use.UseResponse;
import com.payment.point.application.PointFacadeService;
import com.payment.point.domain.transaction.TxType;
import com.payment.point.support.ValidMemberId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
@Validated
public class PointController {

    private final PointFacadeService pointFacadeService;

    /**
     * <b>포인트 적립</b>
     * @param memberId 회원아이디
     * @param request {@link EarnRequest 적립 요청}
     * @return 적립응답
     */
    @PostMapping("/members/{memberId}/points/earn")
    public ResponseEntity<EarnResponse> earn(@PathVariable @ValidMemberId String memberId, @Valid @RequestBody EarnRequest request) {
        EarnResponse response = pointFacadeService.earn(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * <b>포인트 적립취소</b>
     * @param memberId 회원아이디
     * @param request {@link EarnCancelRequest 적립취소 요청}
     * @return 적립취소응답
     */
    @PostMapping("/members/{memberId}/points/earn-cancel")
    public ResponseEntity<EarnCancelResponse> earnCancel(@PathVariable @ValidMemberId String memberId, @Valid @RequestBody EarnCancelRequest request) {
        EarnCancelResponse response = pointFacadeService.earnCancel(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * <b>포인트 사용</b>
     * @param memberId 회원아이디
     * @param request {@link UseRequest 사용 요청}
     * @return 사용응답
     */
    @PostMapping("/members/{memberId}/points/use")
    public ResponseEntity<UseResponse> use(@PathVariable @ValidMemberId String memberId, @Valid @RequestBody UseRequest request) {
        UseResponse response = pointFacadeService.use(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * <b>포인트 사용취소</b>
     * @param memberId 회원아이디
     * @param request {@link UseCancelRequest 사용취소 요청}
     * @return 사용취소응답
     */
    @PostMapping("/members/{memberId}/points/use-cancel")
    public ResponseEntity<UseCancelResponse> useCancel(@PathVariable @ValidMemberId String memberId, @Valid @RequestBody UseCancelRequest request) {
        UseCancelResponse response = pointFacadeService.useCancel(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * <b>포인트 만료</b>
     * @param memberId 회원아이디
     * @param request {@link ExpireRequest 만료 요청}
     * @return 만료응답
     */
    @PostMapping("/members/{memberId}/points/expire")
    public ResponseEntity<ExpireResponse> expire(@PathVariable @ValidMemberId String memberId, @RequestBody ExpireRequest request) {
        ExpireResponse response = pointFacadeService.expire(memberId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * <b>포인트 잔액 조회</b>
     * @param memberId 회원아이디
     * @return 잔액조회응답
     */
    @GetMapping("/members/{memberId}/points/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable @ValidMemberId String memberId) {
        BalanceResponse response = pointFacadeService.getBalance(memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * <b>포인트 거래 이력 조회</b>
     * @param memberId 회원아이디
     * @param startDate 조회 시작일(yyyy-mm-dd)
     * @param endDate 조회 종료일(yyyy-mm-dd)
     * @param txType {@link TxType 포인트 거래 유형}
     * @return 거래이력조회응답
     */
    @GetMapping("/members/{memberId}/points/histories")
    public ResponseEntity<HistoryResponse> getHistories(@PathVariable @ValidMemberId String memberId,
                                                        @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                        @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                        @RequestParam(required = false) TxType txType) {
        HistoryResponse response = pointFacadeService.getHistories(memberId, startDate, endDate, txType);
        return ResponseEntity.ok(response);
    }

    /**
     * <b>주문번호 기반 포인트 거래 조회</b>
     * @param memberId 회원아이디
     * @param orderNo 클라이언트 주문번호
     * @param txType {@link TxType 포인트 거래 유형}
     * @return 주문번호 기반 거래조회응답
     */
    @GetMapping("/members/{memberId}/points/transactions/by-order")
    public ResponseEntity<TransactionLookupResponse> getTransactionByOrder(@PathVariable @ValidMemberId String memberId,
                                                                           @RequestParam @NotBlank @Size(max = 40) String orderNo,
                                                                           @RequestParam @NotNull TxType txType) {
        TransactionLookupResponse response = pointFacadeService.getTransactionByOrder(memberId, orderNo, txType);
        return ResponseEntity.ok(response);
    }
}
