package com.smartdistribution.shared.security.jwt;

import com.smartdistribution.shared.security.config.SecurityProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecurityProperties properties;
    private final SecretKey signingKey;
    private final JwtParser jwtParser;

    public JwtProvider(SecurityProperties properties) {
        this.properties = properties;
        this.signingKey = createSigningKey(properties.jwt().secret());
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.jwt().issuer())
                .build();
    }

    public String generateAccessToken(Long userId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.jwt().accessTokenExpiration());

        return Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(userId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Long getUserId(String token) {
        String subject = jwtParser.parseSignedClaims(token)
                .getPayload()
                .getSubject();

        return Long.valueOf(subject);
    }

    private SecretKey createSigningKey(String secret) {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            SecretKey secretKey = Keys.hmacShaKeyFor(keyBytes);
            return secretKey;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT secret must be a Base64-encoded key of at least 256 bits",
                    exception
            );
        }
    }
}
