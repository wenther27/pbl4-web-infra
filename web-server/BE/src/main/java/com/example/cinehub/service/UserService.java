package com.example.cinehub.service;

import com.example.cinehub.dto.request.CreateUserRequest;
import com.example.cinehub.dto.request.RegisterRequest;
import com.example.cinehub.dto.request.UpdateProfileRequest;
import com.example.cinehub.dto.response.UserDTO;

import java.util.List;

public interface UserService {
    List<UserDTO> getAllUsers();
    UserDTO getUserById(Long id);
    UserDTO getUserByUsername(String username);

    // Dang ky tu FE: role luon la USER, password duoc ma hoa trong service
    UserDTO register(RegisterRequest request);

    // ADMIN tao user, duoc chon role
    UserDTO createUser(CreateUserRequest request);

    UserDTO updateProfile(String username, UpdateProfileRequest request);
    UserDTO updateUser(Long id, UpdateProfileRequest request);
    void deleteUser(Long id);
}
