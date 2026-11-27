# 01-327 — Настройки проекта и чтение задач участниками

Промт пользователя:

```text
настройки у панели очень шумные и малоинформативные. плюс в корне непонятно как добавить участника проекта.
и как дополнение: если сотрудник является участником задачи, он почему-то не видит этой задачи
```

Уточнение пользователя:

```text
именно настройка проекта на странице /projects. могу дать скрин
```

Карточка проекта разделена на сведения и участников внутри прокручиваемого body.
Добавление видно перед длинным roster; в каталоге есть отдельная кнопка участников,
после создания открывается состав. Дополнительные поля, архивирование, передача
владения и аудит свёрнуты. Ввод сохраняется между вкладками; повторный SAVE закрыт,
ошибка GET после подтверждённого create не теряет ID проекта. Отзыв закрывает окно
также во время opening transition. Общая страница настроек панели не изменялась.

Task assignee/co/watcher получают READ по привязанному users.id. Сохранены module/
account/owned-root gates и отдельные WRITE/OWNER grants; проект не открывает задачи.
«Мои задачи» учитывает все три роли по ID. V109 связывает existing roster exact,
восстанавливает отсутствующего assignee из tasks.assignee и сохраняет неизвестные
подписи с NULL. System writer записывает assignee сразу. FK ON DELETE SET NULL и
сохранение исторических bindings не дают новому аккаунту унаследовать прежний
логин. Отзыв участия независим от явного grant; READ блокирует удаление participant
до commit. Roster UI различает происхождение READ. Контракт — правило 13 и 03.

Исходники, SQL, интерфейс и сборка готовятся в isolated worktree от
996dec46e2ee29c92e2e152f3811ef8c4a5cf29f. Рабочая БД/production не используются;
новой web-сборке нужна V109 до старта. Staging/commit/push/deploy агент не выполняет.

Итоговая проверка: 170 Java tests/11 suites одним GREEN run, 16 Node checks,
8 actual Thymeleaf/Edge scenarios light/dark/низкие/мобильные окна. Real disposable
PostgreSQL и миграции, race read/revoke, numeric identity/account/module fences,
assignee restoration и независимый явный grant проверены. Предварительный RED
fixture и native-only SSL probe исправлены; ограничения тестов не ослаблялись.
SCSS/JAR/syntax/diff/hash/scope GREEN, посторонние EOL-only CSS исключены.
Native test cluster остановлен после nonce verification; рабочая БД не использовалась.
Задача 01-327 — 🟣. Evidence, точный scope 25 файлов и commit helper — в target/.
