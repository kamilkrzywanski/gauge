package com.sheahorn.gauge.domain;

public record CreateTaskRequest(
    String title,
    String description
) {}