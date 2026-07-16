# FR 3: Task Ordinal Reordering

## Scope
Tasks within a tasklist have explicit, mutable ordering via an `ordinal` field.

## Details
- `Task.ordinal` is an integer set at creation (auto-appended to end: `ordinal = current task count`)
- Reordering is a single operation: `PATCH /api/tasklists/{id}/task-order` with a full ordered list of task IDs
- The endpoint replaces all ordinals in one transaction — no individual swap/move operations
- Ordinals are 0-based and contiguous after reorder
- Ordering expresses sequential coupling (ADR 12): step 1, step 2, step 3
