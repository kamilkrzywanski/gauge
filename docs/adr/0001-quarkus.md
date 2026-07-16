# ADR 1: Quarkus as Application Framework

## Status
Accepted

## Context
Gauge is an issue tracker (Angelitrack) that needs a JVM-based application framework. The primary criteria: dev-friendly, incorporates relatively small amount of business policies into the technical domain.

## Decision
Use **Quarkus 3.15.1** as the application framework.

## Consequences
- Single fat JAR deployment via `quarkus-maven-plugin`
- Hibernate ORM Panache for simplified data access
- RESTEasy for JAX-RS endpoints
- CDI for dependency injection
- Elytron security for authentication
- Native compilation path available (not currently used)
- `-parameters` compiler flag required for reflection-free REST parameter names
