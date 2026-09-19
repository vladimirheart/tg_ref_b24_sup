# 01-271 — Backup & recovery dark-theme + compactness pass

## Время

2026-09-18 22:30 +03:00

## Контекст

Production commit `5e36b48358c567f5bbacf64eb801aeef348f2224` прошёл runtime review, но ручная UI-приёмка осталась RED: в dark theme `i` и secondary/probe text имели слабый contrast, а workspace оставался слишком растянутым.

## Что меняется

- `i` получает явный border/background/icon contrast, включая dark-theme override, hover/focus/expanded state;
- secondary/header/probe/manual text получает читаемый body-level contrast вместо почти чёрного muted color;
- Destination становится плотнее, а write/delete probe визуально уходит на secondary уровень;
- Retention собирается в компактную строку;
- manual mode/components/status/actions объединяются в одну card вместо двух вертикальных блоков;
- постоянные manual/schedule explanations переносятся в existing `i` popover mechanism без изменения JS;
- Critical/Full остаются двумя cards, но с меньшим padding и без постоянных description paragraphs;
- Restore rehearsal и фактические каталоги собираются в secondary two-column technical summary;
- backend, `settings-backup-runtime.js`, host runner, probe/auth, backup/restore semantics не меняются.

## Safety

Validate-only оператор работает в fresh sandbox clone, проверяет exact remote main/blob baseline, changed-set, `git diff --check`, Sass/Maven source contract и не меняет production/runtime, credentials, probe queue, backup/restore или Git remote.
