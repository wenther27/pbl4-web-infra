package com.pbl4.lb.strategy;

import java.util.List;
import java.util.Map;

public interface LoadBalancingStrategy {
    String next(List<String> backends);

    String name();

    default void release(String backend, long latencyMs, boolean success) {}

    default Map<String, Map<String, Object>> snapshot(List<String> backends) {
        return Map.of();
    }
}
