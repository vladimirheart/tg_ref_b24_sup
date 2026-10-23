# 01-281 R11 — уведомления инцидентов поверх страницы

23.10.2026, 11:22 МСК. Статус новых изменений: `🟣`.

## Промт пользователя

> на странице инцидентов, когда меняю статус, в хэдере появляется соодбение и исчезает, тем самым двигая страницу. пусть это сообщение отображается так-же сверху, но простым выезжающим, например попап, поверх страницы, чтобы  страница не смещалась

## Изменения

- Success/error nodes страницы инцидентов вынесены из normal flow в fixed
  stack сверху справа; короткая анимация, адаптивная ширина, общий theme
  contract, reduced-motion. Уведомление не блокирует остальную страницу.
- Кнопка закрытия скрывает, а не удаляет legacy DOM node. Повторное сообщение
  работает; клавиатурный фокус возвращается без прокрутки. Сохранены ARIA
  status/alert и прежнее автоскрытие успеха через 3500 мс.
- API/status/escalation producers и серверная логика не изменялись.
  CSS version страницы обновлена, source audit/MVC version map синхронизированы.
- Новый browser regression script; task checkpoint и отчёт с точным scope.

## Валидация и поставка

- Baseline HEAD=origin/main `1f059a33265b37785a09df891a8e57bd48ff1d59`.
  Отдельный detached sandbox GREEN, 7 source/test/tool files сверены в main.
- Sass, 5 targeted Java tests, два JS regression scripts с 42 checks,
  node syntax и source audit 74 templates/47 pages успешны.
- 10 browser combinations, 20 PNG: fixture-only PATCH success/503, select
  rollback, неизменная геометрия header/tabs/list/scroll при show/hide,
  auto-hide, manual close/reuse, focus, touch44, reduced-motion и long text.
- Только panel-web: `iguana-panel:neutral-ops-r11-20261023`, healthy,
  sha256:923bfd6cd1e5cb94e68c4de32650023e63c1bd989cdb97cc8b3dc0d2dd8afbea.
  JAR SHA ab795a8fb0592844332710e78fc2494bd6228630a4fb1c9f32577189920af3b0.
  Ровно 3 UI resources; остальные JAR entries, backend classes и libraries
  побайтно сохранены. Несвязанные backend commits не поставлялись.
- Rollback R11 сохранён на R10 c36c7f2b8667ca6a8caa9619710f6a151158fdbcab61986e90c82b4aee1cc43d;
  все предыдущие tags сохранены. Откат не запускался. Environment и 4 mounts
  сверены; runtime JAR/HTTP asset SHA совпадают, anonymous /incidents=302.
- Workers/infra/db-migrate не перезапускались; Flyway 81 до/после,
  migrations/backfill/внешние business writes отсутствуют. В рабочей системе
  статус инцидента для теста не менялся; authenticated acceptance — владельцу.
- Полный scope 10 файлов и команды фиксации:
  docs/ui/neutral-ops-incident-notifications-r11.md.
  Staging/commit/push и ручная приёмка не выполнялись.
