package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.List;
import java.util.Map;

public class ZScoreRule extends ThresholdRule {

    private final double zThreshold;
    private final double minRequests;
    private final double minStd;
    private final int minHistory;

    public ZScoreRule(Map<String, Object> params) {
        super("TRAFFIC_SPIKE", params);
        this.zThreshold = number("z_threshold", 3);
        this.minRequests = number("min_requests", 30);
        this.minStd = number("min_std", 5);
        this.minHistory = (int) number("min_history", 10);
    }

    @Override
    public List<Alert> evaluate(Snapshot s) {
        List<Long> history = s.baselinePerMinute();
        if (history.size() < minHistory) {
            return List.of();
        }

        double current = s.total() * 60.0 / s.windowSeconds();
        if (current < minRequests) {
            return List.of();
        }

        double mean = s.baselineMean();
        double std = Math.max(s.baselineStd(), minStd);
        double z = (current - mean) / std;

        if (z <= zThreshold) {
            return List.of();
        }
        String msg = String.format(
                "Luu luong %.0f req/phut, binh thuong %.1f +- %.1f (%d phut truoc), z = %.1f (nguong %.1f)",
                current, mean, s.baselineStd(), history.size(), z, zThreshold);
        return List.of(alert("global", msg, z, zThreshold));
    }
}