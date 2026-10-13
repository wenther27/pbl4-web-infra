package com.pbl4.lb;

import com.pbl4.lb.config.ServerConfigLoader;
import com.pbl4.lb.health.HealthChecker;
import com.pbl4.lb.proxy.ProxyHandler;
import com.pbl4.lb.strategy.LoadBalancingStrategy;
import com.pbl4.lb.strategy.RoundRobinStrategy;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class LoadBalancerApp {

    public static void main(String[] args) throws Exception {
        ServerConfigLoader.Config config = ServerConfigLoader.load();
        LoadBalancingStrategy strategy = new RoundRobinStrategy();

        //kiem tra moi 5s, that bai 3 lan -> DOWN, thanh cong 2 lan -> UP
        HealthChecker health = new HealthChecker(config.backends(), 5, 3, 2);
        health.start();

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", new ProxyHandler(health, strategy));

        // Mac dinh HttpServer chi dung 1 luong -> request xep hang doi nhau.
        // Co thread pool thi 50 request den cung luc moi duoc xu ly song song.
        server.setExecutor(Executors.newFixedThreadPool(50));

        server.start();
        System.out.println("Load Balancer: http://localhost:" + config.port());
        System.out.println("Thuat toan: " + strategy.name());
        System.out.println("Backends: " + config.backends());
        System.out.println("Health check: moi 5s, DOWN sau 3 lan loi, UP sau 2 lan thanh cong");
    }
}