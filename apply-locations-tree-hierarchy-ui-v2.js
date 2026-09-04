#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { spawnSync } = require('child_process');

const EXPECTED_MAIN = '2200327f0a980eb68766fe239a8a68768d294394';
const TASK_ID = '01-250';
const TEST_CLASS = 'SettingsLocationsTreeHierarchySourceContractTest';

function fail(message) {
  throw new Error(message);
}

function parseArgs(argv) {
  const result = { mode: null, repo: null };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--validate') {
      result.mode = 'validate';
    } else if (arg === '--apply') {
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

function normalizeLf(text) {
  return String(text).replace(/\r\n/g, '\n');
}

function replaceExactlyOnce(text, oldValue, newValue, label) {
  const original = String(text);
  const source = normalizeLf(original);
  const oldNormalized = normalizeLf(oldValue);
  const newNormalized = normalizeLf(newValue);
  const count = countLiteral(source, oldNormalized);
  if (count !== 1) {
    fail(`${label}: expected exactly 1 match, found ${count}`);
  }
  const replaced = source.replace(oldNormalized, newNormalized);
  return original.includes('\r\n') ? replaced.replace(/\n/g, '\r\n') : replaced;
}

function operatorSelfTest() {
  console.log('=== OPERATOR SELF TEST ===');
  const marker = 'alpha\nbeta\ngamma';
  const replacement = 'alpha\nbeta-2\ngamma';

  const lfSource = `before\n${marker}\nafter\n`;
  const lfResult = replaceExactlyOnce(lfSource, marker, replacement, 'LF replacement self-test');
  if (!lfResult.includes(replacement) || lfResult.includes('\r\n')) {
    fail('LF replacement self-test failed');
  }

  const crlfSource = lfSource.replace(/\n/g, '\r\n');
  const crlfResult = replaceExactlyOnce(crlfSource, marker, replacement, 'CRLF replacement self-test');
  if (!crlfResult.includes(replacement.replace(/\n/g, '\r\n'))) {
    fail('CRLF replacement self-test did not replace the marker');
  }
  if (/(^|[^\r])\n/.test(crlfResult)) {
    fail('CRLF replacement self-test introduced bare LF');
  }

  console.log('[GREEN] LF/CRLF transform self-test passed');
}

function sha256File(filePath) {
  if (!fs.existsSync(filePath)) return null;
  return crypto.createHash('sha256').update(fs.readFileSync(filePath)).digest('hex');
}

function localTimestamp() {
  const d = new Date();
  const p2 = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p2(d.getMonth() + 1)}${p2(d.getDate())}-${p2(d.getHours())}${p2(d.getMinutes())}${p2(d.getSeconds())}`;
}

function readUtf8(filePath) {
  return fs.readFileSync(filePath, 'utf8');
}

function writeUtf8(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, content, 'utf8');
}

function preflightGit(repo) {
  console.log('=== FRESH GIT PREFLIGHT ===');
  command(process.platform === 'win32' ? 'git.exe' : 'git', ['fetch', 'origin', 'main'], { cwd: repo });
  const git = process.platform === 'win32' ? 'git.exe' : 'git';
  const head = command(git, ['rev-parse', 'HEAD'], { cwd: repo });
  const originMain = command(git, ['rev-parse', 'origin/main'], { cwd: repo });
  console.log(`HEAD=${head}`);
  console.log(`origin/main=${originMain}`);
  console.log(`expected_main=${EXPECTED_MAIN}`);
  if (head !== EXPECTED_MAIN || originMain !== EXPECTED_MAIN) {
    fail('HEAD/origin/main is not the expected base. Stop and inspect the new main.');
  }

  const status = command(git, ['status', '--porcelain=v1', '--untracked-files=all'], { cwd: repo });
  const lines = status ? status.split(/\r?\n/).filter(Boolean) : [];
  const allowed = /^\?\? (?:apply-iiko-server-sources-collapsible-ui-v\d+|apply-locations-tree-hierarchy-ui-v\d+)\.(?:js|zip|txt)$/;
  for (const line of lines) {
    if (allowed.test(line)) {
      console.log(`allowed_operator_artifact=${line.slice(3)}`);
      continue;
    }
    fail(`Unexpected local change: ${line}`);
  }
}

function buildTransforms(repo) {
  const rel = {
    tree: 'spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js',
    iiko: 'spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js',
    calm: 'spring-panel/src/main/resources/scss/settings/_calm.scss',
    tasks: 'ai-context/tasks/task-list.md',
    taskDetail: `ai-context/tasks/task-details/${TASK_ID}.md`,
    test: `spring-panel/src/test/java/com/example/panel/runtime/${TEST_CLASS}.java`,
  };

  const abs = Object.fromEntries(Object.entries(rel).map(([k, v]) => [k, path.join(repo, v)]));
  for (const key of ['tree', 'iiko', 'calm', 'tasks']) {
    if (!fs.existsSync(abs[key])) fail(`Required file missing: ${rel[key]}`);
  }
  if (fs.existsSync(abs.taskDetail)) fail(`${rel.taskDetail} already exists`);
  if (fs.existsSync(abs.test)) fail(`${rel.test} already exists`);

  let tree = readUtf8(abs.tree);
  let iiko = readUtf8(abs.iiko);
  let calm = readUtf8(abs.calm);
  let tasks = readUtf8(abs.tasks);

  tree = replaceExactlyOnce(
    tree,
    "        badge.className = 'badge rounded-pill text-bg-light';",
    "        badge.className = 'badge rounded-pill location-node-meta__badge';",
    'theme-aware location meta badge',
  );

  const oldSeed = `    function seedDefaultCollapsedNodes(tree) {\n      if (defaultCollapseSeeded) {\n        return;\n      }\n\n      Object.entries(tree || {}).forEach(([business, types]) => {\n        if (types && typeof types === 'object' && Object.keys(types).length > 0) {\n          collapsedLocationNodes.add(makeCollapseKey('business', business));\n        }\n      });\n\n      defaultCollapseSeeded = true;\n    }`;

  const newSeed = `    function seedDefaultCollapsedNodes(tree) {\n      if (defaultCollapseSeeded) {\n        return;\n      }\n\n      Object.entries(tree || {}).forEach(([business, types]) => {\n        if (!types || typeof types !== 'object') {\n          return;\n        }\n        const typeEntries = Object.entries(types);\n        if (typeEntries.length > 0) {\n          collapsedLocationNodes.add(makeCollapseKey('business', business));\n        }\n        typeEntries.forEach(([type, cities]) => {\n          if (!cities || typeof cities !== 'object' || Array.isArray(cities)) {\n            return;\n          }\n          const cityEntries = Object.entries(cities);\n          if (cityEntries.length > 0) {\n            collapsedLocationNodes.add(makeCollapseKey('type', business, type));\n          }\n          cityEntries.forEach(([city, locations]) => {\n            if (Array.isArray(locations) && locations.length > 0) {\n              collapsedLocationNodes.add(makeCollapseKey('city', business, type, city));\n            }\n          });\n        });\n      });\n\n      defaultCollapseSeeded = true;\n    }`;

  tree = replaceExactlyOnce(tree, oldSeed, newSeed, 'default hierarchical collapse seed');

  iiko = replaceExactlyOnce(
    iiko,
    `          ? '<span class="badge text-bg-light border text-body-secondary"><i class="bi bi-key-fill me-1"></i>Секрет сохранён</span>'`,
    `          ? '<span class="badge locations-iiko-source-secret-badge"><i class="bi bi-key-fill me-1"></i>Секрет сохранён</span>'`,
    'theme-aware iiko saved-secret badge',
  );

  const calmAnchor = `    #locationsModal .location-tree__children {\n      margin-left: 1rem;\n      padding-left: 0.7rem;\n      border-left-width: 1px;\n    }`;
  const calmReplacement = `${calmAnchor}\n\n    #locationsModal .location-level-location {\n      padding-left: 3rem;\n    }\n\n    #locationsModal .location-node-meta__badge,\n    #locationsModal .locations-iiko-source-secret-badge {\n      border: 1px solid var(--color-border);\n      background: var(--surface-selected);\n      color: var(--color-text-muted);\n      font-weight: 600;\n    }`;
  calm = replaceExactlyOnce(calm, calmAnchor, calmReplacement, 'desktop location hierarchy/theme styles');

  const mobileAnchor = `      #locationsModal .location-tree__children {\n        margin-left: 0.55rem;\n      }`;
  const mobileReplacement = `${mobileAnchor}\n\n      #locationsModal .location-level-location {\n        padding-left: 0;\n      }`;
  calm = replaceExactlyOnce(calm, mobileAnchor, mobileReplacement, 'mobile location hierarchy reset');

  const taskLine = '🟣 [01-249] Уплотнить UI источников iikoServer API сворачиваемыми карточками';
  const taskReplacement = `🟢 [01-249] Уплотнить UI источников iikoServer API сворачиваемыми карточками\n🟣 [${TASK_ID}] Исправить раскрытие, вложенность и theme-aware бейджи дерева локаций`;
  tasks = replaceExactlyOnce(tasks, taskLine, taskReplacement, 'task list 01-249/01-250');

  const taskDetail = `# Задача [${TASK_ID}] Исправить раскрытие, вложенность и theme-aware бейджи дерева локаций\n\n## Короткое описание\n\nВо вкладке «Структура» бизнес корректно стартует свёрнутым, но после его раскрытия\nвсе дочерние уровни оказываются открыты одновременно. Кроме того, leaf-локации\nвизуально уходят левее строки города, потому что у города есть колонка toggle, а у\nлокации её нет. Служебные meta-бейджи используют Bootstrap \`text-bg-light\` и\nстановятся белыми в тёмных/кастомных темах.\n\n## Цель\n\nСделать дерево последовательным: business → type → city → location раскрывается\nпо одному уровню, каждая локация визуально остаётся внутри своего города, а\nслужебные pills используют theme tokens Iguana.\n\n## Что сделать\n\n- При первом открытии структуры seed'ить collapsed-state для business, type и city.\n- Раскрытие business показывает типы, но не раскрывает автоматически города и локации.\n- Раскрытие type показывает города, но города остаются свёрнутыми до отдельного клика.\n- Сдвинуть leaf-локации вправо на недостающую ширину toggle+gap; на mobile убрать этот compensation offset.\n- Заменить \`text-bg-light\` у metadata pills на theme-aware класс.\n- Исправить сохранённый-secret badge во вкладке iikoServer тем же theme-aware подходом.\n- Не менять tree payload, save contract и backend.\n\n## Критерии готовности\n\n- После раскрытия бизнеса виден только следующий уровень дерева.\n- Локации начинаются правее строки города и читаются как его дочерние элементы.\n- Metadata pills и badge «Секрет сохранён» не становятся белыми в тёмной теме.\n- Светлая/тёмная/кастомная темы используют существующие CSS variables.\n- Targeted source-contract test проходит.\n\n## Связанные файлы\n\n- \`spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js\`\n- \`spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js\`\n- \`spring-panel/src/main/resources/scss/settings/_calm.scss\`\n- \`spring-panel/src/test/java/com/example/panel/runtime/${TEST_CLASS}.java\`\n`;

  const test = `package com.example.panel.runtime;\n\nimport java.io.IOException;\nimport java.nio.charset.StandardCharsets;\nimport java.nio.file.Files;\nimport java.nio.file.Path;\nimport org.junit.jupiter.api.Test;\n\nimport static org.assertj.core.api.Assertions.assertThat;\n\nclass ${TEST_CLASS} {\n\n    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();\n\n    @Test\n    void locationTreeOpensOneLevelAtATimeAndKeepsLeafIndentation() throws IOException {\n        String treeRuntime = read("spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js");\n        String calm = read("spring-panel/src/main/resources/scss/settings/_calm.scss");\n\n        assertThat(treeRuntime)\n            .contains("collapsedLocationNodes.add(makeCollapseKey('business', business));")\n            .contains("collapsedLocationNodes.add(makeCollapseKey('type', business, type));")\n            .contains("collapsedLocationNodes.add(makeCollapseKey('city', business, type, city));");\n\n        assertThat(calm)\n            .contains("#locationsModal .location-level-location")\n            .contains("padding-left: 3rem;")\n            .contains("padding-left: 0;");\n    }\n\n    @Test\n    void locationMetadataAndIikoSavedSecretUseThemeTokens() throws IOException {\n        String treeRuntime = read("spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js");\n        String iikoRuntime = read("spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js");\n        String calm = read("spring-panel/src/main/resources/scss/settings/_calm.scss");\n\n        assertThat(treeRuntime)\n            .contains("badge rounded-pill location-node-meta__badge")\n            .doesNotContain("badge rounded-pill text-bg-light");\n        assertThat(iikoRuntime)\n            .contains("locations-iiko-source-secret-badge")\n            .doesNotContain("text-bg-light border text-body-secondary");\n        assertThat(calm)\n            .contains("#locationsModal .location-node-meta__badge")\n            .contains("#locationsModal .locations-iiko-source-secret-badge")\n            .contains("background: var(--surface-selected);")\n            .contains("color: var(--color-text-muted);");\n    }\n\n    private String read(String relativePath) throws IOException {\n        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8)\n            .replace("\\r\\n", "\\n");\n    }\n}\n`;

  return {
    rel,
    abs,
    originals: {
      tree: readUtf8(abs.tree),
      iiko: readUtf8(abs.iiko),
      calm: readUtf8(abs.calm),
      tasks: readUtf8(abs.tasks),
    },
    updated: { tree, iiko, calm, tasks, taskDetail, test },
  };
}

function makeChangelog() {
  return `# Locations tree progressive disclosure and theme-aware pills\n\n## Пользовательский запрос\n\n> отлично. правда ты пропустил цветовые гаммы под разные темы. сейчас вижу просто белые плашки.\n> продолжим теперь по второй вкладке - Структура.\n> 1. при открытии, бизнес свёрнут - это ок, но при раскрытии, всё его содержимое тоже раскрыто - не логично.\n> 2. дерево не совсем корректно представляется: бизнес-тип сети-город идут ок, но внутри города, сами локации уходят левее, чем плашка города.\n\n## Что изменено\n\n- Default collapse seed расширен с business-only до business/type/city.\n- Раскрытие дерева стало последовательным по уровням.\n- Leaf-локации получили desktop compensation offset, равный отсутствующей колонке toggle+gap.\n- На mobile дополнительный offset отключается.\n- Metadata pills переведены с Bootstrap text-bg-light на theme-aware Iguana tokens.\n- Badge сохранённого iikoServer secret также переведён на theme-aware tokens.\n- Backend/tree payload/save contract не менялись.\n- Добавлен targeted source-contract test.\n\n## Проверки\n\n- node --check для обоих locations runtime JS.\n- git diff --check.\n- Docker Maven: ${TEST_CLASS}.\n- Проверка, что app.css/settings.css не меняются при targeted test.\n`;
}

function applyAndTest(repo, transforms) {
  const created = [];
  const changelogRel = `ai-context/changelog/${localTimestamp()}_locations-tree-progressive-disclosure-theme.md`;
  const changelogAbs = path.join(repo, changelogRel);
  if (fs.existsSync(changelogAbs)) fail(`Changelog already exists: ${changelogRel}`);

  const appCss = path.join(repo, 'spring-panel/src/main/resources/static/css/app.css');
  const settingsCss = path.join(repo, 'spring-panel/src/main/resources/static/css/settings.css');
  const appCssBefore = sha256File(appCss);
  const settingsCssBefore = sha256File(settingsCss);

  try {
    console.log('=== WRITE LOCAL UI CHANGES ===');
    writeUtf8(transforms.abs.tree, transforms.updated.tree);
    writeUtf8(transforms.abs.iiko, transforms.updated.iiko);
    writeUtf8(transforms.abs.calm, transforms.updated.calm);
    writeUtf8(transforms.abs.tasks, transforms.updated.tasks);
    writeUtf8(transforms.abs.taskDetail, transforms.updated.taskDetail);
    created.push(transforms.abs.taskDetail);
    writeUtf8(transforms.abs.test, transforms.updated.test);
    created.push(transforms.abs.test);
    writeUtf8(changelogAbs, makeChangelog());
    created.push(changelogAbs);

    console.log('=== TEST LOCAL UI CHANGES ===');
    const node = process.platform === 'win32' ? 'node.exe' : 'node';
    command(node, ['--check', transforms.abs.tree], { cwd: repo });
    command(node, ['--check', transforms.abs.iiko], { cwd: repo });
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

    if (sha256File(appCss) !== appCssBefore) fail('Targeted test unexpectedly modified app.css');
    if (sha256File(settingsCss) !== settingsCssBefore) fail('Targeted test unexpectedly modified settings.css');
    console.log('[GREEN] app.css/settings.css unchanged');

    console.log('=== FINAL GIT STATUS ===');
    const status = command(git, ['status', '--short'], { cwd: repo });
    if (status) console.log(status);
    console.log('[GREEN] LOCATIONS TREE HIERARCHY UI APPLIED');
    console.log('[INFO] No commit, push, image rebuild, or production restart was performed.');
  } catch (error) {
    console.warn('[WARN] local rollback started');
    writeUtf8(transforms.abs.tree, transforms.originals.tree);
    writeUtf8(transforms.abs.iiko, transforms.originals.iiko);
    writeUtf8(transforms.abs.calm, transforms.originals.calm);
    writeUtf8(transforms.abs.tasks, transforms.originals.tasks);
    for (const filePath of created) {
      try {
        if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
      } catch (_) {
        // Preserve the original failure.
      }
    }
    throw error;
  }
}

function main() {
  const args = parseArgs(process.argv);
  const repo = path.resolve(args.repo);
  if (!fs.existsSync(repo) || !fs.statSync(repo).isDirectory()) fail(`Repo not found: ${repo}`);

  operatorSelfTest();
  preflightGit(repo);
  console.log('=== SOURCE TRANSFORM PREFLIGHT ===');
  const transforms = buildTransforms(repo);
  console.log('[GREEN] all hierarchy/theme transform markers matched exactly once');

  if (args.mode === 'validate') {
    console.log('[VALIDATE_ONLY] Nothing was changed.');
    console.log('[GREEN] V2 VALIDATE PASSED');
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
