package com.sheahorn.gauge.domain;

import java.util.List;

public record ReorderTasksRequest(
    List<String> taskIds
) {}
