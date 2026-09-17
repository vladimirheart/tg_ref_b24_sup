# Project-specific AI rules

Этот каталог содержит правила, специфичные для `tg_ref_b24_sup`.

## Обязательный порядок чтения для AI-агента

Перед изменением репозитория агент обязан прочитать:

1. применимые файлы из `ai-context/baseline/ai-rules/` и `ai-context/baseline/guides/`;
2. `ai-context/rules/00-agent-operating-protocol.md`;
3. `ai-context/rules/01-guarded-change-operator.md`, если агент готовит или проверяет patch/operator/script, который изменяет checkout;
4. `ai-context/rules/02-production-checkpoint-and-acceptance.md`, если работа касается production, deploy, rollback, миграций, backfill, внешних систем или post-deploy acceptance;
5. затем предметные правила из подпапок, например `backend/`.

Правила в этом каталоге имеют project-local приоритет и не должны переноситься в `ai-context/baseline/` только ради удобства: baseline является replaceable source-of-truth и может быть перезаписан sync-процессом.

Если правило из этого каталога конфликтует с более новым явным решением пользователя в текущей задаче, агент должен следовать явному решению пользователя и зафиксировать изменение project-specific правила отдельным commit/step, если это решение должно стать постоянным.
