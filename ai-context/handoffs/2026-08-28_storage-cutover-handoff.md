# Handoff: storage cutover / next chat

Date: 2026-08-28
Repository: `vladimirheart/tg_ref_b24_sup`
Purpose: дать новому чату достаточный контекст для безопасного продолжения production-работ без повторного разбора уже закрытых этапов.

## Как начать новый чат

В первом сообщении новому чату достаточно написать:

> Прочитай `ai-context/handoffs/2026-08-28_storage-cutover-handoff.md`, затем продолжай работу по актуальному `main`. Перед любыми изменениями сверяй живое состояние production и репозиторий через GitHub.

Новый чат должен сначала:

1. выполнить/попросить выполнить `git status --short` и `git pull --ff-only origin main` на production-host, если планируется работа с живым контуром;
2. перечитать актуальные файлы, на которые ссылается этот handoff, а не полагаться только на сохранённые здесь значения;
3. использовать GitHub connector первым для repo-aware inspection/modification;
4. не повторять уже закрытый storage cutover и не возвращать fallback в `true` без явной причины/rollback-сценария.

## Текущее production-состояние

Storage cutover завершён и вручную подтверждён пользователем.

Подтверждено на production 2026-08-28:

- `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false` сохранён в `.env`;
- активный `ops-worker` работает с `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false`;
- активный `panel-web` работает с `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false`;
- `ops-worker` и `panel-web` после targeted recreate были healthy;
- post-cutover authoritative storage gate — GREEN;
- post-cutover client-avatar audit — GREEN;
- пользователь вручную проверил UI/media после отключения fallback и подтвердил корректную работу;
- полный production contour для cutover не останавливался и после проверки оставался запущенным;
- physical purge legacy local storage НЕ выполнялся.

Rollback backup успешного cutover находится в корне production repo:

`\.env.storage-cutover-20260828-162458.bak`

Не удалять этот backup до явного закрытия rollback window.

## Подтверждённые storage counters

Authoritative post-cutover gate:

- `object_bucket=iguana`;
- `object_key_prefix=iguana`;
- `attachment_mappings_checked=72`;
- `raw_missing_s3_dialog_objects=20`;
- `known_unrecoverable_dialog_objects=20`;
- `unexpected_missing_s3_dialog_objects=0`;
- `stale_known_unrecoverable_entries=0`;
- `missing_metadata_rows=0`;
- `panel_avatar_object_refs_checked=1`;
- `missing_s3_panel_avatars=0`;
- `invalid_panel_avatar_refs=0`.

Client-avatar audit:

- `client_avatar_history_users_checked=0`;
- `missing_s3_client_avatars=0`.

`client_avatar_history_users_checked=0` означает, что на момент проверки не было клиентов с avatar history; это не пропущенный audit.

## Reviewed historical data loss

Ровно 20 historical dialog attachments подтверждены как невосстановимые.

Source of truth:

`ai-context/storage-known-unrecoverable-dialog-attachments.json`

Для них подтверждено:

- canonical object отсутствует;
- legacy/unprefixed MinIO source отсутствует;
- текущие local attachment roots не содержат исходные байты;
- старый абсолютный local root не содержит исходные байты;
- read-only поиск по Windows user profile не нашёл копий;
- metadata остаётся `availability_status=missing`;
- фиктивные/пустые MinIO objects создавать запрещено.

Истинное физическое состояние проверенных attachment mappings после repair/audit:

- 52 canonical objects present;
- 20 known-unrecoverable absent.

Старый `scripts/docker-production-storage-backfill.ps1` может давать reporting false-negative для нескольких Unicode paths: он не является authoritative proof существования dialog objects. Для cutover source of truth — `scripts/docker-production-storage-cutover-gate.ps1` + canonical object stat.

## Ключевые файлы

- `ai-context/tasks/task-details/01-217.md` — migration/backfill и подготовка final purge;
- `ai-context/tasks/task-details/01-218.md` — финальный cutover; задача после ручной проверки пользователя отмечена `🟢`;
- `ai-context/storage-known-unrecoverable-dialog-attachments.json` — exact manifest 20 reviewed historical losses;
- `scripts/docker-production-storage-cutover-gate.ps1` — authoritative read-only dialog/panel-avatar gate;
- `scripts/docker-production-client-avatar-cutover-audit.ps1` — read-only client avatar gate;
- `scripts/docker-production-storage-disable-fallback.ps1` — live helper успешного fallback cutover;
- `scripts/docker-production-storage-repair-mappings.ps1` — safe canonical repair helper, без удаления source;
- `scripts/internal/storage-cutover-object-stat.sh` — LF-only helper для `mc stat`;
- `docs/runbooks/storage-legacy-purge-and-rollback.md` — подготовленный purge/rollback runbook, status: prepared, not executed;
- `.gitattributes` — `scripts/internal/*.sh text eol=lf`.

## Что делать дальше по storage

Следующий логический этап — НЕ немедленное удаление файлов, а работа по `docs/runbooks/storage-legacy-purge-and-rollback.md`.

Правила:

- не удалять целиком `attachments/**`;
- не удалять целиком `java-bot/attachments/**`;
- не удалять canonical MinIO `iguana/...`;
- не удалять legacy/unprefixed MinIO objects;
- не менять PostgreSQL rows ради purge;
- сначала read-only inventory;
- затем exact candidate manifest;
- затем quarantine, а не immediate delete;
- повторные gates и UI smoke после quarantine;
- physical deletion только отдельным операторским решением после rollback window.

До отдельного dedicated audit из purge исключать как минимум:

- `knowledge_base`;
- `passport_photos`;
- `forms` / web-form files;
- orphan files без authoritative metadata/object mapping.

## Production runtime / Compose особенности

Production host: Windows, Windows PowerShell 5.1, Docker Desktop/Compose.

Compose project в успешном cutover: `tg_ref_b24_sup`.

Основные long-running services:

- `postgres`;
- `rabbitmq`;
- `redis`;
- `minio`;
- `ops-worker`;
- `panel-web`;
- `panel-direct`;
- observability services.

One-shot services, которые нельзя считать «упавшими» только потому, что они `exited/0`:

- `db-migrate`;
- `minio-init`.

Отдельный incident 2026-08-28: production containers были штатно остановлены внешней командой в две волны. PostgreSQL показал `exit=0` и `received fast shutdown request`. Контур был безопасно восстановлен запуском существующих containers через `docker start`, без recreate и без изменения volumes/.env. После восстановления core services были healthy.

Не делать вывод «PostgreSQL crash» только по тому, что container exited; сначала смотреть `docker inspect` и logs.

## Известная отдельная проблема приложения

В `spring-panel/src/main/java/com/example/panel/service/UiPreferenceService.java` остались PostgreSQL-некорректные SQL fragments вида:

- `is_deleted = 0`;
- запись `is_deleted = 0`.

На текущей PostgreSQL schema `is_deleted` — boolean, поэтому в logs были ошибки `operator does not exist: boolean = integer`.

Это отдельный application bug. Он не являлся причиной штатной остановки PostgreSQL и не исправлялся в рамках storage cutover. Не смешивать этот fix с purge/cutover без явной причины.

# Обязательные правила создания PowerShell-скриптов

Ниже — production rules, сформированные по фактическим проблемам на этом host. Новые или изменяемые operator PS-scripts должны соблюдать их по умолчанию.

## 1. Target — Windows PowerShell 5.1

Нельзя писать script как будто target — PowerShell 7.

Минимальная шапка operator script:

```powershell
param(
    [switch]$ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
} catch {
    # Best effort only.
}
```

Не использовать PowerShell 7-only syntax/features без отдельной compatibility проверки.

## 2. Любой PS script сначала должен пройти parser check на реальном PowerShell 5.1

Перед live execution:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command '$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path ".\scripts\SCRIPT.ps1").Path,[ref]$tokens,[ref]$errors) | Out-Null; if ($errors.Count -gt 0) { $errors | ForEach-Object { Write-Error $_.Message }; exit 1 }; Write-Host "[GREEN] parser OK"'
```

Почему именно одной командой: нельзя печатать `[GREEN]` отдельной следующей командой после failed parser-check — оператор может ошибочно принять это за успешную проверку.

## 3. PowerShell interpolation: переменная перед `:` требует `${...}`

В Windows PowerShell 5.1 это ломает parser:

```powershell
throw "Container $ContainerId: inspect failed"
```

Правильно:

```powershell
throw "Container ${ContainerId}: inspect failed"
```

То же правило применять, когда сразу после имени переменной идёт символ, который PowerShell может интерпретировать как часть variable/provider expression.

Если строка сложная — предпочитать форматирование или `${name}` вместо двусмысленной interpolation.

## 4. Native command exit code — источник истины

Docker/Compose/psql/mc — native executables. Их нельзя обрабатывать как обычные PowerShell cmdlets.

Использовать единый wrapper примерно такого типа:

```powershell
function Invoke-NativeCapture {
    param(
        [string]$Executable,
        [string[]]$Arguments
    )

    $saved = $ErrorActionPreference
    $code = -1
    $output = @()
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& $Executable @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }

    [pscustomobject]@{
        ExitCode = $code
        Output = @($output | ForEach-Object { [string]$_ })
    }
}
```

Затем явно проверять `ExitCode`.

Не считать наличие текста в stderr автоматическим PowerShell exception и не игнорировать `$LASTEXITCODE`.

## 5. Нормализовать native output

Windows/native output может приходить с разными CR/LF и как набор heterogeneous objects.

Использовать явную normalization:

```powershell
function Get-NativeOutputLines {
    param([object[]]$Output)

    $lines = @()
    foreach ($item in @($Output)) {
        $text = [string]$item
        foreach ($line in ($text -split "`r?`n")) {
            $trimmed = $line.Trim()
            if (-not [string]::IsNullOrWhiteSpace($trimmed)) {
                $lines += $trimmed
            }
        }
    }
    return $lines
}
```

Не строить critical parsing на случайной форме native stdout.

## 6. Не использовать `docker compose ps` как authoritative runtime discovery на этом host

На production были подтверждены проблемы:

- `docker compose ps --status running -q postgres` возвращал пустой output при реально доступном service context;
- поэтому Compose `ps` precheck был удалён из authoritative gate;
- critical replica discovery из fallback-helper также переведён с Compose `ps` на raw Docker labels.

Для running container discovery использовать:

```powershell
docker ps -q `
    --filter "label=com.docker.compose.service=ops-worker" `
    --filter "label=com.docker.compose.project=tg_ref_b24_sup"
```

Для project name читать label `com.docker.compose.project` через `docker inspect`.

После определения реального project name targeted `docker compose up` выполнять с явным `--project-name <name>`.

## 7. Для structured Docker data предпочитать `docker inspect` JSON + `ConvertFrom-Json`

Не использовать сложные Go templates с вложенными кавычками в пользовательских PowerShell-командах, если можно получить JSON.

На этом host конструкции вида:

```powershell
--format '{{.Label "com.docker.compose.project"}}'
```

уже ломались из-за quoting и превращались в Docker template error `function "com" not defined`.

Надёжнее:

```powershell
$c = (docker inspect $id | ConvertFrom-Json)[0]
$project = $c.Config.Labels.'com.docker.compose.project'
$status = $c.State.Status
```

Для коротких простых templates без вложенных quoted keys допустим `docker inspect --format '{{.State.Status}}'`.

## 8. Каждый mutating operator script должен иметь безопасный `-ValidateOnly`

`-ValidateOnly` должен проверять, насколько возможно:

- PowerShell syntax/required files;
- Compose model (`docker compose config -q`);
- static helper availability/format;
- manifest syntax/contract;
- параметры bucket/prefix.

При `-ValidateOnly` запрещено:

- менять `.env`;
- recreate/restart containers;
- писать в PostgreSQL;
- копировать/удалять MinIO objects;
- перемещать/удалять local files.

## 9. Read-only gates идут ДО первой мутации

Для storage/cutover-подобных workflows порядок должен быть:

1. parser / `-ValidateOnly`;
2. authoritative live read-only gates;
3. runtime/project/replica discovery;
4. только после GREEN — backup mutable config;
5. mutation;
6. targeted recreate;
7. health/env verification;
8. post-mutation read-only gates;
9. manual UI smoke, если изменение user-visible.

Нельзя сначала менять `.env`, а потом выяснять, что precondition не выполнен.

## 10. Перед изменением `.env` всегда делать backup и fail on ambiguity

Если setting может встречаться несколько раз — duplicate entries должны приводить к fail, а не к silent replace.

Backup создавать до записи нового значения и печатать operator-visible marker:

```text
[RESULT] env_backup=...
[RESULT] persisted_fallback_enabled=false
```

Эти markers определяют rollback boundary.

Если script упал ДО `persisted_...` — считать, что mutation ещё не произошла только если код действительно сохраняет такой порядок.

Если script упал ПОСЛЕ — не продолжать автоматически destructive actions; оценить state и rollback backup.

## 11. UTF-8: текстовые файлы писать явно без BOM

Для `.env`/JSON/helper-generated text:

```powershell
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($path, $content, $utf8NoBom)
```

Не полагаться на различающиеся defaults `Out-File`/`Set-Content` между версиями PowerShell.

## 12. Shell helpers внутри `scripts/internal` должны быть LF-only

Для сложных MinIO/psql shell операций предпочтительны static `.sh` helpers вместо длинных inline `sh -c` строк из PowerShell.

`.gitattributes` уже фиксирует:

```text
scripts/internal/*.sh text eol=lf
```

Перед production использованием helper проверять LF contract. CRLF внутри container shell script уже приводил к проблемам.

## 13. MinIO client — только через Docker helper/service

На Windows host нет требования иметь `mc` локально.

Для production storage scripts использовать MinIO client через Docker/Compose (`minio-init` или static helper), а не инструкции вида «установи mc на host».

Credentials не встраивать string-replace в shell script. Передавать через environment/Compose contract.

## 14. Не использовать destructive Compose shortcuts в targeted operator scripts

Для storage cutover/purge helper запрещены без отдельного явного сценария:

- `docker compose down`;
- `--remove-orphans`;
- `--build`;
- полный `docker-production-up.ps1`, если задача требует только targeted runtime recreate.

Для отключения fallback успешно использовалось только targeted:

- `ops-worker`;
- затем `panel-web`;
- `--no-deps`;
- `--force-recreate`;
- с сохранением фактического replica count.

PostgreSQL, RabbitMQ, Redis, MinIO, panel-direct, bots и observability не нужно пересоздавать только из-за изменения panel fallback env.

## 15. Не менять parent/process env необратимо

Если script временно задаёт process variables (`COMPOSE_IGNORE_ORPHANS`, fallback override и т.п.), сохранить старое значение и вернуть его в `finally`.

Перед cutover проверять inherited process override. Если parent PowerShell задаёт conflicting fallback value — fail до mutation.

## 16. Health verification — по каждому container

После targeted recreate:

- заново получить фактические container IDs;
- проверить, что replica count не изменился;
- дождаться `healthy` или корректного `running` для service без healthcheck;
- проверить реальный runtime env каждого container;
- только после этого переходить к следующему service/post-gate.

Не считать успешный `docker compose up -d` доказательством готовности приложения.

## 17. Output operator scripts должен быть машинно и визуально понятным

Использовать стабильные prefixes:

```text
[INFO] ...
[GREEN] ...
[RESULT] key=value
```

Critical counters печатать отдельными `[RESULT]` строками.

Ошибки должны сообщать:

- какой service/file/key проверялся;
- native exit/output, если он нужен для диагностики;
- произошло ли уже изменение persisted state.

## 18. Storage scripts: никакой «починки» через фиктивные objects

Запрещено:

- создавать zero-byte/placeholder object ради GREEN audit;
- менять metadata на `available`, если canonical object не подтверждён;
- удалять local source в backfill/repair/audit/gate script;
- удалять canonical или legacy MinIO objects во время cutover.

## 19. Purge scripts должны быть manifest-based и reversible

Если в будущем появится mutating purge PS-script, он должен:

- по умолчанию быть read-only;
- генерировать exact manifest candidates;
- не принимать broad root как достаточный deletion target;
- сначала перемещать approved files в quarantine;
- сохранять исходный full path/relative path/size/time/hash при необходимости;
- иметь отдельную явно названную destructive phase;
- требовать повторные GREEN gates перед и после quarantine;
- не удалять quarantine до явного закрытия rollback window.

## 20. Source-contract tests и changelog

При изменении критичных operator scripts:

- обновлять/добавлять source-contract test, который фиксирует safety properties;
- запрещать опасные tokens (`--remove-orphans`, `down`, destructive `mc rm`, DB `DELETE`/`UPDATE` в read-only gate и т.п.), если они не должны появляться;
- не утверждать, что test выполнен, если он только изменён, но реально не запускался;
- после любых file changes создавать отдельный changelog в `ai-context/changelog/` согласно `ai-context/baseline/guides/tasks.md`.

## 21. Команды для пользователя должны быть copy/paste-safe для PowerShell 5.1

Перед отправкой пользователю команды проверить:

- line continuation backtick стоит последним символом строки;
- нет PowerShell 7-only syntax;
- quoting не зависит от bash conventions;
- нет двусмысленного `$var:`;
- Docker templates не содержат хрупких nested quoted labels;
- команда не может напечатать ложный `[GREEN]` после failed step;
- destructive command явно отделён от read-only диагностики.

Если можно решить задачу одним read-only диагностическим блоком — сначала давать его, а не сразу предлагать recreate/restart/cleanup.

# Репозиторный workflow для нового чата

- Для repo inspection/modification сначала использовать GitHub connector.
- Перед записью перечитывать актуальный файл/его SHA.
- Не создавать параллельные sequential updates одного файла.
- После file changes — отдельный changelog.
- Статусы задач соблюдать по `ai-context/baseline/guides/tasks.md`.
- `🟢` ставится только после ручного подтверждения пользователем.
- Архивировать GREEN tasks только по явной команде `архивируй задачи`.
- Не выполнять Docker rebuild для operator-only `.ps1`/`.sh`/docs changes, если runtime application image не менялся.

# Safety summary для следующего чата

1. Fallback уже `false`; не возвращать `true` без rollback reason.
2. 20 reviewed losses — ожидаемое historical data loss, не пытаться «долечить» их пустыми objects.
3. Purge ещё не выполнен.
4. `docs/runbooks/storage-legacy-purge-and-rollback.md` — основной следующий документ.
5. Перед любой storage mutation заново прогнать authoritative storage gate + client-avatar audit.
6. Не доверять `docker compose ps` как единственному runtime-discovery источнику на этом host.
7. Все новые PS scripts — Windows PowerShell 5.1 first, parser-first, `-ValidateOnly` first, mutation-last.
8. Не останавливать весь production contour без необходимости; текущая storage architecture допускает online operations с targeted runtime recreate.
