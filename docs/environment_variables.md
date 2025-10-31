# Настройка переменных окружения

Оба приложения читают чувствительные настройки из `.env` в корне репозитория и из стандартных переменных окружения операционной системы. Ниже приводятся обязательные параметры и примеры для Windows и Linux.

## Обязательные значения

| Переменная | Назначение | Где используется |
| --- | --- | --- |
| `TELEGRAM_BOT_TOKEN` | токен Telegram-бота | Python-бот, Flask и Spring сервисы |
| `GROUP_CHAT_ID` | ID рабочей группы/чата | Python-бот и Flask панель |
| `SECRET_KEY` | ключ Flask-сессий | Flask панель |
| `DATABASE_URL` | (опционально) URL внешней БД вместо `tickets.db` | Python-бот/Flask |
| `SUPPORT_BOT_DB_PATH` | путь к SQLite для Java-бота | `java-bot` |
| `SPRING_DATASOURCE_URL` и `SPRING_DATASOURCE_USERNAME/PASSWORD` | подключения к СУБД для `spring-panel` | Spring панель |

> ℹ️  Если значения `SPRING_DATASOURCE_*` не заданы, Spring использует встроенную H2 в памяти.

## .env в корне

Создайте файл `.env` рядом с `config.py` и заполните минимумом:

```ini
TELEGRAM_BOT_TOKEN=123456:ABCDEF
GROUP_CHAT_ID=-1001234567890
SECRET_KEY=super-secret-key
SUPPORT_BOT_DB_PATH=bot_database.db
```

`python-dotenv` автоматически загрузит файл при старте Python-сервисов.

## Windows PowerShell

```powershell
# Временный экспорт на текущую сессию
$env:TELEGRAM_BOT_TOKEN = "123456:ABCDEF"
$env:GROUP_CHAT_ID = "-1001234567890"
$env:SECRET_KEY = "super-secret-key"

# Maven/Spring Boot
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/panel"
$env:SPRING_DATASOURCE_USERNAME = "panel"
$env:SPRING_DATASOURCE_PASSWORD = "panel"

# Запуск бота
cd java-bot
.\mvnw.cmd spring-boot:run
```

Чтобы сделать переменные постоянными, используйте `setx` или системный интерфейс «Переменные среды» (не забудьте перезапустить PowerShell).

## Linux / macOS Shell

```bash
# Временный экспорт на текущую сессию
export TELEGRAM_BOT_TOKEN="123456:ABCDEF"
export GROUP_CHAT_ID="-1001234567890"
export SECRET_KEY="super-secret-key"

# Maven/Spring Boot параметры
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/panel"
export SPRING_DATASOURCE_USERNAME="panel"
export SPRING_DATASOURCE_PASSWORD="panel"

# Запуск панели
./mvnw spring-boot:run
mvn spring-boot:run
```

Добавьте команды в `~/.bashrc` или `~/.zshrc`, чтобы применять их автоматически.

## Проверка

- Python: выполните `python -c "from config import TELEGRAM_BOT_TOKEN; print(TELEGRAM_BOT_TOKEN)"`.
- Spring: запустите `./mvnw spring-boot:run` (Linux/macOS) или `.\\mvnw.cmd spring-boot:run` (Windows) и убедитесь, что лог сообщает об успешном подключении к БД.
