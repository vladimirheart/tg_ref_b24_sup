const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/settings-it-equipment-runtime.js'),
  'utf8'
);
const context = { window: {} };
vm.runInNewContext(source, context, { filename: 'settings-it-equipment-runtime.js' });

const { buildEquipmentHierarchy } = context.window.SettingsItEquipmentRuntime;
const plain = (value) => JSON.parse(JSON.stringify(value));
const rows = [
  { equipment_type: 'Маршрутизатор', equipment_vendor: 'MikroTik', equipment_model: 'hAP ac²' },
  { equipment_type: 'Маршрутизатор', equipment_vendor: 'MikroTik', equipment_model: 'RB5009' },
  { equipment_type: 'Маршрутизатор', equipment_vendor: 'Cisco', equipment_model: 'C9200' },
  { equipment_type: 'Точка доступа', equipment_vendor: 'Ubiquiti', equipment_model: 'U6 Pro' },
  { equipment_type: 'Принтер', equipment_vendor: '', equipment_model: 'LaserJet Pro' },
];

test('equipment hierarchy keeps only manufacturers of the selected type', () => {
  const hierarchy = plain(buildEquipmentHierarchy(rows, { type: 'Маршрутизатор' }));

  assert.deepEqual(
    hierarchy.vendors.map(({ value }) => value),
    ['Cisco', 'MikroTik']
  );
  assert.deepEqual(
    hierarchy.models.map(({ value }) => value),
    ['C9200', 'hAP ac²', 'RB5009']
  );
});

test('equipment hierarchy keeps only models of the selected type and manufacturer', () => {
  const hierarchy = plain(buildEquipmentHierarchy(rows, {
    type: 'Маршрутизатор',
    vendor: 'MikroTik',
  }));

  assert.deepEqual(
    hierarchy.models.map(({ value }) => value),
    ['hAP ac²', 'RB5009']
  );
});

test('equipment hierarchy represents models without a manufacturer as a separate branch', () => {
  const hierarchy = plain(buildEquipmentHierarchy(rows, { type: 'Принтер' }));

  assert.deepEqual(hierarchy.vendors, [
    { value: '__it_equipment_without_vendor__', label: 'Без производителя' },
  ]);
  assert.deepEqual(
    plain(buildEquipmentHierarchy(rows, {
      type: 'Принтер',
      vendor: '__it_equipment_without_vendor__',
    })).models.map(({ value }) => value),
    ['LaserJet Pro']
  );
});

test('catalogue card has a direct photo-preview action', () => {
  assert.match(source, /data-it-equipment-action="preview-photo"/);
  assert.match(source, /function openCatalogPhotoViewer\(item\)/);
  assert.match(source, /photoViewerItem/);
});
