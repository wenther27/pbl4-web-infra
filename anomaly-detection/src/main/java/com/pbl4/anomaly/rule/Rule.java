package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;

import java.util.List;

public interface Rule {

    String id();

    List<Alert> evaluate(Snapshot snapshot);
}