#!/usr/bin/env node
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const EXPECTED_MAIN = '72461d7ee389fca5ddbff1d6b80b9d3db0bbf010';
const TASK_ID = '01-253';
const TEST_CLASS = 'DialogReplyTargetMessageActionsUiSourceContractTest';
const OPERATOR_NAME = 'apply-dialog-reply-pulse-menu-ui-v3.js';
const DIALOGS_ASSET_VERSION_OLD = '20260823-1';
const DIALOGS_ASSET_VERSION_NEW = '20260904-1';

function fail(message) {
  throw new Error(message);
}

function parseArgs(argv) {
  const result = { mode: null, repo: null };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--validate') {
      if (result.mode) fail('Specify exactly one mode: --validate or --apply');
      result.mode = 'validate';
    } else if (arg === '--apply') {
      if (result.mode) fail('Specify exactly one mode: --validate or --apply');
      result.mode = 'apply';
    } else if (arg === '--repo') {
      i += 1;
      result.repo = argv[i] || null;
    } else {
      fail(`Unknown argument: ${arg}`);
    }
  }
  if (!result.mode) fail('Specify exactly one mode: --validate or --apply');
  if (!result.repo) fail('Specify --repo <path>');
  return result;
}

function command(file, args, options = {}) {
  const result = spawnSync(file, args, {
    cwd: options.cwd,
    encoding: 'utf8',
    stdio: options.stream ? 'inherit' : ['ignore', 'pipe', 'pipe'],
    shell: false,
  });
  if (result.error) {
    fail(`${file} failed to start: ${result.error.message}`);
  }
  if (result.status !== 0) {
    const output = [result.stdout || '', result.stderr || ''].join('').trim();
    fail(`${file} ${args.join(' ')} failed (${result.status})${output ? `:\n${output}` : ''}`);
  }
  return String(result.stdout || '').trim();
}

function countLiteral(text, marker) {
  if (!marker) return 0;
  let count = 0;
  let offset = 0;
  while (true) {
    const found = text.indexOf(marker, offset);
    if (found < 0) return count;
    count += 1;
    offset = found + marker.length;
  }
}

function replaceExactlyOnce(text, oldValue, newValue, label) {
  const count = countLiteral(text, oldValue);
  if (count !== 1) {
    fail(`${label}: expected exactly 1 match, found ${count}`);
  }
  return text.replace(oldValue, newValue);
}

function detectEol(text) {
  return text.includes('\r\n') ? '\r\n' : '\n';
}

function normalizeLf(text) {
  return text.replace(/\r\n/g, '\n');
}

function restoreEol(text, eol) {
  return eol === '\r\n' ? text.replace(/\n/g, '\r\n') : text;
}

function readText(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8');
  return { raw, lf: normalizeLf(raw), eol: detectEol(raw) };
}

function writeTextPreservingEol(filePath, lfText, eol) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, restoreEol(lfText, eol), 'utf8');
}

function writeUtf8(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, content, 'utf8');
}

function sha256File(filePath) {
  if (!fs.existsSync(filePath)) return null;
  const crypto = require('crypto');
  return crypto.createHash('sha256').update(fs.readFileSync(filePath)).digest('hex');
}

function localTimestamp() {
  const d = new Date();
  const p2 = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p2(d.getMonth() + 1)}${p2(d.getDate())}-${p2(d.getHours())}${p2(d.getMinutes())}${p2(d.getSeconds())}`;
}

const RUNTIME_BUBBLE_OPEN = `            <div class="chat-message \${senderType} \${isDeleted ? 'is-deleted' : ''} \${archivedHistory ? 'is-archived-history' : ''}" data-message-preview="\${escapeAttribute(messagePreviewText)}">`;

const RUNTIME_BUBBLE_OPEN_NEW = `            <div class="chat-message-bubble-line \${canReply ? 'has-actions' : ''}">
              <div class="chat-message \${senderType} \${isDeleted ? 'is-deleted' : ''} \${archivedHistory ? 'is-archived-history' : ''}" data-message-preview="\${escapeAttribute(messagePreviewText)}">`;

const RUNTIME_BUBBLE_TAIL = `              \${media}
              \${actionButtons}
            </div>
            \${originalBlock}`;

const RUNTIME_BUBBLE_TAIL_NEW = `              \${media}
              </div>
              \${actionButtons}
            </div>
            \${originalBlock}`;

const DIALOG_SCSS_MARKER = '/* 01-253: reply target pulse and external message action rail */';

const DIALOG_SCSS = `
${DIALOG_SCSS_MARKER}

#dialogDetailsHistory .chat-message-bubble-line {
  display: flex;
  align-items: flex-start;
  gap: 0.32rem;
  width: fit-content;
  max-width: 100%;
}

#dialogDetailsHistory .chat-message-bubble-line.has-actions > .chat-message {
  flex: 0 1 auto;
  max-width: calc(100% - 2.1rem);
}

#dialogDetailsHistory .chat-message-bubble-line > .chat-message-menu {
  position: relative;
  inset: auto;
  top: auto;
  right: auto;
  flex: 0 0 1.78rem;
  width: 1.78rem;
  margin-top: 0.08rem;
  align-items: center;
  z-index: 4;
}

#dialogDetailsHistory .chat-message-menu-toggle {
  width: 1.78rem;
  height: 1.78rem;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-muted);
  opacity: 0;
  transition:
    opacity 0.15s ease,
    background-color 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

#dialogDetailsHistory .chat-message-row:hover .chat-message-menu-toggle,
#dialogDetailsHistory .chat-message-row:focus-within .chat-message-menu-toggle,
#dialogDetailsHistory .chat-message-menu.is-open .chat-message-menu-toggle,
#dialogDetailsHistory .chat-message-menu:hover .chat-message-menu-toggle {
  opacity: 0.82;
}

#dialogDetailsHistory.has-open-message-menu .chat-message-menu:not(.is-open) .chat-message-menu-toggle {
  opacity: 0;
}

#dialogDetailsHistory .chat-message-menu-toggle:hover,
#dialogDetailsHistory .chat-message-menu-toggle:focus-visible,
#dialogDetailsHistory .chat-message-menu.is-open .chat-message-menu-toggle {
  border-color: var(--color-border);
  background: var(--surface-interactive);
  color: var(--color-text);
  opacity: 1;
}

#dialogDetailsHistory .chat-message-menu-toggle:focus-visible {
  outline: 2px solid var(--ring);
  outline-offset: 2px;
}

#dialogDetailsHistory .chat-message-menu.is-portaled-open .chat-message-menu-list {
  display: none;
}

#dialogHistoryActionMenuPortal.chat-message-menu-portal {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 1085;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0.28rem;
  min-width: 8.75rem;
  max-width: min(18rem, calc(100vw - 1.5rem));
  padding: 0.36rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--color-text);
  box-shadow:
    0 0.7rem 1.7rem
    color-mix(in srgb, var(--shadow-color) 16%, transparent);
  opacity: 0;
  transform: translateY(-0.2rem);
  pointer-events: none;
  visibility: hidden;
  transition:
    opacity 0.15s ease,
    transform 0.15s ease;
}

#dialogHistoryActionMenuPortal.chat-message-menu-portal.is-open {
  opacity: 1;
  transform: translateY(0);
  pointer-events: auto;
  visibility: visible;
}

#dialogHistoryActionMenuPortal.chat-message-menu-portal .btn {
  width: 100%;
  justify-content: flex-start;
  text-align: left;
  white-space: nowrap;
}

#dialogDetailsHistory .chat-message-row.is-reply-target-highlight .chat-message {
  z-index: 1;
}

#dialogDetailsHistory .chat-message-row.is-reply-target-highlight .chat-message::after {
  content: "";
  position: absolute;
  inset: -0.2rem;
  border:
    2px solid
    color-mix(in srgb, var(--primary) 78%, var(--color-border));
  border-radius: calc(var(--radius-md) + 0.2rem);
  pointer-events: none;
  opacity: 0;
  box-shadow:
    0 0 0 0
    color-mix(in srgb, var(--primary) 0%, transparent);
  animation:
    dialog-reply-target-ring
    2.2s
    cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes dialog-reply-target-ring {
  0% {
    opacity: 0;
    box-shadow:
      0 0 0 0
      color-mix(in srgb, var(--primary) 0%, transparent);
  }

  12% {
    opacity: 1;
    box-shadow:
      0 0 0 0.24rem
      color-mix(in srgb, var(--primary) 34%, transparent);
  }

  32% {
    opacity: 0.22;
    box-shadow:
      0 0 0 0.48rem
      color-mix(in srgb, var(--primary) 5%, transparent);
  }

  48% {
    opacity: 1;
    box-shadow:
      0 0 0 0.22rem
      color-mix(in srgb, var(--primary) 30%, transparent);
  }

  72% {
    opacity: 0.32;
    box-shadow:
      0 0 0 0.5rem
      color-mix(in srgb, var(--primary) 4%, transparent);
  }

  100% {
    opacity: 0;
    box-shadow:
      0 0 0 0.62rem
      color-mix(in srgb, var(--primary) 0%, transparent);
  }
}

@media (hover: none) {
  #dialogDetailsHistory .chat-message-menu-toggle {
    opacity: 0.72;
  }
}

@media (prefers-reduced-motion: reduce) {
  #dialogDetailsHistory .chat-message-row.is-reply-target-highlight .chat-message::after {
    animation: none;
    opacity: 1;
    box-shadow:
      0 0 0 0.22rem
      color-mix(in srgb, var(--primary) 28%, transparent);
  }
}
`;

const TEST_JAVA = String.raw`package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialogReplyTargetMessageActionsUiSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void replyTargetPulseAndExternalActionRailLiveInSourceFiles() throws IOException {
        String runtime = read("spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js");
        String dialogsScss = read("spring-panel/src/main/resources/scss/app/_dialogs.scss");
        String template = read("spring-panel/src/main/resources/templates/dialogs/index.html");

        assertThat(runtime)
            .contains("target.scrollIntoView({ behavior: 'smooth', block: 'center' });")
            .contains("target.classList.add('is-reply-target-highlight');")
            .contains("<div class=\"chat-message-bubble-line ${'$'}{canReply ? 'has-actions' : ''}\">")
            .contains("${'$'}{media}\n              </div>\n              ${'$'}{actionButtons}\n            </div>")
            .doesNotContain("${'$'}{media}\n              ${'$'}{actionButtons}\n            </div>");

        assertThat(dialogsScss)
            .contains("/* 01-253: reply target pulse and external message action rail */")
            .contains("#dialogDetailsHistory .chat-message-bubble-line")
            .contains("#dialogDetailsHistory .chat-message-menu.is-portaled-open .chat-message-menu-list")
            .contains("#dialogHistoryActionMenuPortal.chat-message-menu-portal")
            .contains(".chat-message-row.is-reply-target-highlight .chat-message::after")
            .contains("@keyframes dialog-reply-target-ring")
            .contains("@media (prefers-reduced-motion: reduce)");

        assertThat(template)
            .contains("@{/css/app.css(v='20260904-1')}")
            .contains("dialogsAssetVersion='20260904-1'")
            .contains("id=\"dialogHistoryActionMenuPortal\"");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }
}
`;

function taskDetail() {
  return `# Задача [${TASK_ID}] Сделать reply-target пульсацию заметной и вынести меню сообщения за bubble

## Короткое описание

При клике по preview ответа история корректно прокручивается к исходному сообщению,
но визуальная пульсация цели не видна. Кнопка меню сообщения «⋯» остаётся внутри
bubble, из-за чего визуально смешивается с содержимым сообщения.

## Root cause

- Runtime уже добавляет классу строки сообщения \`is-reply-target-highlight\` после
  завершения smooth-scroll.
- Portal runtime для меню \`#dialogHistoryActionMenuPortal\` также уже существует.
- Однако стили pulse/portal, появившиеся в более раннем fix, остались только в
  generated \`static/css/app.css\`, а не в source SCSS.
- Production Docker build пересобирает \`app.css\` из SCSS и поэтому теряет эти
  generated-only правила.
- Сам trigger \`chat-message-menu\` рендерится внутри \`chat-message\`, поэтому
  «⋯» физически находится внутри bubble.

## Что сделать

- Перенести bubble и action trigger в общий \`chat-message-bubble-line\`, оставив
  \`chat-message-menu\` sibling-элементом справа от bubble.
- Не менять reply/edit/delete contracts и portal positioning runtime.
- В source \`app/_dialogs.scss\` добавить полноценные portal styles.
- В source SCSS добавить заметную двухтактную theme-aware ring pulse.
- Для reduced motion показывать статичное выделение без анимации.
- На touch-устройствах не скрывать trigger полностью.
- Обновить dialogs JS cache-buster и query-version для \`app.css\`.
- Generated CSS напрямую не редактировать.

## Критерии готовности

- Клик по reply preview прокручивает историю и после scroll показывает заметное
  выделение исходного сообщения.
- «⋯» находится справа за границей bubble, а не поверх текста сообщения.
- Открытое меню по-прежнему рендерится через fixed portal и не обрезается history overflow.
- На длинных сообщениях bubble оставляет место под action rail.
- Светлая/тёмная/кастомная темы используют Iguana tokens.
- Targeted source-contract test и реальная isolated SCSS compilation проходят.
`;
}

function changelog() {
  return `# Dialog reply target pulse and external message action rail

## Пользовательский запрос

> в диалогах, если был ответ на какое-то сообщение, кликнув на этот "ответ", история поднимается до сообщения, но пульсации сообщения, на которое был ответ, не видно.
> и ещё: троеточие меню сообщения вынеси за баббл сообщения

## Что изменено

- Исправлен источник стилей: reply pulse и portal menu теперь живут в source SCSS,
  а не только в generated app.css.
- Bubble и кнопка «⋯» переведены в общий flex-line, где action trigger является
  sibling справа от bubble.
- Добавлена заметная двухтактная theme-aware ring pulse исходного сообщения.
- Для prefers-reduced-motion используется статичное выделение.
- Восстановлен source-SCSS styling fixed portal меню сообщения.
- Для touch pointer trigger не скрывается полностью.
- Обновлены cache-busters dialogs runtime и app.css на странице диалогов.
- Generated CSS напрямую не редактируется.

## Проверки

- node --check dialogs-details-history-runtime.js.
- git diff --check.
- Docker Maven: ${TEST_CLASS}.
- Isolated Docker Maven SCSS compilation в temp-каталоге.
- Проверка compiled temp app.css на новые source markers.
- SHA guard: tracked generated app.css/settings.css/sidebar.css/style.css не меняются.
`;
}

function operatorSelfTest() {
  console.log('=== OPERATOR SELF TEST ===');

  const runtimeFixture = [
    "            <div class=\"chat-message ${senderType} ${isDeleted ? 'is-deleted' : ''} ${archivedHistory ? 'is-archived-history' : ''}\" data-message-preview=\"${escapeAttribute(messagePreviewText)}\">",
    '              ${replyPreview}',
    '              ${media}',
    '              ${actionButtons}',
    '            </div>',
    '            ${originalBlock}',
  ].join('\n');

  let transformed = replaceExactlyOnce(
    runtimeFixture,
    RUNTIME_BUBBLE_OPEN,
    RUNTIME_BUBBLE_OPEN_NEW,
    'self-test bubble open',
  );
  transformed = replaceExactlyOnce(
    transformed,
    RUNTIME_BUBBLE_TAIL,
    RUNTIME_BUBBLE_TAIL_NEW,
    'self-test bubble tail',
  );

  if (!transformed.includes('chat-message-bubble-line ${canReply ? \'has-actions\' : \'\'}')) {
    fail('self-test missing external action rail wrapper');
  }
  if (!transformed.includes('${media}\n              </div>\n              ${actionButtons}')) {
    fail('self-test action menu is still inside bubble');
  }

  const templateFixture = '\t<link rel="stylesheet" th:href="@{/css/app.css}">';
  const templateTransformed = replaceExactlyOnce(
    templateFixture,
    '<link rel="stylesheet" th:href="@{/css/app.css}">',
    `<link rel="stylesheet" th:href="@{/css/app.css(v='${DIALOGS_ASSET_VERSION_NEW}')}">`,
    'self-test dialogs app.css cachebuster',
  );
  if (!templateTransformed.includes(`@{/css/app.css(v='${DIALOGS_ASSET_VERSION_NEW}')}`)) {
    fail('self-test dialogs app.css cachebuster failed');
  }

  console.log('[GREEN] external action rail transform self-test passed');
  console.log('[GREEN] tab-indented app.css cachebuster self-test passed');
}

function assertRealGitClean(repo) {
  const git = process.platform === 'win32' ? 'git.exe' : 'git';

  command(git, ['diff', '--quiet', 'HEAD', '--', '.'], { cwd: repo });
  command(git, ['diff', '--cached', '--quiet', 'HEAD', '--', '.'], { cwd: repo });

  const untracked = command(git, ['ls-files', '--others', '--exclude-standard'], { cwd: repo });
  const allowed = new Set([
    'apply-project-disclosure-ui-v1.js',
    'apply-dialog-reply-pulse-menu-ui-v1.js',
    'apply-dialog-reply-pulse-menu-ui-v2.js',
    OPERATOR_NAME,
  ]);

  for (const rawLine of untracked ? untracked.split(/\r?\n/) : []) {
    const line = rawLine.trim();
    if (!line) continue;
    if (allowed.has(line)) {
      console.log(`allowed_operator_artifact=${line}`);
      continue;
    }
    fail(`Unexpected untracked file: ${line}`);
  }
}

function preflightGit(repo) {
  console.log('=== FRESH GIT PREFLIGHT ===');
  const git = process.platform === 'win32' ? 'git.exe' : 'git';

  command(git, ['fetch', 'origin', 'main'], { cwd: repo });
  const head = command(git, ['rev-parse', 'HEAD'], { cwd: repo });
  const originMain = command(git, ['rev-parse', 'origin/main'], { cwd: repo });

  console.log(`HEAD=${head}`);
  console.log(`origin/main=${originMain}`);
  console.log(`expected_main=${EXPECTED_MAIN}`);

  if (head !== EXPECTED_MAIN || originMain !== EXPECTED_MAIN) {
    fail('HEAD/origin/main is not the expected base. Stop and inspect the new main.');
  }

  assertRealGitClean(repo);
  console.log('[GREEN] Git preflight passed');
}

function buildTransforms(repo) {
  const rel = {
    runtime: 'spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js',
    dialogsScss: 'spring-panel/src/main/resources/scss/app/_dialogs.scss',
    template: 'spring-panel/src/main/resources/templates/dialogs/index.html',
    taskList: 'ai-context/tasks/task-list.md',
    taskDetail: `ai-context/tasks/task-details/${TASK_ID}.md`,
    test: `spring-panel/src/test/java/com/example/panel/runtime/${TEST_CLASS}.java`,
  };

  const abs = Object.fromEntries(
    Object.entries(rel).map(([key, value]) => [key, path.join(repo, value)])
  );

  for (const key of ['runtime', 'dialogsScss', 'template', 'taskList']) {
    if (!fs.existsSync(abs[key])) {
      fail(`Required file missing: ${rel[key]}`);
    }
  }
  for (const key of ['taskDetail', 'test']) {
    if (fs.existsSync(abs[key])) {
      fail(`New file already exists: ${rel[key]}`);
    }
  }

  const originals = new Map();
  const updates = new Map();

  function loadAndUpdate(key, updater) {
    const source = readText(abs[key]);
    originals.set(abs[key], source.raw);
    updates.set(abs[key], {
      lf: updater(source.lf),
      eol: source.eol,
    });
  }

  loadAndUpdate('runtime', (text) => {
    if (text.includes('chat-message-bubble-line')) {
      fail('runtime already contains chat-message-bubble-line');
    }

    let next = replaceExactlyOnce(
      text,
      RUNTIME_BUBBLE_OPEN,
      RUNTIME_BUBBLE_OPEN_NEW,
      'message bubble line open',
    );

    next = replaceExactlyOnce(
      next,
      RUNTIME_BUBBLE_TAIL,
      RUNTIME_BUBBLE_TAIL_NEW,
      'move action menu outside bubble',
    );

    if (countLiteral(next, 'target.classList.add(\'is-reply-target-highlight\');') !== 1) {
      fail('reply highlight runtime contract changed unexpectedly');
    }

    return next;
  });

  loadAndUpdate('dialogsScss', (text) => {
    if (text.includes(DIALOG_SCSS_MARKER)) {
      fail('dialog SCSS marker already exists');
    }
    const suffix = text.endsWith('\n') ? '' : '\n';
    return `${text}${suffix}${DIALOG_SCSS}`;
  });

  loadAndUpdate('template', (text) => {
    let next = replaceExactlyOnce(
      text,
      '<link rel="stylesheet" th:href="@{/css/app.css}">',
      `<link rel="stylesheet" th:href="@{/css/app.css(v='${DIALOGS_ASSET_VERSION_NEW}')}">`,
      'dialogs app.css cachebuster',
    );

    next = replaceExactlyOnce(
      next,
      `dialogsAssetVersion='${DIALOGS_ASSET_VERSION_OLD}'`,
      `dialogsAssetVersion='${DIALOGS_ASSET_VERSION_NEW}'`,
      'dialogs runtime cachebuster',
    );

    return next;
  });

  loadAndUpdate('taskList', (text) => replaceExactlyOnce(
    text,
    '🟣 [01-252] Унифицировать disclosure/accordion-паттерн по всему UI',
    '🟢 [01-252] Унифицировать disclosure/accordion-паттерн по всему UI\n'
      + '🟣 [01-253] Сделать reply-target пульсацию заметной и вынести меню сообщения за bubble',
    'task 01-252 close and 01-253 add',
  ));

  console.log('[GREEN] all source markers matched exactly once');

  return { rel, abs, originals, updates };
}

function runIsolatedScssCompile(repo) {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'iguana-dialog-ui-scss-'));
  try {
    fs.copyFileSync(
      path.join(repo, 'spring-panel', 'pom.xml'),
      path.join(tempRoot, 'pom.xml')
    );

    const sourceScss = path.join(repo, 'spring-panel', 'src', 'main', 'resources', 'scss');
    const tempScss = path.join(tempRoot, 'src', 'main', 'resources', 'scss');
    fs.mkdirSync(path.dirname(tempScss), { recursive: true });
    fs.cpSync(sourceScss, tempScss, { recursive: true });

    const tempCssDir = path.join(tempRoot, 'src', 'main', 'resources', 'static', 'css');
    fs.mkdirSync(tempCssDir, { recursive: true });

    const docker = process.platform === 'win32' ? 'docker.exe' : 'docker';
    command(docker, [
      'run', '--rm',
      '-v', `${tempRoot}:/workspace`,
      '-w', '/workspace',
      'maven:3.9.9-eclipse-temurin-17',
      'mvn', '-q', '-DskipTests', 'generate-resources',
    ], { cwd: repo, stream: true });

    const compiledApp = fs.readFileSync(path.join(tempCssDir, 'app.css'), 'utf8');

    const requiredMarkers = [
      '#dialogDetailsHistory .chat-message-bubble-line',
      '#dialogHistoryActionMenuPortal.chat-message-menu-portal',
      'dialog-reply-target-ring',
    ];

    for (const marker of requiredMarkers) {
      if (!compiledApp.includes(marker)) {
        fail(`isolated compiled app.css misses marker: ${marker}`);
      }
    }
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
}

function applyAndTest(repo, transforms) {
  const created = [];
  const changelogRel =
    `ai-context/changelog/${localTimestamp()}_dialog-reply-pulse-menu-ui.md`;
  const changelogAbs = path.join(repo, changelogRel);

  if (fs.existsSync(changelogAbs)) {
    fail(`Changelog already exists: ${changelogRel}`);
  }

  const generatedCss = [
    path.join(repo, 'spring-panel/src/main/resources/static/css/app.css'),
    path.join(repo, 'spring-panel/src/main/resources/static/css/settings.css'),
    path.join(repo, 'spring-panel/src/main/resources/static/css/sidebar.css'),
    path.join(repo, 'spring-panel/src/main/resources/static/css/style.css'),
  ];
  const generatedBefore = new Map(
    generatedCss.map((filePath) => [filePath, sha256File(filePath)])
  );

  try {
    console.log('=== WRITE LOCAL UI CHANGES ===');

    for (const [filePath, update] of transforms.updates.entries()) {
      writeTextPreservingEol(filePath, update.lf, update.eol);
    }

    writeUtf8(transforms.abs.taskDetail, taskDetail());
    created.push(transforms.abs.taskDetail);

    writeUtf8(transforms.abs.test, TEST_JAVA);
    created.push(transforms.abs.test);

    writeUtf8(changelogAbs, changelog());
    created.push(changelogAbs);

    console.log('=== TEST LOCAL UI CHANGES ===');

    const node = process.platform === 'win32' ? 'node.exe' : 'node';
    command(node, ['--check', transforms.abs.runtime], { cwd: repo });
    console.log('[GREEN] node --check passed');

    const git = process.platform === 'win32' ? 'git.exe' : 'git';
    command(git, ['diff', '--check'], { cwd: repo });
    console.log('[GREEN] git diff --check passed');

    const docker = process.platform === 'win32' ? 'docker.exe' : 'docker';
    command(docker, [
      'run', '--rm',
      '-v', `${repo}:/workspace`,
      '-w', '/workspace/spring-panel',
      'maven:3.9.9-eclipse-temurin-17',
      'mvn', '-q', '-Ddart.sass.skip=true', `-Dtest=${TEST_CLASS}`, 'test',
    ], { cwd: repo, stream: true });
    console.log('[GREEN] targeted Maven test passed');

    runIsolatedScssCompile(repo);
    console.log('[GREEN] isolated SCSS compilation passed');

    for (const filePath of generatedCss) {
      if (sha256File(filePath) !== generatedBefore.get(filePath)) {
        fail(`Operator unexpectedly modified generated CSS: ${path.basename(filePath)}`);
      }
    }
    console.log('[GREEN] tracked generated CSS bytes unchanged');

    console.log('=== FINAL GIT STATUS ===');
    const status = command(git, ['status', '--short'], { cwd: repo });
    if (status) console.log(status);

    console.log('[GREEN] DIALOG REPLY PULSE / MENU UI APPLIED');
    console.log('[INFO] No commit, push, production image rebuild, or restart was performed.');
  } catch (error) {
    console.warn('[WARN] local rollback started');

    for (const [filePath, originalRaw] of transforms.originals.entries()) {
      fs.writeFileSync(filePath, originalRaw, 'utf8');
    }

    for (const filePath of created) {
      try {
        if (fs.existsSync(filePath)) {
          fs.unlinkSync(filePath);
        }
      } catch (_) {
        // Keep original failure.
      }
    }

    throw error;
  }
}

function main() {
  const args = parseArgs(process.argv);
  const repo = path.resolve(args.repo);

  if (!fs.existsSync(repo) || !fs.statSync(repo).isDirectory()) {
    fail(`Repo not found: ${repo}`);
  }

  operatorSelfTest();
  preflightGit(repo);

  console.log('=== SOURCE TRANSFORM PREFLIGHT ===');
  const transforms = buildTransforms(repo);

  if (args.mode === 'validate') {
    console.log('[VALIDATE_ONLY] Nothing was changed.');
    console.log('[GREEN] V3 VALIDATE PASSED');
    return;
  }

  applyAndTest(repo, transforms);
}

try {
  main();
} catch (error) {
  console.error(`[RED] ${error && error.message ? error.message : String(error)}`);
  process.exitCode = 1;
}
