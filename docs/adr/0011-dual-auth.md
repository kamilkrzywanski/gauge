# ADR 11: Dual Authentication — Session for UI, Bearer for API

## Status
Accepted

## Context
Gauge is used by a human (via browser) and an LLM daemon (via REST API) simultaneously. These two consumers have fundamentally different auth needs: the human logs in once and browses pages; the daemon makes stateless API calls. A single auth mechanism would force awkward compromises on one side.

## Decision
**Two independent authentication channels**, separated by path:

- **UI paths** (`/`, `/ui/*`, `/login.html`): Quarkus form-based authentication with session cookies
- **API paths** (`/api/*`): Bearer token authentication via `SecurityFilter`

## Consequences
- `SecurityFilter` is a JAX-RS `ContainerRequestFilter` that only activates on `/api/*` — UI paths pass through untouched
- If a session-authenticated user hits `/api/*`, the session principal is respected and the Bearer check is skipped
- Bearer tokens resolve through `ApiKeyResolver`: master key (config property) or user-created API keys (SHA-256 hashed, ADR 6)
- The daemon never needs a session cookie; the human never needs to manage Bearer tokens in the browser
- `GaugePageResource` reads `SecurityContext` for `isAdmin()` checks on UI pages
- Logout clears the session cookie (`gauge_session`) without affecting API key validity
- No cross-contamination: a compromised session doesn't leak API keys, and a leaked API key doesn't hijack the UI session
