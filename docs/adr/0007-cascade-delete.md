# ADR 7: Cascade Delete with Explicit Confirmation

## Status
Accepted

## Context
Gauge's data model is strictly hierarchical: Project → Issue → Tasklist → Task, with projects also nesting via parentId. Deleting a parent entity could silently destroy an entire subtree of work. The API is consumed by both a human (via UI) and an LLM daemon (via REST) — both can make mistakes.

## Decision
Deleting an entity that has children requires **explicit `?cascade=true` query parameter**. Without it, the API returns `409 CONFLICT` with `CASCADE_REQUIRED`. Leaf entities (no children) can be deleted without cascade.

## Consequences
- Every delete endpoint checks `hasChildren()` before proceeding
- Cascade delete is recursive: deleting a project cascades through subprojects → issues → tasklists → tasks
- The `?cascade=true` requirement acts as a confirmation step — no accidental data loss from a stray DELETE call
- LLM daemon must explicitly opt into cascade, reducing risk of hallucinated deletions
- `removalLock` (ADR 8) adds a second layer: locked projects refuse deletion even with cascade
