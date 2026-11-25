const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = name => fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js', name), 'utf8');

function element(id, dataset = {}) {
  const classes = new Set();
  const node = { id, dataset, hidden: false, controls: [], disabled: false,
    classList: {
      toggle(name, enabled) { if (enabled) classes.add(name); else classes.delete(name); },
      contains: name => classes.has(name),
      add(...names) { names.forEach(name => classes.add(name)); },
      remove(...names) { names.forEach(name => classes.delete(name)); },
    },
    setAttribute(name, value) { if (name === 'data-settings-edit') this.dataset.settingsEdit = value; },
    getAttribute(name) { return name === 'data-bs-target' ? this.target : undefined; },
    closest() { return this.owner || this.wrapper || null; },
    matches() { return false; },
    querySelectorAll(selector) { return selector === 'input, select, textarea, button' ? this.controls : []; },
  };
  return node;
}

test('denied blocks and their launchers disappear; mixed groups retain granted children', async () => {
  const ids = ['channelsModal', 'channels-manage', 'channels-manage-bots', 'channels-templates',
    'channels-automation', 'networkProfilesSection', 'usersModal', 'settingsAuthUsers',
    'settingsAuthOrg', 'parameterItemsModal', 'itConnectionsModal', 'reportingModal'];
  const nodes = Object.fromEntries(ids.map(id => [id, element(id)]));
  nodes.parameterItemsModal.dataset.paramType = 'equipment_model';
  const input = element('channel-token');
  input.owner = nodes['channels-manage-bots']; nodes['channels-manage-bots'].controls.push(input);
  const triggers = ['channelsModal', 'channels-manage-bots', 'channels-templates', 'reportingModal']
    .map(id => { const trigger = element(id + '-launcher', { settingsOverviewTarget: id }); trigger.wrapper = element(id + '-col'); return trigger; });
  const listeners = {};
  let grants = { bot_templates: true, equipment: true };
  let available = true;
  const document = { readyState: 'complete', body: {}, hidden: false,
    getElementById: id => nodes[id],
    querySelectorAll(selector) {
      if (selector === '[data-settings-edit]') return Object.values(nodes).filter(node => node.dataset.settingsEdit);
      if (selector === '[data-param-type]') return [nodes.parameterItemsModal];
      if (selector === '[data-settings-overview-target], [data-bs-target]') return triggers;
      return [];
    },
    addEventListener(name, callback) { listeners[name] = callback; },
  };
  const window = { addEventListener() {} };
  vm.runInNewContext(source('settings-permissions.js'), {
    document, window, MutationObserver: class { observe() {} },
    fetch: async () => ({ ok: available, json: async () => ({ settings: grants, accounts: false }) }),
  });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(nodes['channels-manage-bots'].hidden, true);
  assert.equal(input.disabled, true);
  assert.equal(nodes['channels-templates'].hidden, false);
  assert.equal(nodes.channelsModal.hidden, false);
  assert.equal(nodes['channels-manage'].hidden, true);
  assert.equal(nodes.usersModal.hidden, true);
  assert.equal(nodes.itConnectionsModal.hidden, false);
  assert.equal(triggers[1].wrapper.hidden, true);
  assert.equal(triggers[2].wrapper.hidden, false);
  assert.equal(window.SettingsPermissions.canOpen('parameterItemsModal'), true);
  nodes.parameterItemsModal.dataset.paramType = 'legal_entity';
  assert.equal(window.SettingsPermissions.canOpen('parameterItemsModal'), false);
  let prevented = false;
  listeners['show.bs.modal']({ target: nodes.reportingModal, preventDefault() { prevented = true; } });
  assert.equal(prevented, true);
  grants = { channels: true, organization: true };
  await window.SettingsPermissions.refresh();
  assert.equal(nodes['channels-manage-bots'].hidden, false);
  assert.equal(input.disabled, false);
  assert.equal(nodes.usersModal.hidden, false);
  assert.equal(nodes.settingsAuthUsers.hidden, true);
  assert.equal(nodes.settingsAuthOrg.hidden, false);
  available = false;
  await window.SettingsPermissions.refresh();
  assert.equal(nodes.channelsModal.hidden, true);
  assert.equal(input.disabled, true);
});

test('saving an allowed block omits denied defaults and does not validate denied dialog templates', async () => {
  let request;
  const fail = () => { throw new Error('Denied dialog collector was called'); };
  const context = { window: { SettingsPermissions: { canEdit: block => block === 'network_profiles' } },
    document: { querySelectorAll() { return []; } }, HTMLElement: class {},
    fetch: async (url, options) => { request = { url, body: JSON.parse(options.body) }; return { ok: true, json: async () => ({ success: true }) }; },
  };
  vm.runInNewContext(source('settings-save-runtime.js'), context);
  const runtime = context.window.SettingsSaveRuntime.mount({
    metricsRuntime: { collectTimeMetricsConfig: fail, collectDialogSummaryBadges: fail },
    dialogShellRuntime: { collectDialogSlaConfig: fail },
    templatesRuntime: { collectAutoCloseConfig: fail, collectDialogTemplatesPayload: fail },
    networkProfilesRuntime: { collectNetworkProfilesPayload: () => [{ id: 'allowed-profile' }] },
    getLocationsState: () => ({ confidential: true }),
    serializeNetBoxSyncSettings: () => ({ api_token: 'must-not-send' }),
  });
  assert.equal(await runtime.saveSettings(), true);
  assert.equal(request.url, '/settings');
  assert.deepEqual(request.body, { network_profiles: [{ id: 'allowed-profile' }] });
});
