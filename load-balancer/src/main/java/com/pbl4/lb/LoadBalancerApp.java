package com.pbl4.lb;

import com.pbl4.lb.proxy.ProxyHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class LoadBalancerApp {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String backend = "http://localhost:8081";   // buoi 1: forward cung

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ProxyHandler(backend));

        // Mac dinh HttpServer chi dung 1 luong -> request xep hang doi nhau.
        // Co thread pool thi 50 request den cung luc moi duoc xu ly song song.
        server.setExecutor(Executors.newFixedThreadPool(50));

        server.start();
        System.out.println("Load Balancer: http://localhost:" + port + " -> " + backend);
    }
}