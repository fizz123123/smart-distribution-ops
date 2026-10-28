package com.smartdistribution.identity.mail;

import com.smartdistribution.identity.entity.PasswordTokenPurpose;

import java.time.Instant;

/**
 * 已產生設定密碼的連結，交易成功後要寄信給使用者
 * rawToken 是 token 原文，只用在這封信裡，不會寫進資料庫或 log
 */
public record PasswordLinkIssuedEvent(
        String email,
        String name,
        PasswordTokenPurpose purpose,
        String rawToken,
        Instant expiresAt
) {
}
