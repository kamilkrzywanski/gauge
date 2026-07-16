# ADR 3: Singular Tenant Architecture

## Status
Accepted

## Context
Gauge is an issue tracker designed for a specific use case: a single human and a single LLM daemon collaborating. It is not intended to serve massive multitenancy or agentic swarms.

## Decision
**Singular tenant** — the application assumes exactly two consumers: one human user and one LLM daemon. No tenant isolation, no multi-user scaling concerns.

## Consequences
- H2 file-based database is sufficient — no need for a multi-tenant DB or connection pooling at scale
- Admin auto-seed on startup (`AuthStartupBean`) creates a single admin user; additional users are incidental
- API key system supports one master key (`gauge.api-key.access`) for the daemon plus optional user-created keys
- User favorites system is per-username, not per-tenant
- No tenant-ID columns, no row-level security, no tenant-aware queries
- Simplifies the entire stack: auth, data model, deployment
