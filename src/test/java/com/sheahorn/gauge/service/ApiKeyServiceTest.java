package com.sheahorn.gauge.service;

import com.sheahorn.gauge.domain.ApiKey;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ApiKeyServiceTest {

    @Inject
    ApiKeyService apiKeyService;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void cleanUp() {
        em.createNativeQuery("DELETE FROM apikeys").executeUpdate();
    }

    // ---- helpers ----

    private ApiKeyService.CreateResult createKey(String userId, String name) {
        return apiKeyService.create(userId, name, null);
    }

    // ---- 0: create — returns CreateResult with rawKey and persisted ApiKey ----

    @Test
    @Transactional
    void testCreateApiKey() {
        ApiKeyService.CreateResult result = apiKeyService.create("USER001", "My API Key", null);
        assertNotNull(result);
        assertNotNull(result.apiKey);
        assertNotNull(result.rawKey);
        assertEquals("USER001", result.apiKey.userId);
        assertEquals("My API Key", result.apiKey.name);
        assertTrue(result.rawKey.startsWith("atk-"));
    }

    // ---- 1: create — rawKey format is 'atk-' + UUID ----

    @Test
    @Transactional
    void testRawKeyFormat() {
        ApiKeyService.CreateResult result = createKey("USER001", "Key");
        assertTrue(result.rawKey.startsWith("atk-"));
        // After "atk-" should be a UUID (36 chars with dashes)
        String uuidPart = result.rawKey.substring(4);
        assertEquals(36, uuidPart.length());
        assertTrue(uuidPart.contains("-"));
    }

    // ---- 2: create — stored keyHash is SHA-256 of (rawKey + pepper), not raw key ----

    @Test
    @Transactional
    void testKeyHashDoesNotContainRawKey() {
        ApiKeyService.CreateResult result = createKey("USER001", "Test");
        assertFalse(result.apiKey.keyHash.contains(result.rawKey));
    }

    // ---- 3: hashKey — deterministic, same input = same hash ----

    @Test
    void testHashKeyDeterministic() {
        String hash1 = apiKeyService.hashKey("test-key-1");
        String hash2 = apiKeyService.hashKey("test-key-1");
        String hash3 = apiKeyService.hashKey("test-key-2");

        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
    }

    // ---- 4: hashKey — different pepper produces different hash ----

    @Test
    void testHashKeyDifferentPepperDifferentHash() {
        ApiKeyService service1 = new ApiKeyService("pepper-alpha");
        ApiKeyService service2 = new ApiKeyService("pepper-beta");

        String hash1 = service1.hashKey("same-raw-key");
        String hash2 = service2.hashKey("same-raw-key");

        assertNotEquals(hash1, hash2);
    }

    // ---- 5: findByUserId — returns keys for user, empty list for user with no keys ----

    @Test
    @Transactional
    void testFindByUserId() {
        createKey("USER001", "Key1");
        createKey("USER001", "Key2");
        createKey("USER002", "Key3");

        List<ApiKey> user1Keys = apiKeyService.findByUserId("USER001");
        assertEquals(2, user1Keys.size());

        List<ApiKey> user2Keys = apiKeyService.findByUserId("USER002");
        assertEquals(1, user2Keys.size());
    }

    @Test
    @Transactional
    void testFindByUserIdEmptyForUserWithNoKeys() {
        List<ApiKey> keys = apiKeyService.findByUserId("USER_NO_KEYS");
        assertTrue(keys.isEmpty());
    }

    // ---- 6: findByKeyHash — returns ApiKey when hash matches, null when not ----

    @Test
    @Transactional
    void testFindByKeyHash() {
        ApiKeyService.CreateResult result = createKey("USER001", "Test");
        String keyHash = apiKeyService.hashKey(result.rawKey);

        ApiKey found = apiKeyService.findByKeyHash(keyHash);
        assertNotNull(found);
        assertEquals(result.apiKey.id, found.id);
    }

    @Test
    @Transactional
    void testFindByKeyHashNotFound() {
        ApiKey found = apiKeyService.findByKeyHash("nonexistent-hash");
        assertNull(found);
    }

    // ---- 7: deleteById — deletes key belonging to user, returns true ----

    @Test
    @Transactional
    void testDeleteById() {
        ApiKeyService.CreateResult result = createKey("USER001", "To Delete");
        assertTrue(apiKeyService.deleteById(result.apiKey.id, "USER001"));
        assertNull(ApiKey.findById(result.apiKey.id));
    }

    // ---- 8: deleteById — refuses to delete key belonging to different user ----

    @Test
    @Transactional
    void testDeleteByIdWrongUser() {
        ApiKeyService.CreateResult result = createKey("USER001", "My Key");
        assertFalse(apiKeyService.deleteById(result.apiKey.id, "USER002"));
        assertNotNull(ApiKey.findById(result.apiKey.id));
    }

    // ---- 9: deleteById — returns false for nonexistent key ----

    @Test
    @Transactional
    void testDeleteByIdNotFound() {
        assertFalse(apiKeyService.deleteById("NONEXISTENT", "USER001"));
    }

    // ---- 10: create — empty/blank name → passed through as-is (no defaulting) ----
    // The service does not default empty/blank names. The resource layer may do so.

    @Test
    @Transactional
    void testCreateWithEmptyNamePassesThrough() {
        ApiKeyService.CreateResult result = apiKeyService.create("USER001", "", null);
        assertEquals("", result.apiKey.name);
    }

    @Test
    @Transactional
    void testCreateWithBlankNamePassesThrough() {
        ApiKeyService.CreateResult result = apiKeyService.create("USER001", "   ", null);
        assertEquals("   ", result.apiKey.name);
    }

    // ---- 11: hashKey — same rawKey with different pepper → different hash ----
    // Covered by testHashKeyDifferentPepperDifferentHash above.

    // ---- 12: hashKey — SHA-256 output is 64 hex chars ----

    @Test
    void testHashKeyOutputIs64HexChars() {
        String hash = apiKeyService.hashKey("test");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    // ---- 13: findByKeyHash — returns null for nonexistent hash ----
    // Covered by testFindByKeyHashNotFound above.

    // ---- 14: deleteById — nonexistent key → returns false ----
    // Covered by testDeleteByIdNotFound above.

    // ---- bonus: raw keys are unique each time ----

    @Test
    @Transactional
    void testRawKeyIsDifferentEachTime() {
        ApiKeyService.CreateResult r1 = createKey("USER001", "Key1");
        ApiKeyService.CreateResult r2 = createKey("USER001", "Key2");

        assertNotEquals(r1.rawKey, r2.rawKey);
        assertNotEquals(r1.apiKey.keyHash, r2.apiKey.keyHash);
    }
}
