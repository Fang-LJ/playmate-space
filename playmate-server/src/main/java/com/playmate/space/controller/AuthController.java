package com.playmate.space.controller;

import com.playmate.space.common.ApiResponse;
import com.playmate.space.dto.AccountLoginRequest;
import com.playmate.space.dto.AccountRegisterRequest;
import com.playmate.space.dto.LoginRequest;
import com.playmate.space.service.AuthService;
import com.playmate.space.vo.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/wx-login")
    public ApiResponse<LoginResponse> wxLogin(@Valid @RequestBody(required = false) LoginRequest request) {
        return ApiResponse.success(authService.wxLogin(request));
    }

    @PostMapping("/account-register")
    public ApiResponse<LoginResponse> accountRegister(@Valid @RequestBody AccountRegisterRequest request) {
        return ApiResponse.success(authService.accountRegister(request));
    }

    @PostMapping("/account-login")
    public ApiResponse<LoginResponse> accountLogin(@Valid @RequestBody AccountLoginRequest request) {
        return ApiResponse.success(authService.accountLogin(request));
    }
}
