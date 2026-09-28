package com.example.cinehub.dto.request;

import com.example.cinehub.constant.User.RoleUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Chi ADMIN dung. Khac RegisterRequest o cho duoc chon role.
@Getter
@Setter
public class CreateUserRequest {
    @NotBlank(message = "Username không được để trống")
    @Size(max = 50, message = "Username tối đa 50 kí tự")
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Password không được để trống")
    @Size(min = 8, message = "Password phải có ít nhất 8 kí tự")
    private String password;

    @NotNull(message = "Role không được để trống")
    private RoleUser role;

    private String avatarUrl;
}
