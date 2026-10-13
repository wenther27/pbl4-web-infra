package com.pbl4.lb.proxy;

import com.pbl4.lb.health.HealthChecker;
import com.pbl4.lb.strategy.LoadBalancingStrategy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ProxyHandler implements HttpHandler {

//    true: noi IP client vao sau X-Forwarded-For co san
//    false: bo X-Forwarded-for client gui len, chi ghi IP that(an toan hon khi LB ngoai cung)
    private static final boolean TRUST_CLIENT_FORWARDED_FOR = true;

    //phuong thuc gui lai nhieu lan van cho cung ket qua -> retry an toan
    private static final Set<String> IDEMPOTENT = Set.of("GET", "HEAD", "OPTIONS", "PUT", "DELETE");

    //HEader k duoc chuyen tiep: hop-by-hop, header httpclient cam tu dat
    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of(
            "connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade",
            "proxy-authorization", "proxy-authenticate",
            "host", "content-length", "expect",
            "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto", "x-forwarded-port");

    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of(
            "connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade",
            "proxy-authorization", "proxy-authenticate",
            "content-length", ":status");



    private final HealthChecker health;
    private final LoadBalancingStrategy strategy;
    private final HttpClient client;

    public ProxyHandler(HealthChecker health, LoadBalancingStrategy strategy) {
        this.health = health;
        this.strategy = strategy;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            //Chi chon trong cac be dang song
            List<String> healthy = health.healthyBackends();
            if (healthy.isEmpty()) {
                sendText(exchange, 503, "Khong con backend nao kha dung");
                return;
            }
            String method = exchange.getRequestMethod();
            //Doc body mot lan vao bo nho -> gui lai duoc o lan thu sau
            byte[] body = exchange.getRequestBody().readAllBytes();

            Set<String> tried = new HashSet<>();
            HttpResponse<byte[]> response = null;
            String lastError = "";
            int attempt = 0;

            while(attempt < healthy.size()) {
                attempt++;
                String backend = pickBackend(healthy, tried);
                tried.add(backend);
                try {
                    response = forward(exchange, backend, method, body);
                    System.out.printf("%s %s -> %s (lan %d)%n",
                            method, exchange.getRequestURI(), backend, attempt);
                    break;
                } catch (IOException e) {
                    lastError = e.getClass().getSimpleName();
                    //ket noi khong thanh - > be chua nhan request -> thu lai luon an toan
                    boolean neverReached = e instanceof ConnectException
                            || e instanceof HttpConnectTimeoutException;
                    System.out.printf("[RETRY] %s %s -> %s loi %s (lan %d)%n",
                            method, exchange.getRequestURI(), backend, lastError, attempt);

                    if (!neverReached && !IDEMPOTENT.contains(method))
                        break; // POST da toi be -> khong gui lai tranh gay tac nghen
                }
            }

            if (response == null) {
                sendText(exchange, 502, "Tat ca backend deu loi (" + lastError + ")");
                return;
            }
            exchange.getResponseHeaders().set("X-LB-Attempts", String.valueOf(attempt));
            writeResponse(exchange, response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendText(exchange, 502, "LB bi ngat khi dang cho Backend " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    //Hoi strategy neu no chon trung be da thu thi lay be chua thu dau tien
    private String pickBackend(List<String> healthy, Set<String> tried) {
        String candidate = strategy.next(healthy);
        if (!tried.contains(candidate))
            return candidate;
        for (String b : healthy) {
            if (!tried.contains(b))
                return b;
        }
        return candidate;
    }

    //gui 1 request sang 1 be, nem EX neu be loi
    private HttpResponse<byte[]> forward(HttpExchange exchange, String backend,
                                         String method, byte[] body)
             throws IOException, InterruptedException  {
        URI target = URI.create(backend + exchange.getRequestURI());
        HttpRequest.BodyPublisher publisher = body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(10))
                .method(method, publisher);

        //1. Chuyen tiep moi head cua client, tru danh sach bo qua
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!SKIP_REQUEST_HEADERS.contains(name.toLowerCase())) {
                for (String v : values)
                    builder.header(name, v);
            }
        });

        //2. X-Forwarded-For: ip cua ben vua ket noi toi LB
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        String prior = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        String xff = (TRUST_CLIENT_FORWARDED_FOR && prior != null && !prior.isBlank())
                ? prior + ", " + clientIp
                : clientIp;
        builder.header("X-Forwarded-For", xff);

        //3. dia chi, cong va giao thuc client da goi (localhost:8080, http)
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host != null) {
            builder.header("X-Forwarded-Host", host);
            int colon = host.lastIndexOf(':');
            String port = (colon > host.lastIndexOf(']')) ? host.substring(colon + 1) : "80";
            builder.header("X-Forwarded-Port", port);
        }
        builder.header("X-Forwarded-Proto", "http");

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    //Tra kq be ve client, loi o day la loi phia client, khong retry
    private void writeResponse(HttpExchange exchange, HttpResponse<byte[]> response)
            throws IOException {
        response.headers().map().forEach((name, values) -> {
            if (!SKIP_RESPONSE_HEADERS.contains(name.toLowerCase()))
                exchange.getResponseHeaders().put(name, values);
        });

        byte[] resBody = response.body();
        exchange.sendResponseHeaders(response.statusCode(), resBody.length == 0 ? -1 : resBody.length);
        if (resBody.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resBody);
            }
        }
    }

    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] msg = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, msg.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(msg);
        }
    }
}