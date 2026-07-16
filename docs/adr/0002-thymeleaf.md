# ADR 2: Thymeleaf Server-Side Rendering for UI

## Status
Accepted

## Context
Gauge needs a user-facing UI. The frontend doesn't require high flexibility — it's a structured issue tracker, not a rich interactive app. A monolithic approach avoids certain issues like CSRF that plague SPA+API architectures.

## Decision
Use **Thymeleaf 3.1** with server-side rendering for all UI pages. No JavaScript framework.

## Consequences
- 14 Thymeleaf HTML templates served from `GaugePageResource`
- Form-based login with session cookies — no CSRF token gymnastics needed
- Thymeleaf is a mature framework with abundant examples, making it the most LLM-friendly choice for code generation and maintenance
- Template engine configured via `ThymeleafConfig` CDI producer, with `ClassLoaderTemplateResolver` loading from `/templates/`
- REST API (`/api/*`) remains a separate concern — UI and API are independent but co-located
- No client-side routing, no build step for frontend assets
