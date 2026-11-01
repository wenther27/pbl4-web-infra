package com.example.cinehub.dto.response;

import java.util.List;

// Mot IP bi bo phat hien bat thuong canh bao, da gop tat ca canh bao cua IP do
public record SuspiciousIpDTO(
        String ip,
        int alertCount,          // so lan bi canh bao trong khoang thoi gian
        List<String> rules,      // cac luat da vi pham, vd [BRUTE_FORCE_LOGIN, HIGH_RATE_IP]
        String severity,         // HIGH neu co it nhat 1 canh bao HIGH
        String lastSeen,         // thoi diem canh bao gan nhat (ISO, UTC)
        String lastMessage,      // noi dung canh bao gan nhat
        boolean blocked,         // dang bi chan tai LB?
        String blockedUntil      // het han chan luc nao (null neu khong bi chan)
) {}