package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// R1: mot IP gui qua nhieu request trong cua so
public class HighRateIpRule extends ThresholdRule {

    private final double threshold;
    private final Set<String> whitelist;

    public HighRateIpRule(Map<String, Object> params, Set<String> whitelist) {
        super("HIGH_RATE_IP", params);
        this.threshold = number("threshold", 100);
        this.whitelist = whitelist;
    }

    @Override
    public List<Alert> evaluate(Snapshot s) {
        List<Alert> alerts = new ArrayList<>();
        for (Map.Entry<String, Long> e : s.requestsByIp().entrySet()) {
            String ip = e.getKey();
            long count = e.getValue();
            if (count > threshold && !whitelist.contains(ip)) {
                alerts.add(alert(ip,
                        "IP " + ip + " gui " + count + " request trong " + s.windowSeconds()
                                + "s (nguong " + (long) threshold + ")",
                        count, threshold));
            }
        }
        return alerts;
    }
}