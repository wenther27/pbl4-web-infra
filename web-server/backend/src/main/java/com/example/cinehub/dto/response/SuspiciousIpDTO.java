package com.example.cinehub.dto.response;

import java.util.List;

public record SuspiciousIpDTO(
        String ip,
        int alertCount,
        List<String> rules,
        String severity,
        String lastSeen,
        String lastMessage,
        boolean blocked,
        String blockedUntil
) {}