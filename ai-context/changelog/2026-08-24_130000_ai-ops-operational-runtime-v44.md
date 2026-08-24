# 2026-08-24 13:00 — AI Ops operational runtime v44

## User prompt

`давай теперь пройдёмся по ai-ops. чтобы подсказки и автоответы работали корректно. плюс нужна возможность редактировать уже сохранённые подсказки и\или автоответы. в целом нужен рабочий инструмент, так как сейчас он больше похож на декорацию`

## Scope

Повторно проверить AI Ops не по внешнему UI, а по фактическому пути `incoming message -> retrieval -> policy -> suggestion/auto-reply -> operator feedback`, затем закрыть safety и operator-governance gaps.

## Runtime changes

- per-memory `auto_reply_allowed`, default deny;
- self-derived knowledge больше не считается независимым подтверждением;
- cooldown учитывает фактическое действие `auto_replied`;
- suggestion feedback привязан к `memory_key` и изменяет memory governance;
- rejected memory автоматически становится draft/low/review-required и теряет auto-reply;
- query identity не редактируется без re-key migration;
- DELETE memory стал безопасным soft-disable;
- rollback возвращает текст с auto-reply off;
- `retrieve-debug` дополнен side-effect-free decision preview.

## Operator UI

- solution memory показывает status/trust/intent/source/safety и статистику;
- answer редактируется из AI Ops;
- per-memory auto-reply включается отдельным чекбоксом;
- memory можно отключить без потери истории;
- добавлен Decision Testbench для существующего ticket и произвольного тестового сообщения.

## Dialog feedback

- suggestion API возвращает `memory_key`;
- `Вставить как есть` пишет accepted feedback;
- `Отклонить` пишет rejected feedback по точной memory.

## Safety note

Ни одна существующая memory после миграции не получает auto-reply автоматически. Разрешение является opt-in и дополнительно проходит intent/source/consistency/threshold/dialog/loop guards.