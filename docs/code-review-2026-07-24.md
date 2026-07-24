# Gauge — Whole-Codebase Review (2026-07-24)

**Scope:** ~3,900 LOC Quarkus 3.15 app (Panache/Hibernate + H2, Thymeleaf UI, JAX-RS
API, API-key + form-session auth). Reviewed across five dimensions: authz/auth,
persistence, web/XSS, API correctness, and build/ops.

**Headline:** The codebase is clean in the fundamentals — parameterized queries (no
SQLi), bcrypt password hashing, `@JsonIgnore` on secrets, no IDOR on key deletion,
`@Transactional` on writes. But the **recently-added restricted-API-key feature is
not actually a security boundary** — a restricted key can trivially escape its own
restriction. That, plus two stored-XSS holes, is what should block a release.

---

## 🔴 Critical — the restricted-key feature is bypassable

These defeat the entire point of the 2.1.0 feature:

1. **A restricted key can mint an unrestricted key for itself.** `ApiKeyResource`
   (`ApiKeyResource.java:51`) has *no* `@RolesAllowed` and *no* access guard, and
   `create()` takes `restrictedProjectIds` from the request body (may be omitted =
   unrestricted). Hand an agent a key scoped to project X → it calls
   `POST /api/apikeys {"name":"x"}` → gets a full-access key. `GET`/`DELETE` on that
   resource are also unguarded (enumerate/delete the user's other keys). **The
   restriction is a fence the holder removes in one call.**

2. **Restricted keys inherit the owner's role and are only fenced on project-tree
   data.** `ApiKeyResolver.java:44` copies `user.role` into the key, so an
   admin-owned "restricted" key still returns `isUserInRole("admin")==true`.
   `ProjectAccessGuard` is only invoked in the project/issue/task/tasklist resources
   — nothing guards `AdminResource`, `UserResource`, or `FavoritesResource`. So a
   "restricted" key can still `POST /api/users` (create admins), change others'
   passwords, and hit `/api/admin/*`.

3. **`GET /api/projects/analysis` is unguarded** (`ProjectResource.java:238`) — the
   only project endpoint with no `accessGuard` check; returns aggregated stats across
   *all* projects to a restricted key.

**Fix direction:** API keys need their own privilege scope decoupled from the user
role; API-key management must be forbidden to API-key principals (allow only session
auth, or forbid creating a key less-restricted than the caller); and restriction has
to be enforced on *every* resource, not just the project tree.

## 🔴 Critical — two stored XSS (zero-click, session-riding)

Root cause: the shared `esc()` JS helper (duplicated in `layout.html:280`,
`search.html`, `users.html`…) escapes `& < >` but **not quotes**. Values then land
inside double-quoted attributes:

- **Issue title** → `issueDetail.html:103`, injected into `value="…"`. Payload
  `x" autofocus onfocus="fetch('/api/apikeys',{method:'POST',…})` fires on page load
  for anyone who opens the issue.
- **Project name** → `projectDetail.html:262`, identical breakout.

Session cookie is `HttpOnly`, so the payload can't steal the cookie — but it runs in
the victim's authenticated session and calls `/api/*` as them. A low-priv user plants
a title, an admin views it → admin-level API calls.

**Fix:** make `esc()` also encode `"` and `'` (closes both, plus a latent class of
`esc(id)`-in-attribute spots). Separately, the admin Users page builds
`onclick="showChangePassword('…', '<username>')"` (`users.html:58`) — quote-encoding
won't fix JS-string context there; convert to `addEventListener` + `data-*` (the
pattern already used in `projects.html`/`tasklistDetail.html`).

## 🟠 High

- **Default `admin`/`admin` login ships live.** `MasterKeyGuard` refuses to boot on
  default pepper/access key, but there's *no* equivalent check for the seeded admin
  password (`AuthStartupBean` + `application.properties`). And
  `docker/docker.application.properties` **bakes `admin/admin` + `pepper=change-me`
  into the image** via `QUARKUS_CONFIG_LOCATIONS`, with no env-var override path — the
  shipped container is exploitable out of the box. Extend `MasterKeyGuard` to reject
  the default admin password, and externalize docker secrets to env vars.
- **Task reorder is a silent no-op.** `TaskRepository.java:37` does
  `Task.list("tasklistId", tasklistId)` with **no `ORDER BY ordinal`**. `PATCH
  …/task-order` rewrites ordinals and returns 200, but every read comes back in
  insertion order. Fix: `order by ordinal`.
- **New users default to `admin` role** (`UserResource.java:57`). Endpoint is
  admin-only so it's a footgun, not an outsider escalation — but a UI/client omitting
  `role` silently creates admins, and the value is never validated. Default to `user`
  + allowlist.

## 🟡 Medium

- **Concurrent reparent can create a parent cycle** (`ProjectService.reparent`) — no
  `@Version`/locking anywhere, so two interleaved reparents each pass the cycle check
  and jointly form `X↔Y`. Then every parent-walk (`ProjectAccessGuard.findRootProjectId`
  on *every* restricted request, `getAncestors`, analysis recursion) **loops/
  StackOverflows forever → permanent DoS**. Add optimistic locking **and** a
  visited-set/depth cap in every chain walk. (The single-request cycle loop in
  `findRootProjectId` has no guard today either.)
- **No optimistic locking → lost updates.** Load-modify-save with no version check;
  concurrent `patch(title)` + `updateStatus()` silently clobber each other.
- **No `ExceptionMapper` → 500s + stack traces** where 400/409 is intended: reparent
  cycle, reorder with foreign taskId, invalid `decomposesTaskId` all surface as 500.
- **No body/field validation → NPE 500.** Empty POST body → `body.priority()` NPEs
  (`ProjectResource.java:61` and every create/patch); missing required `title`/`name`
  → DB NOT-NULL 500 instead of 400.
- **Move/create to a nonexistent project succeeds** (`IssueResource.moveToProject`,
  `ProjectResource.createIssue`) — issue points at a bogus project and vanishes from
  all views. Verify existence.
- **CSRF / cookie hardening.** Session-authenticated `/api/*` mutations have no CSRF
  token; login cookie sets no explicit `SameSite`/`Secure` (only logout hardcodes
  `SameSite=Lax`). Set `same-site=strict` + `Secure`.
- **`IdMigrationService` leaves dangling refs** — remaps the four core tables but not
  `apikeys.restricted_project_ids` or `user_favorites.project_id`. Latent (one-time,
  guarded) but incomplete.
- **Unbounded search/list.** All `search()`/`findAll()` load the whole table and
  filter in-memory (`ProjectService`, `IssueService`, etc.); no pagination. Push
  filters into the query.

## 🟢 Low / cleanup

- Master-key check uses non-constant-time `String.equals` (`ApiKeyResolver.java:34`) —
  timing side-channel on the static key; use `MessageDigest.isEqual`.
- `TaskService` computes `ordinal = list.size()` → duplicate ordinals after a delete;
  partial reorder leaves stale ordinals. Use `max(ordinal)+1` / renumber all.
- `FavoritesService.add` check-then-insert races on the composite PK (500 instead of
  idempotent).
- `@JsonCreator` constructors (`Issue.java:50` etc.) call `.name()` on the enum arg →
  NPE 500 if `status`/`priority` omitted.
- `removalLock` accepts any string (only `"locked"` acts); invalid `?priority=`/
  `?status=` filter tokens are silently dropped instead of 400.
- `UserResource.create` uses `Map<String,Object>` with unchecked casts
  (`{"role":123}` → 500); `CreateUserRequest`/`UpdateUserRequest` DTOs are dead code.
- `FavoritesTableStartup`'s manual `CREATE TABLE` is **redundant** — the app runs with
  `generation=update`, so Hibernate already creates `user_favorites`.
- `nginx.conf` comment says port 8080 but proxies 8087; no security headers
  (HSTS/CSP) or rate limiting. `database.generation=update` on the prod H2 file DB is
  risky long-term.

---

## Priorities

The two clusters that should not ship unfixed:

1. **The restricted-key escape** (Critical findings 1–3).
2. **The stored XSS** (both HIGH template holes).

Suggested first moves (small, high-value, low-risk): `esc()` quote-encoding, and an
API-key-management guard that rejects API-key principals.

---

*Review method: four parallel dimension reviewers (auth, persistence, web/XSS, API
correctness) plus a manual build/ops pass; top findings verified against source.*
