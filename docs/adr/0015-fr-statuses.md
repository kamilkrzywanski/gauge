# FR 2: Status Lifecycles for Entities

## Scope
Each entity level has an appropriate status enum reflecting its role in the model.

## Statuses

### IssueStatus
`TODO → DOING → DONE`
- Simple linear progression. Issues represent concerns; they're either pending, active, or resolved.

### TasklistStatus
`TODO → DOING → DONE, CANCELED`
- Adds `CANCELED` — tasklists represent workstreams that can become irrelevant without the issue being done.

### TaskStatus
`TODO → DOING → DONE, FAILED, CANCELED`
- Adds `FAILED` — tasks are atomic operations that can fail independently. A failed task doesn't cancel the tasklist; it signals a blocker.

## Details
- All entities start in `TODO` on creation
- Status transitions are unrestricted (no state machine enforcement)
- Status is mutable via dedicated endpoints (`PATCH /{entity}/{id}/status`)
