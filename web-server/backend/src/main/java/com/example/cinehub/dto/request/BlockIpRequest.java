package com.example.cinehub.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body cua POST /api/admin/security/blocked-ips
// Kiem tra so bo o day; LB kiem tra lai dinh dang IP va danh sach IP duoc bao ve.
public record BlockIpRequest(
        @NotBlank(message = "Vui long nhap IP")
        @Size(max = 45, message = "IP qua dai")
        String ip,

        @Min(value = 1, message = "Toi thieu 1 phut")
        @Max(value = 1440, message = "Toi da 1440 phut (1 ngay)")
        int minutes,

        @Size(max = 200, message = "Ly do toi da 200 ky tu")
        String reason
) {}