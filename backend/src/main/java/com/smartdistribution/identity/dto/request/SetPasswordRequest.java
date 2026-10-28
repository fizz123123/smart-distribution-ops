package com.smartdistribution.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

//用 email 中的連結設定密碼，首次設定與重設密碼共用；使用者還沒登入，由 token 證明身分
public record SetPasswordRequest(
        @NotBlank
        @Size(max = 100)
        String token,

        @NotBlank
        @Size(min = 8, max = 20)
        String newPassword
) {
}
