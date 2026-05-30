package com.payment.point.api.earn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 포인트 적립취소 요청
 *
 * @param orderNo 클라이언트 주문번호
 * @param orderDtm 클라이언트 주문/요청 시각
 * @param pointTransactionNo 취소할 적립 거래번호
 * @param amount 적립취소 금액
 */
public record EarnCancelRequest(
        @NotBlank @Size(max = 40) String orderNo,
        LocalDateTime orderDtm,
        @NotBlank String pointTransactionNo,
        @Positive long amount
) {
}
