package com.smartdistribution.identity.entity;

import com.smartdistribution.shared.persistence.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;

@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppUserStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "authorization_updated_at")
    private Instant authorizationUpdatedAt;

    public AppUser(String email, String name, String passwordHash) {
        this.email = normalizeEmail(email);
        this.name = name.strip();
        this.passwordHash = passwordHash;
        this.status = AppUserStatus.ACTIVE;
    }

    public void changeEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public void changeName(String name) {
        this.name = name.strip();
    }

    public void activate() {
        this.status = AppUserStatus.ACTIVE;
    }

    public void disable() {
        this.status = AppUserStatus.DISABLED;
    }

    public boolean isActive() {
        return status == AppUserStatus.ACTIVE;
    }

    public void markAuthorizationChanged(Instant changedAt) {
        this.authorizationUpdatedAt = changedAt;
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
