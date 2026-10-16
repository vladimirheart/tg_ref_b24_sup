# 01-278 R122: business READ получателя уведомления

Checkpoint: 2026-10-16 15:43:44 MSK.

Промт пользователя: «продолжай».

Baseline HEAD/origin/main: 85043d072d7a87ac2e13b30e408d63c87f2e3382.
Проверены parent R121 261d41b9a72f82d79c988008115ccbe535b98f02,
сообщение и 13 файлов предыдущего commit. Основной checkout исходно чистый;
изменения проверены в detached worktree.

Фактические изменения (10 файлов):

- NotificationBusinessAccessSql: единый business READ predicate с текущими
  canonical recipient, enabled/unblocked account, active business и активными
  membership/ALL_BUSINESSES READ-ролями. ROLE_ADMIN не заменяет business grant;
  коллизия нормализованного имени закрывает доступ, future business получает
  только явно выданный ALL_BUSINESSES scope.
- NotificationService и NotificationRepository: тот же predicate в условном
  ticket-bound INSERT, list/page/count и read/read-all. Late revoke перед
  SQL запрещает доставку; скрытые rows не отмечаются прочитанными. Нет
  fallback-рассылки или SSE refresh при отклонённом INSERT.
- NotificationBusinessAccessPostgresIntegrationTest: 16 новых реальных
  PostgreSQL/Spring/HTTP cases для разграничения A/B, отзыва grants в старой
  сессии, lifecycle, case collision, всех READ-ролей, future/all scope,
  independent grants, SQL snapshot и cursor/count/read-all.
- Existing NotificationScopePostgresIntegrationTest явно получает reader
  account/membership; две минимальные JDBC fixtures дополнены canonical
  users/business access schema. Прежние owner/general assertions сохранены.
  S0-контракт и detail задачи обновлены.

Валидация: 64/64 test cases в 9 suites GREEN по XML reports.
Включены полная Flyway chain V1–V60 и R121 owner/immutability, GENERAL,
legacy/unresolved/generic compatibility, pagination, HTTP revoke и
queue/realtime regressions. Maven wrapper main/test compilation и
diff check GREEN. Переносятся только эти 10 файлов после exact-set/hash
сверки; Sass output восстанавливается до baseline.

Новых migrations/flags нет. Рабочий экземпляр находится на другой локальной
машине и не затрагивался. Рабочие DB migrations/backfill/config/flags,
deployment, staging/commit/push не выполнялись. Локальная PostgreSQL была
изолированной инфраструктурой Testcontainers.

BUSINESS access фильтруется независимо от strict flag; исторические и
generic/unresolved уведомления сохраняют compatibility R121. Этот этап
не задаёт global page/system/task policy и не закрывает business-recipient
SSE и остальные producers. Общие write/realtime, shared-channel UX, graceful
resume, provider E2E, S2B/S3–S6 ещё PENDING. Статус 🟡, rollout не разрешён.
