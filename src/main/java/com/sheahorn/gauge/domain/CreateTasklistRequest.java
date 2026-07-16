package com.sheahorn.gauge.domain;

public record CreateTasklistRequest(
    String title,
    String decomposesTaskId
) {}