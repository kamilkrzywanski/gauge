package com.sheahorn.gauge.security;

import com.sheahorn.gauge.domain.User;
import com.sheahorn.gauge.service.ApiKeyService;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ApiKeyResolverTest {

    @Inject
    ApiKeyResolver apiKeyResolver;

    @Inject
    ApiKeyService apiKeyService;

    @BeforeEach
    @Transactional
    void cleanUp() {
        com.sheahorn.gauge.domain.ApiKey.findAll().stream().forEach(ak -> ak.delete());
        // Clean up test users except admin
        User.findAll().stream()
            .map(u -> (User) u)
            .filter(u -> !"admin".equals(u.username))
            .forEach(u -> u.delete());
    }

    @Test
    void testResolveNullKey() {
        Optional<ApiKey> resolved = apiKeyResolver.resolve(null);
        assertFalse(resolved.isPresent());
    }

    @Test
    void testResolveBlankKey() {
        Optional<ApiKey> resolved = apiKeyResolver.resolve("   ");
        assertFalse(resolved.isPresent());
    }

    @Test
    void testResolveEmptyKey() {
        Optional<ApiKey> resolved = apiKeyResolver.resolve("");
        assertFalse(resolved.isPresent());
    }

    @Test
    void testResolveMasterKey() {
        Optional<ApiKey> resolved = apiKeyResolver.resolve("test-key");
        assertTrue(resolved.isPresent());
        assertEquals("master", resolved.get().username);
        assertTrue(resolved.get().isAdmin());
    }

    @Test
    @Transactional
    void testResolveUserApiKey() {
        // Create a user first
        User user = User.create("testuser", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        // Create an API key for the user
        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Test Key", null);

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertEquals("testuser", resolved.get().username);
        assertEquals("user", resolved.get().role);
        assertFalse(resolved.get().isAdmin());
    }

    @Test
    void testResolveInvalidKey() {
        Optional<ApiKey> resolved = apiKeyResolver.resolve("invalid-key");
        assertFalse(resolved.isPresent());
    }

    @Test
    @Transactional
    void testResolveUserKeyWithAdminRole() {
        User admin = User.create("admin2", BcryptUtil.bcryptHash("password"), "admin");
        admin.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(admin.id, "Admin Key", null);

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().isAdmin());
    }

    // ---- restrictedProjectIds parsing ----

    @Test
    @Transactional
    void testResolveUserKeyWithNullRestrictedProjectIds() {
        User user = User.create("testuser2", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", null);

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertFalse(resolved.get().isRestricted());
        assertNull(resolved.get().restrictedProjectIds);
    }

    @Test
    @Transactional
    void testResolveUserKeyWithBlankRestrictedProjectIds() {
        User user = User.create("testuser3", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", "   ");

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertFalse(resolved.get().isRestricted());
    }

    @Test
    @Transactional
    void testResolveUserKeyWithSingleRestrictedProjectId() {
        User user = User.create("testuser4", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", "ROOT0001");

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().isRestricted());
        assertEquals(1, resolved.get().restrictedProjectIds.size());
        assertTrue(resolved.get().restrictedProjectIds.contains("ROOT0001"));
    }

    @Test
    @Transactional
    void testResolveUserKeyWithMultipleRestrictedProjectIds() {
        User user = User.create("testuser5", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", "ROOT0001,ROOT0002,ROOT0003");

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().isRestricted());
        assertEquals(3, resolved.get().restrictedProjectIds.size());
        assertTrue(resolved.get().restrictedProjectIds.contains("ROOT0001"));
        assertTrue(resolved.get().restrictedProjectIds.contains("ROOT0002"));
        assertTrue(resolved.get().restrictedProjectIds.contains("ROOT0003"));
    }

    @Test
    @Transactional
    void testResolveUserKeyWithWhitespaceInRestrictedProjectIds() {
        User user = User.create("testuser6", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", " ROOT0001 ,  ROOT0002  ,ROOT0003 ");

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().isRestricted());
        assertEquals(3, resolved.get().restrictedProjectIds.size());
        assertTrue(resolved.get().restrictedProjectIds.contains("ROOT0001"));
        assertTrue(resolved.get().restrictedProjectIds.contains("ROOT0002"));
        assertTrue(resolved.get().restrictedProjectIds.contains("ROOT0003"));
    }

    @Test
    @Transactional
    void testResolveUserKeyWithTrailingCommaInRestrictedProjectIds() {
        User user = User.create("testuser7", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", "ROOT0001,ROOT0002,");

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().isRestricted());
        assertEquals(2, resolved.get().restrictedProjectIds.size());
        assertTrue(resolved.get().restrictedProjectIds.contains("ROOT0001"));
        assertTrue(resolved.get().restrictedProjectIds.contains("ROOT0002"));
    }

    @Test
    @Transactional
    void testResolveUserKeyWithEmptyRestrictedProjectIdsString() {
        User user = User.create("testuser8", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", "");

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertFalse(resolved.get().isRestricted());
    }
}
