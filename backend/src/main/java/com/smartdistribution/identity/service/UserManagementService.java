package com.smartdistribution.identity.service;

import com.smartdistribution.identity.dto.request.ChangeUserStatusRequest;
import com.smartdistribution.identity.dto.request.CreateUserRequest;
import com.smartdistribution.identity.dto.request.ReplaceUserPermissionGrantsRequest;
import com.smartdistribution.identity.dto.request.ReplaceUserRolesRequest;
import com.smartdistribution.identity.dto.request.UpdateUserRequest;
import com.smartdistribution.identity.dto.response.AuthorizationOptionsResponse;
import com.smartdistribution.identity.dto.response.UserDetailResponse;
import com.smartdistribution.identity.dto.response.UserSummaryResponse;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface UserManagementService {

    AuthorizationOptionsResponse findAuthorizationOptions();

    PageResponse<UserSummaryResponse> findUsers(Pageable pageable);

    UserDetailResponse findUser(Long userId);

    /**
     * 建立待啟用的帳號，不設密碼；交易成功後寄出設定密碼的連結
     */
    UserDetailResponse createUser(CreateUserRequest request);

    /**
     * 重寄設定密碼的信，只限待啟用的帳號；舊連結隨之失效
     */
    UserDetailResponse resendInvitation(Long userId);

    /**
     * 使用者忘記密碼時，由系統管理員寄出重設密碼連結；只限啟用中的帳號，也不能對自己操作
     * 寄出時不動舊密碼，使用者用連結設定新密碼之前，舊密碼都還能用
     */
    UserDetailResponse sendPasswordReset(Long userId);

    UserDetailResponse updateUser(Long userId, UpdateUserRequest request);

    UserDetailResponse activateUser(
            Long userId,
            ChangeUserStatusRequest request
    );

    UserDetailResponse disableUser(
            Long userId,
            ChangeUserStatusRequest request
    );

    UserDetailResponse replaceRoles(
            Long userId,
            ReplaceUserRolesRequest request
    );

    UserDetailResponse replacePermissionGrants(
            Long userId,
            ReplaceUserPermissionGrantsRequest request
    );
}
