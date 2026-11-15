package com.smartdistribution.identity.service;

import java.util.List;
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

    /**
     * 啟用中、而且有效權限包含指定權限的使用者，給寄通知信找收件人用
     * 有效權限包含角色的權限與有效期間內的個別授權
     */
    List<UserContact> findActiveUsersWithPermission(String permissionCode);

    /**
     * 依 ID 查啟用中使用者的姓名與 email；停用、待啟用或不存在的不會出現在結果中
     */
    List<UserContact> findActiveContactsByIds(Set<Long> userIds);

    record UserContact(
            Long id,
            String name,
            String email
    ) {
    }
}
