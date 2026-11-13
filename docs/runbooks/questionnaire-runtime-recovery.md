# Восстановление запуска назначенных вопросников

## Подтверждённые данные 13.11.2026

Рабочий Docker находится на другом сервере. Пользователь подтвердил:

- `panel-web`, `ops-worker`, `bot-runner` используют tag `iguana-panel:local`;
- у Telegram канала 1 (`tst_int_SV_bot`) активны EXPLICIT_SELECTION привязки
  «СушиВёсла» → `working_r322_sushi` и «Блинбери» → `working_r322_bliny`;
- тестовая business-привязка этого канала выключена;
- у каналов 2 (MAX) и 3 (`Flisoff_bot`) обычный channel template заполнен;
  присланный SQL не проверяет их отдельные GENERAL привязки;
- в окружении `panel-web` отсутствуют `MULTIBUSINESS_*` и
  `SPRING_APPLICATION_JSON`.

В исходном production Compose не было передачи rollout flags. Значения класса
`MultibusinessFeatureFlags` по умолчанию — false. Назначение шаблона в БД не
включает эти флаги: runtime API отклоняет scoped вопросник, а прежний клиент
показывал одинаковое предложение `/start` для любого отказа API. Потеря
runtime overrides при пересоздании объясняет наблюдаемый сбой. JVM arguments,
внешние конфиги и effective flags удалённо не проверены; восстановление на
сервере и functional acceptance ещё не выполнены.

## Постоянная настройка

После выпуска 01-289 production Compose передаёт восемь `APP_MULTIBUSINESS_*`
переменных из рабочего `.env` во все три panel-сервиса. `application.yml`
отображает их на canonical Spring properties. Defaults остаются false.
Префикс `APP_` обязателен: `MULTIBUSINESS_FOUNDATION_ENABLED` конфликтует с
read-only alias Spring configuration properties и не является заменой.

Для восстановления ранее использованного R319/R322 режима в существующем
рабочем `.env` нужны следующие значения (обновить существующие строки, без
дубликатов; остальные настройки сохранить):

```dotenv
APP_MULTIBUSINESS_FOUNDATION_ENABLED=true
APP_MULTIBUSINESS_TICKET_WRITE_REQUIRES_BUSINESS=true
APP_MULTIBUSINESS_SHARED_CHANNEL_RESOLUTION=true
APP_MULTIBUSINESS_GENERAL_CHANNEL_RESOLUTION=true
APP_MULTIBUSINESS_GENERAL_TICKET_QUEUE=true
APP_MULTIBUSINESS_UI_SELECTOR=true
APP_MULTIBUSINESS_STRICT_READ_SCOPE=false
APP_MULTIBUSINESS_MANUAL_TICKET_CREATION=false
```

Этот набор восстанавливает прежний режим, а не включает strict-read или ручное
создание. Canonical properties из более приоритетного `SPRING_APPLICATION_JSON`
или command line сохраняют свой приоритет; противоречащие overrides нужно
сначала сверить с фактическим release config.

## Применение в существующем Docker-проекте

Нужны новый общий image (панель + встроенные JAR Telegram/MAX/VK) и пересоздание
трёх сервисов. Обычный `docker restart` не перечитывает `.env`. Новых миграций
в 01-289 нет; перед выпуском проверить совместимость текущей схемы с выбранным
image и сохранить прежние image IDs/release config как rollback point.

Сначала получить фактические Compose paths и IDs, без вывода окружения:

```powershell
docker inspect tg_ref_b24_sup-panel-web-1 tg_ref_b24_sup-ops-worker-1 tg_ref_b24_sup-bot-runner-1 | ConvertFrom-Json | Select-Object Name,Image,@{Name='ComposeFiles';Expression={$_.Config.Labels.'com.docker.compose.project.config_files'}},@{Name='WorkingDirectory';Expression={$_.Config.Labels.'com.docker.compose.project.working_dir'}}
```

Использовать тот же project name, env-file, порядок Compose files и overrides,
которые принадлежат рабочему выпуску. Если он использует private release JSON,
сначала подготовить его новый проверяемый вариант с этими флагами и новым image;
старый JSON может перезаписать значения из `.env`.

Для проекта, действительно запущенного из обновлённого
`docker-compose.production-contour.yml`, операции такие:

1. Сохранить рабочие `.env`, release config и image IDs в прежнем private
   rollback-каталоге; дать прежним image IDs отдельные rollback tags до build.
2. Обновить восемь строк в существующем `.env` указанными значениями.
3. Теми же Compose arguments выполнить `config --quiet`, затем `build panel-web`
   (общий `docker/panel.Dockerfile` собирает также все bot JAR).
4. Теми же Compose arguments выполнить
   `up -d --no-deps panel-web ops-worker bot-runner`. Это плановый перезапуск
   panel и bot процессов; данные и назначения сохраняются.
5. Проверить `ps`, health/readiness трёх сервисов, затем `/start` в каждом боте.
   В `tst_int_SV_bot` ожидается выбор двух бизнесов с полными именами и первый
   вопрос выбранного шаблона. У `Flisoff_bot` и MAX проверить их назначенный
   GENERAL/обычный вопросник. Довести одну тестовую заявку до создания и проверить
   её scope и отсутствие дубля при повторной доставке.

После пересоздания безопасно вывести только новые флаги:

```powershell
docker exec tg_ref_b24_sup-panel-web-1 sh -c 'env | sort | grep ^APP_MULTIBUSINESS_'
```

Если проверка не проходит, сохранить код ошибки и выполнить диагностику ниже.
При откате восстановить прежние image references и release config/flags из
сохранённого private rollback point. Не удалять этот point до приёмки.

## Диагностика после выпуска

Клиент теперь показывает технический код вместо бесконечного `/start`:

| Код | Что проверить |
| --- | --- |
| `QNR_CONFIG` | flags, active bindings, template SHA, пригодность каталога; 404/409 fresh choices |
| `QNR_ACCESS` | bot principal, разрешения internal API, подпись и время запроса; 401/403 |
| `QNR_SERVICE` | доступность panel API, сеть, readiness; 5xx/IO/timeout |
| `QNR_VERSION` | согласованность backend/bot JAR и writer/media acknowledgement |
| `QNR_RESPONSE` | корректность JSON ответа API |

Предложение `/start` остаётся для действительно устаревшего выбранного меню.
В WARN `Questionnaire runtime lookup failed` выводятся только route без query,
код и HTTP status. Тела ответов, токены, подписи и содержимое вопросников не
записываются этим diagnostic logger.

Не требуется перепривязывать исправные шаблоны, очищать Redis/БД или включать
legacy fallback. Старый legacy черновик MAX после обновления можно явно заменить
командой `/start` либо удалить `/cancel`; обычное сообщение по-прежнему не
продолжает черновик с неподтверждённым scope.
