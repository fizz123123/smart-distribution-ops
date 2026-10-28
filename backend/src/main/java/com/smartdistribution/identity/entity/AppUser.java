package com.smartdistribution.identity.entity;

import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
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

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppUserStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "authorization_updated_at")
    private Instant authorizationUpdatedAt;

    //最後一次登入或被啟用的時間；閒置停用排程用它判斷
    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    //已經有密碼、直接啟用的帳號；初始化的系統管理員與測試使用
    public AppUser(String email, String name, String passwordHash) {
        this.email = normalizeEmail(email);
        this.name = name.strip();
        this.passwordHash = passwordHash;
        this.status = AppUserStatus.ACTIVE;
    }

    private AppUser(String email, String name) {
        this.email = normalizeEmail(email);
        this.name = name.strip();
        this.status = AppUserStatus.PENDING;
    }

    //管理員建立的帳號：沒有密碼，等使用者用信中的連結設定
    public static AppUser pending(String email, String name) {
        return new AppUser(email, name);
    }

    public void changeEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public void changeName(String name) {
        this.name = name.strip();
    }

    //使用者第一次設定密碼：待啟用 → 啟用
    public void completeSetup(String passwordHash, Instant at) {
        if (status != AppUserStatus.PENDING) {
            throw new BusinessException(ErrorCode.USER_STATUS_CONFLICT);
        }

        this.passwordHash = passwordHash;
        this.status = AppUserStatus.ACTIVE;
        this.lastActivityAt = at;
    }

    public void changePassword(String passwordHash) {
        if (status != AppUserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.USER_STATUS_CONFLICT);
        }

        this.passwordHash = passwordHash;
    }

    //重新啟用：設定過密碼的回到啟用並重新起算閒置天數；從沒設定過的回到待啟用
    public void activate(Instant at) {
        if (passwordHash == null) {
            this.status = AppUserStatus.PENDING;
            return;
        }

        this.status = AppUserStatus.ACTIVE;
        this.lastActivityAt = at;
    }

    public void disable() {
        this.status = AppUserStatus.DISABLED;
    }

    public boolean isActive() {
        return status == AppUserStatus.ACTIVE;
    }

    public boolean isPending() {
        return status == AppUserStatus.PENDING;
    }

    public void markAuthorizationChanged(Instant changedAt) {
        this.authorizationUpdatedAt = changedAt;
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
