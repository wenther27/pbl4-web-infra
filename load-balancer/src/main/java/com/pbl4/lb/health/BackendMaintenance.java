package com.pbl4.lb.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static net.logstash.logback.argument.StructuredArguments.kv;

// Bao tri co ke hoach (rolling deploy): "drain" 1 backend truoc khi khoi dong lai no.
//
//   drain   -> LB KHONG gui request MOI vao backend nay, request dang xu ly van chay tiep
//   cho     -> so request dang xu ly (inFlight) ve 0
//   restart -> khoi dong lai / cap nhat backend (script ops/rolling-deploy.ps1 lam)
//   undrain -> backend len lai thi nhan request binh thuong
//
// Lam lan luot tung backend -> nguoi dung khong gap loi nao trong luc cap nhat ca he thong.
public class BackendMaintenance {
    private static final Logger log = LoggerFactory.getLogger(BackendMaintenance.class);

    private final Set<String> draining = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();

    public boolean isDraining(String backend) {
        return draining.contains(backend);
    }

    public void drain(String backend, String by) {
        if (draining.add(backend)) {
            log.info("DRAIN " + backend + ": ngung nhan request moi (" + inFlight(backend) + " dang xu ly)",
                    kv("event", "backend_drain"), kv("backend", backend), kv("inFlight", inFlight(backend)),
                    kv("by", by));
        }
    }

    public void undrain(String backend, String by) {
        if (draining.remove(backend)) {
            log.info("UNDRAIN " + backend + ": nhan request tro lai",
                    kv("event", "backend_undrain"), kv("backend", backend), kv("by", by));
        }
    }

    // Dem request dang xu ly: ProxyHandler goi begin khi gui sang backend, end khi xong han (ke ca stream)
    public void begin(String backend) {
        inFlight.computeIfAbsent(backend, k -> new AtomicInteger()).incrementAndGet();
    }

    public void end(String backend) {
        AtomicInteger n = inFlight.get(backend);
        if (n != null) {
            n.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    public int inFlight(String backend) {
        AtomicInteger n = inFlight.get(backend);
        return n == null ? 0 : n.get();
    }

    // Backend duoc nhan request moi = dang UP va khong bi drain.
    // Neu vi ly do nao do khong con backend nao -> dung ca backend dang drain (con hon tra 503).
    public List<String> available(List<String> healthy) {
        List<String> result = healthy.stream().filter(b -> !draining.contains(b)).toList();
        return result.isEmpty() ? healthy : result;
    }

    // "web-1" hoac "http://web-1:8080" -> URL backend trong danh sach
    public static Optional<String> resolve(String nameOrUrl, List<String> backends) {
        for (String b : backends) {
            if (b.equalsIgnoreCase(nameOrUrl) || nameOf(b).equalsIgnoreCase(nameOrUrl)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    // "http://web-1:8080" -> "web-1"
    public static String nameOf(String backend) {
        try {
            String host = URI.create(backend).getHost();
            return host == null ? backend : host;
        } catch (IllegalArgumentException e) {
            return backend;
        }
    }
}