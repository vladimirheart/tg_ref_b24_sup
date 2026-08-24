# 2026-08-24 11:15 — Employee discount automation closeout v40

## User prompt

`пушнул`

## Scope

После подтверждения v39 продолжить оставшийся рабочий backlog и довести `01-033` до AI-complete без изменения фактических Bitrix24/iiko endpoint semantics перед live acceptance.

## Changes

- исправлена классификация candidate errors: отсутствие/ошибка извлечения телефона остаётся `error`, а не превращается в `skipped`;
- run history и run details теперь scoped по authenticated actor;
- добавлены derived repository queries по `automation_key + actor`;
- PostgreSQL получил `V31` expression-index `(automation_key, lower(actor), started_at DESC)` для actor-scoped recent history;
- controller передаёт текущего пользователя во все history reads;
- добавлены service regression tests для execute-order, iiko failure, extraction failure и actor isolation;
- docs/task detail получили финальный manual acceptance gate;
- `01-033` переведена в `🟣`.

## Runtime invariant

Bitrix24 checklist может быть отмечен только после успешного iiko mutation result. Если iiko возвращает failure/exception, checklist не вызывается и ошибка остаётся в per-task run history.

## Remaining manual acceptance

Нужен один безопасный live dry-run + execute на реальных Bitrix24/iiko credentials с подтверждением бизнес-смысла выбранных iiko categories/wallet programs.
