# Политика мониторинга и выбор сотрудников

## Источник и границы

Решение R12 задачи 01-281, 23.10.2026. Владелец явно потребовал при паузе
направления останавливать сами проверки, а не только уведомления.

- Общая для panel-web/ops-worker политика находится в shared-config
  `incident_monitoring.json`; отсутствующий файл означает прежнее включённое
  поведение без создания файла. Не переносить её в локальные настройки браузера.
- Запись атомарная, с межпроцессной блокировкой. Некорректный файл не должен
  молча включать проверки. Сохранять другие направления при обновлении одного.
- Режимы: enabled, paused с OffsetDateTime until, disabled бессрочно.
  Пресеты используют время сервера; истечение паузы действует без перезаписи.
- Изменять политику вправе только ROLE_ADMIN вместе с PAGE_SETTINGS и CSRF.
  Наличие права на чтение инцидентов не даёт права менять мониторинг.
- Нативные schedulers проверяют политику до lease, manual refresh/backend ops
  проверяют её до сетевых обращений и discovery. В batch проверять между
  целями; выполняющуюся операцию не прерывать. Историю не удалять.
- Подавлять новые автоматические incidents/escalations/notifications направления.
  Старый outbox сохраняется и откладывается без расходования attempts;
  после возобновления действуют обычные retries, не обещать удаления backlog.
- Alertmanager — управление приёмом инцидентов, не выключение внешнего
  Prometheus. Не останавливать доставку рабочих сообщений, ботов, liveness,
  storage/DB coordination или защитные readiness checks вместе с мониторингом.
- Старые версии приложения не понимают новый файл; rollback image не является
  гарантией сохранения пауз. При откате обязательно учитывать эту границу.

## Ошибки отдельных транспортных записей — R17, 25.10.2026

- Failed inbox/outbox records сами по себе не создают, не обновляют и не
  повышают приоритет автоматического инцидента — ни high, ни critical.
- Применять это и к текущему snapshot, и к sustained trend: пересчитывать
  историю по системным счётчикам, не доверять старому severity, который мог
  быть повышен только из-за failed records. Историю не переписывать.
- Сохранять записи, SQL/error details, диагностику и ручные recovery операции.
  Не выполнять replay/requeue и не удалять данные ради исчезновения инцидента.
  Error-only signal штатно снимается по новым условиям с честным системным
  пояснением, а не с утверждением «ошибка исправлена».
- В карточке инцидента не выводить списки ошибок отдельных сообщений, UUID
  и SQL-текст. Они доступны в отдельной диагностике транспорта. Это заменяет
  соответствующую часть представления R14–R16.
- Проверки зависшей обработки, очереди и checkpoints остаются прежними;
  изменение их критериев требует отдельного scope. Ручной приоритет R15
  продолжает действовать для допустимых по системным условиям инцидентов.

## Позиции фоновых задач — R18, 25.10.2026

- Возраст progress cursor не является heartbeat. Нет новых данных (lag=0),
  неизвестный источник/позиция или неиспользуемый checkpoint не создают инцидент.
  operator-notification-watch.feedbacks — сохранённая старая позиция, не активная
  задача. Её не проверять, не переписывать и не удалять ради снятия сигнала.
- Для двух действующих задач нужны последовательные наблюдения новых данных
  перед неизменной позицией дольше10мин (UI) /15мин (chat-history). Продвижение,
  нулевой/неизвестный lag и разрыв наблюдений больше2интервалов+1мин сбрасывают
  подтверждение. Нельзя тревожить сразу после долгого idle при появлении свежих
  записей. ID gap не является количеством пропущенных сообщений.
- Подтверждённое отставание отдельной задачи автоматически имеет high, не critical
  по одному возрасту позиции. Ручной приоритет сохраняется по R15. Дублирующий
  aggregate panel-runtime-checkpoints выведен из эксплуатации. Queue/sustained
  incidents оценивают только очередь/зависшую обработку; старые checkpoint severity
  в истории не должны возвращать critical. Старые ложные сигналы снимает обычный
  монитор с честным reason, не ручное удаление данных/истории.
- В существующем shared policy три независимых поднаправления:
  integration_transport.queue, integration_transport.ui_updates,
  integration_transport.operator_notifications. Общий integration_transport
  останавливает все три; enabled дочернего не отменяет паузу родителя.
  Доступны enabled /час /неделя /до даты /бессрочно, прежние права и CSRF.
- Пауза останавливает сами source/history checks и запись новых worker observations,
  новые incidents/escalations/уведомления; старый outbox откладывается без расхода
  attempts. Scheduler общего snapshot проверяет общий выключатель до lease.
  Рабочие watchers, боты и доставка сообщений не останавливаются. Manual read-only
  диагностика/история и maintenance retention не являются рабочими проверками.
- При изменении настройки обновлять статус зависимых строк, сохраняя несохранённый
  выбор в других строках. Policy не переписывать автоматически при deploy.
  Старые образы не понимают новые дочерние ключи; учитывать это при rollback.

## Закрытие инцидента

Ручной переход в resolved/closed требует непустой конкретный resolution_comment
до изменения entity. Комментарий и статус фиксируются в одной транзакции;
ошибка записи события должна откатить статус. Автоматическое восстановление
сохраняет свой существующий системный reason. UI cancel не отправляет PATCH.

## Единый employee picker

- Исходные id/name/value и native FormData остаются контрактом. Показывать
  выпадающий список с поиском; не создавать новых сотрудников свободным вводом.
- Native select/datalist — авторитетный серверный список. Бизнес/scoped поля
  нельзя расширять справочником глобальных пользователей или новыми grants.
- `/api/panel-users/options` используется только для прежних global/legacy
  полей, возвращает минимальные username/label активных незаблокированных
  пользователей. Это не разрешение на назначение: backend checks обязательны.
- Идентификаторы не преобразовывать в Number. Не расширять существующую
  нормализацию legacy CSV и не считать picker исправлением исторического
  exact-identity debt; границы R175 мультибизнеса остаются действительными.
- URL/department delivery targets, реальные контакты и формы создания аккаунтов
  не являются выбором существующего сотрудника и не подменяются picker.
- Стили — общие SCSS/tokens, не отдельная inline тема. Сохранять клавиатурный
  фокус, required validation, submit/reset и динамические server options.

## Typed channel notification selections — R180

- В R180 только channelEditorPanelNotifEmployees/Exclude включают формат
  data-employee-value-format="username-json": значение textarea — JSON-массив
  username strings без trim, с прежним case folding; backend получает typed arrays.
- Остальные поля, кроме явного opt-in R185 ниже, сохраняют native/CSV id/name/value
  и грамматику. JSON-формат не
  означает новый scoped catalog: global options endpoint и его права прежние,
  native select/datalist не расширяются им, backend eligibility остаётся обязательным.
- Пустой присутствующий control авторитетен; fallback только при отсутствии.
  Некорректный typed список блокирует POST, reset восстанавливает загруженный
  defaultValue. Ошибка/неполнота справочника не подменяет сохранённые ключи.
- Observer игнорирует disconnected added nodes: последовательные render/refresh
  меню не должны запускать бесконечный global rescan. Связанные реальные добавления
  в документ по-прежнему монтируются, удаление не меняет данные/гранты.

## Выбор сотрудников при создании инцидента — R185

- Только incidentCreateWatchers дополнительно включает username-json: textarea
  хранит JSON array строк, POST передаёт typed watchers без trim/split ключей.
  Прежний case folding общего picker и backend сохраняется. Пустой присутствующий
  control — пустой выбор, без fallback к прошлому значению. Неверный JSON, не-array,
  нестроковые и blank элементы блокируют POST, показывают ошибку и сохраняют draft.
- incidentCreateOwner остаётся scalar username, hidden input сохраняет точные
  пробелы и CR/LF. Trim используется только для проверки полного blank. Поскольку
  hidden value изменяет attribute/defaultValue, create form reset явно восстанавливает
  исходный default owner; успешный POST сбрасывает форму, отказ — сохраняет draft.
- Исходные id и семантика JSON API сохраняются. Native FormData owner — raw scalar,
  watchers — явный JSON array text; остальные legacy поля не переводятся в JSON.
  CSV fallback для поля без opt-in не расширяет прежнюю грамматику. Общий picker,
  global catalog/его права, native select/datalist и backend eligibility не меняются.
- На момент R185 исправлена только create form: detail owner, add/remove watcher
  и singular user route оставались отдельным UI identity debt. URL/department/CSV route targets не
  переопределяются. При rollout версия workbench меняется вместе с template.

## Точные scalar ключи в карточке инцидента — R186

- incidentDetailOwner/incidentWatcherInput — scalar hidden inputs с прежними id.
  Loaded owner присваивать через DOM value; HTML parsing нормализует CR/LF в
  attributes. Для owner/watcher/singular user route trim проверяет только blank,
  непустой raw key передаётся без изменения, с прежним backend case folding.
- Кнопки удаления получают watcher_identity через DOM setAttribute после render.
  DELETE использует исходный key и один encodeURIComponent. Не заменять его
  похожим логином и не выполнять лишний decode; display text экранируется.
- Существующий scalar incidentRouteUserPicker и исходный incidentRouteTarget
  при type=user используют hidden input. Users остаётся прежним CSV string;
  URL/department/all_operators не становятся employee fields. Прежняя смена типа
  очищает target/оба person pickers; пустое поле не восстанавливает старый key.
- Сохранять core owner draft при refresh/actions/switch, фокус возвращать на
  видимый picker trigger. Удаление watcher возвращает фокус к picker добавления.
  Ошибки API/directory и отсутствующие options не подменяют выбранный scalar.
- Это существующие global/legacy поля, не новый scoped catalog или grants.
  Общий picker, native select/datalist, backend access/eligibility не меняются.
  Cache versions workbench/UI decorator обновляются вместе с template; scalar
  API и native source values остаются строками. Backend/ingress URL decoding
  и остальной upstream identity не считаются проверенными browser fixtures.

## Связанные объекты инцидентов — R19

- Ручное создание использует поиск существующей записи, а не свободный ввод ID.
  Для диалогов нужны PAGE_DIALOGS, для задач PAGE_TASKS, для паспортов
  PAGE_OBJECT_PASSPORTS. Проверка выбора повторяется на сервере при сохранении.
- Ticket ID остаётся точной непреобразованной строкой. GENERAL и неоднозначные
  raw roots (включая коллизию с GENERAL) не выдаются в legacy picker. Business
  ticket дополнительно требует canonical business READ текущего точного username;
  ROLE_ADMIN сам по себе не является ALL_BUSINESSES. Это не миграция scope
  существующих глобальных инцидентов/задач/паспортов.
- external_object — отдельная запись общего справочника портала, по явному
  решению владельца без business_id. PostgreSQL incident_external_objects —
  relational source of truth (V82), не shared-config JSON и не внешний NetBox.
  Запись существует независимо от черновика инцидента и используется многократно.
- Создание записи: только глобальный ROLE_ADMIN и доступ к модулю инцидентов,
  с серверной проверкой в controller/service и CSRF. Бизнес-роли/гранты не дают
  этого права. Пользователи инцидентов могут искать и выбирать существующие записи.
  UI не заменяет серверную проверку; label внешней связи берётся из справочника.
- Поля: обязательное название (200), внешний номер (200), http(s)-ссылка без
  userinfo (2048), описание (5000). Ссылка не запрашивается сервером. Новые ID
  передаются строками, в том числе за границей Number.MAX_SAFE_INTEGER.
- Смена типа/редактирование запроса сбрасывают выбранный ID. Ответы старого
  поиска не восстанавливают его. Сохранять выбор с клавиатуры, native required,
  черновик при переходе в справочник и при ошибке сохранения.
