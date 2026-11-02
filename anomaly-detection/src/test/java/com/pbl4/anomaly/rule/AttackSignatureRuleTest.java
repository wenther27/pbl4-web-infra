package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Kiem thu R8 bang request dung san, KHONG can Elasticsearch.
// Dung dung cac mau trong src/main/resources/rules.yaml -> sua mau sai la test bao ngay.
class AttackSignatureRuleTest {

    private final AttackSignatureRule r8 = new AttackSignatureRule(paramsFromRulesYaml());

    // ---------- Request binh thuong: KHONG duoc bao (tranh bao dong gia) ----------
    @Test
    void requestBinhThuong_khongBao() {
        assertTrue(r8.match(req("/api/movies", "page=0&size=12&sort=title", "Mozilla/5.0")).isEmpty());
        assertTrue(r8.match(req("/api/movies", "keyword=tom+and+jerry", "Mozilla/5.0")).isEmpty());
        assertTrue(r8.match(req("/api/movies", "keyword=rock%27n+roll", "Mozilla/5.0")).isEmpty());
        assertTrue(r8.match(req("/api/auth/login", "", "Apache-HttpClient/4.5")).isEmpty());
    }

    // ---------- Tung nhom mau ----------
    @Test
    void sqlInjection_daMaHoaUrl_vanBat() {
        // ' OR 1=1  ->  %27%20OR%201%3D1
        assertEquals(Set.of("SQL_INJECTION"),
                r8.match(req("/api/movies", "keyword=%27%20OR%201%3D1", "Mozilla/5.0")));
    }

    @Test
    void sqlInjection_maHoa2Lan_vanBat() {
        // %2527 -> %27 -> '
        assertEquals(Set.of("SQL_INJECTION"),
                r8.match(req("/api/movies", "keyword=%2527%2520or%25201%253D1", "Mozilla/5.0")));
    }

    @Test
    void xss_bat() {
        assertEquals(Set.of("XSS"),
                r8.match(req("/api/movies", "keyword=%3Cscript%3Ealert(1)%3C%2Fscript%3E", "Mozilla/5.0")));
    }

    @Test
    void pathTraversal_bat() {
        assertEquals(Set.of("PATH_TRAVERSAL"),
                r8.match(req("/api/uploads", "file=..%2F..%2Fetc%2Fpasswd", "Mozilla/5.0")));
    }

    @Test
    void congCuQuet_theoUserAgent_bat() {
        assertEquals(Set.of("SCANNER_TOOL"),
                r8.match(req("/api/movies", "", "sqlmap/1.8#stable (https://sqlmap.org)")));
    }

    // ---------- Gom theo IP thanh canh bao ----------
    @Test
    void gomTheoIp_moiIp1CanhBao() {
        Snapshot s = snapshot(List.of(
                req("203.0.113.50", "/api/movies", "keyword=%27%20or%201%3D1", "Mozilla/5.0"),
                req("203.0.113.50", "/api/movies", "keyword=%3Cscript%3E", "Mozilla/5.0"),
                req("203.0.113.50", "/api/movies", "page=1", "Mozilla/5.0"),
                req("198.51.100.7", "/api/movies", "page=1", "Mozilla/5.0")));

        List<Alert> alerts = r8.evaluate(s);
        assertEquals(1, alerts.size(), "chi IP 203.0.113.50 gui mau tan cong");
        Alert a = alerts.get(0);
        assertEquals("203.0.113.50", a.target());
        assertEquals("HIGH", a.severity());
        assertEquals(2.0, a.value(), 0.001);   // 2 request khop
        assertTrue(a.message().contains("SQL_INJECTION") && a.message().contains("XSS"));
    }

    @Test
    void khongCoRequest_khongBao() {
        assertEquals(0, r8.evaluate(snapshot(List.of())).size());
    }

    // ---------- Ham phu ----------

    private static RequestRecord req(String path, String query, String userAgent) {
        return req("203.0.113.50", path, query, userAgent);
    }

    private static RequestRecord req(String ip, String path, String query, String userAgent) {
        return new RequestRecord("2026-11-02T15:00:00Z", ip, path, query, userAgent, 200);
    }

    private static Snapshot snapshot(List<RequestRecord> recent) {
        return new Snapshot(60, recent.size(), 0, 0, null, Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), recent);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paramsFromRulesYaml() {
        try (InputStream in = AttackSignatureRuleTest.class.getResourceAsStream("/rules.yaml")) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> rules = (Map<String, Object>) root.get("rules");
            return (Map<String, Object>) rules.get("ATTACK_SIGNATURE");
        } catch (Exception e) {
            throw new IllegalStateException("Khong doc duoc rules.yaml", e);
        }
    }
}
