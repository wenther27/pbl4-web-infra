package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// R3: mot IP dang nhap that bai (401 tren /api/auth/login) qua nhieu lan.
// Backend: sai mat khau -> BadCredentialsException -> GlobalExceptionHandler tra 401.
public class BruteForceLoginRule extends ThresholdRule {

    private final double threshold;
    private final Set<String> whitelist;

    public BruteForceLoginRule(Map<String, Object> params, Set<String> whitelist) {
        super("BRUTE_FORCE_LOGIN", params);
        this.threshold = number("threshold", 10);
        this.whitelist = whitelist;
    }

    @Override
    public List<Alert> evaluate(Snapshot s) {
        List<Alert> alerts = new ArrayList<>();
        for (Map.Entry<String, Long> e : s.loginFailByIp().entrySet()) {
            String ip = e.getKey();
            long fails = e.getValue();
            if (fails > threshold && !whitelist.contains(ip)) {
                alerts.add(alert(ip,
                        "IP " + ip + " dang nhap that bai " + fails + " lan trong "
                                + s.windowSeconds() + "s (nguong " + (long) threshold + ")",
                        fails, threshold));
            }
        }
        return alerts;
    }
}