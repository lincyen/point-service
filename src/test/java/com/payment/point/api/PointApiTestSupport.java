package com.payment.point.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.payment.point.application.PointFacadeService;
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
}
