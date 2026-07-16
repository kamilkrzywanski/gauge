package com.sheahorn.gauge.domain;

public record CreateUserRequest(
    String username,
    UserRole role
) {}