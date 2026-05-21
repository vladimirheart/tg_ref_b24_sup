# 2026-05-21 10:40:21 - Notification text normalization and dialog routing

## Промпты пользователя

- `уже лучше.`
- `но смотри, встречается битая колировка: "PkPsPIPsPu CfPsPsP±C%aPuPSPePu PI`
- `PsP±CЪР°C%PuPSPēPē`
- `361860bc-66ea-4327-9ada-58fd19fb1af5:`
- `тестовое описание`
- `20.05.2026, 14:23:13"`
- `и при клике на оповещение должно открываться то, куда оно должно по логике привести`

## Что сделано

- В `spring-panel/src/main/java/com/example/panel/service/NotificationService.java` добавлена нормализация уведомлений на чтении и на сохранении:
  - старые legacy URL вида `/dialogs?ticketId=...` переводятся в актуальный маршрут `/dialogs/{ticketId}`
  - для исторических записей с mojibake добавлен repair-проход по битым сегментам текста, чтобы не ломать нормальные фрагменты пользовательского сообщения
  - для самых частых старых notification-префиксов добавлены точечные legacy replacements, чтобы уже сохранённые записи показывались читаемо
- В `spring-panel/src/main/resources/static/js/sidebar.js` клиент sidebar-уведомлений научен:
  - корректно учитывать флаг `read` из `NotificationDto`, а не только legacy `is_read`
  - при клике по уведомлению с диалогом открывать актуальный target `/dialogs/{ticketId}`
  - если пользователь уже находится на странице диалогов, открывать нужное обращение через `window.openDialogDetailsByTicketId(...)`, не теряя текущую рабочую логику workspace
- В `ai-context/tasks/task-list.md` добавлена задача `01-100` про нормализацию текстов и маршрутов sidebar-оповещений.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Что дальше

- Если в runtime останутся уведомления с экзотической битой строкой, которой нет среди текущих cp1251/utf8 legacy-шаблонов, её уже можно будет локально диагностировать по содержимому `notifications.text` и добавить как ещё один точечный паттерн без переделки всего bell flow.
