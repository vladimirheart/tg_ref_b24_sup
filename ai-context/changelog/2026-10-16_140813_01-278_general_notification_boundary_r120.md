# 01-278 R120: scope до записи legacy уведомлений

Checkpoint: 2026-10-16 14:08:13 MSK.

Промт пользователя: «продолжай».

Baseline HEAD/origin/main и remote main:
`9f35f66857d886f3d707cfe17fb251dd12746a9c`. Проверены parent R119
`c86a99a459be6292110553268a833bd2f2e54aac` и его 10 изменённых файлов.

Фактические изменения (15 файлов):

- `OperatorNotificationWatcher`: non-GENERAL history/SLA boundary и повторная
  проверка после channel lookup; курсор пропускает GENERAL, отказ delivery
  не регистрируется как успешная рассылка.
- `NotificationService`: ticket-bound `notifyDialogUsers`, SQL snapshot guard
  непосредственно в INSERT, фактический набор доставленных получателей;
  SQL rechecks recipient reads и запрет scope-denial fallback на всех.
- `AlertQueueService`, `DialogRealtimeEventService`,
  `DialogAiAssistantEscalationService`: явный ticket ID до защищённой delivery.
- Обновлены шесть existing test files и добавлен
  `GeneralNotificationBoundaryPostgresIntegrationTest`; S0-контракт и detail
  задачи дополнены фактическими границами и оставшимися PENDING.

В detached worktree 79/79 cases в десяти Surefire XML reports GREEN.
Проверены GENERAL/unknown/mixed owner denial, поздние изменения перед SQL
INSERT и child reads, предотвращение global fallback, точные ID/URL,
нормализация/исключение получателей, watcher cursor и truthful audit,
AI escalation, системные сообщения и полноценный Spring/PostgreSQL HTTP
history/notification-read flow. Первый test-compile потребовал добавить import
нового assertion; защитные assertions сохранены. Diff check GREEN.

Основной checkout получает только проверенный exact file set после
SHA-256 UTF-8/LF сравнения. Sass output не входит в scope. Staging/commit/push,
рабочие migrations/backfill/config/flags и deployment не выполнялись.
Рабочий экземпляр на другой машине не затрагивался; PostgreSQL проверки
использовали изолированные Testcontainers.

Canonical scope существующих notifications/read APIs и business-recipient
authorization ещё не завершены. GENERAL write/realtime, shared-channel UX,
graceful resume, provider E2E и прочие gates 01-278 остаются PENDING.
Статус 🟡; rollout этим этапом не разрешён.
