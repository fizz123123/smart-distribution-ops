package com.smartdistribution.identity.controller;

import com.smartdistribution.identity.dto.request.LoginRequest;
import com.smartdistribution.identity.dto.request.SetPasswordRequest;
import com.smartdistribution.identity.dto.response.LoginResponse;
import com.smartdistribution.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    //用信中的連結設定密碼：首次設定與重設密碼共用；使用者還沒登入，由 token 證明身分
    @PostMapping("/password")
    public ResponseEntity<Void> setPassword(
            @Valid @RequestBody SetPasswordRequest request
    ) {
        authService.setPassword(request);

        return ResponseEntity.noContent().build();
    }
}
