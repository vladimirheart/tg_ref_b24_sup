# 01-241: channel management tabs and nested-modal scroll lock

Дата: 2026-09-21

## Контекст

После ручной проверки предыдущего UI-polish таблица и icon-only lifecycle controls стали компактнее, но верхняя часть «Управление ботами» всё ещё дублировала блоки профилей и маршрутов, а при открытии вложенного редактора колёсико/тач-скролл мог продолжать прокручивание родительского settings workspace.

## Изменения

- три крупные overview-карточки заменены внутренними вкладками «Боты», «Профили прокси и VPN», «Сетевые маршруты»;
- в bot-tab сохранены счётчики каналов, описание добавления перенесено в доступный info-control, а компактная кнопка «Добавить канал» находится справа сверху;
- логический статус канала перенесён перед названием и отображается иконкой с `title`/`aria-label`;
- runtime status и Start/Stop/Edit собраны в одну более низкую строку, lifecycle hooks не менялись;
- существующий integration-network workspace сохранён по своим DOM ids/data hooks, но его два accordion-раздела стали tab-pane вкладками;
- child-modal scroll containment задан на реальном `.modal-body`, а suspended `inert` parent modal body блокируется от прокрутки;
- source-contract test расширен на tabs/status/scroll contract.

## Mutation boundary

- backend lifecycle mutation: нет;
- DB/data mutation: нет;
- runtime rollout: нет;
- stage/commit/push: нет;
- generated `src/main/resources/static/css/settings.css` напрямую не редактируется.

## Следующая проверка

`git diff --check`, `node --check` изменённого catalog runtime, targeted UI source-contract + реальный `/settings` render test, cleanup только известных generated CSS side-effects, exact commit/push, selective rollout `panel-web`, затем browser acceptance и короткий lifecycle acceptance channel 3.
