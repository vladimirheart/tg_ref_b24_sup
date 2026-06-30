# 2026-06-30 14:31:02 - bot settings compat cleanup

## Промпты пользователя

- `тогда добивай совместимость вокруг bot-settings.js.`
- `ты пишешь "и решить, считаем ли его bridge/Bootstrap-modal слой частью этой задачи или выносим в отдельный финальный cleanup-пакет." дай подробносетй`

## Что изменено

- `spring-panel/src/main/resources/static/js/bot-settings.js`
  переведён с прямого `new bootstrap.Modal(...)` на page-shell modal contract:
  редакторы bot question templates и rating templates теперь используют
  `SettingsPageShell.showModal/hideModal` через runtime-resolver, а не
  собственные bootstrap-инстансы;
- в том же `bot-settings.js` сохранён минимальный legacy `BotSettingsBridge`
  (`getSnapshot/subscribe`) как compatibility API, но снят мёртвый глобальный
  event-bus `window.dispatchEvent(new CustomEvent('bot-settings:*'))`, так как
  в репозитории у него больше нет потребителей;
- `ai-context/tasks/task-details/01-129.md` обновлён: зафиксировано, что
  bot-settings modal/compatibility glue уже относится к закрытым хвостам
  задачи, а оставшийся scope ещё сильнее смещён к финальным compatibility
  export/page-hook cleanup-пакетам.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/bot-settings.js`
- `rg -n "templateModal\\b|ratingModal\\b|new bootstrap\\.Modal|window\\.dispatchEvent|CustomEvent\\(" spring-panel/src/main/resources/static/js/bot-settings.js`
