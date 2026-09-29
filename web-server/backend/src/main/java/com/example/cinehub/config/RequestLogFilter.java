package com.example.cinehub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Hai viec, lam trong cung mot vong filter:
 *
 * 1. Gan header X-Instance-Id vao response -> biet request roi vao
 *    web-1, web-2 hay web-3 khi test load balancer.
 * 2. Ghi mot dong log co cau truc cho moi request (method, path, status,
 *    thoi gian xu ly, IP) -> Filebeat/Logstash day sang Elasticsearch,
 *    Kibana ve dashboard va module anomaly-detection doc de phat hien bat thuong.
 *
 * Cac field duoc dat vao MDC nen o che do JSON chung se thanh cot rieng
 * trong Elasticsearch, khong phai parse lai tu chuoi message.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    private final String instanceId;

    public RequestLogFilter(@Value("${app.instance-id:local}") String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        response.setHeader("X-Instance-Id", instanceId);

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            try {
                MDC.put("instanceId", instanceId);
                MDC.put("httpMethod", request.getMethod());
                MDC.put("path", request.getRequestURI());
                MDC.put("status", String.valueOf(response.getStatus()));
                MDC.put("durationMs", String.valueOf(durationMs));
                MDC.put("clientIp", resolveClientIp(request));

                log.info("{} {} -> {} ({}ms)",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            } finally {
                MDC.clear();
            }
        }
    }

    // Dung sau load balancer thi request.getRemoteAddr() la IP cua LB,
    // IP that cua client nam trong X-Forwarded-For.
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // Health check cua load balancer goi lien tuc, khong log de do nhieu
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }
}
