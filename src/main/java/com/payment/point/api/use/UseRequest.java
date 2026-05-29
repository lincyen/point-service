package com.payment.point.api.use;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 포인트 사용 요청 DTO.
 *
 * @param orderNo 클라이언트 주문번호
 * @param orderDtm 클라이언트 주문/요청 시각
 * @param amount 사용 금액
 */
public record UseRequest(
        @NotBlank @Size(max = 40) String orderNo,
        LocalDateTime orderDtm,
        @Positive long amount
) {
}
