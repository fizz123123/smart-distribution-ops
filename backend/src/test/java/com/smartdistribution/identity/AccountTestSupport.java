package com.smartdistribution.identity;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.support.RecordingMailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 密碼設定、重設與閒置停用測試的共用工具
 * 測試帳號的 Email 都以固定結尾建立，前後自動清除；寄出的信由 RecordingMailSender 記錄
 */
abstract class AccountTestSupport {

    static final String ADMIN_EMAIL = "admin@smartdistribution.local";
    static final String ADMIN_PASSWORD = "Admin1234";
    static final String TEST_EMAIL_SUFFIX = "@account.integration.test";
    static final String PASSWORD = "Test1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    RecordingMailSender mailSender;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        mailSender.clear();

        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT id FROM app_user WHERE email LIKE ?",
                Long.class,
                "%" + TEST_EMAIL_SUFFIX
        );

        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM audit_log WHERE target_type = 'USER' AND target_id = ?", userId);
            jdbcTemplate.update("DELETE FROM password_token WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_permission_grant WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    String testEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + TEST_EMAIL_SUFFIX;
    }

    ResultActions performLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    String login(String email, String password) throws Exception {
        MvcResult result = performLogin(email, password)
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    //管理員建立帳號，回傳使用者 ID；帳號是待啟用，並寄出設定密碼的信
    Long createUser(String adminToken, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "name": "整合測試帳號"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    ResultActions setPassword(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "newPassword": "%s"}
                        """.formatted(token, newPassword)));
    }

    //系統管理員替使用者寄出重設密碼連結
    ResultActions sendReset(String adminToken, Long userId) throws Exception {
        return mockMvc.perform(post("/api/v1/users/{id}/password-reset", userId)
                .header("Authorization", bearer(adminToken)));
    }

    //建立帳號並用信中的連結設定密碼，回傳使用者 ID
    Long createActiveUser(String adminToken, String email) throws Exception {
        Long userId = createUser(adminToken, email);
        setPassword(mailSender.latestTokenFor(email), PASSWORD).andExpect(status().isNoContent());
        return userId;
    }

    String bearer(String token) {
        return "Bearer " + token;
    }
}
