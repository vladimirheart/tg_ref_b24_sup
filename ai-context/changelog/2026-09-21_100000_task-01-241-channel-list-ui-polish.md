# 01-241: compact modal-safe channel list polish

Дата: 2026-09-21

## Контекст

После ручного подтверждения работающих Start/Stop таблица каналов визуально выходила за ожидаемую плотность модалки: длинные platform/template summaries растягивали строку, а текстовые Start/Stop/Edit создавали лишний шум.

## Изменения

- таблица каналов получила `table-layout: fixed`, `width/max-width: 100%` и `min-width: 0` на relevant containers/cells;
- platform metadata остаётся в двух компактных видимых строках с ellipsis, полный текст остаётся доступен через hover и disclosure;
- раскрытые details принудительно переносят длинные tokens и не расширяют modal;
- Start/Stop/Edit стали icon-only (`bi-play-fill`, `bi-stop-fill`, `bi-pencil`) с одинаковым compact hit-area, `title` и `aria-label`;
- info control остаётся icon-only и синхронизирует `title`, `aria-label` и `aria-expanded`;
- lifecycle/status wiring и существующие `data-channel-*` hooks не менялись;
- source-contract test расширен на modal-width/density/accessibility contract.

## Mutation boundary

- runtime mutation: нет;
- DB/data mutation: нет;
- stage/commit/push: нет;
- generated `src/main/resources/static/css/settings.css` напрямую не изменяется.

## Следующая проверка

После apply: `git diff --check`, `node --check` изменённого JS, targeted `SettingsChannelsRuntimeUiSourceContractTest`, cleanup только известного generated CSS side-effect, exact stage/commit/push, selective rollout только `panel-web`, затем browser visual acceptance и короткий lifecycle acceptance channel 3.
