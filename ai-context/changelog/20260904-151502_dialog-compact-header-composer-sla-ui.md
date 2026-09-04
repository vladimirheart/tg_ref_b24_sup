# Compact dialog header, composer and SLA metrics pulse

## Пользовательский запрос

> окно диалога с пользователем. шапка выглядит очень шумной.
>
> 1. кнопки "Участники", "Передать", "Задача" и так далее нужно превратить в иконки, но с описание действий при наведении на них.
> 2. регулировку размера текста сделать тоже иконкой и строку регулировки сделать отображаемой при клике по иконке. поднять её вверх, в ряд с кнопками описанных в первом пункте задачи.
> 3. "Ответ клиенту" как надпись убрать. тем самым сократится разрыв между историей и полем ввода ответа оператором
> 4. "Отправить" сделать тоже иконкой, тем самым уменьшив саму кнопку. и важно: при масштабировании поле ввода ответа оператором, кнопка должна оставаться статичного размера
> 5. слева блок "Метрики" должны начинать пульсировать цветом, в зависимости от SLA

## Что изменено

- 01-253 подтверждена пользователем и переведена в 🟢.
- Добавлена 01-254.
- Все основные header actions модалки диалога переведены на Bootstrap Icons.
- Для action icons добавлены aria-label, title и theme-aware hover/focus tooltip.
- Размер текста перенесён в icon-trigger; range открывается по клику и закрывается
  по outside click/Escape.
- Убрана видимая подпись «Ответ клиенту», textarea сохранила aria-label.
- Composer уплотнён.
- Send control стал icon-only фиксированного размера и больше не растягивается
  вместе с textarea.
- Metrics section получает те же SLA states, что и list runtime, и пульсирует
  green/warning/red для safe/risk/overdue.
- Closed SLA state остаётся статичным.
- Добавлен prefers-reduced-motion fallback.
- Media preview лишён отдельной header-плашки: остаётся только floating close.
- Zoom/download переведены в компактные icon-only controls поверх media stage.
- Отдельный media footer удалён, полезная область просмотра увеличена.
- Обновлены cache-busters dialogs runtime/app.css.
- Удалены три случайно закоммиченных operator-файла 01-253.
- Generated CSS напрямую не редактируется.

## Проверки

- node --check spring-panel/src/main/resources/static/js/dialogs.js.
- git diff --check.
- Targeted Maven source-contract test DialogDetailsCompactHeaderComposerSlaUiSourceContractTest.
- Isolated Docker Maven SCSS compilation.
- Проверка compiled temp app.css на compact header/composer/SLA selectors.
- SHA guard generated app.css/settings.css/sidebar.css/style.css.
- Read-only Docker preflight/post-guard: production roles не меняются.
