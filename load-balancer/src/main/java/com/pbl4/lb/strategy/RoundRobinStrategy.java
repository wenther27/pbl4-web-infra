package com.pbl4.lb.strategy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinStrategy implements LoadBalancingStrategy{
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public String next(List<String> backends) {
        if (backends.isEmpty())
            throw new IllegalStateException("Khong con Backend nao kha dung");
        int index = Math.floorMod(counter.getAndIncrement(), backends.size());
        return backends.get(index);
    }

    @Override
    public String name() {
        return "round-robin";
    }
}