package com.smartdistribution.identity.service.impl;

import com.smartdistribution.audit.entity.AuditActionType;
import com.smartdistribution.audit.service.AuditService;
import com.smartdistribution.identity.dto.request.ChangeUserStatusRequest;
import com.smartdistribution.identity.dto.request.CreateUserRequest;
import com.smartdistribution.identity.dto.request.PermissionGrantItemRequest;
import com.smartdistribution.identity.dto.request.ReplaceUserPermissionGrantsRequest;
import com.smartdistribution.identity.dto.request.ReplaceUserRolesRequest;
import com.smartdistribution.identity.dto.request.UpdateUserRequest;
import com.smartdistribution.identity.dto.response.UserDetailResponse;
import com.smartdistribution.identity.dto.response.UserSummaryResponse;
import com.smartdistribution.identity.dto.response.AuthorizationOptionsResponse;
import com.smartdistribution.identity.dto.response.PermissionResponse;
import com.smartdistribution.identity.dto.response.RoleResponse;
import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.Permission;
import com.smartdistribution.identity.entity.Role;
import com.smartdistribution.identity.entity.UserPermissionGrant;
import com.smartdistribution.identity.entity.UserRole;
import com.smartdistribution.identity.mapper.UserMapper;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.PermissionRepository;
import com.smartdistribution.identity.repository.RoleRepository;
import com.smartdistribution.identity.repository.UserPermissionGrantRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import com.smartdistribution.identity.service.EffectivePermissionResolver;
import com.smartdistribution.identity.service.UserManagementService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserManagementServiceImpl implements UserManagementService {

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionGrantRepository userPermissionGrantRepository;
    private final EffectivePermissionResolver effectivePermissionResolver;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;
    private final AuditService auditService;

    private static final String APP_ADMIN_ROLE = "APP_ADMIN";
    private static final Set<String> NON_GRANTABLE_PERMISSION_CODES = Set.of(
            "USER_MANAGE",
            "USER_ROLE_MANAGE",
            "USER_PERMISSION_MANAGE"
    );

    @Override
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public AuthorizationOptionsResponse findAuthorizationOptions() {
        List<RoleResponse> roles = roleRepository.findAll()
                .stream()
                .filter(role -> !APP_ADMIN_ROLE.equals(role.getRoleCode()))
                .map(userMapper::toRoleResponse)
                .sorted(Comparator.comparing(RoleResponse::roleCode))
                .toList();

        List<PermissionResponse> permissions = permissionRepository.findAll()
                .stream()
                .filter(permission -> !NON_GRANTABLE_PERMISSION_CODES.contains(
                        permission.getPermissionCode()
                ))
                .map(userMapper::toPermissionResponse)
                .sorted(Comparator.comparing(PermissionResponse::permissionCode))
                .toList();

        return new AuthorizationOptionsResponse(roles, permissions);
    }

    @Override
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public PageResponse<UserSummaryResponse> findUsers(Pageable pageable) {
        Page<AppUser> users = userRepository.findAll(pageable);

        List<Long> userIds = users.stream()
                .map(AppUser::getId)
                .toList();

        Map<Long, List<Role>> rolesByUserId = findRolesByUserIds(userIds);

        Page<UserSummaryResponse> responsePage = users.map(user ->
                userMapper.toSummary(
                        user,
                        rolesByUserId.getOrDefault(user.getId(), List.of())
                )
        );

        return PageResponse.from(responsePage);
    }

    @Override
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDetailResponse findUser(Long userId) {
        return toDetail(findUserById(userId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDetailResponse createUser(CreateUserRequest request) {
        AppUser user = new AppUser(
                request.email(),
                request.name(),
                passwordEncoder.encode(request.password())
        );

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
        }

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.USER_CREATE,
                user.getId(),
                "建立使用者",
                null,
                userSnapshot(user),
                null
        );

        return toDetail(user);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDetailResponse updateUser(
            Long userId,
            UpdateUserRequest request
    ) {
        AppUser user = findUserById(userId);
        verifyVersion(user, request.version());

        String beforeData = userSnapshot(user);

        user.changeEmail(request.email());
        user.changeName(request.name());

        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
        }

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.USER_UPDATE,
                userId,
                "修改使用者基本資料",
                beforeData,
                userSnapshot(user),
                null
        );

        return toDetail(user);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDetailResponse activateUser(
            Long userId,
            ChangeUserStatusRequest request
    ) {
        AppUser user = findUserById(userId);
        verifyVersion(user, request.version());

        String beforeData = userSnapshot(user);

        user.activate();
        userRepository.flush();

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.USER_ACTIVATE,
                userId,
                "啟用使用者",
                beforeData,
                userSnapshot(user),
                request.reason().strip()
        );

        return toDetail(user);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDetailResponse disableUser(
            Long userId,
            ChangeUserStatusRequest request
    ) {
        AppUser user = findUserById(userId);
        ensureTargetIsNotCurrentUser(userId);
        verifyVersion(user, request.version());

        String beforeData = userSnapshot(user);

        user.disable();
        userRepository.flush();

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.USER_DISABLE,
                userId,
                "停用使用者",
                beforeData,
                userSnapshot(user),
                request.reason().strip()
        );

        return toDetail(user);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_ROLE_MANAGE')")
    public UserDetailResponse replaceRoles(
            Long userId,
            ReplaceUserRolesRequest request
    ) {
        AppUser user = findUserById(userId);
        ensureTargetIsNotCurrentUser(userId);
        ensureTargetIsNotAppAdmin(userId);

        if (request.roleCodes().contains(APP_ADMIN_ROLE)) {
            throw new BusinessException(ErrorCode.APP_ADMIN_AUTHORIZATION_IMMUTABLE);
        }
        verifyVersion(user, request.version());
        user.markAuthorizationChanged(Instant.now());

        Set<String> requestedRoleCodes = request.roleCodes();

        List<Role> requestedRoles = findRolesByCodes(requestedRoleCodes);

        List<UserRole> existingAssignments = userRoleRepository.findAllByUserId(userId);

        Set<String> existingRoleCodes = existingAssignments.stream()
                .map(assignment -> assignment.getRole().getRoleCode())
                .collect(Collectors.toSet());

        String beforeData = sortedValues(existingRoleCodes);

        List<UserRole> removedAssignments = existingAssignments.stream()
                .filter(assignment -> !requestedRoleCodes.contains(
                        assignment.getRole().getRoleCode()
                )).toList();

        List<UserRole> newAssignments = requestedRoles.stream()
                .filter(role -> !existingRoleCodes.contains(role.getRoleCode()))
                .map(role -> new UserRole(user, role))
                .toList();

        userRoleRepository.deleteAll(removedAssignments);
        userRoleRepository.saveAll(newAssignments);
        userRepository.flush();

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.USER_ROLES_REPLACE,
                userId,
                "取代使用者角色",
                beforeData,
                sortedValues(requestedRoleCodes),
                request.reason().strip()
        );

        return toDetail(user);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_PERMISSION_MANAGE')")
    public UserDetailResponse replacePermissionGrants(
            Long userId,
            ReplaceUserPermissionGrantsRequest request
    ) {
        AppUser user = findUserById(userId);
        ensureTargetIsNotCurrentUser(userId);
        ensureTargetIsNotAppAdmin(userId);
        verifyVersion(user, request.version());
        user.markAuthorizationChanged(Instant.now());

        Set<String> requestedPermissionCodes = request.grants()
                .stream()
                .map(PermissionGrantItemRequest::permissionCode)
                .collect(Collectors.toSet());

        if (!Collections.disjoint(
                requestedPermissionCodes,
                NON_GRANTABLE_PERMISSION_CODES
        )) {
            throw new BusinessException(
                    ErrorCode.USER_MANAGEMENT_PERMISSION_NOT_GRANTABLE
            );
        }

        Map<String, Permission> permissionsByCode =
                findPermissionsByCodes(requestedPermissionCodes);

        List<UserPermissionGrant> existingGrants =
                userPermissionGrantRepository.findAllByUserId(userId);

        String beforeData = permissionGrantSnapshot(existingGrants);

        userPermissionGrantRepository.deleteAll(existingGrants);
        userPermissionGrantRepository.flush();

        List<UserPermissionGrant> newGrants = request.grants()
                .stream()
                .map(grant ->
                        new UserPermissionGrant(
                                user,
                                permissionsByCode.get(grant.permissionCode()),
                                grant.reason().strip(),
                                grant.validFrom(),
                                grant.validTo()
                        )
                ).toList();

        userPermissionGrantRepository.saveAll(newGrants);
        userRepository.flush();

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.USER_PERMISSION_GRANTS_REPLACE,
                userId,
                "取代使用者個別授權",
                beforeData,
                permissionGrantSnapshot(newGrants),
                request.reason().strip()
        );

        return toDetail(user);
    }

    private UserDetailResponse toDetail(AppUser user) {
        Instant evaluatedAt = Instant.now();

        List<Role> roles = userRoleRepository.findAllByUserId(user.getId())
                .stream()
                .map(UserRole::getRole)
                .toList();

        List<UserPermissionGrant> grants =
                userPermissionGrantRepository.findAllByUserId(user.getId());

        Set<String> effectivePermissionCodes = effectivePermissionResolver.resolve(
                user,
                evaluatedAt
        );

        List<Permission> effectivePermissions = permissionRepository
                .findAllByPermissionCodeIn(effectivePermissionCodes);

        return userMapper.toDetail(
                user,
                roles,
                effectivePermissions,
                grants,
                evaluatedAt
        );
    }

    private Map<Long, List<Role>> findRolesByUserIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        // 1. 一次查詢，把這一頁所有使用者的角色全部撈回來
        List<UserRole> assignments = userRoleRepository.findAllByUserIdIn(userIds);

        // 2. 在記憶體裡，依 userId 分組
        Map<Long, List<Role>> rolesByUserId = new HashMap<>();

        for (UserRole assignment : assignments) {
            Long userId = assignment.getUser().getId();
            Role role = assignment.getRole();

            List<Role> roles = rolesByUserId.get(userId);

            if (roles == null) {
                roles = new ArrayList<>();
                rolesByUserId.put(userId, roles);
            }
            roles.add(role);
        }

        return rolesByUserId;
    }

    private List<Role> findRolesByCodes(Set<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return List.of();
        }

        List<Role> roles = roleRepository.findAllByRoleCodeIn(roleCodes);

        if (roles.size() != roleCodes.size()) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }

        return roles;
    }

    private Map<String, Permission> findPermissionsByCodes(Set<String> permissionCodes) {
        if (permissionCodes.isEmpty()) {
            return Map.of();
        }

        List<Permission> permissions = permissionRepository
                .findAllByPermissionCodeIn(permissionCodes);

        Map<String, Permission> permissionsByCode = new HashMap<>();

        for (Permission permission : permissions) {
            permissionsByCode.put(permission.getPermissionCode(), permission);
        }

        if (permissionsByCode.size() != permissionCodes.size()) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }

        return permissionsByCode;
    }

    private AppUser findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void ensureTargetIsNotCurrentUser(Long targetUserId) {
        Long actorId = currentUserProvider.getCurrentUserId();

        if (Objects.equals(targetUserId, actorId)) {
            throw new BusinessException(ErrorCode.USER_SELF_MANAGEMENT_FORBIDDEN);
        }
    }

    private void ensureTargetIsNotAppAdmin(Long targetUserId) {
        boolean targetIsAppAdmin = userRoleRepository
                .existsByUserIdAndRoleRoleCode(targetUserId, APP_ADMIN_ROLE);

        if (targetIsAppAdmin) {
            throw new BusinessException(ErrorCode.APP_ADMIN_AUTHORIZATION_IMMUTABLE);
        }
    }

    private String userSnapshot(AppUser user) {
        return "email=" + user.getEmail()
                + ", name=" + user.getName()
                + ", status=" + user.getStatus();
    }

    private String sortedValues(Collection<String> values) {
        return values.stream()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private String permissionGrantSnapshot(List<UserPermissionGrant> grants) {
        return grants.stream()
                .map(grant -> grant.getPermission().getPermissionCode()
                        + "[validFrom=" + grant.getValidFrom()
                        + ", validTo=" + grant.getValidTo()
                        + ", reason=" + grant.getReason() + "]")
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private void verifyVersion(AppUser user, Long expectedVersion) {
        if (!Objects.equals(user.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

}
