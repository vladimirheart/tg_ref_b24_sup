# 01-281 — согласованность Neutral Ops и чтение дат отчёта

Дата: 22.10.2026, 22:20 МСК. Статус: `🟣`, ожидается приёмка владельцем.

## Промты пользователя

Инициирующий промт (дословно; приложены два снимка графиков):

> кнопка раскрытия - ок, а вот кнопка действия визуально больше кнопки открытия диалога. и внутри диалога сообщения не должны по разным краям находиться.
> новый график приятен глазу, а старые - ужасны, например нагрузка по каналам: проценты и количества не сверху столбца, а внутри него - это не правильно. в графике по ресторанам "7 гвардейская" проценты сливаются с цветом столбца. нагрузка по сотрудникам и вовсе пустая.
> и в целом проверь по проекту, все-ли стили применены как планировалось, нет-ли своих уникальных стилей

Уточнение по узкому серверному исправлению:

> Да, исправить чтение дат

Разрешение дано на чтение PostgreSQL timestamps с часовым поясом и регрессионные
тесты, без изменения БД и формул. Ранее владелец разрешил пересборку и перезапуск
контейнеров; выполнен только panel-web rollout.

## Фактические изменения

- Одинаковые размеры и видимый контур открытия/действий; удалены конфликтующие
  legacy border/background rules. Все типы сообщений расположены слева.
- Общие внешние подписи пяти bar charts с резервом места и theme text color;
  palette исправлена, restaurant top-10, mobile long labels, staff single/empty.
- DashboardAnalyticsService нормализует PostgreSQL timestamp перед прежним
  offset parsing. Старые форматы, системная зона и формулы сохранены.
- В общем SCSS нейтрализованы найденные отдельные shadows/gradients/blur,
  EditGuard styling вынесен из JS в theme tokens; UI Kit использует токены.
- Все 46 app.css links получили R9 cache version. Добавлены chart unit tests,
  timestamp regression, source audit и расширена computed-style/browser QA.
- Области: SCSS/app.css; 46 templates; EditGuard/chart presentation JS;
  пять cache-version UI assertions, новый Java test и новый JS test;
  три QA tools; task checkpoint и новый отчёт. Полный точный список 67 путей —
  `docs/ui/neutral-ops-consistency-r9.md`.

## Проверка и runtime

- Baseline `33a58565ae048cf02d01f2ecb3e57bfc1ddcd9ab`, HEAD=origin/main.
  Detached sandbox GREEN; 64 source/test/tool files перенесены и сверены.
- Package, 160 Java tests и 13 JS checks: 0 failures/errors/skipped.
  До исправления дат 6/15 regression cases воспроизводили дефект.
- 460 screen snapshots + 24 modal checks; 72 дополнительных state checks
  и 63 focus checks; 10 filled scenarios / 70 PNG; source audit 73 templates.
  Найденных overflow/missing tokens/decorative overrides нет.
- Только panel-web: `iguana-panel:neutral-ops-r9-20261022-v2`,
  `sha256:445d26e30d138b6e6132f7697654b9a9b77afffbc9b23a6058d6201aa46127c3`,
  healthy. Точечный overlay 49 UI entries + один approved parser class;
  прочие backend changes не развёртывались.
- HTTP asset SHA, login и runtime JAR SHA проверены. Parser из deployed JAR
  прочитал все 47 DB timestamp pairs; длительности совпали с PostgreSQL.
- Rollback R9 указывает на R8 digest
  `sha256:3936d8495f132a9a73b2a75cc0ccd1e1cdeb0128a7dfedb8fbb25c26a5b445a0`.
  Workers/инфраструктура не перезапускались; Flyway 81,
  migrations/backfill/внешние business writes отсутствуют.
- Staging/commit/push/user acceptance не выполнялись. Fixture browser QA
  не подменяет authenticated business acceptance. Подробности и rollback —
  в новом отчёте; старые changelog/отчёты не переписывались.
