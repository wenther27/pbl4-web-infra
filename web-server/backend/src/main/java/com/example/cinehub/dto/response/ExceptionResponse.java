package com.example.cinehub.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ExceptionResponse {
    private String error;
    private Object details; // Object để có thể dễ dàng đặt List <-> Map
    private LocalDateTime timestamp = LocalDateTime.now();
}
