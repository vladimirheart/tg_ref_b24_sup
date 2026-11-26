# 01-325 — Причины минутного зависания диалогов и рекомендации

Дата: 26.11.2026, 14:02 МСК.

Промт пользователя:

> вот только что оператор на другой машине поймал проблему загрузки - очень долгий отклик. делали ранее сбор подобных логов - прочекай и дай комменты для исправления и причин поведения

Уточнение: обновление страницы диалогов примерно за полторы минуты до сообщения,
через внутренний IP машины с портом 8080.

Изменены только task details 01-325, task-list и этот новый changelog.
Документированы события 13:51–13:53 МСК: шесть занятых connections, до 40 pending,
30-секундное acquisition wait и до 60.8 секунд HTTP /api/notifications, 500/504.
Все шесть долгих заимствований ведут в NotificationService.findPageForUser;
стек ошибок показывает ожидание дополнительного connection для session save.

Изолированное воспроизведение на точных runtime библиотеках подтвердило
конфликт request-bound JPA HOLD и REQUIRES_NEW Spring Session в одном пуле:
6/6 timeout до освобождения EntityManager и 6/6 success после освобождения.
Контрольный READ ONLY EXPLAIN показал JIT 587 из 611 ms; локальное отключение
JIT внутри одной диагностической транзакции сократило выполнение до 8.88 ms.
Это отдельная SQL-задержка, а не универсальная оценка скорости всей панели.

Рекомендованы короткий JDBC/DTO path с прежними access predicates, проверка
освобождения JPA connection перед session commit, адресная оптимизация SQL/JIT,
single-flight для списка колокольчика и request timings/thread dump диагностика.
Увеличение web pool/HTTP timeout/серверного connection limit не устраняет механизм.

Production-код/config/schema не изменены, перезапусков/deploy/backfill нет.
На 14:01 МСК readiness UP, active=0/idle=6/pending=0, restarts=0; runtime R324/V108.
Raw evidence хранится вне git в latency-17b7ce3-r325-20261126-135500
под C:/1C/backup/deploy-feedback-4ca73f33b-20261119/.
Fresh baseline/head/origin_main 17b7ce389b21cd9ff93b6e176326e654702b1b2e.
Документация подготовлена в detached sandbox и применена теми же patches после
content/exact-file guards; без stage/commit/push. Задача 🟣, исправление кода
не выдаётся за выполненное действие.
