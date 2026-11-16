package com.pbl4.anomaly.rule;

public record RequestRecord(
        String timestamp,
        String clientIp,
        String path,
        String query,
        String userAgent,
        int status
) {}
