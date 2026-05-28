package com.payment.point.api;

import com.payment.point.api.earn.EarnCancelRequest;
import com.payment.point.api.earn.EarnCancelResponse;
import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.application.PointFacadeService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class PointController {

    private final PointFacadeService pointFacadeService;

    /**
     * <b>포인트 적립</b>
     * @param memberId 회원아이디
     * @param request {@link EarnRequest 적립 요청}
     * @return 적립응답
     */
    @PostMapping("/members/{memberId}/points/earn")
    public ResponseEntity<EarnResponse> earn(@PathVariable String memberId, @Valid @RequestBody EarnRequest request) {
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
    public ResponseEntity<EarnCancelResponse> earnCancel(
            @PathVariable String memberId,
            @Valid @RequestBody EarnCancelRequest request
    ) {
        EarnCancelResponse response = pointFacadeService.earnCancel(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
