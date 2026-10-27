# 01-278 R184: ручная проверка остановки и запуска одного бота

Дата: 2026-10-27, Europe/Moscow.

Промт пользователя: «проверил на одном боте: остановка и запуск работают».

В task details добавлен отдельный acceptance checkpoint: пользователь подтвердил
положительный start/stop сценарий одного бота после обновления panel-web R184.
ID/платформа и authority проверяющего не указаны и не выводятся из предыдущего
тестового аккаунта. Это user-reported evidence рабочего экземпляра на другой
машине/в другой сессии. Агент source-сессии не вызывал runtime/write endpoints,
не проверял Docker и не повторял deploy. Исторический rollout changelog не менялся.

Рабочая revision из предыдущего checkpoint: 8310d1f7f0001314abdc60e52516e563168bfee1;
нового image/deploy или изменения остальных сервисов это дополнение не объявляет.
Rollback pointer сохраняется. Fleet/provider acceptance, оставшаяся матрица прав,
S2B и другие S3–S6 gates открыты; общий статус 01-278 остаётся 🟡.

Baseline source checkout: af5bb344129dacb45899bc6402b56020900bbf29, clean main/origin/main/actual remote.
Изменены только task-details/01-278.md и этот новый append-only changelog.
Проверки: exact changed-file set, pinned baseline/content hashes, diff --check,
source и runtime files вне scope. Тесты приложения не запускаются для metadata-only
дополнения. Source apply и отдельный Git publisher разделены; действующий grant
commit/push — до 2026-10-28 00:00 +03 (конец 27 октября Europe/Moscow).
Фактические apply/commit/push/deployment статусы фиксируются отдельно в ignored
r184-smoke-result.json после каждого действия.
