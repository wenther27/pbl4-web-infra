package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// R6: LB ghi su kien backend_down trong cua so
public class BackendDownRule extends ThresholdRule {

    public BackendDownRule(Map<String, Object> params) {
        super("BACKEND_DOWN", params);
    }

    @Override
    public List<Alert> evaluate(Snapshot s) {
        List<Alert> alerts = new ArrayList<>();
        for (Map.Entry<String, Long> e : s.downEventsByBackend().entrySet()) {
            alerts.add(alert(e.getKey(),
                    "Load balancer bao " + e.getKey() + " DOWN trong " + s.windowSeconds() + "s qua",
                    e.getValue(), 1));
        }
        return alerts;
    }
}