package com.smartdistribution.identity.service.impl;

import com.smartdistribution.identity.dto.response.CurrentUserResponse;
import com.smartdistribution.identity.dto.request.LoginRequest;
import com.smartdistribution.identity.dto.response.LoginResponse;
import com.smartdistribution.identity.service.AuthService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.config.SecurityProperties;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.security.jwt.JwtProvider;
import com.smartdistribution.shared.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final SecurityProperties securityProperties;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public LoginResponse login(LoginRequest request) {
        CustomUserDetails userDetails = authenticate(request);

        String accessToken = jwtProvider.generateAccessToken(userDetails.getUserId());

        return new LoginResponse(
                accessToken,
                BEARER_TOKEN_TYPE,
                securityProperties.jwt().accessTokenExpiration().toSeconds(),
                toCurrentUserResponse(userDetails)
        );
    }

    @Override
    public CurrentUserResponse getCurrentUser() {
        CustomUserDetails userDetails = currentUserProvider.getCurrentUserDetails();

        return toCurrentUserResponse(userDetails);
    }

    private CurrentUserResponse toCurrentUserResponse(CustomUserDetails userDetails) {
        List<String> roles = userDetails.getRoleCodes()
                .stream()
                .sorted()
                .toList();

        List<String> permissions = userDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();

        return new CurrentUserResponse(
                userDetails.getUserId(),
                userDetails.getUsername(),
                userDetails.getName(),
                roles,
                permissions
        );
    }

    private CustomUserDetails authenticate(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );

            return (CustomUserDetails) authentication.getPrincipal();
        } catch (DisabledException exception) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        } catch (AuthenticationException exception) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
    }
}
