package com.pbl4.lb.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class BlockList {
    private static final Logger log = LoggerFactory.getLogger(BlockList.class);

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6 = Pattern.compile("^(?=.*:)[0-9a-fA-F:]{2,39}$");

    public static final int MAX_MINUTES = 24 * 60;

    public record Entry(String ip, Instant since, Instant until, String reason, String by) {}

    private final Map<String, Entry> blocked = new ConcurrentHashMap<>();
    private final Set<String> neverBlock;

    public BlockList(Set<String> neverBlock) {
        this.neverBlock = Set.copyOf(neverBlock);
    }

    public static BlockList fromEnv() {
        String raw = System.getenv().getOrDefault("LB_NEVER_BLOCK", "127.0.0.1");
        Set<String> ips = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return new BlockList(ips);
    }

    public Set<String> neverBlock() {
        return neverBlock;
    }

    public boolean isBlocked(String ip) {
        Entry e = blocked.get(ip);
        if (e == null) {
            return false;
        }
        if (Instant.now().isBefore(e.until())) {
            return true;
        }
        if (blocked.remove(ip, e)) {
            log.info("Het han chan IP " + ip, kv("event", "ip_unblocked"), kv("clientIp", ip),
                    kv("reason", "expired"));
        }
        return false;
    }

    public Entry block(String ip, int minutes, String reason, String by) {
        String value = (ip == null) ? "" : ip.trim();
        if (!IPV4.matcher(value).matches() && !IPV6.matcher(value).matches()) {
            throw new IllegalArgumentException("IP khong hop le: " + value);
        }
        if (minutes < 1 || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException("So phut phai tu 1 den " + MAX_MINUTES);
        }
        if (neverBlock.contains(value)) {
            throw new ProtectedIpException("IP " + value + " nam trong danh sach khong duoc chan (LB_NEVER_BLOCK)");
        }
        Instant now = Instant.now();
        Entry entry = new Entry(value, now, now.plusSeconds(minutes * 60L),
                blankTo(reason, "khong ghi ly do"), blankTo(by, "admin"));
        blocked.put(value, entry);
        log.warn("Chan IP " + value + " trong " + minutes + " phut (" + entry.reason() + ")",
                kv("event", "ip_blocked"), kv("clientIp", value), kv("minutes", minutes),
                kv("reason", entry.reason()), kv("by", entry.by()));
        return entry;
    }

    public boolean unblock(String ip, String by) {
        Entry removed = blocked.remove(ip);
        if (removed == null) {
            return false;
        }
        log.info("Go chan IP " + ip, kv("event", "ip_unblocked"), kv("clientIp", ip),
                kv("reason", "manual"), kv("by", blankTo(by, "admin")));
        return true;
    }

    public List<Entry> list() {
        List<Entry> result = new ArrayList<>();
        for (String ip : List.copyOf(blocked.keySet())) {
            if (isBlocked(ip)) {
                Entry e = blocked.get(ip);
                if (e != null) {
                    result.add(e);
                }
            }
        }
        result.sort(Comparator.comparing(Entry::until));
        return result;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    public static class ProtectedIpException extends IllegalArgumentException {
        public ProtectedIpException(String message) {
            super(message);
        }
    }
}