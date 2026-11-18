package com.example.cinehub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FalsePositiveRequest(
        @NotBlank(message = "Vui long nhap IP")
        @Pattern(regexp = "[0-9a-fA-F:.]{2,45}", message = "IP khong hop le")
        String ip
) {}
