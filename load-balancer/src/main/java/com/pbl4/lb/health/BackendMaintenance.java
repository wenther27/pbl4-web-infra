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
import java.util.concurrent.atomic.AtomicLong;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class BackendMaintenance {
    private static final Logger log = LoggerFactory.getLogger(BackendMaintenance.class);

    private final Set<String> draining = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> served = new ConcurrentHashMap<>();

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

    public void begin(String backend) {
        inFlight.computeIfAbsent(backend, k -> new AtomicInteger()).incrementAndGet();
        served.computeIfAbsent(backend, k -> new AtomicLong()).incrementAndGet();
    }

    public long served(String backend) {
        AtomicLong n = served.get(backend);
        return n == null ? 0 : n.get();
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

    public List<String> available(List<String> healthy) {
        List<String> result = healthy.stream().filter(b -> !draining.contains(b)).toList();
        return result.isEmpty() ? healthy : result;
    }

    public static Optional<String> resolve(String nameOrUrl, List<String> backends) {
        for (String b : backends) {
            if (b.equalsIgnoreCase(nameOrUrl) || nameOf(b).equalsIgnoreCase(nameOrUrl)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    public static String nameOf(String backend) {
        try {
            String host = URI.create(backend).getHost();
            return host == null ? backend : host;
        } catch (IllegalArgumentException e) {
            return backend;
        }
    }
}