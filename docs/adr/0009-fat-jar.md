# ADR 9: Self-Contained Fat JAR Deployment

## Status
Accepted

## Context
Gauge is a singular-tenant application (ADR 3) that should be trivial to build and deploy. No external app server, no container orchestration, no complex deployment pipeline.

## Decision
Build and ship as a **single fat JAR** via Quarkus's `quarkus-maven-plugin`. Deployment is a one-line shell script.

## Consequences
- `mvn package` produces `target/gauge-2.0.4-runner.jar` — a self-contained executable
- `runner.sh` is literally: `QUARKUS_CONFIG_LOCATIONS=application.properties java -jar target/gauge-2.0.4-runner.jar`
- H2 database file lives alongside the JAR in `./data/`
- No Docker required (though a `docker/` directory exists for optional containerization)
- Quarkus fast-start and low memory footprint make it suitable for modest hardware
- One-click build, one-click run — fits the "ride shotgun with your daemons" ethos
