package com.pbl4.lb.proxy;

import com.pbl4.lb.strategy.LoadBalancingStrategy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class ProxyHandler implements HttpHandler {

    private final List<String> backends;
    private final LoadBalancingStrategy strategy;
    private final HttpClient client;

    public ProxyHandler(List<String> backends, LoadBalancingStrategy strategy) {
        this.backends = backends;
        this.strategy = strategy;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            //Hoi strategy lan nay gui cho ai
            String backend = strategy.next(backends);
            URI target = URI.create(backend + exchange.getRequestURI());
            System.out.printf("%s %s -> %s%n",
                    exchange.getRequestMethod(), exchange.getRequestURI(), backend);
            byte[] body = exchange.getRequestBody().readAllBytes();

            // 3. Dung request gui sang backend, giu nguyen method va body
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(10))
                    .method(exchange.getRequestMethod(),
                            HttpRequest.BodyPublishers.ofByteArray(body));

            // Chuyen tiep Content-Type va Authorization (can cho POST JSON va JWT)
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType != null) builder.header("Content-Type", contentType);
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null) builder.header("Authorization", auth);

            HttpResponse<byte[]> res = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            // 4. Tra ket qua ve client: copy header cua backend, roi status + body
            res.headers().map().forEach((name, values) -> {
                if (!isHopByHop(name)) {
                    exchange.getResponseHeaders().put(name, values);
                }
            });

            byte[] resBody = res.body();
            exchange.sendResponseHeaders(res.statusCode(),
                    resBody.length == 0 ? -1 : resBody.length);
            if (resBody.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resBody);
                }
            }

        } catch (Exception e) {
            // Backend chet / timeout -> 502 Bad Gateway
            byte[] msg = ("Loi ket noi backend: " + e.getMessage())
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, msg.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(msg);
            }
        } finally {
            exchange.close();
        }
    }

    // Header chi co nghia tren mot chang ket noi, khong duoc chuyen tiep
    private boolean isHopByHop(String name) {
        String n = name.toLowerCase();
        return n.equals("connection") || n.equals("transfer-encoding")
                || n.equals("keep-alive") || n.equals("upgrade")
                || n.equals("te") || n.equals("trailer")
                || n.equals("proxy-authenticate") || n.equals("proxy-authorization")
                || n.equals("content-length") || n.equals(":status");
    }
}