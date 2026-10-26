package com.smartdistribution.identity.service;

import java.util.Map;
import java.util.Set;

public interface UserDirectoryService {

    Map<Long, String> findNamesByIds(Set<Long> userIds);

    /**
     * 使用者存在、啟用中，且具有業務角色（SALES）；SO 指定負責業務時使用
     */
    boolean isActiveSalesUser(Long userId);
}
