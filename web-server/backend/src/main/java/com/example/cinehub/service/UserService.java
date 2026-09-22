package com.example.cinehub.service;

import com.example.cinehub.dto.response.UserDTO;
import com.example.cinehub.entity.User;

import java.util.List;

public interface UserService {
    List<UserDTO> getAllUsers();
    UserDTO getUserById(Long id);
    UserDTO createUser(User user);
    UserDTO updateUser(Long id, User userDetails);
    void deleteUser(Long id);
}
