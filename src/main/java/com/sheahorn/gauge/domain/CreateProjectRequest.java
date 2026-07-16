package com.sheahorn.gauge.domain;

public record CreateProjectRequest(
    String name,
    String description,
    String parentId
) {}