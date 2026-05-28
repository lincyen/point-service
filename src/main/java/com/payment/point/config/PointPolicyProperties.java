package com.payment.point.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "point")
public record PointPolicyProperties(
        Earn earn,
        Member member
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
}
