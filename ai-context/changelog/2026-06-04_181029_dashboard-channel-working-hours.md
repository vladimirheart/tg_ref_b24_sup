# 2026-06-04 18:10:29 — dashboard channel working hours

## Промты пользователя

- `есть отображение рабочего времени, а где его настройка?`
- `да, эта настройка нужна. но настраиваться она должна для каждого канала отдельно на странице настроек в блоке "Каналы (боты)"`

## Что изменено

- в редактор канала на странице `settings` добавлены отдельные поля начала и конца рабочего времени; значения сохраняются в `delivery_settings.working_hours`;
- сохранение канала теперь бережно сохраняет существующие `delivery_settings` и дополняет их новыми рабочими часами, не затирая остальные ключи;
- `DashboardAnalyticsService` научен читать рабочие часы по `channelId` из настроек канала и подставлять их в `activity_stats` вместо общего хардкода;
- для смешанной выборки каналов dashboard отдаёт подпись с источником интервала рабочего времени по основному каналу среза;
- легенда heatmap на dashboard переведена на реальные значения из payload и больше не показывает фиксированное `09:00 - 18:00`.

## Проверка

- `./mvnw.cmd -q -DskipTests compile`
- `git diff --check -- ai-context/tasks/task-list.md ai-context/tasks/task-details/01-121.md spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/java/com/example/panel/service/DashboardAnalyticsService.java spring-panel/src/main/resources/templates/dashboard/index.html`
