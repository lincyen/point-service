package com.payment.point.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "point")
public record PointPolicyProperties(
        Earn earn,
        Member member,
        Lock lock
) {

    public record Earn(
            long minAmount,
            long maxAmount,
            String defaultExpirePeriod,
            String minExpirePeriod,
            String maxExpirePeriod
    ) {
    }

    public record Member(
            long maxBalanceAmount
    ) {
    }

    public record Lock(
            Duration ttl
    ) {
    }
}
