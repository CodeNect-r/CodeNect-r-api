package com.lovable.auth_service.service;

import com.lovable.auth_service.dto.*;
import com.lovable.auth_service.exception.BadRequestException;
import com.lovable.auth_service.exception.ResourceNotFoundException;
import com.lovable.auth_service.model.Role;
import com.lovable.auth_service.model.User;
import com.lovable.auth_service.repository.UserRepository;
import com.lovable.auth_service.security.JwtService;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse signup(SignupRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("Email already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        var refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        String accessToken = jwtService.generateAccessToken(user);
        var refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken());

    }
    public AuthResponse refreshToken(String refreshTokenValue) {

        var refreshToken = refreshTokenService.validateRefreshToken(refreshTokenValue);

        String accessToken = jwtService.generateAccessToken(refreshToken.getUser());

        return new AuthResponse(accessToken, refreshToken.getToken());
    }



}
