package com.sheahorn.gauge.domain;

public record CreateIssueRequest(
    String title,
    String description,
    Priority priority
) {}