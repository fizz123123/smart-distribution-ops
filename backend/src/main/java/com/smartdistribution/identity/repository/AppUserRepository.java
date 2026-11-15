package com.smartdistribution.identity.repository;

import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.AppUserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String normalizedEmail);

    //閒置停用排程：最後活動時間早於門檻的帳號；最後活動時間是空的不會被選到
    List<AppUser> findAllByStatusAndLastActivityAtBefore(AppUserStatus status, Instant threshold);

    /**
     * 有效權限包含指定權限的使用者：角色帶有這個權限，或有在有效期間內的個別授權
     * 判斷規則和登入時的 EffectivePermissionResolver 相同，只是改成一次查出所有人
     */
    @Query("""
            SELECT user
            FROM AppUser user
            WHERE user.status = :status
              AND (
                  EXISTS (
                      SELECT userRole.id
                      FROM UserRole userRole, RolePermission rolePermission
                      WHERE userRole.user = user
                        AND rolePermission.role = userRole.role
                        AND rolePermission.permission.permissionCode = :permissionCode
                  )
                  OR EXISTS (
                      SELECT grant.id
                      FROM UserPermissionGrant grant
                      WHERE grant.user = user
                        AND grant.permission.permissionCode = :permissionCode
                        AND grant.validFrom <= :at
                        AND (grant.validTo IS NULL OR grant.validTo > :at)
                  )
              )
            ORDER BY user.id
            """)
    List<AppUser> findAllWithPermission(
            @Param("permissionCode") String permissionCode,
            @Param("status") AppUserStatus status,
            @Param("at") Instant at
    );

    List<AppUser> findAllByIdInAndStatus(Collection<Long> userIds, AppUserStatus status);

    /**
     * 登入成功時更新最後活動時間
     * 用 UPDATE 語句直接寫入，不會讓版本號加 1；否則使用者每次登入，
     * 管理員手上的編輯畫面就會變成舊版本而無法儲存
     */
    @Modifying
    @Query("UPDATE AppUser user SET user.lastActivityAt = :at WHERE user.id = :userId")
    void updateLastActivityAt(@Param("userId") Long userId, @Param("at") Instant at);
}
