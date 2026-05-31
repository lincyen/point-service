package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.payment.point.api.earn.EarnRequest;
import com.payment.point.api.earn.EarnResponse;
import com.payment.point.api.use.UseRequest;
import com.payment.point.api.use.UseResponse;
import com.payment.point.application.PointFacadeService;
import com.payment.point.domain.earn.EarnType;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
abstract class PointApiTestSupport {

    protected static final String POINT_ID_PATTERN = "\\d{26}";

    @Autowired
    protected PointFacadeService pointFacadeService;

    protected void assertPointId(String ptxno) {
        assertTrue(ptxno.matches(POINT_ID_PATTERN));
    }

    protected String memberId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    protected String orderNo(String prefix) {
        return prefix + "-" + memberId();
    }

    protected EarnResponse givenEarn(String memberId, String orderPrefix, EarnType earnType,
                                     long amount, String expirePeriod) {
        return givenEarnByOrderNo(memberId, orderNo(orderPrefix), earnType, amount, expirePeriod);
    }

    protected EarnResponse givenEarnByOrderNo(String memberId, String orderNo, EarnType earnType,
                                              long amount, String expirePeriod) {
        return pointFacadeService.earn(
                memberId,
                new EarnRequest(orderNo, null, earnType, amount, expirePeriod)
        );
    }

    protected UseResponse givenUse(String memberId, String orderPrefix, long amount) {
        return givenUseByOrderNo(memberId, orderNo(orderPrefix), amount);
    }

    protected UseResponse givenUseByOrderNo(String memberId, String orderNo, long amount) {
        return pointFacadeService.use(memberId, new UseRequest(orderNo, null, amount));
    }
}
