package com.example.cinehub.service.impl;

import com.example.cinehub.constant.User.RoleUser;
import com.example.cinehub.dto.request.CreateUserRequest;
import com.example.cinehub.dto.request.RegisterRequest;
import com.example.cinehub.dto.request.UpdateProfileRequest;
import com.example.cinehub.dto.response.UserDTO;
import com.example.cinehub.entity.User;
import com.example.cinehub.exception.BadRequestException;
import com.example.cinehub.exception.ResourceNotFoundException;
import com.example.cinehub.repository.UserRepository;
import com.example.cinehub.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ModelMapper modelMapper;

    @Transactional(readOnly = true)
    @Override
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public UserDTO getUserById(Long id) {
        return convertToDTO(findById(id));
    }

    @Transactional(readOnly = true)
    @Override
    public UserDTO getUserByUsername(String username) {
        return convertToDTO(findByUsername(username));
    }

    @Transactional
    @Override
    public UserDTO register(RegisterRequest request) {
        checkDuplicate(request.getUsername(), request.getEmail());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        // Ma hoa ngay trong service, khong de controller tu lam -> khong the quen ma hoa
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(RoleUser.USER); // FE khong duoc tu chon role

        return convertToDTO(userRepository.save(user));
    }

    @Transactional
    @Override
    public UserDTO createUser(CreateUserRequest request) {
        checkDuplicate(request.getUsername(), request.getEmail());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setAvatarUrl(request.getAvatarUrl());
        user.setRole(request.getRole());

        return convertToDTO(userRepository.save(user));
    }

    @Transactional
    @Override
    public UserDTO updateProfile(String username, UpdateProfileRequest request) {
        User user = findByUsername(username);
        user.setAvatarUrl(request.getAvatarUrl());
        return convertToDTO(userRepository.save(user));
    }

    @Transactional
    @Override
    public UserDTO updateUser(Long id, UpdateProfileRequest request) {
        User user = findById(id);
        user.setAvatarUrl(request.getAvatarUrl());
        return convertToDTO(userRepository.save(user));
    }

    @Transactional
    @Override
    public void deleteUser(Long id) {
        userRepository.delete(findById(id));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user"));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }

    private void checkDuplicate(String username, String email) {
        if (userRepository.existsByUsername(username))
            throw new BadRequestException("Username đã tồn tại");
        if (userRepository.existsByEmail(email))
            throw new BadRequestException("Email đã tồn tại");
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với ID = " + id));
    }

    private User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user " + username));
    }

    private UserDTO convertToDTO(User user) {
        return modelMapper.map(user, UserDTO.class);
    }
}
