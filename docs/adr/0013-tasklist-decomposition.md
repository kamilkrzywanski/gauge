# ADR 13: Tasklist-as-Decomposition (Intra-Issue Loopback)

## Status
Accepted

## Context
The 4-level model (ADR 12) is intentionally strict: Project → Issue → Tasklist → Task. But within a single issue, a task may be too coarse to act on directly — it describes *what* needs to happen, and a separate tasklist is needed to express *how*. This is a controlled exception to the strict hierarchy: a tasklist can declare that it breaks down a task from another tasklist in the same issue.

## Decision
A tasklist can optionally reference a task via **`decomposesTaskId`**. The referenced task must belong to a **different tasklist within the same issue**. This is the only structural cross-reference in the data model.

## Rationale
- Keeps the loopback contained within the issue boundary — no cross-issue or cross-project structural links
- A task that needs breakdown stays in its original tasklist as a "header" or "intent"; the decomposing tasklist provides the "execution plan"
- Cross-project escalation (a task spawning an entire new project) is intentionally left to text — expressed in descriptions, not schema. Less information, less noise.

## Consequences
- `decomposesTaskId` is a nullable string on `Tasklist`, settable at creation and mutable via `PATCH /{tasklistId}/decomposes-task`
- No referential integrity — it's a semantic link, not a foreign key. No cascade, no reverse lookup
- The "same issue, different tasklist" constraint is not enforced in the database — it's a convention enforced at the application level (to be implemented)
- Deleting a tasklist does not affect the task it decomposes; deleting the decomposed task leaves a dangling reference
- This is the one place the strict hierarchy bends — and it bends only within a single issue
