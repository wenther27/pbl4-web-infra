package com.pbl4.anomaly.alert;

public record Alert(
        String rule,
        String severity,
        String target,
        String message,
        double value,
        double threshold
) {
    public String key() {
        return rule + "|" + target;
    }
}