# Разделение модулей настроек и мониторинга, общие стили и recovery archive

- Время: `2026-11-27 14:00:00` (Europe/Moscow).
- Задача: `01-330`, статус 🟣 — ожидается ручная проверка.
- Промт пользователя:

```text
исправь всё. но кореектно и ничего не сломай. напоминаю, что проект развёрнут на другой машине.
```

- Области: auth-management/bot-settings JS и зависимости шаблона; RMS service
  и внутренние protocol/text/network компоненты; общий SCSS и compiled CSS;
  cache revisions шаблонов; browser/source/composition tests; документация.
- Auth и bot разделены по обязанностям с сохранением authoritative state,
  обработчиков, capabilities и публичных точек входа. Шесть перекрытых bot
  declarations удалены с сохранением исполнявшихся реализаций.
- Queue/lifecycle/API RMS сохранены; протокол, текст и команды выделены отдельно.
- Общие правила страниц получили одного владельца, notifications/controls/resize
  выделены из base без изменения порядка CSS. Исправлена повторная инверсия
  кнопки закрытия в тёмной теме, убрана тень карточки вопроса.
- 52 старых recovery файла (~911 MiB) выведены в проверенный внешний архив.
  Добавлены manifest, verify/restore tool и ignore rules. История Git и рабочие
  БД сохранены; миграции, production, staging/commit/push/deploy не выполнялись.
- Проверки: 262 Java tests; AST parity; 74 RMS differential cases; 133 Edge
  scenarios/cases, включая 32 CSS comparisons. Node 190/197, семь ошибок
  независимо воспроизведены на baseline; новых нет. Sass/JAR, content/file
  guards и diff checks фиксируются в ignored apply evidence.
- Подробности: `docs/architecture/settings-modules.md`, exact file list/hash
  manifest — `spring-panel/target/architecture-330-files.txt` и apply report.
