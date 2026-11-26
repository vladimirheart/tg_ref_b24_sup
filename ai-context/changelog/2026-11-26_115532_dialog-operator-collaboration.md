# 01-322 — Действия с бизнесовым диалогом и внутренняя переписка

Дата: 26.11.2026, 11:55 МСК.

Промт пользователя:

> какая-то херня:
>
> - пытаюсь "Взять диалог себе" - говорит "диалог не найден"
> - пыаюсь изменить бизнес, получаю пустоя список и предупреждение - смотри на скрин. плюс на скрине видно что последняя буква в сообщении оператора или клиента почему-то переносится. это браузер edge, такое-же поведение и в chrome
>
>
>
> ещё появилась идея скрытых сообщений внутри диалога. например когда 2 оператора в одном диалоге и им необходимо переписываться между собой. т.е. сообщения не должен видеть клиент, а только операторы - любые, приглашённые в диалог или нет - не важно

Выполнено:

- BUSINESS take подключён к существующему assignment workflow по canonical owner,
  Authentication и revision. Legacy route и его guards сохранены.
- Добавлен отдельный перенос BUSINESS с OPERATE в обоих бизнесах, locks/CAS,
  атомарными аудитом/outbox, сохранением истории/анкеты и сбросом категорий и
  ответственного исходного бизнеса. Picker обрабатывает readonly, пустые цели,
  поздние ответы и конфликты; GENERAL scope не переклассифицируется.
- Исправлено двойное резервирование ширины сообщения под внешнее меню.
- Добавлена отдельная внутренняя текстовая переписка с точным author/owner,
  current READ/OPERATE, append-only storage, UUID idempotency, paging и безопасным
  DOM-выводом. Клиентские транспорты, история и AI её не получают. Приглашения
  в участники для доступа не требуются; текущие права остаются обязательными.
- При запуске обнаружено удержание пула JPA-чтением каналов во время Telegram
  HTTP. Список каналов возвращает сохранённую информацию; refresh выполняется
  явными create/update. Лимит пула панели сохранён равным 6.
- Штатный migration owner применил V106: таблицы сообщений и аудита переносов.
  Обновлён только panel-web; итог R2 healthy/readiness UP, установленный JAR/HTTP
  assets проверены. Остальные 17 project containers, настройки/права/анкета
  сохранены. Backup и rollback pointer сохранены; backfill/rollback не выполнялись.

Области: DialogOperatorCollaborationService/Controller, ChannelTransportService,
V106, dialogs actions/business/collaboration JS, template, SCSS/app.css,
PostgreSQL/MVC/Node tests и browser harness, правила scope/истории, задача 01-322.

Валидация: 129 Java tests, 26 Node cases, 6 actual-MVC Edge/Chromium сценариев,
полная V1–V106 chain на отдельной PostgreSQL; JAR Java 17 и runtime integrity.
Тестовые provider sends и реальные действия в клиентском диалоге не выполнялись.
Ручная приёмка остаётся за пользователем, статус 🟣.

Baseline HEAD/origin/main: b27c873b65e9595dbd0c645f22d32f58ba800ef2.
Detached worktree, content/exact-file guards, затем те же patches в main.
Artifacts: C:/1C/backup/deploy-feedback-4ca73f33b-20261119/
dialog-ops-b27c873-r322-20261126-114200/. Stage/commit/push не выполнялись.
