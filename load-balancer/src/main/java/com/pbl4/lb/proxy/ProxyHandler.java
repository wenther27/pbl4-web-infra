package com.pbl4.lb.proxy;

import com.pbl4.lb.health.BackendMaintenance;
import com.pbl4.lb.health.HealthChecker;
import com.pbl4.lb.security.BlockList;
import com.pbl4.lb.security.RateLimiter;
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

    private static final boolean TRUST_CLIENT_FORWARDED_FOR =
            Boolean.parseBoolean(System.getenv().getOrDefault("LB_TRUST_XFF", "true"));

    private static final Set<String> IDEMPOTENT = Set.of("GET", "HEAD", "OPTIONS", "PUT", "DELETE");

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
    private final BlockList blockList;
    private final BackendMaintenance maintenance;
    private final RateLimiter rateLimiter;
    private final HttpClient client;

    public ProxyHandler(HealthChecker health, LoadBalancingStrategy strategy, BlockList blockList) {
        this(health, strategy, blockList, new BackendMaintenance());
    }

    public ProxyHandler(HealthChecker health, LoadBalancingStrategy strategy, BlockList blockList,
                        BackendMaintenance maintenance) {
        this(health, strategy, blockList, maintenance, RateLimiter.disabled());
    }

    public ProxyHandler(HealthChecker health, LoadBalancingStrategy strategy, BlockList blockList,
                        BackendMaintenance maintenance, RateLimiter rateLimiter) {
        this.health = health;
        this.strategy = strategy;
        this.blockList = blockList;
        this.maintenance = maintenance;
        this.rateLimiter = rateLimiter;
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
        String query = truncate(exchange.getRequestURI().getRawQuery(), 1000);
        String userAgent = truncate(exchange.getRequestHeaders().getFirst("User-Agent"), 300);

        int status = 500;
        String backend = "-";
        int attempt = 0;
        boolean blocked = false;
        boolean rateLimited = false;
        boolean streamingRequest = isStreamingRequest(method, path);

        String bodySample = null;
        String pendingBackend = null;
        long pendingStart = 0;
        boolean pendingOk = false;

        try {
            if (blockList.isBlocked(clientIp)) {
                blocked = true;
                status = 403;
                sendText(exchange, status, "IP cua ban dang bi tam chan boi quan tri vien");
                return;
            }

            RateLimiter.Decision quota = rateLimiter.tryAcquire(clientIp);
            if (quota.remaining() >= 0) {
                exchange.getResponseHeaders().set("X-RateLimit-Limit", String.valueOf(rateLimiter.limits().burst()));
                exchange.getResponseHeaders().set("X-RateLimit-Remaining", String.valueOf(quota.remaining()));
            }
            if (!quota.allowed()) {
                rateLimited = true;
                status = 429;
                exchange.getResponseHeaders().set("Retry-After", String.valueOf(quota.retryAfterSeconds()));
                sendText(exchange, status, "Qua nhieu request, thu lai sau " + quota.retryAfterSeconds() + " giay");
                return;
            }

            List<String> healthy = maintenance.available(health.healthyBackends());
            if (healthy.isEmpty()) {
                status = 503;
                sendText(exchange, status, "Khong con backend nao kha dung");
                return;
            }

            byte[] bufferedBody = streamingRequest ? null : exchange.getRequestBody().readAllBytes();
            bodySample = BodySampler.sample(method, exchange.getRequestHeaders().getFirst("Content-Type"), bufferedBody);
            Set<String> tried = new HashSet<>();
            HttpResponse<InputStream> response = null;
            String lastError = "";
            int maximumAttempts = streamingRequest ? 1 : healthy.size();

            while (attempt < maximumAttempts) {
                attempt++;
                backend = pickBackend(healthy, tried);
                tried.add(backend);
                long attemptStart = System.nanoTime();
                boolean attemptOk = false;
                maintenance.begin(backend);
                try {
                    response = forward(exchange, backend, method, bufferedBody, streamingRequest);
                    attemptOk = response.statusCode() < 500;
                    pendingBackend = backend;
                    pendingStart = attemptStart;
                    pendingOk = attemptOk;
                    break;
                } catch (IOException e) {
                    lastError = e.getClass().getSimpleName();
                    boolean neverReached = e instanceof ConnectException
                            || e instanceof HttpConnectTimeoutException;

                    log.warn("RETRY " + method + " " + path + " -> " + backend
                                    + " loi " + lastError + " (lan " + attempt + ")",
                            kv("event", "retry"),
                            kv("httpMethod", method),
                            kv("path", path),
                            kv("backend", backend),
                            kv("errorType", lastError),
                            kv("attempt", attempt),
                            kv("clientIp", clientIp));

                    if (!neverReached && !IDEMPOTENT.contains(method)) {
                        break;
                    }
                } finally {
                    if (pendingBackend == null) {
                        strategy.release(backend, (System.nanoTime() - attemptStart) / 1_000_000, attemptOk);
                        maintenance.end(backend);
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
            if (pendingBackend != null) {
                strategy.release(pendingBackend, (System.nanoTime() - pendingStart) / 1_000_000, pendingOk);
                maintenance.end(pendingBackend);
            }
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
                    kv("clientIp", clientIp),
                    kv("blocked", blocked),
                    kv("rateLimited", rateLimited),
                    kv("strategy", strategy.name()),
                    kv("query", query),
                    kv("body", bodySample),
                    kv("userAgent", userAgent));
            exchange.close();
        }
    }

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

    private String pickBackend(List<String> healthy, Set<String> tried) {
        List<String> candidates = healthy.stream().filter(b -> !tried.contains(b)).toList();
        return strategy.next(candidates.isEmpty() ? healthy : candidates);
    }

    private HttpResponse<InputStream> forward(HttpExchange exchange,
                                              String backend,
                                              String method,
                                              byte[] bufferedBody,
                                              boolean streamingRequest)
            throws IOException, InterruptedException {
        URI target = URI.create(backend + exchange.getRequestURI());
        HttpRequest.BodyPublisher publisher;
        if (streamingRequest) {
            long contentLength = requestContentLength(exchange);
            HttpRequest.BodyPublisher streamingPublisher =
                    HttpRequest.BodyPublishers.ofInputStream(exchange::getRequestBody);
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

        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!SKIP_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                for (String v : values) {
                    builder.header(name, v);
                }
            }
        });

        String remoteIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        String prior = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        String xff = (TRUST_CLIENT_FORWARDED_FOR && prior != null && !prior.isBlank())
                ? prior + ", " + remoteIp
                : remoteIp;
        builder.header("X-Forwarded-For", xff);

        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host != null) {
            builder.header("X-Forwarded-Host", host);
            int colon = host.lastIndexOf(':');
            String port = (colon > host.lastIndexOf(']')) ? host.substring(colon + 1) : "80";
            builder.header("X-Forwarded-Port", port);
        }
        builder.header("X-Forwarded-Proto", "http");

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

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
                    .orElse(0L);
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

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
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
