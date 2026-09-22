package com.example.cinehub.controller;

import com.example.cinehub.constant.User.RoleUser;
import com.example.cinehub.dto.response.AuthResponse;
import com.example.cinehub.dto.request.LoginRequest;
import com.example.cinehub.dto.request.RegisterRequest;
import com.example.cinehub.dto.response.UserDTO;
import com.example.cinehub.entity.User;
import com.example.cinehub.exception.ResourceNotFoundException;
import com.example.cinehub.repository.UserRepository;
import com.example.cinehub.security.JwtUtil;
import com.example.cinehub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        String token = jwtUtil.generateToken(request.getUsername());
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(()-> new ResourceNotFoundException("Không tìm thấy username"));
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getRole().name()));
    }
    @PostMapping("/register")
    public ResponseEntity<UserDTO> register (@Valid @RequestBody RegisterRequest registerRequest) {
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword())); //Mã hóa pass
        user.setRole(RoleUser.USER); //Mặc định là User
        return ResponseEntity.ok(userService.createUser(user));
    }
}
