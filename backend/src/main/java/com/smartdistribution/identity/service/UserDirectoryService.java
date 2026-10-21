package com.smartdistribution.identity.service;

import java.util.Map;
import java.util.Set;

public interface UserDirectoryService {

    Map<Long, String> findNamesByIds(Set<Long> userIds);
}
