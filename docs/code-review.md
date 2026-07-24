# Code Review — Gauge

**Date:** 2026-07-24  
**Scope:** Full codebase (`main`, clean working tree)  
**Issue counts:** 5 bugs · 5 suggestions · 2 nits

---

## Summary

Gauge is a focused, well-structured singular-tenant Quarkus tracker with a clear dual-auth model, deliberate cascade/removal-lock semantics, and solid unit coverage of `ProjectAccessGuard` and cascade services. The new restricted API-key feature is wired through most project/issue/tasklist/task endpoints, but it is incomplete: restricted keys can mint unrestricted keys (privilege escalation), and `GET /api/projects/analysis` returns the full tree with no filtering. Docker sample config still ships `change-me` values that `MasterKeyGuard` rejects, so the docker path fails startup as-is. Fit for local singular-tenant use after fixing the restriction gaps and secrets; not production-ready for any network-exposed multi-key isolation story until those are closed.

### What looks solid

- Clear singular-tenant design and ADRs
- Dual auth (session + Bearer) with master-key startup guard
- `ProjectAccessGuard` unit tests and cascade/removal-lock model
- Readable layering (resource → service → repository → domain)

### Top issues (quick scan)

| Severity | Location | Finding |
|----------|----------|---------|
| bug | `ApiKeyResource` | Restricted keys can mint unrestricted keys |
| bug | `ProjectResource` analysis | Full tree leaked to restricted keys |
| bug | `ProjectResource` cascade delete | Locked descendant → 404 instead of 409 |
| bug | Docker sample config | `change-me` secrets fail `MasterKeyGuard` |
| bug | Entity create/move services | No parent existence validation |

---

## Issues

### Issue 1 — Severity: bug

- **File:** `src/main/java/com/sheahorn/gauge/resource/ApiKeyResource.java:51-75`
- **Description:** Any authenticated caller (including a project-restricted API key) can `POST /api/apikeys` with no `restrictedProjectIds` and receive a new unrestricted key for the same user. That bypasses the entire restricted-project model. `ProjectAccessGuard` is injected on this resource but never used; create also does not force new scopes to be a subset of the caller's scopes. Restricted keys resolved from admin users still inherit `role=admin` (`ApiKeyResolver` copies `user.role`), so they additionally retain `@RolesAllowed("admin")` powers (users, ID migration, removal locks).
- **Suggestion:** Reject key create/list/delete when `CurrentApiKey.get()` is restricted (or require session/master only). If restricted callers may mint keys, require non-empty `restrictedProjectIds` ⊆ caller's set and strip admin elevation for restricted principals. Validate project IDs exist and are roots.
- **Status:** open

### Issue 2 — Severity: bug

- **File:** `src/main/java/com/sheahorn/gauge/resource/ProjectResource.java:240-242`
- **Description:** `GET /api/projects/analysis` calls `analysisService.analyzeAll()` with no `ProjectAccessGuard` filter. A restricted key receives counts, bubbled priorities, and existence signals for every project in the database—directly contradicting list/get filtering on `/api/projects` and descendants.
- **Suggestion:** Filter the result map to project IDs where `accessGuard.canAccessProject(id)` is true (or add a scoped analyze API). Add a REST test that proves a restricted key does not see out-of-scope project analysis.
- **Status:** open

### Issue 3 — Severity: bug

- **File:** `src/main/java/com/sheahorn/gauge/resource/ProjectResource.java:264-268`
- **Description:** When cascade delete fails because a descendant is locked, `ProjectService.cascadeDelete` returns `false` (see service tests around locked descendants). The resource maps that `false` to **404 NOT_FOUND**, not **409 LOCKED**, even though the target project exists. Callers (especially LLM clients) get a misleading "missing" response and no `LOCKED` error body. ADR 8 documents locked-descendant refusal but the HTTP contract only handles lock on the target itself (lines 254–258).
- **Suggestion:** Distinguish "not found", "self locked", and "descendant locked" in the service (e.g. enum/result type) and return 409 with `error: LOCKED` when any descendant is locked.
- **Status:** open

### Issue 4 — Severity: bug

- **File:** `docker/docker.application.properties:6-13`
- **Description:** Docker sample config still sets `gauge.api-key.access=change-me` and `gauge.api-key.pepper=change-me`. `MasterKeyGuard` fatally refuses those defaults at startup, so `docker/setup.sh` / compose will not yield a running app without manual secret edits. Dockerfile copies this file as the runtime override.
- **Suggestion:** Generate secrets in `setup.sh` (or require env overrides), write them into the docker properties, and document the required values. Never ship startable images with literal `change-me`.
- **Status:** open

### Issue 5 — Severity: bug

- **File:** `src/main/java/com/sheahorn/gauge/service/IssueService.java:32-35`
- **Description:** `create` / `moveToProject` (and similarly project create/reparent parent IDs, tasklist/task creates) do not verify that referenced parent entities exist. There are no JPA FKs—only string IDs—so unrestricted keys (and session auth) can create orphan issues, broken parent chains, and tasklists/tasks hanging off missing parents. That poisons search, analysis, cascade, and `ProjectAccessGuard` root walks (`findRootProjectId` returns the missing id when a parent is gone).
- **Suggestion:** Validate parent existence in services (return 404/400 from resources). Prefer real FK constraints or explicit repository existence checks on every create/move/reparent.
- **Status:** open

### Issue 6 — Severity: suggestion

- **File:** `src/test/java/com/sheahorn/gauge/resource/ApiKeyResourceTest.java:16-19`
- **Description:** Auth is disabled in the test profile (`src/test/resources/application.properties`). Resource tests never exercise Bearer resolution, restricted-key 403s, analysis filtering, or key-minting escalation. `ProjectAccessGuardTest` is thorough in isolation, but the incomplete REST wiring (analysis, apikeys) would not have been caught by current tests. `SecurityFilterTest` even documents that invalid tokens still return 200 under the test profile.
- **Suggestion:** Add a test profile (or `@QuarkusTestResource` / integration suite) with security enabled and end-to-end cases: restricted key denied on foreign projects; analysis filtered; restricted key cannot create unrestricted keys; cascade locked-descendant → 409.
- **Status:** open

### Issue 7 — Severity: suggestion

- **File:** `src/main/java/com/sheahorn/gauge/service/TasklistService.java:133-167`
- **Description:** Decomposition is validated on link create/update, but task/tasklist delete paths do not clear or block `decomposesTaskId` references pointing at deleted tasks. Cascades leave dangling decomposition links that still serialize to clients until someone tries to re-validate.
- **Suggestion:** On task delete (and cascade), null out `tasklists.decomposesTaskId` where it matches the deleted task(s), or refuse delete while references exist.
- **Status:** open

### Issue 8 — Severity: suggestion

- **File:** `src/test/java/com/sheahorn/gauge/resource/TasklistResourceTest.java:138`
- **Description:** Domain validation failures (`IllegalArgumentException` from reparent cycles, bad `decomposesTaskId`, bad reorder cross-tasklist IDs) surface as HTTP 500. The test suite encodes that expectation. LLM clients get opaque 500s instead of 400 with the validation message.
- **Suggestion:** Add an `ExceptionMapper<IllegalArgumentException>` → 400 with `{error, message}`.
- **Status:** open

### Issue 9 — Severity: suggestion

- **File:** `src/main/java/com/sheahorn/gauge/resource/FavoritesResource.java:35-44`
- **Description:** Favorites add/list/remove never call `ProjectAccessGuard`. A restricted key can bookmark arbitrary project IDs and list out-of-scope IDs previously favorited under the same username. Low impact for singular-tenant, but inconsistent with the restriction model.
- **Suggestion:** Check `canAccessProject` on add; filter list results through the guard for restricted keys.
- **Status:** open

### Issue 10 — Severity: suggestion

- **File:** `src/main/resources/application.properties:81-83`
- **Description:** `/q/openapi` is `permit` (unauthenticated) while Swagger UI is authenticated. On any host binding beyond loopback this advertises the full API surface without credentials.
- **Suggestion:** Require authentication for OpenAPI when exposing beyond localhost, or document that LLM discovery intentionally leaves the spec public and bind only to loopback/VPN.
- **Status:** open

### Issue 11 — Severity: nit

- **File:** `src/main/java/com/sheahorn/gauge/resource/UserResource.java:57-59`
- **Description:** Creating a user with omitted `role` defaults to `"admin"`, not a least-privilege role. Easy to mint extra admins by accident when the second human/user is added.
- **Suggestion:** Default to a non-admin role (e.g. `"user"`) or require an explicit role field.
- **Status:** open

### Issue 12 — Severity: nit

- **File:** `src/main/java/com/sheahorn/gauge/service/TaskService.java:76-99`
- **Description:** `reorder` skips missing task IDs and does not reassign ordinals for tasks omitted from the list, allowing duplicate/gapped ordinals under concurrent or partial payloads.
- **Suggestion:** Require a complete permutation of the tasklist's task IDs (400 if not), or re-normalize all ordinals after applying the requested order.
- **Status:** open
