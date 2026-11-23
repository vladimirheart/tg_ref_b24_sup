# 01-298: диалоги из профиля сотрудника и фото заметок

Baseline: `73753a7649aa7d30a912cfed2a0cada8acce9777`.

В карточке `/client/{userId}` добавлено открытие обычных обращений и GENERAL поверх
профиля через существующий runtime паспорта. Закрытие сохраняет страницу, раскрытые
блоки и фокус; href поддерживает отдельную вкладку. Проекция проверяет владельца,
канал и однозначность legacy ID. Обновлены версии embedded JS и CSS профиля.

Исправлен 404 фото заметок: появился отдельный защищённый upload с регистрацией
назначения в существующей knowledge_article_files; для старых фото читается точная
markdown-ссылка в knowledge_notes.body. Ссылки и файлы переносить не требуется.
Защита article attachments имеет приоритет, поэтому заметка не открывает закрытую
статью. Предпросмотр работает до сохранения, metadata переживает пересоздание сервисов.
Требуются PAGE_KNOWLEDGE_BASE и действующая привязанная учётная запись.

Проверки: main/test compile, 50 targeted Java tests / 12 suites, 4 браузерных сценария
390/1440 × light/dark, source audit (79 templates / 50 pages), Node syntax и diff check.
Использованы только собственные одноразовые PostgreSQL и loopback browser fixtures.
В браузере дочерняя карточка/upload/save — fixtures; PostgreSQL HTTP tests отдельно
проверяют настоящие сервисы доступа, сохранение заметки и bytes из object storage.

Прежний DialogKnowledgeNotesSourceContractTest остаётся RED из-за устаревшей ожидаемой
версии JS (20261119-session-relogin вместо baseline 20261120-time-contrast-r1);
assertion не ослаблялся. Финальные targeted suites GREEN, failures/errors/skips=0.

Доставка: пересобрать backend image и пересоздать panel-web. Миграций нет.
Staging/commit/push/deploy и изменения рабочей БД не выполнялись.
Временная среда проверок удаляется после guarded source transfer.

Промт пользователя:

> по задаче 01-298 делалось открытие диалогов. нужна такая-же штука и из профиля сотрудника.
> и ещё: в базе знаний, во вкладке заметкок, к заметке можно приложить фото. но при попытке открыть загруженное фото, возвращает 404-ю. адрес открывает "[http://127.0.0.1:8080/api/attachments/knowledge-base/c8a9ddc5-831c-4ce7-a0b8-cca080989956_photo_2026-07-22_11-11-39.jpg](http://127.0.0.1:8080/api/attachments/knowledge-base/c8a9ddc5-831c-4ce7-a0b8-cca080989956_photo_2026-07-22_11-11-39.jpg)"
