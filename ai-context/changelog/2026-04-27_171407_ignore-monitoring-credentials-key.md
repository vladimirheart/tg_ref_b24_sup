# 2026-04-27 17:14:07 — Ignore local monitoring credentials key

## Что изменено

- В `.gitignore` добавлено правило для `config/shared/monitoring-credentials.key`, чтобы локальный ключ шифрования RMS-кредитов не попадал в git.

## Затронутые файлы

- `.gitignore`

## Практический смысл

- encrypt-at-rest для RMS остаётся рабочим локально;
- ключ шифрования не смешивается с кодом и не утекает в репозиторий при обычном коммите.
