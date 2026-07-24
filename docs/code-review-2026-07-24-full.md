# Gauge — Full Codebase Review (2026-07-24)

**Scope:** Entire codebase at `main` (clean tree) — ~3,900 LOC of Java across 70 files
plus 14 Thymeleaf/JS templates, build, Docker, and test suites. Quarkus 3.15,
Panache/Hibernate on H2, Thymeleaf server pages with hand-built client-side JS,
JAX-RS API, dual auth (form session + `Bearer` API key), with a project-restricted
API-key feature.

**Method:** Independent review conducted fresh from source (not derived from the
earlier `docs/code-review*.md` files). Five parallel dimension passes — auth/authz,
persistence/concurrency, web/XSS, API correctness, build/ops/tests — with the headline
findings hand-verified against the code by the reviewer.

---

## Verdict

The skeleton is sound: parameterized Panache queries (no SQL injection), bcrypt for
user passwords, SHA-256+pepper for stored API keys, `@JsonIgnore` on secrets,
`@Transactional` on every write, a coherent `ProjectAccessGuard` with real unit tests,
clean resource→service→repository→domain layering, and a genuine startup guard against
default master secrets.

But the release-blocking problems are structural, not cosmetic:

1. **The restricted-API-key feature — the app's headline capability — is not a
   security boundary.** A restricted key can mint an unrestricted one, keeps its owner's
   admin role, and leaks all-project analytics. (§C1)
2. **Two zero-click stored XSS holes** in the project and issue pages. (§C2)
3. **The shipped Docker image cannot boot** and, if patched to boot, is exploitable out
   of the box. (§C3)
4. **The test suite runs with authentication disabled** and asserts several insecure /
   incorrect behaviors as expected — so the most important guarantees have negative
   coverage. (§H)

Fit for local, trusted, single-user use today. **Not** fit for any network-exposed or
multi-key-isolation deployment until §C and §H are closed.

---

## 🔴 Critical

### C1 — The restricted-key feature is bypassable three ways

The 2.1.0 restricted-key feature is meant to hand an agent a key scoped to a project
subtree. It fails as a boundary on every axis:

1. **A restricted key can mint an unrestricted key for itself.**
   `ApiKeyResource.create` (`resource/ApiKeyResource.java:51`) has **no**
   `@RolesAllowed` and **no** check on the caller's own restriction. `restrictedProjectIds`
   comes straight from the request body and may be omitted (= unrestricted). The injected
   `ProjectAccessGuard` is never called. Exploit: hand an agent a key scoped to project X →
   it calls `POST /api/apikeys {"name":"x"}` → receives a full-access key. `GET` and
   `DELETE /{id}` on the same resource are equally unguarded (enumerate/delete the user's
   other keys).

2. **A restricted key keeps its owner's role.** `ApiKeyResolver.resolve`
   (`security/ApiKeyResolver.java:44`) copies `user.role` into the resolved key, and
   `restrictedProjectIds` is consulted *only* by `ProjectAccessGuard` — never by
   `isAdmin()`. A "restricted" key owned by an admin still returns
   `isUserInRole("admin") == true` (`security/SecurityFilter.java:83`). Since
   `ProjectAccessGuard` guards only the project/issue/tasklist/task resources, that key can
   still hit `POST /api/users` to create a fresh admin, `POST /api/admin/*`
   (`AdminResource`, destructive ID migration / removal locks), etc.

3. **`GET /api/projects/analysis` is unguarded** (`resource/ProjectResource.java:240`).
   It calls `analysisService.analyzeAll()` with no access-guard filter — the only project
   endpoint that skips the guard — returning counts and bubbled priorities for *every*
   project to a restricted key.

**Fix direction:** Give API keys a privilege scope decoupled from the user role; forbid
API-key principals from managing keys (allow session/master only, or require a new key's
scope ⊆ caller's scope and never broader); strip admin elevation from restricted keys; and
enforce access on **every** resource, including analysis, favorites, users, and admin.

### C2 — Two zero-click stored XSS (session-riding)

Root cause: the client-side `esc()` helper, duplicated verbatim in ~11 templates
(`layout.html:280` and copies), escapes `& < >` but **not** `"` or `'`:

```js
function esc(s){ return s ? s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') : ''; }
```

Values are then concatenated into double-quoted HTML attributes:

- **Project name** → `projectDetail.html:262`, injected into `value="…"`.
- **Issue title** → `issueDetail.html:103`, identical pattern.

Because `<`/`>` are escaped no new tag is injected, but the quote-breakout adds new
attributes to the same tag; a duplicate `style` (HTML honors the first) unhides the
"hidden" input and an `autofocus onfocus="…"` fires on page load. Payload for either:

```
" autofocus onfocus="fetch('/api/users',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'pwn',password:'pwn',role:'admin'})})" style="display:block" x="
```

Create/patch of project name and issue title carry no `@RolesAllowed`, so any
authenticated principal can plant the payload; it then runs in the victim's session (e.g.
an admin viewing the issue) and calls `/api/*` as them. The `gauge_session` cookie is
`HttpOnly` so the cookie itself can't be read — but the authenticated API calls are the
real damage. There is **no CSP** anywhere as a backstop.

A third, narrower hole: the admin Users page builds
`onclick="showChangePassword('…','<username>')"` (`users.html:58`) — a JS-string context
that quote-encoding alone won't fix.

**Fix direction:** Stop hand-building HTML for user data — assign via DOM properties
(`input.value = issue.title`), which is context-safe. At minimum, make `esc()` also encode
`"` and `'`. Convert the `onclick` builder to `addEventListener` + `data-*` (the pattern
already used in `tasklistDetail.html`). Add a `Content-Security-Policy` header.

### C3 — The shipped Docker image cannot boot; once patched, it's exploitable

Two independent boot blockers in the shipped Docker config, plus baked-in secrets:

- **`change-me` trips the startup guard.** `docker/docker.application.properties` sets
  `gauge.api-key.access=change-me` and `gauge.api-key.pepper=change-me`; `MasterKeyGuard`
  (`config/MasterKeyGuard.java:25`) throws `IllegalStateException` on exactly that value.
  With `restart: unless-stopped`, `docker compose up` crash-loops forever.
- **No schema.** The Docker override never sets
  `quarkus.hibernate-orm.database.generation`, so it inherits the packaged default `none`
  (`application.properties:24`) — even after fixing the secret, the app boots against an
  empty H2 file with no tables and every query fails.
- **Baked-in `admin`/`admin`.** The same file hardcodes the seeded admin credentials into
  the image layer with no env-var override path. If the app is patched to boot, the
  container ships a default admin login. There is *no* startup guard on the admin password
  (unlike the master key), and `AuthStartupBean` seeds `admin`/`admin` on first run.

**Fix direction:** Generate/inject secrets via env vars in `setup.sh` (never bake
literals), add `database.generation=update` to the Docker properties, and extend
`MasterKeyGuard` to reject the default admin password too.

---

## 🟠 High

- **`MasterKeyGuard` is a single-string check, defeated by `change-me2`.** It rejects only
  the literal `"change-me"`; the repo's own `application.properties:13` ships
  `access=change-me2` / `pepper=change-me2`, which sail through. A committed, guessable
  master key grants unrestricted admin. Fix: reject any `change-me*` prefix and enforce a
  minimum-entropy/length policy.
- **Task reorder is a silent no-op.** `TaskRepository.findByTasklistId`
  (`repository/TaskRepository.java:38`) does `Task.list("tasklistId", …)` with **no
  `ORDER BY ordinal`**. `TaskService.reorder` faithfully rewrites ordinals and returns 200,
  but every read path (`TasklistResource:78,92`) returns tasks in PK order — the feature has
  no observable effect. Fix: `order by ordinal` in the query.
- **No optimistic locking → lost updates.** No entity declares `@Version`; every mutator is
  a read-modify-write that rewrites all columns (`IssueService.updateStatus/updatePriority`,
  every `patch`/`reparent`/`moveToProject`). Concurrent `updateStatus` + `updatePriority` on
  one issue: last commit silently discards the other field. Fix: add `@Version`, or do
  targeted column updates.
- **No parent/FK existence validation → orphans.** Relationship columns are plain
  `VARCHAR` with no FK constraints, and services never verify parents exist:
  `IssueService.create`/`moveToProject`, `TasklistService.create`, `TaskService.create`,
  and `ProjectService.reparent` (validates cycles but not that `newParentId` exists). You
  can create an issue under a bogus project (invisible in every view) or reparent a project
  under a deleted id (neither root nor reachable). Fix: validate existence on every
  create/move/reparent, ideally with real FK constraints.
- **No `ExceptionMapper`; validation failures surface as HTTP 500.** There is no
  `@Provider`/`ExceptionMapper` anywhere. Every escaping exception becomes a generic 500
  with a non-JSON body. This turns a whole class of should-be-4xx into 500s:
  - `IllegalArgumentException` from `ProjectService.reparent` (cycle), `TasklistService`
    decomposition validation, `TaskService.reorder` (foreign taskId) → 500 instead of
    400/409.
  - **Null request body** → first `body.xxx()` NPEs → 500 (every POST/PATCH taking a
    record, e.g. `ProjectResource.java:61`).
  - **Null enum field** (`{}` or `{"status":null}`) → domain constructor calls
    `status.name()` → NPE → 500 (`IssueResource`, `TasklistResource`, `TaskResource` status/
    priority endpoints).
  - Unchecked `Map<String,Object>` casts in `ApiKeyResource`/`UserResource`
    (`{"name":123}`) → `ClassCastException` → 500.
  
  Fix: add `ExceptionMapper` for `IllegalArgumentException`→400, `NullPointerException`/
  deserialization→400, and null-guard bodies. This single change corrects most of the
  medium/low API findings at once.
- **Test suite runs with auth disabled and enshrines insecure behavior.** The test profile
  (`src/test/resources/application.properties`) sets
  `quarkus.security.jdbc.enabled=false`, form auth off, basic off — so `SecurityFilter`
  never enforces the Bearer gate. `SecurityFilterTest` asserts that an **invalid** token
  returns **200** and that a request with **no auth header** returns **200** (comment:
  "filter should block but doesn't in test mode"). Every 200/201 in the resource tests is
  achieved with zero credentials; deleting the Bearer gate entirely would keep the suite
  green. `ApiKeyResourceTest`'s "empty name" cases assert 401 (auth short-circuit), never
  reaching the validation they claim to cover; `ProjectResourceTest.deleteLocked` skips
  setting the lock and deletes an unlocked project; `TasklistResourceTest:138` asserts 500
  for a bad request. Fix: add integration tests with security enabled covering
  valid/invalid/missing tokens (→401), restricted-key 403s, analysis filtering, key-minting
  escalation, and locked-descendant→409.
- **New users default to the `admin` role.** `UserResource.create`
  (`resource/UserResource.java:57`) defaults an omitted `role` to `"admin"`, with no
  allowlist validation. Endpoint is admin-only so it's a footgun rather than an outsider
  escape, but a client omitting `role` silently mints admins. Fix: default to `user` +
  validate against `UserRole`.

---

## 🟡 Medium

- **Parent-chain walks can infinite-loop / StackOverflow on a cycle.** `findRootProjectId`
  (`security/ProjectAccessGuard.java:90` — runs on *every* restricted request),
  `ProjectService.getAncestors`/`isDescendantOf`, and `ProjectAnalysisService.computeRecursive`
  all walk the graph with no visited-set or depth cap. A cycle (reachable via the
  missing-validation gap above, a manual DB edit, or migration) hangs a request thread or
  blows the stack — permanent DoS. Notably the cycle-*detection* in `reparent` is itself one
  of the walks that would hang. Fix: track visited ids / cap depth in every walk.
- **Password change requires no current-password check.** `PATCH /api/users/{id}/password`
  (`UserResource.java:86`, `UserService.java:47`) sets a new password with no re-auth and no
  knowledge of the old one. A leaked/limited API key for a user can rotate that user's
  password and lock the owner out. Fix: require + verify the current password; consider
  blocking password change when authenticated via API key.
- **No CSRF protection.** `SecurityFilter` passes any session-authenticated request straight
  through (`security/SecurityFilter.java:38`), and the session cookie has no configured
  `SameSite`. A logged-in admin visiting a malicious page can be forced into `POST
  /api/users`, `POST /api/apikeys`, or destructive deletes via ambient-cookie cross-site
  requests. Fix: CSRF token or required custom header + `SameSite=strict`.
- **Session cookie not hardened; cleartext exposure.** No
  `quarkus.http.auth.form.cookie-secure` / explicit `SameSite`; Docker binds `0.0.0.0:8087`
  with no TLS, so the 24h session cookie travels in cleartext. Fix: `cookie-secure=true`,
  explicit `SameSite`, TLS in front, shorter timeout.
- **404-vs-403 divergence / existence oracle.** For a nonexistent entity, an unrestricted
  key gets 404 (guard passes, then `findById` empty) while a restricted key gets 403 (guard
  returns false on the null lookup). Same URL, status depends on caller — inconsistent
  semantics and a minor existence oracle. Fix: look up first (404 if absent), then authorize.
- **Ordinal / favorites check-then-insert races.** `TaskService.create` computes
  `ordinal = findByTasklistId(...).size()` — concurrent creates get duplicate ordinals (no
  unique constraint on `(tasklistId, ordinal)`). `FavoritesService.add` lists-then-inserts —
  concurrent adds throw on the composite PK instead of being idempotent. Fix: `MAX(ordinal)+1`
  under lock + unique constraint; `INSERT … ON CONFLICT DO NOTHING` / catch the violation.
- **Deletes leave dangling references.** `IssueService`/`TasklistService`/`TaskService`/
  `ProjectService` single deletes leave orphaned children; task delete leaves
  `tasklists.decomposesTaskId` pointing at the deleted task; `cascadeDelete` never removes
  `user_favorites` rows for the deleted project. Fix: cascade or block, and clean up
  `decomposesTaskId` / favorites.
- **Unbounded search/list.** `search()` in every service and `analyzeAll()` load whole
  tables and filter in-memory; no pagination, no DB-side filtering. Grows linearly with row
  count. (`analyzeAll` is also non-`@Transactional`, so its four reads aren't a consistent
  snapshot.) Fix: push filters into queries, paginate.
- **nginx ships without baseline hardening.** `docker/nginx.conf` terminates TLS but sets no
  HSTS / `X-Content-Type-Options` / `X-Frame-Options` / CSP, no `limit_req`, and no port-80→
  443 redirect; the header comment says port 8080 while it proxies `:8087`. For an app framed
  for "open network exposure" behind a home-grown Bearer filter, this is thin. Fix: add
  security headers + rate limiting + redirect.

---

## 🟢 Low / cleanup

- **Master key compared non-constant-time.** `ApiKeyResolver.java:34` uses
  `providedKey.equals(accessKey)` (plaintext) while user keys are hashed. Timing side-channel
  on the static key. Fix: hash + `MessageDigest.isEqual`.
- **`active` user flag is dead access control.** `User.active` is always `true` and consulted
  nowhere (not in the principal-query, not in `ApiKeyResolver`), though `login-failed.html`
  has a "deleted" reason implying disable was intended. Fix: `WHERE … AND active = true` and
  check it in the resolver.
- **Unauthenticated OpenAPI spec.** `/q/openapi` is `permit` while Swagger UI is
  authenticated, and `ApiKeyResource`/`AdminResource` aren't excluded from the scan — the full
  admin/key API surface is disclosed without credentials. Fix: authenticate it or bind to
  loopback.
- **Invalid filter/sort tokens silently ignored.** `ProjectResource.listIssues` falls back to
  a default sort and drops unknown `?status=`/`?priority=` tokens instead of 400 — a
  fully-invalid filter returns *unfiltered* data. Fix: reject unknown tokens.
- **Enum-as-String rows can become unreadable.** `Issue/Task/Tasklist` getters call
  `valueOf(...)`; any non-matching DB value throws on read and breaks listing the whole
  tasklist. Fix: tolerant converter / validate on write.
- **`generation=none` (Docker) / `update` (prod) with H2 file DB.** No migration tool; schema
  drift and no rollback path. `FavoritesTableStartup` swallows DDL failures and continues, so
  favorites fail later at runtime rather than at boot. Fix: adopt Flyway/Liquibase, fail fast.
- **Dead-code DTOs.** `CreateUserRequest`/`UpdateUserRequest` are unreferenced;
  `UserResource` uses raw `Map<String,Object>`. Wire the records in (also fixes the unchecked
  casts) or delete them.
- **Favorites weak semantics.** `add` returns 201 even when the favorite already exists and
  never validates the project exists / is accessible.
- **Toolchain / version drift.** `pom.xml` targets Java 17 but `Dockerfile` runs
  `temurin:21-jre`; Thymeleaf is hand-pinned to `3.1.2.RELEASE` outside the Quarkus BOM;
  `runner.sh` hardcodes `gauge-2.1.0-runner.jar`; `docker-compose.yml` uses the obsolete
  `version:` key. Quarkus 3.15.1 / Thymeleaf 3.1.2 are 2024-era — worth a CVE audit.

---

## Priorities

Do not ship until these two clusters are fixed:

1. **The restricted-key escape** (§C1 — all three legs) and **the stored XSS** (§C2).
2. **A bootable, non-default Docker image** (§C3).

Highest-leverage first moves (small, low-risk, high-value):

- Quote-encode in `esc()` (or switch to DOM-property assignment) — closes both XSS holes.
- Guard `ApiKeyResource` so API-key principals cannot manage keys.
- Filter `/api/projects/analysis` through `ProjectAccessGuard`.
- Add `ExceptionMapper`s (`IllegalArgumentException`→400, NPE/deserialization→400) + null-body
  guards — flips a whole class of 500s to correct 4xx.
- Fix the test profile to run *with* auth enabled, then add the restricted-key end-to-end
  tests. Without this, none of the above fixes are actually protected against regression.

---

*Review method: five parallel dimension reviewers (auth, persistence, web/XSS, API
correctness, build/ops+tests) reading source directly, with critical findings hand-verified
against the code. Independent of the earlier `docs/code-review*.md` files.*
