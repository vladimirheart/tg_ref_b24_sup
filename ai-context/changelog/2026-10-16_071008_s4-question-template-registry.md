# 2026-10-16 07:10 МСК — registry вопросников бизнеса (01-278, R112)

Промт пользователя: «продолжи».

Добавлена V55 `business_question_template_bindings`: отдельный ID и SHA-256
вопросника для каждого бизнеса, уникальность ID между бизнесами, возможность
выключить привязку. `BusinessQuestionTemplateRegistryService` проверяет
активность канала и бизнеса, режим `EXPLICIT_SELECTION`, отдельность шаблона
от legacy default/channel templates и неизменность его содержимого. Scoped
runtime-ответ содержит только выбранный вопросник; при конфликте нет fallback
на общий вопросник или дерево локаций. Добавлены защищённые административный
и внутренний API, PostgreSQL- и WebMvc-тесты. В `01-278.md` зафиксирована
незавершённая интеграция с ботами и граница включения флага.

Проверки: чистый целевой Maven 5/5 GREEN и полный Flyway V1–V55 1/1 GREEN
в изолированной PostgreSQL-схеме; `git diff --check` GREEN. При первом
прогоне Docker Desktop был остановлен и один WebMvc assertion был неверен;
Docker Desktop локально запущен, assertion исправлен, повторные прогоны
успешны. Рабочая БД, shared config, feature flags и deployment не менялись.
