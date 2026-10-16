package com.smartdistribution.identity.controller;

import com.smartdistribution.identity.dto.request.ChangeUserStatusRequest;
import com.smartdistribution.identity.dto.request.CreateUserRequest;
import com.smartdistribution.identity.dto.request.ReplaceUserPermissionGrantsRequest;
import com.smartdistribution.identity.dto.request.ReplaceUserRolesRequest;
import com.smartdistribution.identity.dto.request.UpdateUserRequest;
import com.smartdistribution.identity.dto.response.AuthorizationOptionsResponse;
import com.smartdistribution.identity.dto.response.CurrentUserResponse;
import com.smartdistribution.identity.dto.response.UserDetailResponse;
import com.smartdistribution.identity.dto.response.UserSummaryResponse;
import com.smartdistribution.identity.service.AuthService;
import com.smartdistribution.identity.service.UserManagementService;
import com.smartdistribution.shared.web.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserManagementService userManagementService;

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        CurrentUserResponse response = authService.getCurrentUser();

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserSummaryResponse>> findUsers(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<UserSummaryResponse> response =
                userManagementService.findUsers(pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailResponse> findUser(
            @PathVariable Long userId
    ) {
        UserDetailResponse response = userManagementService.findUser(userId);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<UserDetailResponse> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserDetailResponse response = userManagementService.createUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserDetailResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserDetailResponse response = userManagementService.updateUser(userId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<UserDetailResponse> activateUser(
            @PathVariable Long userId,
            @Valid @RequestBody ChangeUserStatusRequest request
    ) {
        UserDetailResponse response = userManagementService.activateUser(userId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/disable")
    public ResponseEntity<UserDetailResponse> disableUser(
            @PathVariable Long userId,
            @Valid @RequestBody ChangeUserStatusRequest request
    ) {
        UserDetailResponse response = userManagementService.disableUser(userId, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/roles")
    public ResponseEntity<UserDetailResponse> replaceRoles(
            @PathVariable Long userId,
            @Valid @RequestBody ReplaceUserRolesRequest request
    ) {
        UserDetailResponse response = userManagementService.replaceRoles(userId, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/permission-grants")
    public ResponseEntity<UserDetailResponse> replacePermissionGrants(
            @PathVariable Long userId,
            @Valid @RequestBody ReplaceUserPermissionGrantsRequest request
    ) {
        UserDetailResponse response =
                userManagementService.replacePermissionGrants(userId, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/authorization-options")
    public ResponseEntity<AuthorizationOptionsResponse> findAuthorizationOptions() {
        return ResponseEntity.ok(
                userManagementService.findAuthorizationOptions()
        );
    }
}
