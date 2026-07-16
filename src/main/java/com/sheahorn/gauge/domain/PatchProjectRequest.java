package com.sheahorn.gauge.domain;

public record PatchProjectRequest(
    String name,
    String description,
    String removalLock
) {}