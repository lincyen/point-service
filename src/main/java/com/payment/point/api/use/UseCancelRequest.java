package com.payment.point.api.use;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 포인트 사용취소 요청 DTO.
 *
 * @param orderNo 클라이언트 주문번호
 * @param orderDtm 클라이언트 주문/요청 시각
 * @param originalUsePtxno 취소할 원 사용 거래번호
 * @param amount 사용취소 금액
 */
public record UseCancelRequest(
        @NotBlank @Size(max = 40) String orderNo,
        LocalDateTime orderDtm,
        @NotBlank String originalUsePtxno,
        @Positive long amount
) {
}
