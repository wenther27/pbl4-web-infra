package com.pbl4.anomaly.rule;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// "Anh chup" luu luong trong mot cua so: ket qua cua 1 lan hoi Elasticsearch,
// da doi tu JSON sang doi tuong Java. Moi luat chi doc Snapshot, khong tu goi ES.
public record Snapshot(
        int windowSeconds,
        long total,                             // tong so request
        long err4xx,
        long err5xx,
        Double p95Ms,                           // null neu khong co request nao
        Map<String, Long> requestsByIp,         // IP -> so request            (R1)
        Map<String, Long> scanPathsByIp,        // IP -> so path 403/404 khac nhau (R2)
        Map<String, Long> downEventsByBackend   // backend -> so lan bao DOWN   (R6)
) {

    // Doc ket qua tra ve tu QUERY trong AnomalyDetectorApp
    public static Snapshot from(JsonNode res, int windowSeconds) {
        JsonNode aggs = res.path("aggregations");
        JsonNode req = aggs.path("req");

        long total = req.path("doc_count").asLong();
        long err4xx = req.path("errors_4xx").path("doc_count").asLong();
        long err5xx = req.path("errors_5xx").path("doc_count").asLong();

        JsonNode p95Node = req.path("latency").path("values").path("95.0");
        Double p95 = p95Node.isNumber() ? p95Node.asDouble() : null;

        Map<String, Long> byIp = countBuckets(req.path("by_ip"));

        // Moi bucket IP co them so do con "paths" (cardinality = so gia tri khac nhau)
        Map<String, Long> scan = new LinkedHashMap<>();
        for (JsonNode b : req.path("scan").path("by_ip").path("buckets")) {
            scan.put(b.path("key").asText(), b.path("paths").path("value").asLong());
        }

        Map<String, Long> down = countBuckets(aggs.path("down").path("by_backend"));

        return new Snapshot(windowSeconds, total, err4xx, err5xx, p95,
                Collections.unmodifiableMap(byIp),
                Collections.unmodifiableMap(scan),
                Collections.unmodifiableMap(down));
    }

    // terms aggregation -> Map<key, doc_count>, giu nguyen thu tu (nhieu nhat truoc)
    private static Map<String, Long> countBuckets(JsonNode termsAgg) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (JsonNode b : termsAgg.path("buckets")) {
            map.put(b.path("key").asText(), b.path("doc_count").asLong());
        }
        return map;
    }

    // Mot dong tom tat de in log (giong buoi 1)
    public String summary() {
        String p95 = (p95Ms == null) ? "-" : String.format("%.0fms", p95Ms);
        StringBuilder top = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Long> e : requestsByIp.entrySet()) {
            if (n++ == 5) break;   // chi in 5 IP dau
            top.append(e.getKey()).append('=').append(e.getValue()).append(' ');
        }
        return windowSeconds + "s qua: " + total + " request, 4xx=" + err4xx + ", 5xx=" + err5xx
                + ", p95=" + p95 + ", top IP: " + (top.isEmpty() ? "-" : top.toString().trim());
    }
}