package com.pbl4.lb;

import com.pbl4.lb.admin.AdminHandler;
import com.pbl4.lb.admin.BackendAdminHandler;
import com.pbl4.lb.admin.RateLimitAdminHandler;
import com.pbl4.lb.config.ServerConfigLoader;
import com.pbl4.lb.health.BackendMaintenance;
import com.pbl4.lb.health.HealthChecker;
import com.pbl4.lb.proxy.ProxyHandler;
import com.pbl4.lb.security.BlockList;
import com.pbl4.lb.security.RateLimiter;
import com.pbl4.lb.strategy.LoadBalancingStrategy;
import com.pbl4.lb.strategy.RoundRobinStrategy;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pbl4.lb.strategy.DwlcStrategy;
import java.util.Locale;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class LoadBalancerApp {
    private static final Logger log = LoggerFactory.getLogger(LoadBalancerApp.class);

    private static LoadBalancingStrategy createStrategy() {
        String name = System.getenv().getOrDefault("LB_STRATEGY", "dwlc").trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "dwlc" -> new DwlcStrategy();
            case "round-robin", "rr" -> new RoundRobinStrategy();
            default -> throw new IllegalStateException(
                    "LB_STRATEGY khong hop le: " + name + " (chi nhan dwlc hoac round-robin)");
        };
    }

    public static void main(String[] args) throws Exception {
        ServerConfigLoader.Config config = ServerConfigLoader.load();
        LoadBalancingStrategy strategy = createStrategy();

        long grace = Long.parseLong(System.getenv().getOrDefault("LB_STARTUP_GRACE_SECONDS", "180").trim());
        HealthChecker health = new HealthChecker(config.backends(), 5, 3, 2, grace);
        BackendMaintenance maintenance = new BackendMaintenance();
        health.setMaintenanceCheck(maintenance::isDraining);
        health.start();

        BlockList blockList = BlockList.fromEnv();
        RateLimiter rateLimiter = RateLimiter.fromEnv(blockList.neverBlock());

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", new ProxyHandler(health, strategy, blockList, maintenance, rateLimiter));

        server.setExecutor(Executors.newFixedThreadPool(50));

        server.start();
        log.info("Load Balancer chay tai cong " + config.port()
                        + ", thuat toan " + strategy.name()
                        + ", backends " + config.backends(),
                kv("event", "lb_start"),
                kv("port", config.port()),
                kv("strategy", strategy.name()),
                kv("backends", config.backends()));

        startAdminServer(blockList, config.backends(), health, maintenance, rateLimiter, strategy);
    }

    private static void startAdminServer(BlockList blockList, java.util.List<String> backends,
                                         HealthChecker health, BackendMaintenance maintenance,
                                         RateLimiter rateLimiter, LoadBalancingStrategy strategy) throws Exception {
        String token = System.getenv("LB_ADMIN_TOKEN");
        if (token == null || token.isBlank()) {
            log.warn("Khong dat LB_ADMIN_TOKEN -> tat API quan tri (chan IP)", kv("event", "admin_disabled"));
            return;
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("LB_ADMIN_PORT", "8090").trim());
        HttpServer admin = HttpServer.create(new InetSocketAddress(port), 0);
        admin.createContext("/admin", new AdminHandler(blockList, token.trim()));
        admin.createContext("/admin/backends", new BackendAdminHandler(backends, health, maintenance, strategy, token.trim()));
        admin.createContext("/admin/ratelimit", new RateLimitAdminHandler(rateLimiter, token.trim()));
        admin.setExecutor(Executors.newFixedThreadPool(2));
        admin.start();
        log.info("API quan tri chay tai cong " + port + ", khong bao gio chan: " + blockList.neverBlock(),
                kv("event", "admin_start"), kv("port", port));
    }
}