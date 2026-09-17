# AI-agent operating rules for repository handoff safety

Date: 2026-09-17
Baseline: `057aaa1f968a433cc2afd5846d8fb9bcb8004522`

## Scope

Закрепить project-specific правила, чтобы новый AI-agent или другая модель могли безопасно продолжить работу без неявных предположений из предыдущего чата.

## Changes

- добавить `ai-context/rules/README.md` как обязательный индекс project-specific правил;
- добавить общий operating protocol: fresh-main-first, точная трактовка `готово`, разделение sandbox/apply/commit/push/deploy и truthful RESULT;
- добавить guarded-operator/Windows PowerShell protocol с lessons learned по phase guards, emitted-code escaping, PowerShell 5.1 scalar unrolling, `git status --porcelain`, CRLF и cross-runtime sorting;
- добавить production checkpoint/acceptance protocol: revision/image/rollback, отрицательные факты по migration/backfill/external systems и приоритет functional acceptance изменённого behavior/data integrity;
- не изменять `ai-context/baseline/`, потому что project-specific правила принадлежат локальному слою `ai-context/rules/`.

## Validation

Guarded operator применяет эти файлы сначала в detached worktree, проверяет обязательные markers, text quality и exact changed-file set, после чего может записать те же статические файлы в рабочий checkout.

Runtime/source/deployment/DB migration/backfill/external-system mutation не выполняются.
