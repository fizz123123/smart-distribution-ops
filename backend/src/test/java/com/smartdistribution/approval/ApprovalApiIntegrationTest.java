package com.smartdistribution.approval;

import com.jayway.jsonpath.JsonPath;
import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.UserRole;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.RoleRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApprovalApiIntegrationTest.ApprovalTestConfiguration.class)
class ApprovalApiIntegrationTest {

    private static final String TEST_EMAIL_SUFFIX = "@approval.integration.test";
    private static final String PASSWORD = "Test1234";
    private static final Long TARGET_ID = 9001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private TestSupplierApprovalHandler approvalHandler;

    @BeforeEach
    @AfterEach
    void cleanTestData() {
        jdbcTemplate.update("DELETE FROM approval_action");
        jdbcTemplate.update("DELETE FROM approval_request");

        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT id FROM app_user WHERE email LIKE ?",
                Long.class,
                "%" + TEST_EMAIL_SUFFIX
        );

        for (Long userId : userIds) {
            jdbcTemplate.update(
                    "DELETE FROM user_permission_grant WHERE user_id = ?",
                    userId
            );
            jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    @Test
    void purchasingManagerCanApproveAndReadCompleteHistory() throws Exception {
        AppUser creator = createUser("creator", "PURCHASING");
        AppUser submitter = creator;
        AppUser approver = createUser("approver", "PURCHASING_MANAGER");
        approvalHandler.reset(creator.getId());

        Long approvalRequestId = submitAndGetRequestId(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID,
                submitter.getId()
        );

        String token = login(approver.getEmail());

        mockMvc.perform(post(
                                "/api/v1/approval-requests/{id}/approve",
                                approvalRequestId
                        )
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment": "資料正確"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("APPROVED"))
                .andExpect(jsonPath("$.actions.length()").value(2))
                .andExpect(jsonPath("$.actions[1].actionType").value("APPROVE"))
                .andExpect(jsonPath("$.actions[1].actor.id").value(approver.getId()));

        assertThat(approvalHandler.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void creatorOrSubmitterCannotApproveOwnRequest() throws Exception {
        AppUser approver = createUser("self", "PURCHASING_MANAGER");
        AppUser submitter = createUser("other-submitter", "PURCHASING");
        approvalHandler.reset(approver.getId());

        Long approvalRequestId = submitAndGetRequestId(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID,
                submitter.getId()
        );

        mockMvc.perform(post(
                                "/api/v1/approval-requests/{id}/approve",
                                approvalRequestId
                        )
                        .header("Authorization", bearer(login(approver.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("APPROVAL_SELF_APPROVAL_FORBIDDEN"));

        approvalHandler.reset(submitter.getId());
        Long submittedByApproverId = submitAndGetRequestId(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID + 1,
                approver.getId()
        );

        mockMvc.perform(post(
                                "/api/v1/approval-requests/{id}/approve",
                                submittedByApproverId
                        )
                        .header("Authorization", bearer(login(approver.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("APPROVAL_SELF_APPROVAL_FORBIDDEN"));
    }

    @Test
    void managerWithApprovePermissionCanApprove() throws Exception {
        AppUser creator = createUser("manager-target-creator", "PURCHASING");
        AppUser submitter = createUser("manager-target-submitter", "PURCHASING");
        AppUser manager = createUser("manager-approver", "MANAGER");
        approvalHandler.reset(creator.getId());

        Long approvalRequestId = submitAndGetRequestId(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID,
                submitter.getId()
        );

        mockMvc.perform(post(
                                "/api/v1/approval-requests/{id}/approve",
                                approvalRequestId
                        )
                        .header("Authorization", bearer(login(manager.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment": "   "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions[1].actor.id").value(manager.getId()));

        String storedComment = jdbcTemplate.queryForObject(
                """
                SELECT comment
                FROM approval_action
                WHERE approval_request_id = ? AND action_type = 'APPROVE'
                """,
                String.class,
                approvalRequestId
        );
        assertThat(storedComment).isNull();
    }

    @Test
    void returnedTargetCanStartANewRoundWithoutLosingHistory() throws Exception {
        AppUser creator = createUser("round-creator", "PURCHASING");
        AppUser submitter = createUser("round-submitter", "PURCHASING");
        AppUser approver = createUser("round-approver", "PURCHASING_MANAGER");
        approvalHandler.reset(creator.getId());
        String token = login(approver.getEmail());

        Long firstRoundId = submitAndGetRequestId(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID,
                submitter.getId()
        );

        mockMvc.perform(post(
                                "/api/v1/approval-requests/{id}/return",
                                firstRoundId
                        )
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment": "請補齊聯絡資料"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("RETURNED"));

        approvalHandler.prepareResubmission();
        Long secondRoundId = submitAndGetRequestId(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID,
                submitter.getId()
        );

        mockMvc.perform(get(
                                "/api/v1/approval-requests/{id}",
                                secondRoundId
                        )
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.roundNo").value(2))
                .andExpect(jsonPath("$.request.status").value("PENDING"))
                .andExpect(jsonPath("$.actions.length()").value(3))
                .andExpect(jsonPath("$.actions[1].actionType").value("RETURN"))
                .andExpect(jsonPath("$.actions[2].actionType").value("SUBMIT"));
    }

    @Test
    void processedRequestCannotBeProcessedAgain() throws Exception {
        AppUser creator = createUser("repeat-creator", "PURCHASING");
        AppUser submitter = createUser("repeat-submitter", "PURCHASING");
        AppUser approver = createUser("repeat-approver", "PURCHASING_MANAGER");
        approvalHandler.reset(creator.getId());
        String token = login(approver.getEmail());

        Long approvalRequestId = submitAndGetRequestId(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID,
                submitter.getId()
        );

        mockMvc.perform(post(
                                "/api/v1/approval-requests/{id}/approve",
                                approvalRequestId
                        )
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                                "/api/v1/approval-requests/{id}/return",
                                approvalRequestId
                        )
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment": "已核准後再退回"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("APPROVAL_ALREADY_PROCESSED"));

        assertThat(approvalHandler.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void pendingListUsesEffectivePermissionsAndExcludesOwnRequests() throws Exception {
        AppUser creator = createUser("pending-creator", "PURCHASING");
        AppUser submitter = createUser("pending-submitter", "PURCHASING");
        AppUser approver = createUser("pending-approver", "PURCHASING_MANAGER");
        AppUser warehouse = createUser("pending-warehouse", "WAREHOUSE");
        approvalHandler.reset(creator.getId());

        Long approvalRequestId = submitAndGetRequestId(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID,
                submitter.getId()
        );

        mockMvc.perform(get("/api/v1/approval-requests/my-pending")
                        .header("Authorization", bearer(login(approver.getEmail()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(approvalRequestId));

        mockMvc.perform(get("/api/v1/approval-requests/my-pending")
                        .header("Authorization", bearer(login(submitter.getEmail()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/approval-requests/my-pending")
                        .header("Authorization", bearer(login(warehouse.getEmail()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(post(
                                "/api/v1/approval-requests/{id}/approve",
                                approvalRequestId
                        )
                        .header("Authorization", bearer(login(warehouse.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void returnRequiresAComment() throws Exception {
        AppUser creator = createUser("comment-creator", "PURCHASING");
        AppUser submitter = createUser("comment-submitter", "PURCHASING");
        AppUser approver = createUser("comment-approver", "PURCHASING_MANAGER");
        approvalHandler.reset(creator.getId());

        Long approvalRequestId = submitAndGetRequestId(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID,
                submitter.getId()
        );

        mockMvc.perform(post(
                                "/api/v1/approval-requests/{id}/return",
                                approvalRequestId
                        )
                        .header("Authorization", bearer(login(approver.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void targetCannotHaveTwoPendingApprovalRequests() {
        AppUser submitter = createUser("duplicate-pending", "PURCHASING");
        approvalHandler.reset(submitter.getId());

        approvalService.submit(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID,
                submitter.getId()
        );

        assertThatThrownBy(() -> approvalService.submit(
                ApprovalTargetType.SUPPLIER,
                TARGET_ID,
                submitter.getId()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APPROVAL_ALREADY_PENDING);
    }

    private Long submitAndGetRequestId(
            ApprovalTargetType targetType,
            Long targetId,
            Long submitterId
    ) {
        approvalService.submit(targetType, targetId, submitterId);

        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM approval_request
                WHERE target_type = ? AND target_id = ?
                ORDER BY round_no DESC
                LIMIT 1
                """,
                Long.class,
                targetType.name(),
                targetId
        );
    }

    private AppUser createUser(String prefix, String roleCode) {
        AppUser user = userRepository.save(new AppUser(
                prefix + "-" + UUID.randomUUID() + TEST_EMAIL_SUFFIX,
                prefix,
                passwordEncoder.encode(PASSWORD)
        ));
        Role role = roleRepository.findByRoleCode(roleCode).orElseThrow();
        userRoleRepository.save(new UserRole(user, role));

        return user;
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.accessToken"
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ApprovalTestConfiguration {

        @Bean
        TestSupplierApprovalHandler testSupplierApprovalHandler() {
            return new TestSupplierApprovalHandler();
        }
    }

    static class TestSupplierApprovalHandler implements ApprovalHandler {

        private Long createdBy;
        private String status;

        void reset(Long createdBy) {
            this.createdBy = createdBy;
            this.status = "PENDING";
        }

        void prepareResubmission() {
            this.status = "PENDING";
        }

        String getStatus() {
            return status;
        }

        @Override
        public ApprovalTargetType targetType() {
            return ApprovalTargetType.SUPPLIER;
        }

        @Override
        public ApprovalTarget loadTarget(Long targetId) {
            return new ApprovalTarget(
                    createdBy,
                    "測試供應商",
                    List.of()
            );
        }

        @Override
        public void onApproved(Long targetId, Long approverId) {
            this.status = "APPROVED";
        }

        @Override
        public void onReturned(Long targetId, Long approverId) {
            this.status = "RETURNED";
        }
    }
}
