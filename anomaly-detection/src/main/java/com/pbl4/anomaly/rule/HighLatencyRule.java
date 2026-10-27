package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.List;
import java.util.Map;

// R5: p95 thoi gian phan hoi vuot nguong
public class HighLatencyRule extends ThresholdRule {

    private final double thresholdMs;
    private final double minRequests;

    public HighLatencyRule(Map<String, Object> params) {
        super("HIGH_LATENCY", params);
        this.thresholdMs = number("threshold_ms", 1000);
        this.minRequests = number("min_requests", 20);
    }

    @Override
    public List<Alert> evaluate(Snapshot s) {
        if (s.p95Ms() == null || s.total() < minRequests || s.p95Ms() <= thresholdMs) {
            return List.of();
        }
        String msg = String.format("p95 thoi gian phan hoi %.0fms tren %d request (nguong %.0fms)",
                s.p95Ms(), s.total(), thresholdMs);
        return List.of(alert("global", msg, s.p95Ms(), thresholdMs));
    }
}