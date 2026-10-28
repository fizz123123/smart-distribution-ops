package com.smartdistribution.identity.service.impl;

import com.smartdistribution.identity.dto.response.CurrentUserResponse;
import com.smartdistribution.identity.dto.request.ChangeOwnPasswordRequest;
import com.smartdistribution.identity.dto.request.LoginRequest;
import com.smartdistribution.identity.dto.request.SetPasswordRequest;
import com.smartdistribution.identity.dto.response.LoginResponse;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.PasswordToken;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.PasswordTokenRepository;
import com.smartdistribution.identity.service.AuthService;
import com.smartdistribution.identity.service.PasswordTokenIssuer;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final AppUserRepository appUserRepository;
    private final PasswordTokenRepository passwordTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        CustomUserDetails userDetails = authenticate(request);
        appUserRepository.updateLastActivityAt(userDetails.getUserId(), Instant.now());

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

    @Override
    @Transactional
    public void setPassword(SetPasswordRequest request) {
        Instant now = Instant.now();

        //不存在、用過、過期、帳號狀態不符，一律回同一個錯誤，不透露是哪一種
        PasswordToken token = passwordTokenRepository.findByTokenHash(PasswordTokenIssuer.hash(request.token()))
                .filter(found -> found.isUsableAt(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_TOKEN_INVALID));

        AppUser user = token.getUser();
        String passwordHash = passwordEncoder.encode(request.newPassword());

        switch (token.getPurpose()) {
            case INVITE -> {
                if (!user.isPending()) {
                    throw new BusinessException(ErrorCode.PASSWORD_TOKEN_INVALID);
                }
                user.completeSetup(passwordHash, now);
            }
            case RESET -> {
                if (!user.isActive()) {
                    throw new BusinessException(ErrorCode.PASSWORD_TOKEN_INVALID);
                }
                user.changePassword(passwordHash);
            }
        }

        token.markUsed(now);
    }

    @Override
    @Transactional
    public void changeOwnPassword(ChangeOwnPasswordRequest request) {
        AppUser user = appUserRepository.findById(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
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
            throw new BusinessException(isPending(request.email())
                    ? ErrorCode.USER_PENDING_ACTIVATION
                    : ErrorCode.USER_DISABLED);
        } catch (AuthenticationException exception) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
    }

    //待啟用與停用的帳號都不能登入，但提示不同：待啟用要提醒使用者去信箱設定密碼
    private boolean isPending(String email) {
        return appUserRepository.findByEmail(AppUser.normalizeEmail(email))
                .map(AppUser::isPending)
                .orElse(false);
    }
}
