package com.pbl4.anomaly.alert;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Kiem thu tu dong chan bang LB gia (ghi lai cac lan goi), KHONG can LB that.
class AutoBlockerTest {

    private final List<String> calls = new ArrayList<>();   // "ip|minutes" cac lan goi LB
    private int lbStatus = 201;                             // ma LB gia tra ve

    private AutoBlocker blocker(boolean enabled, boolean dryRun, int maxPerMinute) {
        AutoBlocker.Config cfg = new AutoBlocker.Config(enabled, dryRun,
                Set.of("ATTACK_SIGNATURE", "BRUTE_FORCE_LOGIN"), 15, maxPerMinute);
        return new AutoBlocker(cfg, (ip, minutes, reason) -> {
            calls.add(ip + "|" + minutes);
            assertTrue(reason.length() <= 200, "ly do toi da 200 ky tu");
            return lbStatus;
        });
    }

    @Test
    void chiChanLuatDuocPhep() {
        blocker(true, false, 10).handle(List.of(
                alert("ATTACK_SIGNATURE", "203.0.113.66"),
                alert("BRUTE_FORCE_LOGIN", "203.0.113.67"),
                alert("HIGH_RATE_IP", "203.0.113.68")));          // R1: de admin quyet
        assertEquals(List.of("203.0.113.66|15", "203.0.113.67|15"), calls);
    }

    @Test
    void boQuaDoiTuongKhongPhaiIp() {
        // R6 target la URL backend, R7 la "global" -> khong bao gio goi LB
        AutoBlocker.Config cfg = new AutoBlocker.Config(true, false,
                Set.of("BACKEND_DOWN", "TRAFFIC_SPIKE"), 15, 10);
        new AutoBlocker(cfg, (ip, m, r) -> { calls.add(ip); return 201; }).handle(List.of(
                alert("BACKEND_DOWN", "http://web-1:8080"),
                alert("TRAFFIC_SPIKE", "global")));
        assertEquals(0, calls.size());
    }

    @Test
    void tatThiKhongChan() {
        blocker(false, false, 10).handle(List.of(alert("ATTACK_SIGNATURE", "203.0.113.66")));
        assertEquals(0, calls.size());
    }

    @Test
    void dryRunKhongGoiLb() {
        blocker(true, true, 10).handle(List.of(alert("ATTACK_SIGNATURE", "203.0.113.66")));
        assertEquals(0, calls.size(), "dry run chi ghi log");
    }

    @Test
    void vuotGioiHanMoiPhut_tamDung() {
        AutoBlocker b = blocker(true, false, 2);
        b.handle(List.of(
                alert("ATTACK_SIGNATURE", "203.0.113.1"),
                alert("ATTACK_SIGNATURE", "203.0.113.2"),
                alert("ATTACK_SIGNATURE", "203.0.113.3"),     // vuot 2 IP/phut -> khong chan
                alert("ATTACK_SIGNATURE", "203.0.113.4")));
        assertEquals(2, calls.size());
    }

    @Test
    void ipDuocBaoVe_khongTinhVaoGioiHan() {
        lbStatus = 409;                                       // LB: IP duoc bao ve
        AutoBlocker b = blocker(true, false, 1);
        b.handle(List.of(alert("ATTACK_SIGNATURE", "172.18.0.1")));
        lbStatus = 201;
        b.handle(List.of(alert("ATTACK_SIGNATURE", "203.0.113.66")));
        assertEquals(List.of("172.18.0.1|15", "203.0.113.66|15"), calls,
                "lan 409 khong duoc tinh la da chan");
    }

    @Test
    void lbLoi_khongNemLoiRaNgoai() {
        AutoBlocker.Config cfg = new AutoBlocker.Config(true, false, Set.of("ATTACK_SIGNATURE"), 15, 10);
        new AutoBlocker(cfg, (ip, m, r) -> { throw new java.net.ConnectException("LB tat"); })
                .handle(List.of(alert("ATTACK_SIGNATURE", "203.0.113.66")));
        // khong nem exception la dat: vong phat hien van chay tiep
    }

    private static Alert alert(String rule, String target) {
        return new Alert(rule, "HIGH", target, "IP " + target + " vi pham " + rule, 1, 1);
    }
}
