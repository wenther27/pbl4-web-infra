package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Kiem thu R7 bang du lieu dung san, KHONG can Elasticsearch hay sinh request that.
// Moi test la mot tinh huong; so lieu dua vao bang so sanh trong bao cao.
class ZScoreRuleTest {

    private final ZScoreRule r7 = new ZScoreRule(Map.of(
            "z_threshold", 3, "min_requests", 30, "min_std", 5, "min_history", 10));

    // R1 nguong co dinh 100 request / IP / 60s, de so sanh
    private final HighRateIpRule r1 = new HighRateIpRule(Map.of("threshold", 100), Set.of());

    // ---------- Tinh huong 1: he thong vang, mot IP tang len 60 req/phut ----------
    @Test
    void heThongVang_tangLen60_R7BaoNhungR1KhongBao() {
        Snapshot s = snapshot(60, repeat(30, 8, 10, 12));   // binh thuong ~10 req/phut
        List<Alert> a7 = r7.evaluate(s);
        assertEquals(1, a7.size());
        assertTrue(a7.get(0).value() > 3, "z phai > 3, thuc te " + a7.get(0).value());
        assertEquals(0, r1.evaluate(s).size(), "60 < 100 nen R1 bo lot");
    }

    // ---------- Tinh huong 2: he thong ban ron on dinh ~200 req/phut ----------
    @Test
    void heThongBanRon_230req_R7KhongBao() {
        Snapshot s = snapshot(230, repeat(30, 180, 220));   // 200 +- 20
        assertEquals(0, r7.evaluate(s).size(), "z = 1.5: dao dong binh thuong");
    }

    @Test
    void heThongBanRon_tangLen400_R7Bao() {
        Snapshot s = snapshot(400, repeat(30, 180, 220));
        List<Alert> a7 = r7.evaluate(s);
        assertEquals(1, a7.size());
        assertEquals(10.0, a7.get(0).value(), 0.01);   // (400-200)/20
    }

    // ---------- Tinh huong 3: he thong im lang (moi phut = 0) ----------
    @Test
    void imLang_vaiRequest_khongBao() {
        // std = 0 -> neu khong co min_std, 1 request cung ra z = vo cung
        assertEquals(0, r7.evaluate(snapshot(10, repeat(30, 0))).size());
    }

    @Test
    void imLang_tangLen50_bao() {
        List<Alert> a7 = r7.evaluate(snapshot(50, repeat(30, 0)));
        assertEquals(1, a7.size());
        assertEquals(10.0, a7.get(0).value(), 0.01);   // (50-0)/min_std 5
    }

    // ---------- Tinh huong 4: cac truong hop bien ----------
    @Test
    void thieuLichSu_khongBao() {
        assertEquals(0, r7.evaluate(snapshot(500, repeat(5, 10))).size());
    }

    @Test
    void luuLuongGiam_khongBao() {
        assertEquals(0, r7.evaluate(snapshot(0, repeat(30, 200))).size());
    }

    // ---------- Ham phu ----------

    // total request trong cua so 60s, mot IP gui het
    private static Snapshot snapshot(long total, List<Long> baseline) {
        Map<String, Long> byIp = total > 0 ? Map.of("203.0.113.7", total) : Map.of();
        return new Snapshot(60, total, 0, 0, null, byIp, Map.of(), Map.of(), Map.of(), baseline);
    }

    // n phut, gia tri lap lai theo mau: repeat(4, 8, 12) -> [8, 12, 8, 12]
    private static List<Long> repeat(int n, long... pattern) {
        List<Long> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(pattern[i % pattern.length]);
        }
        return list;
    }
}