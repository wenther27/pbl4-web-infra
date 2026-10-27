package com.pbl4.anomaly.rule;

import com.pbl4.anomaly.alert.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// Chay lan luot moi luat tren cung mot Snapshot, gom tat ca canh bao lai.
public class RuleEngine {
    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<Alert> evaluate(Snapshot snapshot) {
        List<Alert> all = new ArrayList<>();
        for (Rule rule : rules) {
            try {
                all.addAll(rule.evaluate(snapshot));
            } catch (Exception e) {
                // 1 luat loi khong duoc lam hong cac luat con lai
                log.error("Luat " + rule.id() + " bi loi: " + e);
            }
        }
        return all;
    }

    public List<String> ruleIds() {
        return rules.stream().map(Rule::id).toList();
    }
}