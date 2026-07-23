package com.sheahorn.gauge.security;

import java.util.Collections;
import java.util.Set;

public class ApiKey {
    public final String userId;
    public final String username;
    public final String role;
    public final Set<String> restrictedProjectIds;

    public static final ApiKey MASTER = new ApiKey(null, "master", "admin", null);

    public ApiKey(String userId, String username, String role, Set<String> restrictedProjectIds) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.restrictedProjectIds = restrictedProjectIds == null || restrictedProjectIds.isEmpty()
            ? null
            : Collections.unmodifiableSet(restrictedProjectIds);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    /** Returns true if this key is restricted to specific root projects. */
    public boolean isRestricted() {
        return restrictedProjectIds != null && !restrictedProjectIds.isEmpty();
    }
}
