# 01-241: template and automation UI polish

Дата: 2026-09-21

## Контекст

После ручной приёмки management workspace пользователь подтвердил все пять пунктов предыдущего polish. Следующий визуальный шум сосредоточен в «Шаблоны» и «Автоматические действия»: auto-generated «Подробнее», крупные template cards, текстовые action buttons и смешанные в одном потоке шаблоны вопросов/оценок.

## Изменения

- «Шаблоны вопросов» и «Система оценок» разнесены по внутренним вкладкам;
- описания и активные diagnostic summaries перенесены в локальные `i` controls с hover/focus panel;
- question/rating template cards сделаны компактнее;
- Edit/Duplicate/Delete переведены в icon-only controls с `title` и `aria-label`, data hooks сохранены;
- auto-close cards получили тот же compact treatment, Configure/Delete переведены в icon-only controls;
- статический helper auto-close перенесён в `i`, чтобы `content-disclosure` не создавал лишний текстовый «Подробнее»;
- global `content-disclosure.js`, lifecycle API, bot ownership и backend semantics не меняются.

## Mutation boundary

- backend lifecycle mutation: нет;
- DB/data mutation: нет;
- runtime rollout: нет;
- generated `static/css/*.css` напрямую не редактируются.

## Проверка

`node --check` двух изменённых runtime JS, `git diff --check`, targeted `SettingsChannelsRuntimeUiSourceContractTest` + реальный `/settings` render smoke, cleanup только известных generated CSS side-effects, exact commit/push и затем selective `panel-web` rollout.
