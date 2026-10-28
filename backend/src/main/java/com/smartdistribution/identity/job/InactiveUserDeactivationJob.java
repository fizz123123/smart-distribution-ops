package com.smartdistribution.identity.job;

import com.smartdistribution.audit.entity.AuditActionType;
import com.smartdistribution.audit.service.AuditService;
import com.smartdistribution.identity.config.AccountProperties;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.AppUserStatus;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 每天凌晨停用閒置的帳號：狀態是啟用中，而且最後一次登入或被啟用超過設定天數
 * 系統管理員不停用，避免沒有人能管理帳號；待啟用與已停用的帳號不檢查
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional
public class InactiveUserDeactivationJob {

    private static final String APP_ADMIN_ROLE = "APP_ADMIN";

    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;
    private final AccountProperties accountProperties;

    @Scheduled(cron = "${app.account.inactivity-cron}", zone = "Asia/Taipei")
    public void run() {
        int count = deactivateInactiveUsers(Instant.now());
        log.info("閒置帳號停用完成，共 {} 個", count);
    }

    /**
     * @return 這次停用的帳號數
     */
    public int deactivateInactiveUsers(Instant now) {
        Instant threshold = now.minus(Duration.ofDays(accountProperties.inactivityDays()));

        List<AppUser> inactiveUsers = appUserRepository
                .findAllByStatusAndLastActivityAtBefore(AppUserStatus.ACTIVE, threshold)
                .stream()
                .filter(user -> !userRoleRepository.existsByUserIdAndRoleRoleCode(user.getId(), APP_ADMIN_ROLE))
                .toList();

        for (AppUser user : inactiveUsers) {
            user.disable();

            auditService.record(
                    AuditService.SYSTEM_ACTOR_ID,
                    AuditActionType.USER_AUTO_DISABLE,
                    user.getId(),
                    "閒置超過 " + accountProperties.inactivityDays() + " 天自動停用",
                    "lastActivityAt=" + user.getLastActivityAt(),
                    "status=" + user.getStatus(),
                    null
            );
        }

        return inactiveUsers.size();
    }
}
