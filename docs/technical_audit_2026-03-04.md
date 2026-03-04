# Технический аудит проекта (2026-03-04)

## Что проверялось

- Запуск тестов/сборки в `spring-panel` и `java-bot` через Maven.
- Проверка рабочих скриптов запуска (`mvnw`, `run-linux.sh`) на Linux.
- Быстрая валидация соответствия README фактическому состоянию стартовых скриптов.

## Найденные проблемы

### 1) Linux-запуск через `./mvnw` не работает (критично)

**Симптом:** при запуске `./mvnw test -q` получаем `Permission denied`.

**Причина:** wrapper-скрипты лежат в репозитории без executable-бита.

- `spring-panel/mvnw`
- `java-bot/mvnw`

Это ломает документированный сценарий запуска через wrapper на Linux.

---

### 2) Wrapper-скрипты в CRLF и падают в POSIX shell (критично)

**Симптом:** запуск `bash ./mvnw test -q` падает с ошибками вида:

- `set: -\r: invalid option`
- `$'\r': command not found`
- `syntax error near unexpected token 'elif'`

**Причина:** в Unix shell-скриптах (`mvnw`, `run-linux.sh`) сохранены Windows line endings (`CRLF`).

На Linux это делает запуск нестабильным/невозможным в стандартном сценарии.

---

### 3) Проверка `mvn test` блокируется скачиванием parent POM (высокий риск для CI)

**Симптом:** и в `spring-panel`, и в `java-bot` команда `mvn -q test` падает с:

- `Could not transfer artifact ... spring-boot-starter-parent:pom:3.2.5`
- `status code: 403, reason phrase: Forbidden (403)`

**Комментарий:** это может быть как ограничение текущего окружения/прокси, так и проблема настроек репозитория в целевой инфраструктуре. В любом случае сейчас автоматическая проверка тестов не проходит.

## Что работает

- Структура модулей и POM-файлы читаются Maven до шага разрешения зависимостей.
- Документация по запуску и конфигурации присутствует.

## Рекомендации по исправлению

1. Нормализовать окончания строк для Unix-скриптов в LF:
   - `spring-panel/mvnw`
   - `java-bot/mvnw`
   - `spring-panel/run-linux.sh`
2. Выставить executable-бит:
   - `chmod +x spring-panel/mvnw java-bot/mvnw spring-panel/run-linux.sh`
3. Добавить `.gitattributes` для фиксации line endings (минимум для `*.sh`, `mvnw`).
4. Проверить доступность Maven Central из CI/рантайма (proxy, mirror, firewall, credentials).

## Приоритеты

- **P0:** починить `mvnw` + line endings (иначе Linux-старт и локальная проверка не работают).
- **P1:** стабилизировать сетевой доступ к Maven Central/репозиторию зависимостей.
