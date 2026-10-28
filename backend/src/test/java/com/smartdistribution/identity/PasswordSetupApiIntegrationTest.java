package com.smartdistribution.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理員建立帳號後，使用者用信中的連結第一次設定密碼
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordSetupApiIntegrationTest extends AccountTestSupport {

    @Test
    void createdUserIsPendingAndCannotLoginBeforeSettingPassword() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("pending");

        Long userId = createUser(admin, email);

        mockMvc.perform(get("/api/v1/users/{id}", userId).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // 建立帳號後寄出一封設定密碼的信
        assertThat(mailSender.messagesTo(email)).hasSize(1);
        assertThat(mailSender.messagesTo(email).getFirst().html()).contains("/set-password?token=");

        performLogin(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_PENDING_ACTIVATION"));
    }

    @Test
    void setupLinkActivatesAccountAndCanBeUsedOnlyOnce() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("setup");
        Long userId = createUser(admin, email);
        String token = mailSender.latestTokenFor(email);

        setPassword(token, PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/{id}", userId).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        login(email, PASSWORD);

        // 同一個連結不能再用一次
        setPassword(token, "Another99")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOKEN_INVALID"));
    }

    @Test
    void expiredOrUnknownLinkIsRejected() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("expired");
        Long userId = createUser(admin, email);
        String token = mailSender.latestTokenFor(email);

        jdbcTemplate.update(
                "UPDATE password_token SET expires_at = '2000-01-01 00:00:00' WHERE user_id = ?",
                userId
        );

        setPassword(token, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOKEN_INVALID"));

        setPassword("not-a-real-token", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOKEN_INVALID"));
    }

    @Test
    void resendingInvitationInvalidatesPreviousLink() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("resend");
        Long userId = createUser(admin, email);
        String oldToken = mailSender.latestTokenFor(email);

        mockMvc.perform(post("/api/v1/users/{id}/invitation", userId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        assertThat(mailSender.messagesTo(email)).hasSize(2);
        setPassword(oldToken, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOKEN_INVALID"));

        setPassword(mailSender.latestTokenFor(email), PASSWORD).andExpect(status().isNoContent());

        // 已經啟用的帳號不能再重寄
        mockMvc.perform(post("/api/v1/users/{id}/invitation", userId).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_STATUS_CONFLICT"));
    }

    @Test
    void mailContainsExpiryNotice() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("content");
        createUser(admin, email);

        assertThat(mailSender.messagesTo(email).getFirst().subject()).contains("設定你的登入密碼");
        assertThat(mailSender.messagesTo(email).getFirst().html()).contains("只能使用一次");

        //有按鈕連結，也帶入了姓名
        String html = mailSender.messagesTo(email).getFirst().html();
        assertThat(html).contains("href=\"http").contains("/set-password?token=").contains("整合測試帳號");
    }
}
