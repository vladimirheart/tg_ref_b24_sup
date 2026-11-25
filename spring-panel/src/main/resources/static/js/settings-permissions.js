(function () {
  'use strict';
  const scopes = {
    'channels-manage-bots': 'channels', channelEditorModal: 'channels', addChannelModal: 'channels', vkWebhookModal: 'channels',
    'channels-templates': 'bot_templates', 'channels-automation': 'auto_close',
    'channels-manage-profiles': 'network_profiles', 'channels-manage-routes': 'network_profiles',
    botRatingTemplateModal: 'bot_templates', botTemplateEditorModal: 'bot_templates',
    reportingModal: 'reporting', inputFormattingModal: 'input_formatting', categoriesModal: 'dialogs',
    panelDesignSettingsModal: 'appearance', parameterItemsModal: 'parameters',
    itEquipmentSection: 'equipment', itEquipmentAddModal: 'equipment', itEquipmentPhotoAddModal: 'equipment',
    itEquipmentPhotoEditModal: 'equipment', itEquipmentPhotoConfirmModal: 'equipment',
    itConnectionsSection: 'it_connections', itConnectionAddModal: 'it_connections', remoteAccessSection: 'it_connections',
    netBoxSyncSection: 'netbox', networkProfilesSection: 'network_profiles', networkProfileEditorModal: 'network_profiles',
    integrationNetworkProfileEditorModal: 'network_profiles',
    locationsModal: 'locations', locationWizardModal: 'locations', legalEntitiesModal: 'legal_entities',
    partnerContactEditorModal: 'legal_entities', backupSettingsModal: 'backup', managerBindingsModal: 'manager_bindings',
    orgMembersModal: 'organization',
    settingsAuthOrg: 'organization', settingsAuthPositions: 'workforce',
    settingsAuthUsers: '__accounts', settingsAuthRoles: '__accounts', authUserDetailsModal: '__accounts',
    productionReadinessModal: 'backup', storageInventoryModal: 'backup',
  };
  const groups = { channelsModal: ['channels', 'bot_templates', 'auto_close', 'network_profiles'],
    'channels-manage': ['channels', 'network_profiles'], usersModal: ['__accounts', 'organization', 'workforce'],
    parametersModal: ['parameters', 'equipment', 'it_connections', 'legal_entities'],
    itConnectionsModal: ['it_connections', 'equipment', 'network_profiles', 'netbox'] };
  let permissions = {};
  function targetAllowed(id) {
    if (groups[id]) return groups[id].some(block => permissions[block] === true);
    const type = document.getElementById(id)?.dataset?.paramType;
    if (id === 'parameterItemsModal' && type) return permissions[parameterBlock(type)] === true;
    return scopes[id] ? permissions[scopes[id]] === true : true;
  }
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
      section.hidden = !allowed;
      section.classList.toggle('d-none', !allowed);
      section.querySelectorAll('input, select, textarea, button').forEach(control => {
        if (control.closest('[data-settings-edit]') !== section || control.matches(readControls)) return;
        if (!allowed && !control.disabled) { control.dataset.permissionDisabled = 'true'; control.disabled = true; }
        if (allowed && control.dataset.permissionDisabled === 'true') { control.disabled = false; delete control.dataset.permissionDisabled; }
      });
    });
    Object.keys(groups).forEach(id => {
      const section = document.getElementById(id);
      if (section) { section.hidden = !targetAllowed(id); section.classList.toggle('d-none', !targetAllowed(id)); }
    });
    document.querySelectorAll('[data-settings-overview-target], [data-bs-target]').forEach(trigger => {
      const id = trigger.dataset.settingsOverviewTarget || trigger.getAttribute('data-bs-target')?.replace(/^#/, '');
      if (!id || (!scopes[id] && !groups[id])) return;
      const allowed = targetAllowed(id);
      const wrapper = trigger.closest('.settings-tiles > .col, .nav-item') || trigger;
      wrapper.hidden = !allowed;
      wrapper.classList.toggle('d-none', !allowed);
    });
    document.querySelectorAll('#channelsTabs, #channelsManageTabs, [data-auth-tabs]').forEach(tabs => {
      const buttons = Array.from(tabs.querySelectorAll('[data-bs-target]'));
      const allowed = buttons.filter(button => !button.closest('.nav-item')?.hidden);
      if (allowed.length && !allowed.some(button => button.classList.contains('active'))) {
        buttons.forEach(button => {
          button.classList.remove('active'); button.setAttribute('aria-selected', 'false');
          const pane = document.querySelector(button.getAttribute('data-bs-target'));
          pane?.classList.remove('active', 'show');
        });
        allowed[0].classList.add('active'); allowed[0].setAttribute('aria-selected', 'true');
        document.querySelector(allowed[0].getAttribute('data-bs-target'))?.classList.add('active', 'show');
      }
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
      const payload = await response.json(); permissions = { ...(payload.settings || {}), __accounts: payload.accounts === true };
    } catch (_) { permissions = {}; }
    window.SettingsPermissions = Object.freeze({ canEdit: block => permissions[block] === true, canOpen: targetAllowed, refresh });
    apply();
  }
  function mount() {
    document.addEventListener('show.bs.modal', event => {
      if (event.target?.id && !targetAllowed(event.target.id)) event.preventDefault();
    });
    apply();
    new MutationObserver(apply).observe(document.body, { childList: true, subtree: true });
    refresh();
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount, { once: true });
  else mount();
})();
