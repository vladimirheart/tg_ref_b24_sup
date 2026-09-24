# 01-274 Phase B corrective — CSRF write boundary

Date: 2026-09-24
Task: 01-274

## Acceptance blocker

Production manual acceptance after the Phase B rollout showed that both project save and task save returned `403 Forbidden (CSRF or access denied)`.

## Root cause

- Spring Security uses `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
- The Tasks and Projects pages did not expose `_csrf` / `_csrf_header` metadata.
- Their local fetch wrappers therefore sent unsafe POST/PATCH/DELETE requests without the required CSRF header.

## Corrective change

- expose the request CSRF token and actual Spring header name in both templates;
- attach that header to unsafe methods in `tasks.js` and `projects.js` while keeping same-origin credentials;
- preserve native FormData content-type handling for task writes;
- add a source-contract regression test tied to the CookieCsrfTokenRepository contract;
- keep SecurityConfig, backend APIs, DB schema and V42 unchanged.

## Safety

This source hotfix performs no runtime, DB, queue or environment mutation. Task 01-274 remains YELLOW until production save/reload acceptance succeeds.
