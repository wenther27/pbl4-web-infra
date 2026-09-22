package com.example.cinehub.repository;

import com.example.cinehub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Tìm user theo username (Trả về Optional để tránh lỗi NullPointerException)
    Optional<User> findByUsername(String username);

    // Check xem email hoặc username đã tồn tại khi đăng ký chưa
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
