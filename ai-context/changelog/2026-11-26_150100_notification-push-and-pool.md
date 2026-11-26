# 01-326 — Освобождение JDBC, быстрые оповещения и push

Дата: 26.11.2026, 15:01 МСК.

Промт пользователя:

> в целом давай всё делать, но наверное с 6 нужно увеличить до 15, т.к. одновременно работающих операторов планируется порядка 10

Согласованные требования: оповещения порциями максимум 10 и scroll pagination,
адресная оптимизация JIT, server push счётчиков/данных/оценок, single-flight,
диагностика timings/request ID/thread dump. Предшествующее ограничение «пока
анализ, без изменений» снято этим запросом на реализацию.

Изменены:

- JPA transaction dialect/config: physical lease возвращается после штатной
  cleanup с сохранением custom isolation/readOnly и прежнего OSIV.
- Notification service/controller: cap 10, cursor, SET LOCAL jit=off;
  SQL account/business/shared-work/full-owner predicates сохранены.
- Новые SnapshotReader/PushService: trusted numeric recipient, current access,
  scoped готовый unread/page, coalescing, afterCommit, revoke и heartbeat resync.
- Scoped latest rating projection и адресное обновление строки/открытого диалога.
- Sidebar/sound/history JS: push, stale-response guards, single-flight/backoff,
  scroll paging, reconnect, coalescing без бесконечного переноса таймера.
  Удалены скрытые GET списка из звукового модуля.
- Web pool 15 отдельно от других JVM; Nginx JSON timings/request ID, async HTTP
  correlation и ограниченный thread dump при длительном pool pending.
- Native PostgreSQL/session/isolation/JIT, scope/push/MVC tests, Node tests,
  actual-MVC browser harness; task details/list и правило SSE/JDBC.

Проверки: 93 Java, 37 Node, 12 Edge/Chromium UI scenarios GREEN. Конкурентный
тест использует 20 request-bound EM, pools 6/15 и настоящий JDBC SessionRepository.
Это регрессия причины инцидента, не полный benchmark всех интеграций.

26.11.2026 в 14:58 МСК установлен только panel-web R326; схема V108 без миграции.
Nginx перечитан без пересоздания, остальные 17 containers/роли/grants/settings
сохранены. Healthy/readiness UP, restarts=0, Hikari max=15/pending=0.
Backup и rollback R324 с pool 6 доступны; rollback/backfill не выполнялись.
Production test sends и операторские mutations не делались.

Evidence вне git:
C:/1C/backup/deploy-feedback-4ca73f33b-20261119/
panel-realtime-17b7ce3-r326-20261126-142000/.
Fresh baseline/head/origin_main 17b7ce389b21cd9ff93b6e176326e654702b1b2e;
те же validated patches перенесены из detached sandbox с exact content/file guards.
Три ранее подготовленных документа 01-325 сохранены. Без stage/commit/push.
Задача 🟣, ручная приёмка остаётся за пользователем.
