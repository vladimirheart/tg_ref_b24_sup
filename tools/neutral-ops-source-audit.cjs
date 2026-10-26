/* Source inventory: запрещаем отдельные inline-темы, сохраняем геометрию и данные. */
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const resources = path.join(root, 'spring-panel/src/main/resources');
const output = path.join(root, 'spring-panel/target/neutral-ops-style-audit');
const files = directory => fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? files(path.join(directory, entry.name)) : [path.join(directory, entry.name)]);
const templates = files(path.join(resources, 'templates')).filter(file => file.endsWith('.html'));
const rows = templates.map(file => {
  const source = fs.readFileSync(file, 'utf8');
  const relative = path.relative(resources, file).replaceAll('\\', '/');
  const standalone = /<body\b/.test(source);
  const styleBlocks = [...source.matchAll(/<style\b[^>]*>[\s\S]*?<\/style>/g)].map(match => match[0].slice(0, 100));
  const inline = [...source.matchAll(/\b(?:th:)?style=("[^"]*"|'[^']*')/g)].map(match => match[0]);
  const decorativeInline = inline.filter(value => /(?<![-\w])(?:background(?:-color|-image)?|color|box-shadow|font-family)\s*:/.test(value) && !value.startsWith('th:style='));
  if (standalone) {
    assert(source.includes('data-ui-page='), relative + ': missing page scope');
    const r12 = ['incidents/index', 'dialogs/index', 'tasks/index', 'projects/index', 'settings/index', 'businesses/manage', 'businesses/dialog-assignment', 'businesses/dialogs', 'general/work'].map(view => 'templates/' + view + '.html');
    const version = relative === 'templates/incidents/index.html' ? '20261026-incidents-r19' : r12.includes(relative) ? '20261023-incidents-r12'
      : ['templates/home/index.html', 'templates/knowledge/note-editor.html'].includes(relative)
        ? '20261023-neutral-ops-r10' : '20261022-neutral-ops-r9';
    assert(source.includes(`app.css(v='${version}')`), relative + ': stale stylesheet version');
    assert(source.includes('/css/style.css'), relative + ': missing theme');
  }
  assert(!styleBlocks.length || relative === 'templates/fragments/ui-head.html', relative + ': separate inline stylesheet');
  assert(!decorativeInline.length, relative + ': separate inline theme: ' + decorativeInline.join(', '));
  assert(!inline.some(value => /#[0-9a-fA-F]{3,8}\b/.test(value)), relative + ': literal inline palette instead of theme tokens');
  return { path: relative, standalone, styleBlocks, inline, decorativeInline };
});
assert.equal(rows.filter(row => row.standalone).length, 47);
const runtimeStyleBlocks = files(path.join(resources, 'static/js')).filter(file => file.endsWith('.js')).flatMap(file => {
  const source = fs.readFileSync(file, 'utf8');
  return /<style\b|createElement\(['"]style['"]\)/.test(source) ? [path.relative(resources, file).replaceAll('\\', '/')] : [];
});
assert.deepEqual(runtimeStyleBlocks, [], 'Runtime JS не должен внедрять собственные stylesheet');
fs.mkdirSync(output, { recursive: true });
fs.writeFileSync(path.join(output, 'source-report.json'), JSON.stringify({ templates: rows, runtimeStyleBlocks }, null, 2));
console.log(`GREEN: ${rows.length} templates, 47 pages; only shared first-paint style; no separate inline themes or runtime stylesheets. Structural/data-dependent inline styles retained.`);
