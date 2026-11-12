(function () {
  'use strict';
  const scopes = {
    channelsModal: 'channels', channelEditorModal: 'channels', addChannelModal: 'channels', vkWebhookModal: 'channels',
    'channels-templates': 'bot_templates', 'channels-automation': 'auto_close',
    'channels-manage-profiles': 'network_profiles', 'channels-manage-routes': 'network_profiles',
    botRatingTemplateModal: 'bot_templates', botTemplateEditorModal: 'bot_templates',
    reportingModal: 'reporting', inputFormattingModal: 'input_formatting', categoriesModal: 'dialogs',
    panelDesignSettingsModal: 'appearance', parametersModal: 'parameters', parameterItemsModal: 'parameters',
    itEquipmentSection: 'equipment', itEquipmentAddModal: 'equipment', itEquipmentPhotoAddModal: 'equipment',
    itEquipmentPhotoEditModal: 'equipment', itEquipmentPhotoConfirmModal: 'equipment',
    itConnectionsSection: 'it_connections', itConnectionAddModal: 'it_connections', remoteAccessSection: 'it_connections',
    netBoxSyncSection: 'netbox', networkProfilesSection: 'network_profiles', networkProfileEditorModal: 'network_profiles',
    integrationNetworkProfileEditorModal: 'network_profiles',
    locationsModal: 'locations', locationWizardModal: 'locations', legalEntitiesModal: 'legal_entities',
    partnerContactEditorModal: 'legal_entities', backupSettingsModal: 'backup', managerBindingsModal: 'manager_bindings',
    orgMembersModal: 'organization',
    settingsAuthOrg: 'organization', settingsAuthPositions: 'workforce',
  };
  let permissions = {};
  const readControls = '[data-bs-dismiss], [data-bs-toggle], [data-dialog-template-toggle], [data-it-collapse-tile], [data-settings-read], [type="search"], .pagination button, .pagination a';
  function apply() {
    Object.entries(scopes).forEach(([id, block]) => document.getElementById(id)?.setAttribute('data-settings-edit', block));
    document.querySelectorAll('[data-workforce-user-section]').forEach(section => section.dataset.settingsEdit = 'workforce');
    document.querySelectorAll('[data-param-type]').forEach(section => {
      const type = section.dataset.paramType;
      if (section.matches('.parameter-card, [data-param-container], #parameterItemsModal')) section.dataset.settingsEdit = parameterBlock(type);
    });
    document.querySelectorAll('[data-settings-edit]').forEach(section => {
      const block = section.dataset.settingsEdit;
      const allowed = permissions[block] === true;
      section.querySelectorAll('input, select, textarea, button').forEach(control => {
        if (control.closest('[data-settings-edit]') !== section || control.matches(readControls)) return;
        if (!allowed && !control.disabled) { control.dataset.permissionDisabled = 'true'; control.disabled = true; }
        if (allowed && control.dataset.permissionDisabled === 'true') { control.disabled = false; delete control.dataset.permissionDisabled; }
      });
      let notice = Array.from(section.querySelectorAll('[data-settings-readonly-notice]'))
        .find(item => item.closest('[data-settings-edit]') === section);
      if (!allowed && !notice) {
        notice = document.createElement('p'); notice.dataset.settingsReadonlyNotice = '';
        notice.className = 'small text-muted px-3 pt-2 mb-1'; notice.setAttribute('role', 'status');
        notice.textContent = 'Доступен просмотр. Для изменения этого блока требуется отдельное разрешение.';
        const content = section.classList.contains('modal') ? section.querySelector('.modal-content') : section;
        if (content) content.prepend(notice);
      }
      if (allowed && notice) notice.remove();
    });
  }
  function parameterBlock(type) {
    if (['legal_entity', 'partner_contact'].includes(type)) return 'legal_entities';
    if (['it_connection', 'remote_access', 'iiko_server'].includes(type)) return 'it_connections';
    return type?.startsWith('equipment_') ? 'equipment' : 'parameters';
  }
  async function refresh() {
    try {
      const response = await fetch('/api/auth/permissions', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('Permissions unavailable');
      const payload = await response.json(); permissions = payload.settings || {};
    } catch (_) { permissions = {}; }
    window.SettingsPermissions = Object.freeze({ canEdit: block => permissions[block] === true, refresh });
    apply();
  }
  function mount() {
    apply();
    new MutationObserver(apply).observe(document.body, { childList: true, subtree: true });
    refresh();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount, { once: true });
  else mount();
})();
