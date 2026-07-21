package com.yo.security.domain;

import java.util.Set;

public record AuthenticatedUser(
    Long userId,
    Long orgId,
    String username,
    Set<String> roles,
    Set<String> permissions,
    long tokenVersion) {}
