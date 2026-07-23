package com.sheahorn.gauge.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyTest {

    @Test
    void testMasterKeyIsAdmin() {
        assertTrue(ApiKey.MASTER.isAdmin());
    }

    @Test
    void testMasterKeyIsNotRestricted() {
        assertFalse(ApiKey.MASTER.isRestricted());
    }

    @Test
    void testMasterKeyHasNullRestrictedProjectIds() {
        assertNull(ApiKey.MASTER.restrictedProjectIds);
    }

    @Test
    void testUserKeyIsNotAdmin() {
        ApiKey key = new ApiKey("user1", "testuser", "user", null);
        assertFalse(key.isAdmin());
    }

    @Test
    void testAdminKeyIsAdmin() {
        ApiKey key = new ApiKey("user1", "adminuser", "admin", null);
        assertTrue(key.isAdmin());
    }

    @Test
    void testNullRestrictedProjectIdsIsNotRestricted() {
        ApiKey key = new ApiKey("user1", "testuser", "user", null);
        assertFalse(key.isRestricted());
        assertNull(key.restrictedProjectIds);
    }

    @Test
    void testEmptyRestrictedProjectIdsIsNotRestricted() {
        ApiKey key = new ApiKey("user1", "testuser", "user", Set.of());
        assertFalse(key.isRestricted());
        assertNull(key.restrictedProjectIds);
    }

    @Test
    void testSingleRestrictedProjectIdIsRestricted() {
        ApiKey key = new ApiKey("user1", "testuser", "user", Set.of("ROOT0001"));
        assertTrue(key.isRestricted());
        assertNotNull(key.restrictedProjectIds);
        assertEquals(1, key.restrictedProjectIds.size());
        assertTrue(key.restrictedProjectIds.contains("ROOT0001"));
    }

    @Test
    void testMultipleRestrictedProjectIdsIsRestricted() {
        ApiKey key = new ApiKey("user1", "testuser", "user", Set.of("ROOT0001", "ROOT0002"));
        assertTrue(key.isRestricted());
        assertEquals(2, key.restrictedProjectIds.size());
        assertTrue(key.restrictedProjectIds.contains("ROOT0001"));
        assertTrue(key.restrictedProjectIds.contains("ROOT0002"));
    }

    @Test
    void testRestrictedProjectIdsIsUnmodifiable() {
        ApiKey key = new ApiKey("user1", "testuser", "user", Set.of("ROOT0001"));
        assertThrows(UnsupportedOperationException.class, () ->
            key.restrictedProjectIds.add("ROOT0002"));
    }

    @Test
    void testUserIdAndUsername() {
        ApiKey key = new ApiKey("user1", "testuser", "user", null);
        assertEquals("user1", key.userId);
        assertEquals("testuser", key.username);
        assertEquals("user", key.role);
    }
}
