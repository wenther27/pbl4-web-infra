package com.example.cinehub.dto.response;

import com.example.cinehub.constant.User.RoleUser;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
    private RoleUser role;
    private LocalDateTime createAt;

}
