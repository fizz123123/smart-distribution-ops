package com.smartdistribution.identity.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 帳號相關設定
 *
 * @param frontendBaseUrl       信件連結指向的前端網址
 * @param mailFrom              寄件者信箱
 * @param passwordTokenValidity 設定密碼連結的有效時間，首次設定與忘記密碼共用
 * @param inactivityDays        啟用中的帳號超過幾天沒有活動就自動停用
 */
@Validated
@ConfigurationProperties(prefix = "app.account")
public record AccountProperties(
        @NotBlank String frontendBaseUrl,
        @NotBlank String mailFrom,
        @NotNull Duration passwordTokenValidity,
        @Positive int inactivityDays
) {
}
