package com.smartdistribution.identity;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.identity.job.InactiveUserDeactivationJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 閒置超過 90 天的啟用帳號由排程自動停用
 * 測試直接呼叫排程方法，並改寫最後活動時間來模擬時間經過
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InactiveUserDeactivationTest extends AccountTestSupport {

    @Autowired
    InactiveUserDeactivationJob job;

    @Test
    void activeUserInactiveForMoreThan90DaysIsDisabled() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Long idleUser = createActiveUser(admin, testEmail("idle"));
        Long recentUser = createActiveUser(admin, testEmail("recent"));
        Long pendingUser = createUser(admin, testEmail("pending"));

        setLastActivityDaysAgo(idleUser, 91);
        setLastActivityDaysAgo(recentUser, 89);

        job.deactivateInactiveUsers(Instant.now());

        assertThat(statusOf(idleUser)).isEqualTo("DISABLED");
        assertThat(statusOf(recentUser)).isEqualTo("ACTIVE");
        assertThat(statusOf(pendingUser)).isEqualTo("PENDING");

        // 稽核寫成「系統自動停用」，操作者 ID 是 0
        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_log
                WHERE target_type = 'USER' AND target_id = ? AND action_type = 'USER_AUTO_DISABLE' AND actor_id = 0
                """,
                Integer.class,
                idleUser
        );
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void reactivatedUserStartsCountingAgain() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Long userId = createActiveUser(admin, testEmail("reactivate"));
        setLastActivityDaysAgo(userId, 120);
        job.deactivateInactiveUsers(Instant.now());

        mockMvc.perform(post("/api/v1/users/{id}/activate", userId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "回來上班", "version": %d}
                                """.formatted(versionOf(admin, userId))))
                .andExpect(status().isOk());

        // 重新啟用後從當下重新起算，隔天的排程不會又停用它
        job.deactivateInactiveUsers(Instant.now());

        assertThat(statusOf(userId)).isEqualTo("ACTIVE");
    }

    @Test
    void appAdminIsNeverDisabled() throws Exception {
        Long adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE email = ?", Long.class, ADMIN_EMAIL
        );

        try {
            setLastActivityDaysAgo(adminId, 365);

            job.deactivateInactiveUsers(Instant.now());

            assertThat(statusOf(adminId)).isEqualTo("ACTIVE");
        } finally {
            setLastActivityDaysAgo(adminId, 0);
        }
    }

    @Test
    void loginUpdatesLastActivityWithoutChangingVersion() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = testEmail("login");
        Long userId = createActiveUser(admin, email);
        setLastActivityDaysAgo(userId, 30);
        long versionBefore = versionOf(admin, userId);

        login(email, PASSWORD);

        // 登入不能讓版本號加 1，否則管理員正在編輯的畫面會變成舊版本
        assertThat(versionOf(admin, userId)).isEqualTo(versionBefore);
        Integer daysAgo = jdbcTemplate.queryForObject(
                "SELECT DATEDIFF(NOW(), last_activity_at) FROM app_user WHERE id = ?", Integer.class, userId
        );
        assertThat(daysAgo).isLessThan(2);
    }

    private void setLastActivityDaysAgo(Long userId, int days) {
        jdbcTemplate.update(
                "UPDATE app_user SET last_activity_at = DATE_SUB(NOW(), INTERVAL ? DAY) WHERE id = ?",
                days,
                userId
        );
    }

    private String statusOf(Long userId) {
        return jdbcTemplate.queryForObject("SELECT status FROM app_user WHERE id = ?", String.class, userId);
    }

    private long versionOf(String adminToken, Long userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/{id}", userId)
                        .header("Authorization", bearer(adminToken)))
                .andReturn();
        Number version = JsonPath.read(result.getResponse().getContentAsString(), "$.version");
        return version.longValue();
    }
}
