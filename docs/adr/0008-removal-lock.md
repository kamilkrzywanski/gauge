# ADR 8: Removal Lock for Project Protection

## Status
Accepted

## Context
Gauge's project tree forms the backbone of work organization. Accidental deletion of a core project (by either the human or the LLM daemon) would be catastrophic. With singular tenancy (ADR 3), role-based permissions are unnecessary — there's no untrusted third party. The threat model is mistakes, not malice.

## Decision
Projects have a **`removalLock` field**. When set to `"locked"`, the project cannot be deleted — even with `?cascade=true`. Only admins can set or unset the lock.

## Consequences
- `removalLock` is a simple string column on the `projects` table — no separate permissions table, no ACLs
- Lock check happens before cascade check in `ProjectResource.delete()` — locked projects return `409 CONFLICT` with `LOCKED`
- `ProjectService.cascadeDelete()` also checks locks on all descendants before proceeding (non-transitive: any locked descendant blocks the whole operation)
- Setting the lock requires admin role (`PATCH /api/projects/{id}` with `removalLock` field)
- Covers both "silicon mistakes" (LLM hallucinating a DELETE) and "protein mistakes" (human clicking wrong button)
- Simpler than a full permission system — appropriate for two tenants who trust each other but make errors
