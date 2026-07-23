package com.sheahorn.gauge.security;

import com.sheahorn.gauge.domain.User;
import com.sheahorn.gauge.service.ApiKeyService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ApiKeyResolver {

    private final String accessKey;

    @Inject
    ApiKeyService apiKeyService;

    public ApiKeyResolver(
        @ConfigProperty(name = "gauge.api-key.access") String accessKey
    ) {
        this.accessKey = accessKey;
    }

    public Optional<ApiKey> resolve(String providedKey) {
        if (providedKey == null || providedKey.isBlank()) {
            return Optional.empty();
        }
        // Static config master key
        if (providedKey.equals(accessKey)) {
            return Optional.of(ApiKey.MASTER);
        }
        // User-created API keys (hashed)
        String keyHash = apiKeyService.hashKey(providedKey);
        com.sheahorn.gauge.domain.ApiKey entity = apiKeyService.findByKeyHash(keyHash);
        if (entity != null) {
            User user = User.findById(entity.userId);
            if (user != null) {
                Set<String> restrictedProjectIds = parseRestrictedProjectIds(entity.restrictedProjectIds);
                return Optional.of(new ApiKey(user.id, user.username, user.role, restrictedProjectIds));
            }
        }
        return Optional.empty();
    }

    private Set<String> parseRestrictedProjectIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    }
}
