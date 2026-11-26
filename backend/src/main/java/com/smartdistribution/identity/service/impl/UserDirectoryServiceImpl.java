package com.smartdistribution.identity.service.impl;

import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.entity.AppUserStatus;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.service.EffectivePermissionResolver;
import com.smartdistribution.identity.service.UserDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDirectoryServiceImpl implements UserDirectoryService {

    private static final String SO_CREATE_PERMISSION = "SO_CREATE";

    private final AppUserRepository userRepository;
    private final EffectivePermissionResolver effectivePermissionResolver;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findNamesByIds(Set<Long> userIds) {
        return userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(
                        AppUser::getId,
                        AppUser::getName
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public String findName(Long userId) {
        return userRepository.findById(userId)
                .map(AppUser::getName)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveSalesUser(Long userId) {
        return userRepository.findById(userId)
                //停用的帳號有效權限一律是空的，所以這裡也一併排除停用帳號
                .map(user -> effectivePermissionResolver.resolve(user, Instant.now()).contains(SO_CREATE_PERMISSION))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserContact> findActiveSalesUsers() {
        return findActiveUsersWithPermission(SO_CREATE_PERMISSION);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserContact> findActiveUsersWithPermission(String permissionCode) {
        return userRepository.findAllWithPermission(permissionCode, AppUserStatus.ACTIVE, Instant.now())
                .stream()
                .map(this::toContact)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserContact> findActiveContactsByIds(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllByIdInAndStatus(userIds, AppUserStatus.ACTIVE)
                .stream()
                .map(this::toContact)
                .toList();
    }

    private UserContact toContact(AppUser user) {
        return new UserContact(user.getId(), user.getName(), user.getEmail());
    }
}
