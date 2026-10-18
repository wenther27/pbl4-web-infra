package com.pbl4.lb.proxy;

import com.pbl4.lb.health.HealthChecker;
import com.pbl4.lb.strategy.LoadBalancingStrategy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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

import static net.logstash.logback.argument.StructuredArguments.kv;

public class ProxyHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(ProxyHandler.class);
    private static final int STREAM_BUFFER_SIZE = 64 * 1024;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = timeoutFromEnvironment(
            "LB_REQUEST_TIMEOUT_SECONDS", 30);
    private static final Duration VIDEO_UPLOAD_TIMEOUT = timeoutFromEnvironment(
            "LB_UPLOAD_TIMEOUT_SECONDS", 600);

    // LB_TRUST_XFF=true : tin X-Forwarded-For client gui (dung de gia lap nhieu IP khi test bat thuong)
    // LB_TRUST_XFF=false: bo qua, chi ghi IP ket noi that (an toan khi LB la lop ngoai cung)
    private static final boolean TRUST_CLIENT_FORWARDED_FOR =
            Boolean.parseBoolean(System.getenv().getOrDefault("LB_TRUST_XFF", "true"));

    // Phuong thuc gui lai nhieu lan van cho cung ket qua -> retry an toan
    private static final Set<String> IDEMPOTENT = Set.of("GET", "HEAD", "OPTIONS", "PUT", "DELETE");

    // Header khong chuyen tiep: hop-by-hop + header HttpClient cam tu dat + header LB tu dat lai
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
        long start = System.nanoTime();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String clientIp = originalClientIp(exchange);

        // Gia tri mac dinh, se duoc cap nhat dan -> finally ghi 1 dong log du thong tin
        int status = 500;
        String backend = "-";
        int attempt = 0;
        boolean streamingRequest = isStreamingRequest(method, path);

        try {
            List<String> healthy = health.healthyBackends();
            if (healthy.isEmpty()) {
                status = 503;
                sendText(exchange, status, "Khong con backend nao kha dung");
                return;
            }

            // Video co the toi 500 MB: khong doc toan bo vao RAM. Cac request JSON/anh nho
            // van duoc buffer de co the retry an toan khi backend chua nhan duoc request.
            byte[] bufferedBody = streamingRequest ? null : exchange.getRequestBody().readAllBytes();
            Set<String> tried = new HashSet<>();
            HttpResponse<InputStream> response = null;
            String lastError = "";
            int maximumAttempts = streamingRequest ? 1 : healthy.size();

            while (attempt < maximumAttempts) {
                attempt++;
                backend = pickBackend(healthy, tried);
                tried.add(backend);
                try {
                    response = forward(exchange, backend, method, bufferedBody, streamingRequest);
                    break;
                } catch (IOException e) {
                    lastError = e.getClass().getSimpleName();
                    boolean neverReached = e instanceof ConnectException
                            || e instanceof HttpConnectTimeoutException;

                    // Ten field "errorType" (khong dung "error"): Filebeat drop_fields xoa field "error",
                    // va "error" la ten dat truoc trong chuan ECS cua Elastic (kieu object).
                    log.warn("RETRY " + method + " " + path + " -> " + backend
                                    + " loi " + lastError + " (lan " + attempt + ")",
                            kv("event", "retry"),
                            kv("httpMethod", method),
                            kv("path", path),
                            kv("backend", backend),
                            kv("errorType", lastError),
                            kv("attempt", attempt),
                            kv("clientIp", clientIp));

                    if (streamingRequest || (!neverReached && !IDEMPOTENT.contains(method))) {
                        break; // request khong idempotent da toi backend -> khong gui lai
                    }
                }
            }

            if (response == null) {
                status = 502;
                sendText(exchange, status, "Tat ca backend deu loi (" + lastError + ")");
                return;
            }

            status = response.statusCode();
            exchange.getResponseHeaders().set("X-LB-Attempts", String.valueOf(attempt));
            writeResponse(exchange, response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = 502;
            sendText(exchange, status, "LB bi ngat khi dang cho backend");
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info(method + " " + path + " -> " + backend + " " + status
                            + " (" + durationMs + "ms, lan " + attempt + ")",
                    kv("event", "request"),
                    kv("httpMethod", method),
                    kv("path", path),
                    kv("status", status),
                    kv("durationMs", durationMs),
                    kv("backend", backend),
                    kv("attempts", attempt),
                    kv("streamingRequest", streamingRequest),
                    kv("clientIp", clientIp));
            exchange.close();
        }
    }

    // IP goc cua client: phan tu dau cua X-Forwarded-For (neu tin), neu khong la IP ket noi truc tiep
    private String originalClientIp(HttpExchange exchange) {
        String remote = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (!TRUST_CLIENT_FORWARDED_FOR) {
            return remote;
        }
        String xff = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remote;
        }
        return xff.split(",")[0].trim();
    }

    // Hoi strategy; neu no chon trung backend da thu thi lay backend chua thu dau tien
    private String pickBackend(List<String> healthy, Set<String> tried) {
        String candidate = strategy.next(healthy);
        if (!tried.contains(candidate)) {
            return candidate;
        }
        for (String b : healthy) {
            if (!tried.contains(b)) {
                return b;
            }
        }
        return candidate;
    }

    // Upload video dung InputStream de du lieu di tung khoi toi backend. Request nho
    // dung byte[] de van co the retry neu backend loi truoc khi xu ly request.
    private HttpResponse<InputStream> forward(HttpExchange exchange, String backend,
                                              String method, byte[] bufferedBody,
                                              boolean streamingRequest)
            throws IOException, InterruptedException {
        URI target = URI.create(backend + exchange.getRequestURI());
        HttpRequest.BodyPublisher publisher;
        if (streamingRequest) {
            InputStream requestBody = exchange.getRequestBody();
            HttpRequest.BodyPublisher streamingPublisher =
                    HttpRequest.BodyPublishers.ofInputStream(() -> requestBody);
            long contentLength = requestContentLength(exchange);
            publisher = contentLength > 0
                    ? HttpRequest.BodyPublishers.fromPublisher(streamingPublisher, contentLength)
                    : streamingPublisher;
        } else {
            publisher = bufferedBody.length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(bufferedBody);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(streamingRequest ? VIDEO_UPLOAD_TIMEOUT : DEFAULT_REQUEST_TIMEOUT)
                .method(method, publisher);

        // 1. Chuyen tiep moi header cua client, tru danh sach bo qua
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!SKIP_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                for (String v : values) {
                    builder.header(name, v);
                }
            }
        });

        // 2. X-Forwarded-For: IP cua ben vua ket noi toi LB
        String remoteIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        String prior = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        String xff = (TRUST_CLIENT_FORWARDED_FOR && prior != null && !prior.isBlank())
                ? prior + ", " + remoteIp
                : remoteIp;
        builder.header("X-Forwarded-For", xff);

        // 3. Dia chi, cong va giao thuc client da goi (localhost:8080, http)
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host != null) {
            builder.header("X-Forwarded-Host", host);
            int colon = host.lastIndexOf(':');
            String port = (colon > host.lastIndexOf(']')) ? host.substring(colon + 1) : "80";
            builder.header("X-Forwarded-Port", port);
        }
        builder.header("X-Forwarded-Proto", "http");

        // Chi cho den khi co response headers, phan body duoc doc dan trong writeResponse.
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    // Tra response theo tung khoi 64 KB. Dieu nay rat quan trong voi video/Range:
    // LB khong con giu ca file trong heap/direct memory truoc khi gui cho trinh duyet.
    private void writeResponse(HttpExchange exchange, HttpResponse<InputStream> response)
            throws IOException {
        response.headers().map().forEach((name, values) -> {
            if (!SKIP_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                exchange.getResponseHeaders().put(name, values);
            }
        });

        int responseStatus = response.statusCode();
        boolean bodyAllowed = responseBodyAllowed(exchange.getRequestMethod(), responseStatus);

        try (InputStream input = response.body()) {
            if (!bodyAllowed) {
                exchange.sendResponseHeaders(responseStatus, -1);
                return;
            }

            long contentLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(0L); // 0 = chunked khi backend khong biet truoc kich thuoc
            exchange.sendResponseHeaders(responseStatus, contentLength);

            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            try (OutputStream output = exchange.getResponseBody()) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
        }
    }

    private static boolean isStreamingRequest(String method, String path) {
        return "POST".equalsIgnoreCase(method)
                && (path.equals("/api/uploads/videos") || path.startsWith("/api/uploads/videos/"));
    }

    private static boolean responseBodyAllowed(String method, int status) {
        return !"HEAD".equalsIgnoreCase(method)
                && status >= 200
                && status != 204
                && status != 205
                && status != 304;
    }

    private static long requestContentLength(HttpExchange exchange) {
        String raw = exchange.getRequestHeaders().getFirst("Content-Length");
        if (raw == null || raw.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static Duration timeoutFromEnvironment(String name, long fallbackSeconds) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return Duration.ofSeconds(fallbackSeconds);
        }
        try {
            long seconds = Long.parseLong(raw);
            return Duration.ofSeconds(seconds > 0 ? seconds : fallbackSeconds);
        } catch (NumberFormatException ignored) {
            return Duration.ofSeconds(fallbackSeconds);
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
