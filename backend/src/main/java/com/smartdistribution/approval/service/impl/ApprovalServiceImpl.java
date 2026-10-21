package com.smartdistribution.approval.service.impl;

import com.smartdistribution.approval.dto.request.ApproveRequest;
import com.smartdistribution.approval.dto.request.ReturnRequest;
import com.smartdistribution.approval.dto.response.ApprovalActionResponse;
import com.smartdistribution.approval.dto.response.ApprovalDetailResponse;
import com.smartdistribution.approval.dto.response.ApprovalSummaryResponse;
import com.smartdistribution.approval.entity.ApprovalAction;
import com.smartdistribution.approval.entity.ApprovalActionType;
import com.smartdistribution.approval.entity.ApprovalRequest;
import com.smartdistribution.approval.entity.ApprovalStatus;
import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.handler.ApprovalHandler;
import com.smartdistribution.approval.handler.ApprovalTarget;
import com.smartdistribution.approval.mapper.ApprovalMapper;
import com.smartdistribution.approval.repository.ApprovalActionRepository;
import com.smartdistribution.approval.repository.ApprovalRequestRepository;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.identity.service.UserDirectoryService;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.security.userdetails.CustomUserDetails;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalActionRepository approvalActionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserDirectoryService userDirectoryService;
    private final ApprovalMapper approvalMapper;
    private final Map<ApprovalTargetType, ApprovalHandler> handlers;

    public ApprovalServiceImpl(
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalActionRepository approvalActionRepository,
            CurrentUserProvider currentUserProvider,
            UserDirectoryService userDirectoryService,
            ApprovalMapper approvalMapper,
            List<ApprovalHandler> approvalHandlers
    ) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalActionRepository = approvalActionRepository;
        this.currentUserProvider = currentUserProvider;
        this.userDirectoryService = userDirectoryService;
        this.approvalMapper = approvalMapper;
        this.handlers = new EnumMap<>(ApprovalTargetType.class);

        for (ApprovalHandler handler : approvalHandlers) {
            // 同一類型有兩個 handler 時，put 會默默覆蓋其中一個，所以在啟動時就失敗
            if (handlers.put(handler.targetType(), handler) != null) {
                throw new IllegalStateException(
                        "Duplicate approval handler for " + handler.targetType()
                );
            }
        }
    }

    @Override
    @Transactional
    public void submit(
            ApprovalTargetType targetType,
            Long targetId,
            Long submitterId
    ) {
        if (approvalRequestRepository.existsByTargetTypeAndTargetIdAndStatus(
                targetType,
                targetId,
                ApprovalStatus.PENDING
        )) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_PENDING);
        }

        ApprovalTarget target = getHandler(targetType).loadTarget(targetId);
        int roundNo = Math.toIntExact(
                approvalRequestRepository.countByTargetTypeAndTargetId(
                        targetType,
                        targetId
                ) + 1
        );

        ApprovalRequest approvalRequest = approvalRequestRepository.save(
                new ApprovalRequest(
                        targetType,
                        targetId,
                        roundNo,
                        target.label(),
                        target.createdBy(),
                        submitterId,
                        Instant.now()
                )
        );

        approvalActionRepository.save(new ApprovalAction(
                approvalRequest,
                ApprovalActionType.SUBMIT,
                submitterId,
                null
        ));
    }

    @Override
    public PageResponse<ApprovalSummaryResponse> findMyPending(Pageable pageable) {
        CustomUserDetails currentUser = currentUserProvider.getCurrentUserDetails();
        Set<ApprovalTargetType> targetTypes = findApprovableTargetTypes(currentUser);

        if (targetTypes.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }

        Page<ApprovalRequest> requests = approvalRequestRepository.findPendingForUser(
                ApprovalStatus.PENDING,
                targetTypes,
                currentUser.getUserId(),
                pageable
        );

        Set<Long> userIds = new HashSet<>();
        for (ApprovalRequest request : requests) {
            userIds.add(request.getTargetCreatedBy());
            userIds.add(request.getSubmittedBy());
        }

        Map<Long, String> userNames = userDirectoryService.findNamesByIds(userIds);
        Page<ApprovalSummaryResponse> response = requests.map(
                request -> approvalMapper.toSummary(request, userNames)
        );

        return PageResponse.from(response);
    }

    @Override
    public ApprovalDetailResponse findDetail(Long approvalRequestId) {
        ApprovalRequest approvalRequest = findApprovalRequest(approvalRequestId);
        ensureHasApprovalPermission(
                approvalRequest.getTargetType(),
                currentUserProvider.getCurrentUserDetails()
        );

        return toDetail(approvalRequest);
    }

    @Override
    @Transactional
    public ApprovalDetailResponse approve(
            Long approvalRequestId,
            ApproveRequest request
    ) {
        ApprovalRequest approvalRequest = findPendingRequest(approvalRequestId);
        CustomUserDetails actor = currentUserProvider.getCurrentUserDetails();

        ensureCanProcess(approvalRequest, actor);

        approvalRequest.approve();
        approvalActionRepository.save(new ApprovalAction(
                approvalRequest,
                ApprovalActionType.APPROVE,
                actor.getUserId(),
                request.comment()
        ));

        //先寫入資料庫，因為兩人同時處理同一申請時，後到者在這裡因樂觀鎖失敗，不會執行到 handler
        approvalRequestRepository.flush();

        getHandler(approvalRequest.getTargetType()).onApproved(
                approvalRequest.getTargetId(),
                actor.getUserId()
        );

        return toDetail(approvalRequest);
    }

    @Override
    @Transactional
    public ApprovalDetailResponse returnForRevision(
            Long approvalRequestId,
            ReturnRequest request
    ) {
        ApprovalRequest approvalRequest = findPendingRequest(approvalRequestId);
        CustomUserDetails actor = currentUserProvider.getCurrentUserDetails();

        ensureCanProcess(approvalRequest, actor);

        approvalRequest.returnForRevision();
        approvalActionRepository.save(new ApprovalAction(
                approvalRequest,
                ApprovalActionType.RETURN,
                actor.getUserId(),
                request.comment()
        ));

        //先寫入資料庫，因為兩人同時處理同一申請時，後到者在這裡因樂觀鎖失敗，不會執行到 handler
        approvalRequestRepository.flush();

        getHandler(approvalRequest.getTargetType()).onReturned(
                approvalRequest.getTargetId(),
                actor.getUserId()
        );

        return toDetail(approvalRequest);
    }

    private ApprovalRequest findApprovalRequest(Long approvalRequestId) {
        return approvalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.APPROVAL_REQUEST_NOT_FOUND
                ));
    }

    private ApprovalRequest findPendingRequest(Long approvalRequestId) {
        ApprovalRequest approvalRequest = findApprovalRequest(approvalRequestId);

        if (approvalRequest.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_PROCESSED);
        }

        return approvalRequest;
    }

    private void ensureCanProcess(
            ApprovalRequest approvalRequest,
            CustomUserDetails actor
    ) {
        ensureHasApprovalPermission(approvalRequest.getTargetType(), actor);

        if (actor.getUserId().equals(approvalRequest.getTargetCreatedBy())
                || actor.getUserId().equals(approvalRequest.getSubmittedBy())) {
            throw new BusinessException(
                    ErrorCode.APPROVAL_SELF_APPROVAL_FORBIDDEN
            );
        }
    }

    private void ensureHasApprovalPermission(
            ApprovalTargetType targetType,
            CustomUserDetails user
    ) {
        if (!hasAuthority(user, targetType.getPermissionCode())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private Set<ApprovalTargetType> findApprovableTargetTypes(
            CustomUserDetails user
    ) {
        Set<ApprovalTargetType> targetTypes = new HashSet<>();

        for (ApprovalTargetType targetType : ApprovalTargetType.values()) {
            if (hasAuthority(user, targetType.getPermissionCode())) {
                targetTypes.add(targetType);
            }
        }

        return targetTypes;
    }

    private boolean hasAuthority(CustomUserDetails user, String permissionCode) {
        for (GrantedAuthority authority : user.getAuthorities()) {
            if (permissionCode.equals(authority.getAuthority())) {
                return true;
            }
        }

        return false;
    }

    private ApprovalDetailResponse toDetail(ApprovalRequest approvalRequest) {
        ApprovalTarget target = getHandler(approvalRequest.getTargetType())
                .loadTarget(approvalRequest.getTargetId());

        List<ApprovalAction> actions = approvalActionRepository.findHistory(
                approvalRequest.getTargetType(),
                approvalRequest.getTargetId()
        );

        Set<Long> userIds = new HashSet<>();
        userIds.add(approvalRequest.getTargetCreatedBy());
        userIds.add(approvalRequest.getSubmittedBy());

        for (ApprovalAction action : actions) {
            userIds.add(action.getActorId());
        }

        Map<Long, String> userNames = userDirectoryService.findNamesByIds(userIds);

        List<ApprovalActionResponse> actionResponses = actions.stream()
                .map(action -> approvalMapper.toAction(action, userNames))
                .toList();

        return new ApprovalDetailResponse(
                approvalMapper.toSummary(approvalRequest, userNames),
                target.warnings(),
                actionResponses
        );
    }

    private ApprovalHandler getHandler(ApprovalTargetType targetType) {
        ApprovalHandler handler = handlers.get(targetType);

        if (handler == null) {
            throw new IllegalStateException("No approval handler registered for " + targetType);
        }

        return handler;
    }
}
