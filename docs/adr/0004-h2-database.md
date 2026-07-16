# ADR 4: H2 File-Based Embedded Database

## Status
Accepted

## Context
Gauge is a singular-tenant application (ADR 3). It needs persistent storage with zero operational overhead. The database should require no installation, no configuration, and no separate process.

## Decision
Use **H2** in file-based embedded mode (`jdbc:h2:file:./data/gauge`).

## Consequences
- Database is a single file (`gauge.mv.db`) in the `data/` directory — backup is a file copy
- `DB_CLOSE_DELAY=-1` keeps the DB alive across connections; `DB_CLOSE_ON_EXIT=FALSE` prevents accidental shutdown
- Global database locks — acceptable for singular tenant, would be a bottleneck under concurrency
- Hibernate `database.generation=update` auto-creates/evolves schema on startup
- No external DB server, no Docker dependency, no connection pool tuning
- Alternatives (PostgreSQL, MySQL, etc.) are trivially swappable — just change `application.properties` and the JDBC driver
