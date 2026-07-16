# ADR 12: 4-Level Hierarchical Data Model

## Status
Accepted

## Context
Work tracking requires structure, but real-world concerns resist rigid classification. The data model must provide enough hierarchy for organization while leaving room for the fuzzy, cross-cutting relationships that naturally emerge. The model is consumed by both a human and an LLM — it must be navigable by both.

## Decision
A **4-level model**: `Project → Issue → Tasklist → Task`. Each level serves a distinct structural role and offers a specific coupling model: **tree → undefined → parallel → sequential**.

### Projects — Tree Coupling
Projects form a strict tree (`parentId`). They provide clear categorization of concerns. The tree is the backbone: every other entity hangs off it. Coupling is explicit and hierarchical.

### Issues — Undefined Coupling
Issues are a **flat list** within a project. They are deliberately coupled only to a single parent project — no cross-project links, no tags, no many-to-many relationships. This is the point: issues are where the rigid tree meets messy reality. Concern coupling is often inexpressible in user-friendly ways with well-defined structures ("life is a complex process"). The flat list lets fuzzy relationships live in titles, descriptions, and human/LLM interpretation rather than forcing them into schema. Coupling is **undefined** — intentionally left to interpretation.

### Tasklists — Parallel Coupling
Tasklists express **independent sub-workstreams** within an issue's scope. They create horizontal coupling with other tasklists in the same issue — **parallel** tracks that may or may not relate to each other.

### Tasks — Sequential Coupling
Tasks are intended to be **atomic from their parent issue's point of view**. Within a tasklist, tasks are ordered — they express **sequential** (vertical) coupling: step 1, step 2, step 3. A task is either done or not; if it needs breakdown, it should be a tasklist instead (see ADR 13: Tasklist-as-Decomposition).

## Consequences
- 4 JPA entities: `Project`, `Issue`, `Tasklist`, `Task` — each with its own repository, service, and resource
- Cascade delete flows down the hierarchy (ADR 7)
- Issues cannot belong to multiple projects — fuzzy cross-cutting concerns live in naming conventions and search, not in the schema
- Tasklists within an issue are parallel by default; ordering between them is implicit
- Tasks have an `ordinal` field for explicit ordering within a tasklist
- The model is intentionally shallow at the issue level — this is where the human and LLM negotiate meaning
- The coupling progression (tree → undefined → parallel → sequential) means each level adds a different kind of structure, rather than repeating the same pattern
