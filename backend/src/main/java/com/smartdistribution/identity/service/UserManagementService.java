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

    UserDetailResponse createUser(CreateUserRequest request);

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
