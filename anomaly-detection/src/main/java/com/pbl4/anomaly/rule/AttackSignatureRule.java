package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class AttackSignatureRule extends ThresholdRule {

    private static final int SAMPLE_MAX = 120;

    private final double minMatches;
    private final Map<String, List<Pattern>> contentSignatures;
    private final List<Pattern> userAgentSignatures;

    public AttackSignatureRule(Map<String, Object> params) {
        super("ATTACK_SIGNATURE", params);
        this.minMatches = number("min_matches", 1);
        this.contentSignatures = compileGroups(params.get("signatures"));
        this.userAgentSignatures = compileList(params.get("user_agent_signatures"));
    }

    @Override
    public List<Alert> evaluate(Snapshot s) {
        Map<String, Hit> byIp = new LinkedHashMap<>();
        for (RequestRecord r : s.recentRequests()) {
            String ip = r.clientIp();
            if (ip == null || ip.isBlank()) {
                continue;
            }
            Set<String> groups = match(r);
            if (groups.isEmpty()) {
                continue;
            }
            Hit hit = byIp.computeIfAbsent(ip, k -> new Hit(sample(r, groups)));
            hit.count++;
            hit.groups.addAll(groups);
        }

        List<Alert> alerts = new ArrayList<>();
        byIp.forEach((ip, hit) -> {
            if (hit.count >= minMatches) {
                alerts.add(alert(ip,
                        "IP " + ip + " gui " + hit.count + " request chua dau hieu tan cong "
                                + hit.groups + ", vd: " + hit.sample,
                        hit.count, minMatches));
            }
        });
        return alerts;
    }

    Set<String> match(RequestRecord r) {
        Set<String> groups = new LinkedHashSet<>();
        String target = decode(nz(r.path()) + (nz(r.query()).isEmpty() ? "" : "?" + r.query()));
        contentSignatures.forEach((group, patterns) -> {
            for (Pattern p : patterns) {
                if (p.matcher(target).find()) {
                    groups.add(group);
                    break;
                }
            }
        });
        String ua = nz(r.userAgent());
        for (Pattern p : userAgentSignatures) {
            if (p.matcher(ua).find()) {
                groups.add("SCANNER_TOOL");
                break;
            }
        }
        return groups;
    }

    static String decode(String s) {
        String current = s;
        for (int i = 0; i < 2; i++) {
            try {
                String next = URLDecoder.decode(current, StandardCharsets.UTF_8);
                if (next.equals(current)) {
                    break;
                }
                current = next;
            } catch (IllegalArgumentException e) {
                break;
            }
        }
        return current;
    }

    private static String sample(RequestRecord r, Set<String> groups) {
        boolean onlyUserAgent = groups.size() == 1 && groups.contains("SCANNER_TOOL");
        String text = onlyUserAgent
                ? "User-Agent: " + nz(r.userAgent())
                : decode(nz(r.path()) + (nz(r.query()).isEmpty() ? "" : "?" + r.query()));
        text = text.replaceAll("\\p{Cntrl}", " ");
        return text.length() > SAMPLE_MAX ? text.substring(0, SAMPLE_MAX) + "..." : text;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static Map<String, List<Pattern>> compileGroups(Object raw) {
        Map<String, List<Pattern>> map = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                List<Pattern> list = compileList(v);
                if (!list.isEmpty()) {
                    map.put(String.valueOf(k), list);
                }
            });
        }
        return map;
    }

    private static List<Pattern> compileList(Object raw) {
        List<Pattern> list = new ArrayList<>();
        if (raw instanceof List<?> l) {
            for (Object o : l) {
                list.add(Pattern.compile(String.valueOf(o), Pattern.CASE_INSENSITIVE));
            }
        }
        return list;
    }

    private static final class Hit {
        int count;
        final Set<String> groups = new LinkedHashSet<>();
        final String sample;

        Hit(String sample) {
            this.sample = sample;
        }
    }
}
