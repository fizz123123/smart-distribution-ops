package com.smartdistribution.identity.dto.response;

import java.time.Instant;

public record PermissionGrantResponse(
        PermissionResponse permission,
        String reason,
        Instant validFrom,
        Instant validTo,
        boolean effective
) {
}
