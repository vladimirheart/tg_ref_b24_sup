# 01-278 — исторический S2B снят; оставшиеся продуктовые решения

## Решение владельца — 2026-10-27, R190

Промт пользователя:

> тогда продолжай работу. старые данные в корне не интересны - проект ещё не запускался в прод, все данные являются тестами не имеющими ценности

Исторические alias mapping, классификация пустых/спорных records, history
channel mismatch, dry-run и backfill больше не нужны для выпуска. Прежняя
форма S2B остаётся в Git history. Не требуется отдельно решать судьбу двух
старых задач и 105 инцидентов, переносить старые counts/ID/связи или сохранять
тестовую историю. Это снимает historical S2B gate, а не утверждает новые права.

Первый production запуск проектируется с чистой базой и актуальной схемой.
В текущем source шаге база/файлы данных не удалялись и не пересоздавались;
рабочие проект и Docker тестового стенда на другой машине/в другой сессии
C:\Users\SinicinVV\git_h\tg_ref_b24_sup.

## Что остаётся согласовать для новых данных

| Область | Состояние / нужное решение |
| --- | --- |
| Manual tasks (R12) | **PROPOSED_NOT_APPROVED.** Ticket-derived задачи наследуют business canonical ticket; ручные задачи независимы и могут иметь несколько поддерживаемых links разных бизнесов. Ещё нужны собственная visibility/provenance и отдельный допуск к каждому link. Links не дают доступ и не назначают owner. |
| Projects / boards / global Kanban | **PENDING:** scope владельца, global-system grants и фильтрация задач во всех projections. ALL_BUSINESSES не является global-system правом. |
| Incidents / notifications | **PENDING:** тип/owner новых business/shared/global событий, получатели и отдельное право просмотра. Delivery channel не расширяет data scope. |
| Shared channel | Явный выбор business/«Без бизнеса» до вопросника уже зафиксирован. **PENDING:** precedence иных verified sources и поведение при их конфликте/отсутствии. |
| Ticket actions | Реализованные scoped guards сохраняются; оставшиеся action → READ/OPERATE/MANAGE/CONFIGURE paths требуют полной согласованной матрицы и negative tests. |

R12 остаётся предложением в части ещё не утверждённой visibility/link policy:
отмена сохранения двух исторических задач не даёт product signoff. Проверка
каждой связи обязательна для lists, search, projects/Kanban, details, files,
notifications/SSE, exports и AI context. Неоднозначный owner новой ticket-derived
задачи остаётся fail-closed, без угадывания по links или каналу.

## Условия реализации и первого запуска

- Для каждого нового path фиксируются contract/approver, критерии приёмки
  и точный scope; неутверждённые действия остаются deny-by-default.
- Чистая схема и новые записи должны пройти FK/owner/child integrity,
  single/many/all/general scope, отзыв прав и cross-business negative tests.
- S2A public/composite locator и security guards остаются действующими.
- Подготовка чистой базы тестового стенда, schema apply, совместимые web/
  worker/bot images, feature flags и первый production запуск требуют своего
  runtime плана/checkpoint/rollback/приёмки; source commit их не выполняет.
