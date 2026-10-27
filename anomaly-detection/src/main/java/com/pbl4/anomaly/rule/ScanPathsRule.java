package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// R2: mot IP nhan 403/404 tren qua nhieu duong dan KHAC NHAU -> dang do quet.
// Dem so path khac nhau chu khong dem so lan loi: nguoi dung bam F5 5 lan
// tren 1 link hong chi la 1 path.
public class ScanPathsRule extends ThresholdRule {

    private final double threshold;
    private final Set<String> whitelist;

    public ScanPathsRule(Map<String, Object> params, Set<String> whitelist) {
        super("SCAN_PATHS", params);
        this.threshold = number("threshold", 15);
        this.whitelist = whitelist;
    }

    @Override
    public List<Alert> evaluate(Snapshot s) {
        List<Alert> alerts = new ArrayList<>();
        for (Map.Entry<String, Long> e : s.scanPathsByIp().entrySet()) {
            String ip = e.getKey();
            long paths = e.getValue();
            if (paths > threshold && !whitelist.contains(ip)) {
                alerts.add(alert(ip,
                        "IP " + ip + " nhan 403/404 tren " + paths + " duong dan khac nhau trong "
                                + s.windowSeconds() + "s (nguong " + (long) threshold + ")",
                        paths, threshold));
            }
        }
        return alerts;
    }
}