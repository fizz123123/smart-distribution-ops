package com.smartdistribution.shared.security.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors
) {

    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String secret,
            @NotNull Duration accessTokenExpiration
    ) {

        public Jwt {
            if (accessTokenExpiration != null && !accessTokenExpiration.isPositive()) {
                throw new IllegalArgumentException(
                        "JWT access token expiration must be positive"
                );
            }
        }
    }

    public record Cors(
            @NotEmpty List<@NotBlank String> allowedOrigins
    ) {
    }
}
