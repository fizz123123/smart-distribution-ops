package com.smartdistribution.shared.security.userdetails;

import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import com.smartdistribution.identity.service.EffectivePermissionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final EffectivePermissionResolver effectivePermissionResolver;

    @Override
    public CustomUserDetails loadUserByUsername(String email) {
        String normalizedEmail = AppUser.normalizeEmail(email);

        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return createUserDetails(user);
    }

    public CustomUserDetails loadUserById(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return createUserDetails(user);
    }

    private CustomUserDetails createUserDetails(AppUser user) {
        Set<String> roleCodes = userRoleRepository.findAllByUserId(user.getId())
                .stream()
                .map(userRole -> userRole.getRole().getRoleCode())
                .collect(Collectors.toUnmodifiableSet());

        Set<String> permissionCodes = effectivePermissionResolver.resolve(
                user,
                Instant.now()
        );

        Set<SimpleGrantedAuthority> authorities =
                permissionCodes.stream()
                        .map(permissionCode -> new SimpleGrantedAuthority(permissionCode))
                        .collect(Collectors.toUnmodifiableSet());

        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPasswordHash(),
                user.isActive(),
                roleCodes,
                authorities
        );
    }

}
