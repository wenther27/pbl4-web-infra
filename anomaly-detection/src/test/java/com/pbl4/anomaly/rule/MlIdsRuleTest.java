package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlIdsRuleTest {

    private static RequestRecord req(String ip, String path, String query) {
        return new RequestRecord("2026-11-18T10:00:00Z", ip, path, query, "Mozilla/5.0", 200);
    }

    private static Snapshot snapshot(List<RequestRecord> recent) {
        return new Snapshot(60, recent.size(), 0, 0, null, Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), recent);
    }

    private static final MlIdsRule.Scorer FAKE = batch -> batch.stream()
            .map(r -> r.query().contains("<svg") ? 0.97 : 0.05)
            .toList();

    @Test
    void canhBaoIpGuiRequestDiemCao() {
        MlIdsRule rule = new MlIdsRule(Map.of("threshold", 0.9), FAKE);
        List<Alert> alerts = rule.evaluate(snapshot(List.of(
                req("203.0.113.60", "/api/movies", "title=<svg/onfocus=alert(1)>"),
                req("203.0.113.60", "/api/movies", "title=<svg/onload=x>"),
                req("10.0.0.5", "/api/movies", "page=1"))));

        assertEquals(1, alerts.size());
        assertEquals("ML_IDS", alerts.get(0).rule());
        assertEquals("203.0.113.60", alerts.get(0).target());
        assertEquals(2.0, alerts.get(0).value());
        assertTrue(alerts.get(0).message().contains("0.97"));
    }

    @Test
    void khongGoiLaiModelChoRequestDaCham() {
        List<Integer> calls = new ArrayList<>();
        MlIdsRule rule = new MlIdsRule(Map.of(), batch -> {
            calls.add(batch.size());
            return FAKE.score(batch);
        });
        Snapshot s = snapshot(List.of(req("1.1.1.1", "/api/movies", "page=1"),
                req("2.2.2.2", "/api/movies", "page=1")));
        rule.evaluate(s);
        rule.evaluate(s);
        assertEquals(List.of(1), calls);
    }

    @Test
    void khongGuiRequestKhongCoThamSo() {
        List<Integer> calls = new ArrayList<>();
        MlIdsRule rule = new MlIdsRule(Map.of(), batch -> {
            calls.add(batch.size());
            return FAKE.score(batch);
        });
        rule.evaluate(snapshot(List.of(req("1.1.1.1", "/api/admin/infra/overview", ""),
                req("1.1.1.1", "/css/app.css", null))));
        assertTrue(calls.isEmpty());
    }

    @Test
    void khongLoiKhiModelSap() {
        MlIdsRule rule = new MlIdsRule(Map.of(), batch -> {
            throw new java.net.ConnectException("Connection refused");
        });
        List<Alert> alerts = rule.evaluate(snapshot(List.of(req("1.1.1.1", "/api/movies", "title=<svg>"))));
        assertTrue(alerts.isEmpty());
    }
}
