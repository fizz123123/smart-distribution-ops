package com.smartdistribution.identity.service.impl;

import com.smartdistribution.identity.entity.AppUser;
import com.smartdistribution.identity.repository.AppUserRepository;
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

    private final AppUserRepository userRepository;

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
}
