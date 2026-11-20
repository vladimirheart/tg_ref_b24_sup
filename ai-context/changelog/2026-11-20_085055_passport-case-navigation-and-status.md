# Паспорт объекта: открытие обращений и видимость статусов

Время: 2026-11-20 08:50:55 Europe/Moscow. Задача: 01-298.
Baseline: f8647bcb2e9742da99d87104c8e582dd40f7ddfe.

Промт пользователя:

```text
ага, всё ок.
давай дальше: на странице паспорта объекта есть вкладка "обращения", но их невозможно открыть. и плюс отображаются id обращений, а не их номера
да, а ещё во вкладке оборудования, в плашках самого оборудования статус инога съезжает вправо и большую часть названия статуса не видно
```

- ObjectPassportAppealQuery: номера общей дневной последовательности до фильтра
  локации, channel и ссылки на существующие страницы. JOIN сверяет user/id/channel;
  карточки дедуплицируются по составной идентичности. Неоднозначные legacy ID и
  записи без ticket не получают подставных номеров или ссылки на чужую карточку.
- Passport activity runtime: ссылки, human number вместо ID, описание проблемы,
  клавиатура, hover/focus и экранирование. Задачи/инциденты сохраняют прежнее поведение.
- Passport workspace SCSS и compiled app.css: перенос статусов/названий оборудования,
  min-width/max-width для карточек. Detail template и source audit: cache version.
- PostgreSQL/navigation tests, прежние fixtures и source-layout expectations
  актуализированы; tools/passport-cases-qa.cjs проверяет реальную MVC-разметку.
- 57 targeted tests / 8 suites и 8 browser cases GREEN; 79-template source audit,
  Node syntax, diff check и Maven package GREEN. JAR payload/classes сверены.
- Первый RED: C locale native test PG и изменившийся SQL source literal.
  Среда исправлена новым owned ICU cluster, expectation уточнён. Старый unrelated
  settings CSS-version assertion остаётся прежним; 5 других workspace cases GREEN.
  Подробности и артефакты указаны в task-details/01-298.md.
- ai-context/tasks: новая задача и фактическая валидация. Detached sandbox затем
  переносится exact guarded operator; финальный source-applied state — в ignored
  .tmp/01-298-applied.json. Рабочая БД, commit/push/deploy не выполнялись.
