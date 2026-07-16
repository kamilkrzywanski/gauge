package com.sheahorn.gauge.security;

public class ApiKey {
    public final String userId;
    public final String username;
    public final String role;

    public static final ApiKey MASTER = new ApiKey(null, "master", "admin");

    public ApiKey(String userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
