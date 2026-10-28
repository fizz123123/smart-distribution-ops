package com.smartdistribution.identity.service;

import java.util.Map;
import java.util.Set;

public interface UserDirectoryService {

    //清單頁一次查多個人的姓名
    Map<Long, String> findNamesByIds(Set<Long> userIds);

    //查單一使用者的姓名，找不到回傳 null
    String findName(Long userId);

    /**
     * 可以擔任 SO 負責業務：使用者存在、啟用中，且有效權限包含 SO_CREATE
     * 看權限而不看角色，所以業務、業務主管、被個別授權代理業務的人都符合
     */
    boolean isActiveSalesUser(Long userId);
}
