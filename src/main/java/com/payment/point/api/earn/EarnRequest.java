package com.payment.point.api.earn;

import com.payment.point.domain.earn.EarnType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 포인트 적립 요청 DTO.
 *
 * @param orderNo 클라이언트 주문번호
 * @param orderDtm 클라이언트 주문/요청 시각
 * @param earnType 적립 유형
 * @param amount 적립 금액
 * @param expirePeriod ISO-8601 period 형식의 만료 기간
 */
public record EarnRequest(
        @NotBlank @Size(max = 40) String orderNo,
        LocalDateTime orderDtm,
        @NotNull EarnType earnType,
        @Positive long amount,
        String expirePeriod
) {
}
