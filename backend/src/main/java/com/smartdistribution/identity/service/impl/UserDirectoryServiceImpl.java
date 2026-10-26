package com.smartdistribution.identity.service.impl;

import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.repository.AppUserRepository;
import com.smartdistribution.identity.repository.UserRoleRepository;
import com.smartdistribution.identity.service.UserDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDirectoryServiceImpl implements UserDirectoryService {

    private static final String SALES_ROLE = "SALES";

    private final AppUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

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
    public boolean isActiveSalesUser(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.isActive() && userRoleRepository.existsByUserIdAndRoleRoleCode(userId, SALES_ROLE))
                .orElse(false);
    }
}
