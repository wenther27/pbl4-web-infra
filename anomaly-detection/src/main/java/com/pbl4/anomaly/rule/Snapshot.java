package com.pbl4.anomaly.rule;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Snapshot(
        int windowSeconds,
        long total,
        long err4xx,
        long err5xx,
        Double p95Ms,
        Map<String, Long> requestsByIp,
        Map<String, Long> scanPathsByIp,
        Map<String, Long> downEventsByBackend,
        Map<String, Long> loginFailByIp,
        List<Long> baselinePerMinute,
        List<RequestRecord> recentRequests
) {

    public Snapshot(int windowSeconds, long total, long err4xx, long err5xx, Double p95Ms,
                    Map<String, Long> requestsByIp, Map<String, Long> scanPathsByIp,
                    Map<String, Long> downEventsByBackend, Map<String, Long> loginFailByIp,
                    List<Long> baselinePerMinute) {
        this(windowSeconds, total, err4xx, err5xx, p95Ms, requestsByIp, scanPathsByIp,
                downEventsByBackend, loginFailByIp, baselinePerMinute, List.of());
    }

    public static Snapshot from(JsonNode res, int windowSeconds) {
        return from(res, null, windowSeconds);
    }

    public static Snapshot from(JsonNode res, JsonNode baselineRes, int windowSeconds) {
        return from(res, baselineRes, null, windowSeconds);
    }

    public static Snapshot from(JsonNode res, JsonNode baselineRes, JsonNode recentRes, int windowSeconds) {
        JsonNode aggs = res.path("aggregations");
        JsonNode req = aggs.path("req");

        long total = req.path("doc_count").asLong();
        long err4xx = req.path("errors_4xx").path("doc_count").asLong();
        long err5xx = req.path("errors_5xx").path("doc_count").asLong();

        JsonNode p95Node = req.path("latency").path("values").path("95.0");
        Double p95 = p95Node.isNumber() ? p95Node.asDouble() : null;

        Map<String, Long> byIp = countBuckets(req.path("by_ip"));

        Map<String, Long> scan = new LinkedHashMap<>();
        for (JsonNode b : req.path("scan").path("by_ip").path("buckets")) {
            scan.put(b.path("key").asText(), b.path("paths").path("value").asLong());
        }

        Map<String, Long> down = countBuckets(aggs.path("down").path("by_backend"));
        Map<String, Long> loginFail = countBuckets(req.path("login_fail").path("by_ip"));

        return new Snapshot(windowSeconds, total, err4xx, err5xx, p95,
                Collections.unmodifiableMap(byIp),
                Collections.unmodifiableMap(scan),
                Collections.unmodifiableMap(down),
                Collections.unmodifiableMap(loginFail),
                List.copyOf(readBaseline(baselineRes)),
                List.copyOf(readRecent(recentRes)));
    }

    private static List<RequestRecord> readRecent(JsonNode recentRes) {
        List<RequestRecord> list = new ArrayList<>();
        if (recentRes == null) {
            return list;
        }
        for (JsonNode hit : recentRes.path("hits").path("hits")) {
            JsonNode d = hit.path("_source");
            list.add(new RequestRecord(
                    d.path("@timestamp").asText(""),
                    d.path("clientIp").asText(""),
                    d.path("path").asText(""),
                    d.path("query").asText(""),
                    d.path("userAgent").asText(""),
                    d.path("status").asInt(0),
                    d.path("body").asText("")));
        }
        return list;
    }

    private static List<Long> readBaseline(JsonNode baselineRes) {
        List<Long> list = new ArrayList<>();
        if (baselineRes == null) {
            return list;
        }
        for (JsonNode b : baselineRes.path("aggregations").path("per_minute").path("buckets")) {
            list.add(b.path("doc_count").asLong());
        }
        return list;
    }

    public double baselineMean() {
        if (baselinePerMinute.isEmpty()) return 0;
        double sum = 0;
        for (long v : baselinePerMinute) sum += v;
        return sum / baselinePerMinute.size();
    }

    public double baselineStd() {
        if (baselinePerMinute.isEmpty()) return 0;
        double mean = baselineMean();
        double sq = 0;
        for (long v : baselinePerMinute) sq += (v - mean) * (v - mean);
        return Math.sqrt(sq / baselinePerMinute.size());
    }

    private static Map<String, Long> countBuckets(JsonNode termsAgg) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (JsonNode b : termsAgg.path("buckets")) {
            map.put(b.path("key").asText(), b.path("doc_count").asLong());
        }
        return map;
    }

    public String summary() {
        String p95 = (p95Ms == null) ? "-" : String.format("%.0fms", p95Ms);
        StringBuilder top = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Long> e : requestsByIp.entrySet()) {
            if (n++ == 5) break;
            top.append(e.getKey()).append('=').append(e.getValue()).append(' ');
        }
        return windowSeconds + "s qua: " + total + " request, 4xx=" + err4xx + ", 5xx=" + err5xx
                + ", p95=" + p95 + ", top IP: " + (top.isEmpty() ? "-" : top.toString().trim())
                + (baselinePerMinute.isEmpty() ? ""
                : String.format(", nen %d phut: %.1f +- %.1f req/phut",
                baselinePerMinute.size(), baselineMean(), baselineStd()));
    }
}