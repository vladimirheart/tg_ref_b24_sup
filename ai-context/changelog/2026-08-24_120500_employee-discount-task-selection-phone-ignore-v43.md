# 2026-08-24 12:05 — Employee discount task selection, phone canonicalization and ignore-list v43

## User prompt

Пользователь уточнил production UX для `01-033`: видеть конкретные Bitrix-задачи до обработки и выбирать их вручную; нормализовать разные российские форматы телефонов к `+7XXXXXXXXXX`; поддержать ручной ignore-list «левых» номеров с явным возвратом причины пропуска.

## Changes

- Preview получил task-level checkboxes, selected count, select-all/clear и добавление номера в ignore-list формы.
- Execute API принимает explicit `selected_task_ids`; UI не запускает боевой режим без Preview/выбора.
- Backend повторно сверяет выбранные task id с текущим Bitrix discovery перед внешними side effects.
- Phone pipeline канонизирует 10/11-значные российские номера и использует безопасный fallback только для единственного distinct phone.
- Несколько разных fallback-номеров дают `error`, а не случайный выбор.
- Добавлен `ignored_phone_numbers` с canonical storage, dedupe и validation.
- Ignore match возвращается как `ignored`; iiko и Bitrix checklist не вызываются.
- Добавлены regression tests на форматы телефонов, manual selection, ignore behavior и ambiguous phones.

## Status

`01-033` остаётся `🟣` до реального Bitrix24+iiko acceptance smoke.