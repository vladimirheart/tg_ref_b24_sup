# 01-278: общий вопросник в runtime и сессиях ботов

Время: 2026-10-16 09:09 MSK.

Промт пользователя: «продолжай».

Значимое предыдущее уточнение: «обращения без бизнеса вообще. вопросни должен уметь быть свободно собираемым с разным порядком и вопрсами».

Изменены `spring-panel` runtime service/controller и ingestion service,
`java-bot` runtime client, Telegram/VK/MAX handlers и сессии, соответствующие
целевые тесты, S0-контракт и detail задачи `01-278`.

- Основной internal runtime endpoint выдаёт verified general-only вопросник
  и явные scope/template markers; при stale binding или выключенном флаге
  отвечает 409 вместо legacy-конфигурации.
- Bot runtime client распознаёт scoped snapshot и не использует локальный
  fallback на 409. Telegram/VK/MAX фиксируют scope при старте опроса и
  передают `GENERAL/GENERAL_CHANNEL` в команду создания обращения; VK/MAX
  сохраняют scope в persisted session state.
- Для general-only опроса отключено повторное заполнение business/location
  из прошлого обращения. Panel ingestion отклоняет новые legacy-события
  канала с активной general-only привязкой.
- Проверены компиляция bot-модулей и panel, targeted panel service/WebMvc/
  ingestion tests и bot-core/MAX session tests. Флаги и рабочие данные не
  менялись; перенос template ID/SHA-256 в ticket и полный rollout остаются
  следующими стадиями.
