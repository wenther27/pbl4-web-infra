package com.example.cinehub.controller;

import com.example.cinehub.dto.request.BlockIpRequest;
import com.example.cinehub.dto.response.SuspiciousIpDTO;
import com.example.cinehub.service.SecurityAdminService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

// API cho trang Admin > Bao mat. Chi ROLE_ADMIN goi duoc (khai bao trong SecurityConfig).
@RestController
@RequestMapping("/api/admin/security")
public class SecurityAdminController {

    private final SecurityAdminService service;

    public SecurityAdminController(SecurityAdminService service) {
        this.service = service;
    }

    // IP bi canh bao trong N phut qua (mac dinh 60, toi da 1 ngay)
    @GetMapping("/suspicious-ips")
    public List<SuspiciousIpDTO> suspiciousIps(@RequestParam(defaultValue = "60") int minutes) {
        return service.suspiciousIps(Math.max(1, Math.min(minutes, 1440)));
    }

    @GetMapping("/blocked-ips")
    public JsonNode blockedIps() {
        return service.blockedIps();
    }

    // Principal = tai khoan admin dang dang nhap (JwtFilter dat vao SecurityContext)
    @PostMapping("/blocked-ips")
    public ResponseEntity<JsonNode> block(@Valid @RequestBody BlockIpRequest request, Principal admin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.block(request, admin.getName()));
    }

    @DeleteMapping("/blocked-ips/{ip}")
    public JsonNode unblock(@PathVariable String ip, Principal admin) {
        return service.unblock(ip, admin.getName());
    }
}