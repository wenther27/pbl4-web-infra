package com.example.cinehub.security;

import com.example.cinehub.constant.User.RoleUser;
import com.example.cinehub.entity.User;
import com.example.cinehub.exception.ResourceNotFoundException;
import com.example.cinehub.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Lay thong tin user dang dang nhap tu Authentication.
 * Truoc day moi controller (Favorite, Review, WatchHistory) tu viet lai
 * mot ham getUserId() giong nhau - gom ve mot cho de sua mot lan.
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User entity(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user " + username));
    }

    public Long id(Authentication authentication) {
        return entity(authentication).getId();
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + RoleUser.ADMIN.name()));
    }
}
