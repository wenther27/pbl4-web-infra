package com.pbl4.lb;

import com.pbl4.lb.admin.AdminHandler;
import com.pbl4.lb.config.ServerConfigLoader;
import com.pbl4.lb.health.HealthChecker;
import com.pbl4.lb.proxy.ProxyHandler;
import com.pbl4.lb.security.BlockList;
import com.pbl4.lb.strategy.LoadBalancingStrategy;
import com.pbl4.lb.strategy.RoundRobinStrategy;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class LoadBalancerApp {
    private static final Logger log = LoggerFactory.getLogger(LoadBalancerApp.class);

    public static void main(String[] args) throws Exception {
        ServerConfigLoader.Config config = ServerConfigLoader.load();
        LoadBalancingStrategy strategy = new RoundRobinStrategy();

        // Kiem tra moi 5s, that bai 3 lan -> DOWN, thanh cong 2 lan -> UP
        HealthChecker health = new HealthChecker(config.backends(), 5, 3, 2);
        health.start();

        // Danh sach IP bi chan: dung chung giua ProxyHandler (kiem tra) va AdminHandler (them/xoa)
        BlockList blockList = BlockList.fromEnv();

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", new ProxyHandler(health, strategy, blockList));

        // Mac dinh HttpServer chi dung 1 luong -> request xep hang doi nhau.
        // Co thread pool thi 50 request den cung luc moi duoc xu ly song song.
        server.setExecutor(Executors.newFixedThreadPool(50));

        server.start();
        log.info("Load Balancer chay tai cong " + config.port()
                        + ", thuat toan " + strategy.name()
                        + ", backends " + config.backends(),
                kv("event", "lb_start"),
                kv("port", config.port()),
                kv("strategy", strategy.name()),
                kv("backends", config.backends()));

        startAdminServer(blockList);
    }

    // API quan tri chay o cong rieng. Khong dat LB_ADMIN_TOKEN thi KHONG mo -> mac dinh an toan.
    private static void startAdminServer(BlockList blockList) throws Exception {
        String token = System.getenv("LB_ADMIN_TOKEN");
        if (token == null || token.isBlank()) {
            log.warn("Khong dat LB_ADMIN_TOKEN -> tat API quan tri (chan IP)", kv("event", "admin_disabled"));
            return;
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("LB_ADMIN_PORT", "8090").trim());
        HttpServer admin = HttpServer.create(new InetSocketAddress(port), 0);
        admin.createContext("/admin", new AdminHandler(blockList, token.trim()));
        admin.setExecutor(Executors.newFixedThreadPool(2));
        admin.start();
        log.info("API quan tri chay tai cong " + port + ", khong bao gio chan: " + blockList.neverBlock(),
                kv("event", "admin_start"), kv("port", port));
    }
}