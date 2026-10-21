package com.smartdistribution.approval.dto.request;

import jakarta.validation.constraints.Size;

public record ApproveRequest(
        @Size(max = 500)
        String comment
) {
}
