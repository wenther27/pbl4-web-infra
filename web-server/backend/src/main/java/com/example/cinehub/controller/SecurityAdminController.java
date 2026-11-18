package com.example.cinehub.controller;

import com.example.cinehub.dto.request.BlockIpRequest;
import com.example.cinehub.dto.request.FalsePositiveRequest;
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

@RestController
@RequestMapping("/api/admin/security")
public class SecurityAdminController {

    private final SecurityAdminService service;

    public SecurityAdminController(SecurityAdminService service) {
        this.service = service;
    }

    @GetMapping("/suspicious-ips")
    public List<SuspiciousIpDTO> suspiciousIps(@RequestParam(defaultValue = "60") int minutes) {
        return service.suspiciousIps(Math.max(1, Math.min(minutes, 1440)));
    }

    @GetMapping("/ml-ids")
    public JsonNode mlIdsStatus() {
        return service.mlIdsStatus();
    }

    @PostMapping("/ml-ids/false-positive")
    public JsonNode reportFalsePositive(@Valid @RequestBody FalsePositiveRequest request, Principal admin) {
        return service.reportFalsePositive(request.ip(), admin.getName());
    }

    @GetMapping("/blocked-ips")
    public JsonNode blockedIps() {
        return service.blockedIps();
    }

    @PostMapping("/blocked-ips")
    public ResponseEntity<JsonNode> block(@Valid @RequestBody BlockIpRequest request, Principal admin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.block(request, admin.getName()));
    }

    @DeleteMapping("/blocked-ips/{ip}")
    public JsonNode unblock(@PathVariable String ip, Principal admin) {
        return service.unblock(ip, admin.getName());
    }
}