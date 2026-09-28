package com.example.cinehub.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    // STRICT: chi map khi ten thuoc tinh trung khop hoan toan.
    // Tranh truong hop ModelMapper tu doan (vi du movie.id -> movieId) roi
    // keo theo LAZY proxy hoac map sai field.
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        return modelMapper;
    }

    // De o day (khong de trong SecurityConfig) vi UserServiceImpl can PasswordEncoder,
    // ma SecurityConfig lai can UserDetailsService -> de sinh circular dependency.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
