package com.smartdistribution.identity.dto.response;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String email,
        String name,
        List<String> roles,
        List<String> permissions
) {
}
