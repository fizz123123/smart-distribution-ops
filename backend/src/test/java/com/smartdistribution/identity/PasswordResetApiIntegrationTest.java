package com.smartdistribution.identity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系統管理員寄出重設密碼連結，以及登入後修改自己的密碼
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetApiIntegrationTest extends AccountTestSupport {

    @Test
    void adminSendsResetLinkAndOldPasswordWorksUntilNewOneIsSet() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("reset");
        Long userId = createActiveUser(admin, email);
        mailSender.clear();

        sendReset(admin, userId).andExpect(status().isOk());
        assertThat(mailSender.messagesTo(email)).hasSize(1);
        assertThat(mailSender.messagesTo(email).getFirst().subject()).contains("重設密碼");

        //只是寄出連結，舊密碼仍然可以登入
        login(email, PASSWORD);

        setPassword(mailSender.latestTokenFor(email), "NewPass99").andExpect(status().isNoContent());

        performLogin(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        login(email, "NewPass99");

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE target_type = 'USER' AND target_id = ? AND action_type = 'USER_PASSWORD_RESET_SEND'",
                Integer.class,
                userId
        );
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void resendingResetLinkVoidsThePreviousOne() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("resend");
        Long userId = createActiveUser(admin, email);
        mailSender.clear();

        sendReset(admin, userId).andExpect(status().isOk());
        String oldToken = mailSender.latestTokenFor(email);
        sendReset(admin, userId).andExpect(status().isOk());
        String newToken = mailSender.latestTokenFor(email);

        setPassword(oldToken, "NewPass99")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOKEN_INVALID"));
        setPassword(newToken, "NewPass99").andExpect(status().isNoContent());
    }

    @Test
    void resetLinkCanOnlyBeSentToOtherActiveUsers() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String pendingEmail = testEmail("pending");
        Long pendingUserId = createUser(admin, pendingEmail);
        mailSender.clear();

        //待啟用的帳號要用重寄邀請，不是重設密碼
        sendReset(admin, pendingUserId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_STATUS_CONFLICT"));
        assertThat(mailSender.messagesTo(pendingEmail)).isEmpty();

        //系統管理員不能替自己寄，要登入後自己修改密碼
        MvcResult me = mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(admin)))
                .andReturn();
        Number adminId = JsonPath.read(me.getResponse().getContentAsString(), "$.id");
        sendReset(admin, adminId.longValue())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_SELF_MANAGEMENT_FORBIDDEN"));
    }

    @Test
    void userWithoutUserManagePermissionCannotSendResetLink() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("normal");
        Long userId = createActiveUser(admin, email);
        String userToken = login(email, PASSWORD);

        sendReset(userToken, userId).andExpect(status().isForbidden());
    }

    @Test
    void disabledUserCannotUseResetLink() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("disabled");
        Long userId = createActiveUser(admin, email);
        mailSender.clear();

        sendReset(admin, userId).andExpect(status().isOk());
        String token = mailSender.latestTokenFor(email);

        MvcResult detail = mockMvc.perform(get("/api/v1/users/{id}", userId).header("Authorization", bearer(admin)))
                .andReturn();
        Number version = JsonPath.read(detail.getResponse().getContentAsString(), "$.version");

        mockMvc.perform(post("/api/v1/users/{id}/disable", userId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "離職", "version": %d}
                                """.formatted(version.longValue())))
                .andExpect(status().isOk());

        setPassword(token, "NewPass99")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOKEN_INVALID"));
    }

    @Test
    void userCanChangeOwnPasswordWithCurrentPassword() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("change");
        createActiveUser(admin, email);
        String token = login(email, PASSWORD);

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Wrong999", "newPassword": "NewPass99"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "NewPass99"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        login(email, "NewPass99");
    }
}
