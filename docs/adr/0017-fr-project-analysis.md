# FR 4: Recursive Project Analysis with Bubbled Priority

## Scope
`GET /api/projects/analysis` returns computed stats for every project, recursively aggregating across subproject trees.

## Output per Project

| Field | Description |
|---|---|
| `issueTodo` | Count of TODO issues (direct + all descendants) |
| `issueDoing` | Count of DOING issues (direct + all descendants) |
| `issueDone` | Count of DONE issues (direct + all descendants) |
| `subprojectCount` | Total descendant subprojects |
| `taskTodo` | Count of TODO tasks (direct + all descendants) |
| `taskDoing` | Count of DOING tasks (direct + all descendants) |
| `bubbledPriority` | Highest active priority across the entire subtree |

## Bubbled Priority

- `NONE` — no issues at all
- `DONE` — all issues done, none active
- `LOW` — highest active priority is LOW
- `NORMAL` — highest active priority is NORMAL
- `HIGH` — highest active priority is HIGH

Bubbled priority surfaces the most urgent thing anywhere in a project tree, enabling triage at any level of the hierarchy.
