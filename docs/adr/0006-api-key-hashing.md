# ADR 6: API Key Hashing with Pepper + SHA-256

## Status
Accepted

## Context
Gauge exposes a REST API consumed by an LLM daemon over SSL. API keys need to be stored securely — if the H2 database file is compromised, raw keys should not be recoverable. The security model is intentionally minimal: with the frontend disabled via properties, security boils down to changing the pepper and initial credentials.

## Decision
API keys are hashed with **SHA-256 using a configurable pepper** (`gauge.api-key.pepper`) before storage. A static master key (`gauge.api-key.access`) also exists for daemon access.

## Consequences
- `ApiKeyService.hashKey()` computes `SHA-256(rawKey + pepper)` — raw key is never persisted
- Raw key is returned only once at creation time (prefixed with `atk-`)
- Master key is resolved directly in `ApiKeyResolver` without hashing — it's a config property, not a DB row
- `ApiKey` (security package) is a runtime principal, separate from `domain.ApiKey` (the JPA entity)
- Bcrypt is used for user passwords (via Elytron), SHA-256 for API keys — different tradeoffs: passwords need slow hashing, API keys are high-entropy random strings
- If the frontend is disabled, the entire security surface reduces to: change the pepper, change the master key, change the admin password
