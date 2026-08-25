# Docker production edge layer

- Время: `2026-08-25 15:34:00`
- Файлы:
  - `docker-compose.production-contour.yml`
  - `docker-compose.production-edge.yml`
  - `docker/nginx/templates/http-only.conf.template`
  - `docker/nginx/templates/tls.conf.template`
  - `scripts/docker-production-up.ps1`
  - `scripts/docker-production-up.sh`
  - `scripts/docker-production-down.ps1`
  - `scripts/docker-production-down.sh`
  - `.env.example`
  - `docs/docker-production-contour.md`
  - `docs/runbooks/docker-production-edge-deploy.md`
  - `docs/runbooks/production-launch-checklist.md`
  - `docs/environment_variables.md`
  - `README.md`
  - `deploy/nginx/certs/.gitkeep`
  - `ai-context/tasks/task-details/01-208.md`
- Промты пользователя:

```text
продолжай
что ещё?
делай всё
```

- Что сделано:
  - добавлен отдельный `nginx` edge override для dockerized production contour с HTTP-only и TLS-ready шаблонами;
  - базовый compose переведён на более безопасные bind-host defaults и подготовлен к работе за reverse proxy через forwarded headers;
  - helper-скрипты расширены флагом `edge`, preflight-проверками публичного host/TLS certs и корректным `compose down` для layered contour;
  - документация, env contract и task detail обновлены под layered deployment model `base stack + public ingress`.
