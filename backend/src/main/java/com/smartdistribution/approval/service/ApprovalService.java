package com.smartdistribution.approval.service;

import com.smartdistribution.approval.dto.request.ApproveRequest;
import com.smartdistribution.approval.dto.request.ReturnRequest;
import com.smartdistribution.approval.dto.response.ApprovalDetailResponse;
import com.smartdistribution.approval.dto.response.ApprovalHistoryResponse;
import com.smartdistribution.approval.dto.response.ApprovalSummaryResponse;
import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ApprovalService {

    void submit(
            ApprovalTargetType targetType,
            Long targetId,
            Long submitterId
    );

    PageResponse<ApprovalSummaryResponse> findMyPending(Pageable pageable);

    ApprovalDetailResponse findDetail(Long approvalRequestId);

    /**
     * 查詢一筆資料所有輪次的核准歷程，讓建立者、送審者看得到退回意見
     * 有該類型的查看權限或核准權限即可；還沒送審過的資料回傳空清單
     *
     * @throws com.smartdistribution.shared.exception.BusinessException 兩種權限都沒有時（ACCESS_DENIED）
     */
    ApprovalHistoryResponse findHistory(ApprovalTargetType targetType, Long targetId);

    ApprovalDetailResponse approve(
            Long approvalRequestId,
            ApproveRequest request
    );

    ApprovalDetailResponse returnForRevision(
            Long approvalRequestId,
            ReturnRequest request
    );
}
