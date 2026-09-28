package com.example.cinehub.entity;

import com.example.cinehub.constant.User.RoleUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    private RoleUser role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updateAt;

    // Bang users co DEFAULT CURRENT_TIMESTAMP, nhung Hibernate van INSERT
    // gia tri NULL tuong minh vao 2 cot nay -> phai tu set o tang Java.
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createAt = now;
        updateAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updateAt = LocalDateTime.now();
    }

    // Luu y: KHONG khai bao thêm @ManyToMany tro vao bang "favorites" o day.
    // Bang favorites da duoc entity Favorite quan ly (co them cot added_at);
    // map 2 lan cung mot bang se lam Hibernate ghi trung/xung dot.
    // Muon lay danh sach yeu thich -> dung FavoriteService.
}
