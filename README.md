# 🤖 Bender — CRM-система поддержки через Telegram

![Логотип Bender](panel/static/bender-icon.svg)

## 🧩 Описание

**Bender** — это многофункциональная CRM-система поддержки, которая позволяет:
- ✅ Принимать заявки через Telegram-бота
- ✅ Обрабатывать их через веб-панель
- ✅ Вести историю переписки
- ✅ Закрывать заявки с категорией
- ✅ Запрашивать оценку качества
- ✅ Анализировать статистику

---

## 🚀 Возможности

### 🤖 Бот
- ✅ Принимает заявки от пользователей
- ✅ Запрашивает:
  - Бизнес
  - Тип локации
  - Город
  - Локацию
  - Проблему
- ✅ Поддерживает:
  - 📷 Фото
  - 🎤 Голосовые
  - 🎥 Видео
  - 📄 Документы
- ✅ Отправляет заявки в Telegram-группу

### 🌐 Веб-панель
- ✅ Список всех заявок
- ✅ Фильтрация и поиск:
  - По ID, юзернейму, бизнесу, локации, дате и т.д.
- ✅ Ответы через Telegram API
- ✅ История переписки по каждой заявке
- ✅ Закрытие заявок с категорией
- ✅ Статистика и аналитика:
  - 📊 По бизнесу
  - 📍 По городам
  - 🏷️ По категориям
  - 📈 По статусам

---
## 🪟 Запуск на Windows

Spring-версия панели совместима с Windows 10/11. Подробный пошаговый гайд расположен в [`docs/windows_setup.md`](docs/windows_setup.md). Вкратце:

- установите только JDK 17 (скрипт проверяет версию; Maven будет загружен автоматически при первом запуске `mvnw`);
- перейдите в `spring-panel` и выполните `.\run-windows.bat` в PowerShell или `cmd.exe`;
- дополнительные параметры можно передать через переменные окружения `JAVA_OPTS` (для JVM) и `SPRING_OPTS` (для аргументов приложения), например:

  ```powershell
  $env:JAVA_OPTS = "-Xmx1024m"
  $env:SPRING_OPTS = "--server.port=9090"
  .\run-windows.bat
  ```
- по умолчанию приложение стартует на <http://localhost:8080/> с логином `admin` / `admin`.

## 🐧 Запуск на Linux

Для Linux доступен аналогичный скрипт `run-linux.sh`:

```bash
cd spring-panel
export JAVA_OPTS="-Xmx1024m"
export SPRING_OPTS="--server.port=9090"
./run-linux.sh
```

Скрипт проверяет наличие JDK 17, предпочитает локальный `mvnw` и корректно завершает работу по `Ctrl+C`.

### 📦 Каталоги хранения

Пути к директориям для вложений можно переопределять без правки `application.yml` — достаточно указать переменные окружения перед запуском:

```bash
export APP_STORAGE_ATTACHMENTS="/srv/panel/attachments"
export APP_STORAGE_KNOWLEDGE_BASE="/srv/panel/knowledge_base"
export APP_STORAGE_AVATARS="/srv/panel/avatars"
```

Аналогично в PowerShell:

```powershell
$env:APP_STORAGE_ATTACHMENTS = "D:/PanelData"
$env:APP_STORAGE_KNOWLEDGE_BASE = "D:/PanelData/knowledge_base"
$env:APP_STORAGE_AVATARS = "D:/PanelData/avatars"
```

Эти переменные прокидываются в свойства `app.storage.*` и создают каталоги автоматически при старте.

---

## 📚 Документация

- [Снимок схем SQLite](docs/sqlite_schema_snapshot.md) — актуальные структуры `tickets.db`, `users.db` и `bot_database.db` плюс привязка к JPA-сущностям.
- [Переменные окружения](docs/environment_variables.md) — как задать `.env` и переменные в Windows PowerShell и Linux shell.

---

## 📁 Структура проекта
Bender/
├── bot_support.py         ← Telegram-бот
├── tickets.db             ← База заявок
├── users.db               ← База пользователей панели
├── config/
│   └── shared/
│       ├── settings.json      ← Настройки, общие для Python и Java
│       ├── locations.json     ← Структура локаций
│       └── org_structure.json ← Описание оргструктуры
├── panel/
│   ├── app.py             ← Веб-панель (Flask)
│   ├── config.py          ← Конфигурация
│   ├── templates/
│   │   ├── index.html     ← Главная страница
│   │   ├── client_profile.html ← Карточка клиента
│   │   ├── analytics.html       ← Аналитика
│   │   ├── dashboard.html       ← Дашборд
│   │   ├── settings.html        ← Настройки
│   │   └── login.html           ← Авторизация
│   └── static/
│       └── style.css      ← Стили
├── shared_config.py       ← Доступ к общим JSON-конфигам
└── requirements.txt       ← Зависимости
