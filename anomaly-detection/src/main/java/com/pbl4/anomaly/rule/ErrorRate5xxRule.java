package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.List;
import java.util.Map;

// R4: ti le loi 5xx vuot nguong
public class ErrorRate5xxRule extends ThresholdRule {

    private final double thresholdPercent;
    private final double minRequests;

    public ErrorRate5xxRule(Map<String, Object> params) {
        super("ERROR_RATE_5XX", params);
        this.thresholdPercent = number("threshold_percent", 5);
        this.minRequests = number("min_requests", 20);
    }

    @Override
    public List<Alert> evaluate(Snapshot s) {
        // It request qua thi ti le khong dang tin (1 loi / 2 request = 50%)
        if (s.total() < minRequests) {
            return List.of();
        }
        double percent = 100.0 * s.err5xx() / s.total();
        if (percent <= thresholdPercent) {
            return List.of();
        }
        String msg = String.format("Ti le loi 5xx %.1f%% (%d/%d request, nguong %.0f%%)",
                percent, s.err5xx(), s.total(), thresholdPercent);
        return List.of(alert("global", msg, percent, thresholdPercent));
    }
}