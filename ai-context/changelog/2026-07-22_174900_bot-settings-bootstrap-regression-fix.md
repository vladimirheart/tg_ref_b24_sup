# 2026-07-22 17:49:00 — bot settings bootstrap regression fix

## Контекст
- Пользователь: `смотри на скрин, что отображается. что-то не всё так, как описываешь`
- Дополнительный пользовательский контекст:
  - на скрине в `Каналы (боты)` → `Шаблоны` question templates не отображались вообще;
  - в блоке `Система оценок` оставался лишний diagnostic/source note, хотя основной legacy cleanup уже был заявлен завершённым.

## Что сделано
- В `spring-panel/src/main/resources/static/js/bot-settings.js` исправлен bootstrap/hydration path:
  - убрана повторная прогонка уже нормализованного state через raw `normalizeSettings(...)`;
  - `initialState` и post-save reset теперь хранятся как canonical raw payload, а `hydrateStateFrom(...)` умеет отдельно распознавать уже нормализованную форму;
  - это устраняет сценарий, при котором question templates терялись при инициализации и UI показывал пустой список.
- В том же runtime расширена совместимость transitional question payload:
  - `normalizeTemplate(...)` теперь принимает `questions` как fallback наряду с `question_flow/questionFlow`;
  - `normalizeQuestion(...)` поддерживает строковые legacy questions и не выбрасывает их молча.
- Убран лишний user-facing diagnostic noise:
  - `data-bot-legacy-diagnostic` скрыт по умолчанию в `spring-panel/src/main/resources/templates/settings/index.html`;
  - блок раскрывается только если runtime реально передаст legacy diagnostic message.
- Обновлён `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java` под новое hidden/placeholder поведение.
- В `ai-context/tasks/task-details/01-150.md` зафиксирован текущий этап задачи и следующий шаг: live/manual verification против фактического runtime config.

## Проверки
- `node --check spring-panel/src/main/resources/static/js/bot-settings.js`
- `spring-panel\mvnw.cmd "-Dtest=ManagementControllerWebMvcTest,RestExceptionHandlerTest" test`

## Следующий шаг
- Открыть live `/settings` и проверить, что:
  - question templates снова отображаются;
  - активный шаблон вопросов подсвечен и диагностируется;
  - под `Система оценок` больше нет лишнего source/legacy note.
- Если скрин всё ещё будет расходиться с репозиторием, отдельно проверить фактический `shared-config.dir` или живой `settingsPageInitPayload`.
