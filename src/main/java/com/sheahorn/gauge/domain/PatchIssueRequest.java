package com.sheahorn.gauge.domain;

public record PatchIssueRequest(
    String title,
    String description
) {}