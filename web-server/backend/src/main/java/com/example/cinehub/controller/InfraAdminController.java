package com.example.cinehub.controller;

import com.example.cinehub.dto.request.RateLimitRequest;
import com.example.cinehub.service.SecurityAdminService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/infra")
public class InfraAdminController {

    private final SecurityAdminService service;

    public InfraAdminController(SecurityAdminService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public JsonNode overview() {
        return service.infraOverview();
    }

    @PostMapping("/backends/{name}/drain")
    public JsonNode drain(@PathVariable String name, Principal admin) {
        return service.drainBackend(name, true, admin.getName());
    }

    @PostMapping("/backends/{name}/undrain")
    public JsonNode undrain(@PathVariable String name, Principal admin) {
        return service.drainBackend(name, false, admin.getName());
    }

    @PutMapping("/rate-limit")
    public JsonNode updateRateLimit(@Valid @RequestBody RateLimitRequest request, Principal admin) {
        return service.updateRateLimit(request, admin.getName());
    }
}
