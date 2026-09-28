package com.example.cinehub.controller;

import com.example.cinehub.dto.request.LoginRequest;
import com.example.cinehub.dto.request.RegisterRequest;
import com.example.cinehub.dto.response.AuthResponse;
import com.example.cinehub.dto.response.UserDTO;
import com.example.cinehub.security.JwtUtil;
import com.example.cinehub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // Sai mat khau -> BadCredentialsException -> GlobalExceptionHandler tra 401
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserDTO user = userService.getUserByUsername(request.getUsername());
        String token = jwtUtil.generateToken(user.getUsername());

        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getRole().name()));
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        // Ma hoa password va gan role USER duoc lam trong UserService
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }
}
