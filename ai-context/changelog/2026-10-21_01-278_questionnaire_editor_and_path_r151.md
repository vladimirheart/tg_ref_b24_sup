# 01-278 — редактор бизнес-presets и финальный путь вопросника (R151)

Дата: 2026-10-21, Europe/Moscow. Статус задачи 🟡.

Промт пользователя:

> продолжай

Сохраняется выбранный размер пакета:

> давай дальше большим пакетом

Действующее разрешение на фиксацию:

> продолжай. возьми более широкий пакет.
> коммит и пуш разрешаю сразу после внесения правок без моего подтверждения - разрешение только в течении этих суток

Разрешение действует только 2026-10-21 Europe/Moscow, до 2026-10-22 00:00 +03:00.
Оно заменяет default manual git flow на эту дату, не разрешает rollout.
Рабочий экземпляр проекта — на другой машине, в другой сессии агента.

Baseline HEAD/origin/main/actual remote main: 8cf6d3fb181b2c10616065e2d5e75ce6bfd378ac,
parent 2cd0713d2e7e21b25a4be41eaa67f077dbd27bcc; actual R150 32 files проверен.
Detached sandbox: .tmp/01-278-r151. Source apply, stage, commit и push выполняются
отдельно после GREEN, exact 21 files/content guards и повторной проверки remote/grant.

- На /businesses/manage добавлен редактор собственных готовых полей: явный
  активный бизнес, группа/поле, строки вариантов, добавление/удаление и очистка.
  PUT использует SHA из GET; 409 сохраняет черновик, обновление версии выполняется
  явно. Во время записи владелец и поля заблокированы. Задержанный GET другого
  бизнеса не подменяет текущий экран. Значения выводятся через textContent/value.
- В существующем редакторе шаблона доступен явный справочник для просмотра:
  прежний общий, конкретный бизнес или «Без бизнеса». GET .../questionnaire-presets/resources
  требует тех же foundation/PAGE_SETTINGS/ROLE_ADMIN; business ADMIN/ALL_BUSINESSES
  не заменяют эти права. В бизнес-контексте нет global fallback; GENERAL не
  получает бизнес-ресурсы. Выбор здесь — предпросмотр, привязка остаётся отдельной
  операцией в каналах. Ссылки, exclusions и маршруты остальных шаблонов сохраняются
  при общей сериализации; проверка их forward targets использует собственный flow.
- Только v3 catalog/selection применяет QuestionnaireFlowContract: до 200 вопросов,
  уникальные ID и effective keys (binding_key либо ID), стабильная сортировка order,
  boolean required/dashboard, select IDs/labels и однозначные переходы вперёд на
  существующие вопросы. Ключ problem допускается для свободного ответа. Общая
  business-привязка и v1/v2 catalogs сохраняют прежний admission contract; отдельный тест
  доказывает, что несовместимый flow доступен старому каталогу и закрыт для v3.
- До первого нового root/child INSERT проверяется весь финальный v3 путь:
  обязательные ответы на посещённых шагах, отсутствие ответов из пропущенной
  ветки, ID/key/type/text/dashboard metadata, select value ID/label/text, preset
  membership/exclusions/согласованная локация и problem projection. Необязательный
  свободный ответ можно опустить; обязательный вопрос вне выбранной ветки не
  требуется. Порядок transport attributes не определяет маршрут. Exact replay
  существующего root сохраняет прежний frozen proof и не перевалидирует новый flow.
- Telegram теперь использует строку preset-ответа как route value, как MAX/VK;
  JSON restore и отсутствие промежуточного ответа проверены. MAX production code
  не менялся: отдельный сценарий двух возвратов и смены ветки подтвердил очистку
  старых ответов. История provider updates не становится серверным answer ledger.
- Validation: 468 Java tests / 19 XML и 11 Node checks,
  failures/errors/skips=0. Backend 262/12; SDK + Telegram/MAX 206/7.
  PostgreSQL/Spring transaction/inbox/rollback, v1/v2 compatibility, HTTP access/CSRF,
  templates/settings, session restore/retry, быстрые переключения и stale CAS проверены.
  Новые v3 writer fixtures передают canonical question text/dashboard из шаблона.
- Первый серверный прогон: 247 tests, errors=1 — flow guard ошибочно стоял в общем
  bind path и ломал прежний negative fixture с duplicate question. Guard перенесён
  на v3 capture; старые assertions сохранены и усилены v3 assertion. Повторены
  зависимые серверные suites и добавлены четыре compatibility cases. Исходные логи
  сохранены в .tmp/r151-*. Sass outputs исключены из source-пакета.
- Source-схема остаётся V75; новые starts требуют ACK=3, но один ACK не доказывает
  совместимость произвольных старых образов с новым route guard. Рабочий экземпляр
  проекта находится на другой машине и в другой сессии агента. Здесь source и
  isolated tests; runtime revision/DB/flags неизвестны. Deploy/restart/working
  migration/backfill/flags/provider sends и browser/live приёмка не выполнялись.
- Остались >100 choices, scoped media/edit/feedback, live Redis/provider/proxy,
  rolling-upgrade/retention, прочие S3–S5, S2B и S6 gates. Общий статус 🟡.


Первый record/source apply остановлен EOF guard: две лишние пустые строки в
новых окончаниях task/S0. Основной checkout не изменён; окончания исправлены,
повторный operator — r151-source-apply-v2.cjs. Java/JS source не менялся.
