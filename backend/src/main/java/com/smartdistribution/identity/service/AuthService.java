package com.smartdistribution.identity.service;

import com.smartdistribution.identity.dto.request.LoginRequest;
import com.smartdistribution.identity.dto.response.CurrentUserResponse;
import com.smartdistribution.identity.dto.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    CurrentUserResponse getCurrentUser();
}
