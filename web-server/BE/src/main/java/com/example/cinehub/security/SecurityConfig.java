package com.example.cinehub.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired private JwtFilter jwtFilter;
    @Autowired private RestAuthenticationEntryPoint authenticationEntryPoint;
    @Autowired private RestAccessDeniedHandler accessDeniedHandler;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // CORS phai cau hinh o day. Chi dat @CrossOrigin tren controller
                // la khong du: request preflight OPTIONS bi chan tu tang Security
                // truoc khi den controller.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // ---- Ha tang: load balancer goi /actuator/health de health check ----
                        .requestMatchers("/actuator/**", "/error").permitAll()

                        // ---- Dang nhap / dang ky ----
                        .requestMatchers("/api/auth/**").permitAll()

                        // ---- Chi ADMIN (dat TRUOC cac rule permitAll de khong bi /api/movies/* an mat) ----
                        .requestMatchers(HttpMethod.GET, "/api/movies/all").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/movies").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/movies/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/movies/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/genres").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/episodes/**", "/api/movies/*/episodes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/episodes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/episodes/**").hasRole("ADMIN")

                        // ---- Public (FE goi khi chua dang nhap) ----
                        .requestMatchers(HttpMethod.GET,
                                "/api/movies",
                                "/api/movies/trending",
                                "/api/movies/top-rated",
                                "/api/movies/type",
                                "/api/movies/genre/**",
                                "/api/movies/*",
                                "/api/movies/*/episodes",
                                "/api/movies/*/reviews",
                                "/api/genres/**").permitAll()

                        // ---- Ho so cua chinh minh: chi can dang nhap ----
                        .requestMatchers("/api/users/me").authenticated()
                        // ---- Quan ly user: chi ADMIN ----
                        .requestMatchers("/api/users/**").hasRole("ADMIN")

                        // ---- Con lai: phai dang nhap ----
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Instance-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
