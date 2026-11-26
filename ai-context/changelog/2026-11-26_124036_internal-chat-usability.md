# 01-323 — Внутренняя переписка: ФИО, отправка, непрочитанные и прокрутка

Дата: 26.11.2026, 12:40 МСК.

Промт пользователя:

> сё неплохо, но указанная стрелкой строка тут не нужна вообще. плюс должен отображаться не логин, а ФИО из карточки сотрудника, если оно заполнено. да и метод отправки сообщения должен быть такой-же, как и в основном диалоге с клиентом.
> если сообщения переполняются, то нужно отображать последнее сообщение, а не оставлять поле статичным. ну и смайликов тут точно не хватает.
> чуть не забыл: нет понимания что есть какое-то непрочитанное сообщение у внутренней переписки.
> ах, да: при открытом окне истории, скролл не должен передаваться на основную страницу

Изменено:

- Убрана штатная поясняющая строка. Автор выводится по ФИО из карточки через
  numeric employee ID, с fallback на snapshot login.
- Подключены общий с клиентским полем send-mode control, Enter/Ctrl+Enter,
  Shift+Enter, кнопка с самолётиком и вставка эмодзи. Тексты/drafts/endpoints
  внутренней и клиентской отправки сохраняются отдельными.
- История следует за последним сообщением у нижнего края; чтение старых не
  сбрасывает позицию, доступен переход к новым. Старые страницы дополняются.
- Добавлены персональные persisted read markers и scoped batch счётчиков:
  collapsed summary и строка списка показывают непрочитанные; свои сообщения
  исключены, ack не закрывает будущее и не влияет на клиентский unread.
- Wheel/touch внутри открытого окна не прокручивают background, в том числе
  после закрытия вложенной modal. Используется штатный composer overlay.

Области: collaboration service/controller, unread controller, V107, два JS,
template/SCSS/app.css, PostgreSQL/MVC tests и actual-MVC browser harness,
правило истории/внутренних сообщений, task details/list.

Проверки: 48 Java tests, 26 Node cases, 6 Edge/Chromium сценариев и V1–V107 chain
на отдельной PostgreSQL; Java 17/JAR/resources/runtime integrity GREEN.
Реальные тестовые provider sends и отметки прочтения за пользователя не выполнялись.

Установлен только panel-web, schema V107 применена migration owner до запуска
нового web. Контейнер healthy/readiness UP. Остальные 17 project containers,
роли/GENERAL grants/questionnaire bindings и settings.json сохранены.
Backup/rollback pointer и evidence:
C:/1C/backup/deploy-feedback-4ca73f33b-20261119/
internal-chat-d728c1f-r323-20261126-122600/.

Работа начиналась с local HEAD d728c1f и origin/main b27c873; во время задачи
пользователь опубликовал предыдущий commit. Fresh apply HEAD/origin/main:
d728c1f56283873ecf7487f094df5b4b4e3d7566. Detached sandbox, content/exact-file
guards, затем те же patches в main. Stage/commit/push/backfill/rollback не выполнялись.
Задача 🟣, ручная приёмка остаётся за пользователем.
