package com.smartdistribution.approval.dto.response;

import java.util.List;

public record ApprovalDetailResponse(
        ApprovalSummaryResponse request,
        List<String> warnings,
        List<ApprovalActionResponse> actions
) {
}
