package com.smartdistribution.shared.security.userdetails;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String name;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<String> roleCodes;
    private final Set<GrantedAuthority> authorities;

    public CustomUserDetails(
            Long userId,
            String email,
            String name,
            String passwordHash,
            boolean enabled,
            Collection<String> roleCodes,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.roleCodes = Set.copyOf(roleCodes);
        this.authorities = Set.copyOf(authorities);
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public Set<String> getRoleCodes() {
        return roleCodes;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
