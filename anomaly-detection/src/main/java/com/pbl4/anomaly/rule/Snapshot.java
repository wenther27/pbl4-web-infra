package com.pbl4.anomaly.rule;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
        Map<String, Long> downEventsByBackend,  // backend -> so lan bao DOWN   (R6)
        Map<String, Long> loginFailByIp,        // IP -> so lan dang nhap sai    (R3)
        List<Long> baselinePerMinute            // so request moi phut trong N phut truoc (R7)
) {

    // Giu cach goi cu (khong co baseline) -> R7 tu bo qua vi thieu lich su
    public static Snapshot from(JsonNode res, int windowSeconds) {
        return from(res, null, windowSeconds);
    }

    // res: ket qua QUERY_TEMPLATE, baselineRes: ket qua BASELINE_TEMPLATE (co the null)
    public static Snapshot from(JsonNode res, JsonNode baselineRes, int windowSeconds) {
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
        Map<String, Long> loginFail = countBuckets(req.path("login_fail").path("by_ip"));

        return new Snapshot(windowSeconds, total, err4xx, err5xx, p95,
                Collections.unmodifiableMap(byIp),
                Collections.unmodifiableMap(scan),
                Collections.unmodifiableMap(down),
                Collections.unmodifiableMap(loginFail),
                List.copyOf(readBaseline(baselineRes)));
    }

    // date_histogram -> danh sach so request cua tung phut (ke ca phut = 0)
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

    // Do lech chuan (chia cho n): do muc "dao dong binh thuong" quanh trung binh
    public double baselineStd() {
        if (baselinePerMinute.isEmpty()) return 0;
        double mean = baselineMean();
        double sq = 0;
        for (long v : baselinePerMinute) sq += (v - mean) * (v - mean);
        return Math.sqrt(sq / baselinePerMinute.size());
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
                + ", p95=" + p95 + ", top IP: " + (top.isEmpty() ? "-" : top.toString().trim())
                + (baselinePerMinute.isEmpty() ? ""
                : String.format(", nen %d phut: %.1f +- %.1f req/phut",
                baselinePerMinute.size(), baselineMean(), baselineStd()));
    }
}