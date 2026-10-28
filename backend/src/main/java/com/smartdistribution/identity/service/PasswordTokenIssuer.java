package com.smartdistribution.identity.service;

import com.smartdistribution.identity.config.AccountProperties;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.PasswordToken;
import com.smartdistribution.identity.entity.PasswordTokenPurpose;
import com.smartdistribution.identity.mail.PasswordLinkIssuedEvent;
import com.smartdistribution.identity.repository.PasswordTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 產生設定密碼的一次性 token，建立帳號、重寄邀請、重設密碼共用
 * 必須在交易內呼叫，信件會在交易成功後才寄出
 */
@Component
@RequiredArgsConstructor
public class PasswordTokenIssuer {

    private final PasswordTokenRepository passwordTokenRepository;
    private final AccountProperties accountProperties;
    private final ApplicationEventPublisher eventPublisher;

    //同用途還沒用過的舊 token 一律作廢，永遠只有最新寄出的連結有效
    public void issue(AppUser user, PasswordTokenPurpose purpose, Instant now) {

        passwordTokenRepository.findAllByUserIdAndPurposeAndUsedAtIsNull(user.getId(), purpose)
                .forEach(token -> token.markUsed(now));

        //rawToken 放連結，資料庫只存雜湊值
        String rawToken = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(accountProperties.passwordTokenValidity());

        passwordTokenRepository.save(
                new PasswordToken(
                        user,
                        purpose,
                        hash(rawToken),
                        now,
                        expiresAt
                )
        );

        //建立並發布事件
        eventPublisher.publishEvent(
                new PasswordLinkIssuedEvent(
                        user.getEmail(),
                        user.getName(),
                        purpose,
                        rawToken,
                        expiresAt
                )
        );
    }

    //Hash the token with SHA-256
    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
