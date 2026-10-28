package com.smartdistribution.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 設定密碼連結裡的一次性 token
 */
@Getter
@Entity
@Table(name = "password_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PasswordTokenPurpose purpose;

    //SHA-256 十六進位字串
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    //用過或被新的 token 取代時寫入
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PasswordToken(
            AppUser user,
            PasswordTokenPurpose purpose,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.user = user;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsableAt(Instant at) {
        return usedAt == null && expiresAt.isAfter(at);
    }

    public void markUsed(Instant at) {
        this.usedAt = at;
    }
}
