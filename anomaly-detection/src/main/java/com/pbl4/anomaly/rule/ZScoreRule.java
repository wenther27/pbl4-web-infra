package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.List;
import java.util.Map;

// R7: TRAFFIC_SPIKE - tong luu luong tang dot bien so voi chinh no trong qua khu.
//
// Khac R1-R6 (so voi mot con so co dinh), R7 so voi "binh thuong" cua he thong:
//   z = (hien_tai - trung_binh) / do_lech_chuan
// trung_binh, do_lech_chuan tinh tu so request moi phut trong N phut truoc (baseline).
// z = 3 nghia la: hien tai cao hon trung binh 3 lan do lech chuan -> rat hiem khi xay ra tu nhien.
public class ZScoreRule extends ThresholdRule {

    private final double zThreshold;
    private final double minRequests;   // it hon so nay / phut thi khong bao (0 -> 5 request khong phai su co)
    private final double minStd;        // san cua do lech chuan: tranh chia cho 0 khi he thong im lang
    private final int minHistory;       // can it nhat bao nhieu phut lich su moi danh gia

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
            return List.of();   // chua du lich su de biet the nao la "binh thuong"
        }

        // Quy ve "request / phut" de so sanh cung don vi voi baseline
        double current = s.total() * 60.0 / s.windowSeconds();
        if (current < minRequests) {
            return List.of();
        }

        double mean = s.baselineMean();
        double std = Math.max(s.baselineStd(), minStd);
        double z = (current - mean) / std;

        // Chi bao TANG dot bien. Giam dot bien (vd: tat ca backend chet) da co R4, R6 lo.
        if (z <= zThreshold) {
            return List.of();
        }
        String msg = String.format(
                "Luu luong %.0f req/phut, binh thuong %.1f +- %.1f (%d phut truoc), z = %.1f (nguong %.1f)",
                current, mean, s.baselineStd(), history.size(), z, zThreshold);
        return List.of(alert("global", msg, z, zThreshold));
    }
}