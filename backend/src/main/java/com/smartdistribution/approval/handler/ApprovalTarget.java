package com.smartdistribution.approval.handler;

import java.util.List;

public record ApprovalTarget(
        Long createdBy,
        String label,
        List<String> warnings
) {
}
