package com.yo.security.domain;

import java.util.Set;

public record LoginUserVO(
    Long userId,
    String username,
    String displayName,
    Long orgId,
    Set<String> roles,
    Set<String> permissions) {}
