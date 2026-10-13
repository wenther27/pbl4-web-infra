package com.pbl4.lb;

import com.pbl4.lb.config.ServerConfigLoader;
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

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", new ProxyHandler(config.backends(), strategy));

        // Mac dinh HttpServer chi dung 1 luong -> request xep hang doi nhau.
        // Co thread pool thi 50 request den cung luc moi duoc xu ly song song.
        server.setExecutor(Executors.newFixedThreadPool(50));

        server.start();
        System.out.println("Load Balancer: http://localhost:" + config.port());
        System.out.println("Thuat toan: " + strategy.name());
        System.out.println("Backends: " + config.backends());
    }
}