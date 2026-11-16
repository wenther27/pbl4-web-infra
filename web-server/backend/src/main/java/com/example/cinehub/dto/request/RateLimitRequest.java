package com.example.cinehub.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RateLimitRequest(
        @NotNull(message = "Vui long nhap so request/giay")
        @DecimalMin(value = "0", message = "So request/giay khong duoc am (0 = tat gioi han)")
        @DecimalMax(value = "10000", message = "Toi da 10000 request/giay")
        Double ratePerSecond,

        @NotNull(message = "Vui long nhap burst")
        @Min(value = 1, message = "Burst toi thieu 1")
        @Max(value = 100000, message = "Burst toi da 100000")
        Integer burst
) {}
