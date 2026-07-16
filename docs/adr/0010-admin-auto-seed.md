# ADR 10: Admin Auto-Seed on Startup

## Status
Accepted

## Context
Gauge is a singular-tenant application (ADR 3) with no multi-user onboarding flow. The first admin user must exist for the human to log in and configure the system. A manual setup wizard or SQL script would add friction to the "one-click run" ethos (ADR 9).

## Decision
On startup, **automatically create a default admin user** if none exists with the configured username. Controlled by config properties.

## Consequences
- `AuthStartupBean` runs at `@Startup` via `@PostConstruct`, wrapped in `QuarkusTransaction.run()`
- Config properties: `gauge.auth.admin.username` (default: `admin`), `gauge.auth.admin.password` (default: `admin`), `gauge.auth.seed-on-startup` (default: `true`)
- Password is Bcrypt-hashed before storage
- If `seed-on-startup` is `false`, the check is skipped entirely — for production or when the admin already exists
- Idempotent: if a user with the configured username already exists, nothing happens
- Eliminates the need for a setup wizard, SQL init scripts, or first-run UI flow
- The human is expected to change the default password immediately after first login
