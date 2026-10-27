package com.example.cinehub.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Autowired private JwtFilter jwtFilter;
    @Autowired private UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Cho phép trình duyệt gửi CORS preflight trước các request có JSON/JWT.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public: đăng nhập, đăng ký, tài liệu API và health check.
                        .requestMatchers(
                                "/error",
                                "/api/auth/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/uploads/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // ADMIN: quản trị toàn bộ dữ liệu hệ thống.
                        .requestMatchers(HttpMethod.GET, "/api/movies/all" ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/uploads/images").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/uploads/videos").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/movies", "/api/genres").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/movies/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/movies/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/movies/*/episodes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/episodes/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/episodes/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/reviews/*").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasRole("ADMIN")

                        // Public: nội dung dùng để duyệt website mà không cần đăng nhập.
                        .requestMatchers(HttpMethod.GET, "/api/movies").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/movies/trending",
                                "/api/movies/type",
                                "/api/movies/genre/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/genres/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/movies/*/episodes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/movies/*/reviews").permitAll()

                        // USER và ADMIN: dữ liệu cá nhân và tương tác của thành viên.
                        .requestMatchers("/api/favorites/**", "/api/history/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/movies/*/reviews").authenticated()

                        // Endpoint mới phải được khai báo quyền rõ ràng trước khi sử dụng.
                        .anyRequest().denyAll()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncode() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
        throws  Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // Cho frontend doc duoc request roi vao instance nao (web-1/2/3)
        configuration.setExposedHeaders(List.of("X-Instance-Id"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
