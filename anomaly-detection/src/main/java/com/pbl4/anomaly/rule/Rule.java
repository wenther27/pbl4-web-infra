package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.List;

// Mot luat phat hien bat thuong. Giong LoadBalancingStrategy cua LB (Strategy pattern):
// RuleEngine chi biet "Rule", khong can biet tung luat cu the -> them luat = them 1 lop.
public interface Rule {

    // Ma luat, vd "HIGH_RATE_IP"
    String id();

    // Nhin vao Snapshot, tra ve danh sach canh bao (rong neu binh thuong)
    List<Alert> evaluate(Snapshot snapshot);
}