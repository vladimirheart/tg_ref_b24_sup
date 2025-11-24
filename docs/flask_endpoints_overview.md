# Flask Endpoints Overview and Java Migration Requirements

## Overview
This document summarizes the existing Flask routes in `panel/app.py` that we must migrate to Spring Boot. The routes are grouped by responsibility to help design controller boundaries for the new Java codebase.

## Template Rendering Endpoints
- `GET /` – protected by `login_required` and `page_access_required("dialogs")`. Renders `index.html` with aggregated ticket statistics and settings loaded from `config/shared/settings.json`.【F:panel/app.py†L5409-L5462】
- `GET /knowledge_base`, `GET /knowledge-base` – protected by knowledge base permissions; render `knowledge_base.html` with article list pulled from SQLite.【F:panel/app.py†L6721-L6727】
- `GET /knowledge_base/new` – renders a blank article editor using `knowledge_base_article.html` and generates a draft token.【F:panel/app.py†L6730-L6751】
- `GET /knowledge_base/<int:article_id>` – renders an editor populated with an existing article by ID.【F:panel/app.py†L6754-L6764】
- `GET /analytics` – renders `analytics.html` with ticket and client statistics derived from `_prepare_ticket_analytics` / `_prepare_client_analytics`. Redirect helper `GET /analytics/clients` keeps legacy URLs working.【F:panel/app.py†L7859-L7890】
- Additional template routes such as `/clients`, `/dashboard`, `/object_passports`, `/users`, `/settings`, etc., follow the same pattern and require page-level permissions; see `panel/app.py` for details when mapping to Java controllers.

### Java Controller Implications
- Introduce MVC controllers (e.g., `DashboardController`, `KnowledgeBaseViewController`, `AnalyticsViewController`) returning Thymeleaf templates.
- Enforce authorization annotations mirroring `login_required` / `page_access_required` decorators.
- Load view models through services that wrap database access (JPA/JdbcTemplate) and caching where appropriate.

## Authentication & Session Endpoints
- `GET/POST /login` – shows the login form and authenticates against the SQLite `users` table, populating Flask session keys like `user`, `role`, `user_id`, `user_email`.【F:panel/app.py†L4983-L5015】
- `GET /logout` – clears the session and redirects to `/login`.【F:panel/app.py†L5017-L5020】
- `GET /api/whoami` – returns JSON with the logged-in user context extracted from the session.【F:panel/app.py†L5921-L5934】
- `GET /api/ping_auth` – lightweight auth check returning `{success: True}` when the session is valid.【F:panel/app.py†L5938-L5943】

### Java Controller Requirements
- Provide a Spring Security login page backed by `UserDetailsService` fetching credentials/roles from the database.
- Expose REST endpoints (`/api/auth/whoami`, `/api/auth/ping`) that read authentication details from the `SecurityContext`.
- Configure session persistence (Spring Session JDBC/Redis) and CSRF tokens for both MVC forms and REST clients.

## Public Web Form API
- `GET /public/forms/<channel_id>` – renders `public_form.html` for a given channel if it exists.【F:panel/app.py†L5034-L5051】
- `GET /api/public/forms/<channel_id>/config` – returns channel configuration JSON; 404 when missing.【F:panel/app.py†L5054-L5062】
- `POST /api/public/forms/<channel_id>/sessions` – creates a web-form session; validates message text and persists data.【F:panel/app.py†L5078-L5120】
- `GET /api/public/forms/<channel_id>/sessions/<token>` – loads a specific form session with message history.【F:panel/app.py†L5123-L5175】
- `POST /api/public/forms/<channel_id>/sessions/<token>/messages` – appends a message to the open session.【F:panel/app.py†L5178-L5203】

### Java Controller Requirements
- Create a dedicated `PublicFormController` with MVC + REST methods mirroring the above routes.
- Validate payloads with Bean Validation, return structured error responses, and secure public endpoints via rate limiting/captcha if required.

## File & Media Handling
- `GET /media/<ticket_id>/<filename>` – serves attachments from `attachments/<ticket_id>` with sanitization and MIME detection.【F:panel/app.py†L5465-L5483】
- `GET /object_passports/media/<filename>` – streams passport uploads from `object_passport_uploads` with path normalization.【F:panel/app.py†L5485-L5491】
- Knowledge base upload API handles CRUD on article attachments (`/api/knowledge_base/uploads`, `/knowledge_base/attachments/<id>`, etc.).【F:panel/app.py†L6958-L7058】
- Avatar endpoints supply cached images and history for users (`/avatar/<user_id>`, `/avatar/history/<entry_id>`, `/api/client/<user_id>/avatar_history>`).【F:panel/app.py†L5770-L5855】

### Java Controller Requirements
- Implement `AttachmentController` with streaming responses backed by `Resource`/`InputStreamResource` and permission checks before file access.
- Support multipart uploads for knowledge base files with size/MIME validation and persistence of metadata.
- Provide endpoints to fetch avatar thumbnails/history using caching to avoid disk thrash.
- Centralize storage paths via configuration properties.

## Analytics Endpoints
- Template route `/analytics` (covered above) plus REST exports: `/analytics/clients`, `/analytics/clients/<user_id>/details`, `/analytics/clients/export`, `/analytics/export`. Exports build XLSX reports dynamically before streaming as attachments.【F:panel/app.py†L7859-L8115】

### Java Controller Requirements
- Offer REST controllers returning aggregated statistics and streaming XLSX exports (use `StreamingResponseBody` or WebFlux `DataBuffer` publisher).
- Leverage scheduled jobs/caching to precompute expensive aggregations.
- Enforce role-based access for analytics APIs.

## Authorization & Permissions
- Flask decorators `login_required`, `page_access_required`, and `login_required_api` guard routes based on session keys and role permissions stored in DB/JSON.【F:panel/app.py†L5022-L5049】【F:panel/app.py†L6767-L6772】

### Java Controller Requirements
- Translate permission checks into Spring Security authorities (e.g., `hasAuthority('PAGE_ANALYTICS')`).
- Map role permissions from the database into `GrantedAuthority` sets during authentication.
- Provide an authorization evaluator for resource-level checks (e.g., verifying ticket ownership before streaming files).

## Background Tasks & Caching
- Flask uses timers/APScheduler for periodic operations (e.g., delayed ticket responses).【F:panel/app.py†L12369-L12370】

### Java Requirements
- Replace timers with Spring `@Scheduled` jobs managed by `TaskScheduler`.
- Use Spring Cache (`@Cacheable`, `@CacheEvict`) to memoize analytics, avatars, etc., matching Flask’s caching semantics where present.
- Ensure background tasks run under service classes with proper transaction boundaries.

## Summary of Java Components
1. **MVC Controllers:** render Thymeleaf templates for dashboard, knowledge base, analytics, clients, etc.
2. **REST Controllers:** expose JSON/streaming APIs for forms, analytics, settings, files, tasks.
3. **Security Layer:** `SecurityFilterChain`, `UserDetailsService`, CSRF + session persistence, per-route authorization.
4. **Storage Services:** handle attachments (tickets, knowledge base, passports, avatars) with validation and auditing.
5. **Schedulers & Cache:** precompute analytics, refresh avatar caches, expire draft tokens.

Use this document as the blueprint when implementing the Spring Boot modules.