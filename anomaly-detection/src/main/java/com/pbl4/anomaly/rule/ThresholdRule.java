package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.Map;

// Lop cha cho cac luat "nguong co dinh" (R1-R6).
// Gom phan chung: ma luat, muc do, doc tham so tu rules.yaml, tao Alert.
// abstract: khong tao truc tiep duoc, chi dung lam lop cha.
public abstract class ThresholdRule implements Rule {

    private final String id;
    private final String severity;
    private final Map<String, Object> params;

    protected ThresholdRule(String id, Map<String, Object> params) {
        this.id = id;
        this.params = params;
        this.severity = String.valueOf(params.getOrDefault("severity", "MEDIUM"));
    }

    @Override
    public String id() {
        return id;
    }

    // Doc 1 tham so so tu rules.yaml; YAML co the cho Integer hoac Double nen dung Number
    protected double number(String key, double defaultValue) {
        Object v = params.get(key);
        return (v instanceof Number n) ? n.doubleValue() : defaultValue;
    }

    protected Alert alert(String target, String message, double value, double threshold) {
        return new Alert(id, severity, target, message, value, threshold);
    }
}