package com.payment.point.api;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        EarnApiTests.class,
        EarnCancelApiTests.class,
        UseApiTests.class,
        UseCancelApiTests.class,
        ExpireApiTests.class,
        BalanceApiTests.class,
        HistoryApiTests.class,
        TransactionLookupApiTests.class
})
class PointApiTestSuite {
}
