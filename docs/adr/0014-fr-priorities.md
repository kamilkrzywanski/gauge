# FR 1: Priority Levels for Issues

## Scope
Issues support three priority levels: **LOW, NORMAL, HIGH**.

## Details
- `Priority` enum: `LOW`, `NORMAL`, `HIGH`
- Default priority for new issues: `NORMAL`
- Priority is mutable via `PATCH /api/issues/{id}/priority`
- Priority drives sort ordering and bubbled priority in project analysis (FR 4)
- Tasklists and Tasks do not have independent priority — they inherit context from their parent issue
