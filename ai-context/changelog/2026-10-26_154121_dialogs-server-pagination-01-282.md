# 01-282: серверная пагинация списка диалогов

Время checkpoint: 26.10.2026, 15:41:21 MSK (12:41:21 UTC).

## Промты пользователя

Инициирующий вопрос, на котором была выполнена read-only диагностика без изменений:

> уже лучше.
> давай дальше. я правильно понимаю, что, например, страница списка диалогов сейчас загружается целиком из БД, целиком рендерится в HTML, а пагинация лишь скрывает строки в браузере? если так, то что будет при тысячах обращений? проект станет потреблять необоснованно много ресурсов. пока ничего менять не нужно - это вопрос

Разрешение реализации и обязательная новая задача:

> хорошо. делай. не забудь создать файл задачи, как это описано в правилах

Контекст: пользователь также указал читать `ai-context/baseline/ai-rules`; ранее в этом чате разрешил перезапуск/пересборку контейнеров. Миграции/flags/backfill для этого шага не требовались и не выполнялись.

## Фактически изменено

- Создана отдельная задача `ai-context/tasks/task-details/01-282.md`, строка в task-list, итоговый статус 🟣 (не ручная приёмка).
- Добавлены DialogListQuery/DialogListPage и DialogListPageSql. Общая legacy SQL-проекция переиспользована для page/preview; старые фоновые full-read callers не перенаправлялись и их алгоритмы не менялись.
- DialogsController рендерит ограниченную страницу, DialogListController предоставляет bounded GET и read-only POST/query с PAGE_DIALOGS/CSRF. DialogListReadService разделяет страницу, полные counts и ограниченный My preview.
- Поиск/статус/view/SLA/sort работают в PostgreSQL до limit, count и clamped page — единый snapshot. Нумерация до фильтров; GENERAL/opaque-ID/collision guards сохранены. Timestamp offset поддержан в SLA helper для согласованности с SQL, без смены формул.
- Browser runtime больше не получает весь архив и не прячет его строками пагинации; debounce, abort/generation, coalescing SSE, visible-tab/time-based refresh, обработка ошибки и page-local selection. Сохранены общие счётчики и адресные закрепления.
- Тронуты только dialog HTML/JS; самостоятельных CSS/theme changes нет. Cache versions и соответствующие точные source-contract ожидания обновлены.
- Добавлены PostgreSQL/HTTP/async JS/browser проверки; отчёт и точный manifest всех 28 файлов: `docs/ui/dialogs-server-pagination-01-282.md`. Постоянный контракт: `ai-context/rules/07-dialog-list-pagination.md`.

## Проверки

178 Java tests / 11 suites, 98 Node runner tests, 4 браузерных контекста / 8 PNG — GREEN. 10 000 синтетических записей в изолированном PostgreSQL: 20 строк первой страницы, 100 последней, My preview 20 при count 10 000. Проверены номера, literal search, scope/collisions, фильтры, три My-группы, pin вне preview, race/error/SSE. Полный suite проекта не объявлялся GREEN: четыре старых legacy-API 404 и VM notes test воспроизведены на неизменённом baseline и отдельно описаны в отчёте.

Guarded source transfer: baseline `eebffe9bf34a36648e22fab73f14e5b63e594967`, sandbox GREEN → exact content/file guards → основной checkout. Staging/commit/push не выполнялись.

## Рабочая среда

Обновлён только panel-web узким overlay на R20: `iguana-panel:dialogs-pagination-20261026`, image `sha256:1a8f1c4f2ca630fb15314fcba23123fadc00b2c79bf5cd04db58420679e16733`, container `79e03807eb0af94461581fe2089638711962570eedaf4d097ebf7faf53010be8`, healthy. Три JS HTTP 200/source hashes совпадают. Остальные 17 Compose-контейнеров, ops-worker, 4 bot JVM, environment/binds, V82/history, monitoring policy сохранены; нет миграций/backfill/flags/external mutation. Неразвёрнутые R177/R178 не поставлялись.

Release-v1 ошибочно трактовал progress Docker stderr как PowerShell exception и вызвал промежуточный возврат на R20; healthy возврат подтверждён. Release-v2 проверяет native exit code и успешно выпущен. Rollback tag `iguana-panel:rollback-dialogs-pagination-web-20261026` сохранён и указывает на прежний R20 image. Авторизованная ручная приёмка — PENDING, credentials не создавались/не подбирались.
