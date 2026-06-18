(function () {
  if (window.SettingsIntegrationNetworkRuntime) {
    return;
  }

  function notify(message, type = 'info') {
    if (typeof window.showPopup === 'function') {
      window.showPopup(message, type);
      return;
    }
    console.log(message);
  }

  function escapeHtml(value) {
    if (value === null || value === undefined) {
      return '';
    }
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function pluralizeRu(count, one, few, many) {
    const normalized = Math.abs(Number.parseInt(count, 10) || 0);
    const mod10 = normalized % 10;
    const mod100 = normalized % 100;
    if (mod10 === 1 && mod100 !== 11) {
      return one;
    }
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
      return few;
    }
    return many;
  }

  function parsePlatformConfig(raw) {
    if (!raw) {
      return {};
    }
    if (typeof raw === 'string') {
      try {
        const value = JSON.parse(raw);
        return value && typeof value === 'object' ? value : {};
      } catch (error) {
        return {};
      }
    }
    if (typeof raw === 'object') {
      return Object.assign({}, raw);
    }
    return {};
  }

  function normalizeChannelWorkingHours(raw) {
    const source = raw && typeof raw === 'object' ? raw : {};
    const startRaw = Number.parseInt(source.start_hour ?? source.startHour ?? source.start ?? 9, 10);
    const endRaw = Number.parseInt(source.end_hour ?? source.endHour ?? source.end ?? 18, 10);
    const startHour = Number.isFinite(startRaw) ? Math.max(0, Math.min(23, startRaw)) : 9;
    const endCandidate = Number.isFinite(endRaw) ? Math.max(1, Math.min(24, endRaw)) : 18;
    const endHour = endCandidate <= startHour ? Math.min(24, startHour + 1) : endCandidate;
    return {
      start_hour: startHour,
      end_hour: endHour,
    };
  }

  function slugifyIntegrationNetworkProfileName(value) {
    const slug = String(value || '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9а-яё]+/gi, '-')
      .replace(/^-+|-+$/g, '');
    return slug || `network-profile-${Date.now()}`;
  }

  function normalizeNetworkProxyConfig(raw) {
    const source = raw && typeof raw === 'object' ? raw : {};
    const scheme = String(source.scheme || '').toLowerCase();
    const token = String(source.token || '').trim();
    const fallbackToken = scheme === 'vless'
      ? (token || String(source.username || source.password || '').trim())
      : token;
    const port = Number.parseInt(source.port, 10);
    return {
      scheme: ['http', 'https', 'socks5', 'socks4', 'vless'].includes(scheme)
        ? scheme
        : 'http',
      host: String(source.host || '').trim(),
      port: Number.isFinite(port) && port > 0 && port <= 65535 ? port : 8080,
      username: String(source.username || '').trim(),
      password: String(source.password || '').trim(),
      token: fallbackToken,
    };
  }

  function normalizeNetworkVpnConfig(raw) {
    const source = raw && typeof raw === 'object' ? raw : {};
    return {
      name: String(source.name || '').trim(),
      endpoint: String(source.endpoint || '').trim(),
      config_path: String(source.config_path || source.configPath || '').trim(),
      notes: String(source.notes || '').trim(),
      env: source.env && typeof source.env === 'object' ? source.env : {},
    };
  }

  function normalizeProfileIds(rawProfileIds, fallbackProfileId = '') {
    const result = [];
    const source = Array.isArray(rawProfileIds) ? rawProfileIds : [];
    source.forEach((value) => {
      const normalized = String(value || '').trim();
      if (normalized && !result.includes(normalized)) {
        result.push(normalized);
      }
    });
    const fallback = String(fallbackProfileId || '').trim();
    if (fallback && !result.includes(fallback)) {
      result.unshift(fallback);
    }
    return result;
  }

  function normalizeFailoverDowntimeSeconds(raw) {
    const parsed = Number.parseInt(raw, 10);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return 120;
    }
    return Math.min(86400, Math.max(10, parsed));
  }

  function normalizeNetworkRoute(raw, allowInherit = false) {
    const source = raw && typeof raw === 'object' ? raw : {};
    const allowed = allowInherit ? ['inherit', 'direct', 'profile', 'proxy', 'vpn'] : ['direct', 'profile', 'proxy', 'vpn'];
    const mode = allowed.includes(String(source.mode || '').toLowerCase())
      ? String(source.mode).toLowerCase()
      : (allowInherit ? 'inherit' : 'direct');
    const profileId = String(source.profile_id || source.profileId || '').trim();
    const profileIds = normalizeProfileIds(source.profile_ids || source.profileIds, profileId);
    return {
      mode,
      profile_id: profileId || profileIds[0] || '',
      profile_ids: profileIds,
      failover_downtime_seconds: normalizeFailoverDowntimeSeconds(source.failover_downtime_seconds ?? source.failoverDowntimeSeconds),
      proxy: normalizeNetworkProxyConfig(source.proxy),
      vpn: normalizeNetworkVpnConfig(source.vpn),
    };
  }

  function normalizeIntegrationNetworkConfig(raw) {
    const source = raw && typeof raw === 'object' ? raw : {};
    return {
      project: normalizeNetworkRoute(source.project, false),
      bots: normalizeNetworkRoute(source.bots, true),
    };
  }

  function normalizeIntegrationNetworkProfile(raw) {
    const source = raw && typeof raw === 'object' ? raw : {};
    const mode = String(source.mode || '').toLowerCase() === 'vpn' ? 'vpn' : 'proxy';
    const name = String(source.name || '').trim();
    const id = String(source.id || '').trim() || slugifyIntegrationNetworkProfileName(name);
    return {
      id,
      name,
      mode,
      proxy: normalizeNetworkProxyConfig(source.proxy),
      vpn: normalizeNetworkVpnConfig(source.vpn),
    };
  }

  function createRuntime(options = {}) {
    const state = {
      integrationNetwork: normalizeIntegrationNetworkConfig(options.initialIntegrationNetwork),
    };

    const elements = {
      integrationNetworkSaveBtn: document.getElementById('integrationNetworkSaveBtn'),
      integrationNetworkStatusEl: document.getElementById('integrationNetworkStatus'),
      projectNetworkModeInput: document.getElementById('projectNetworkMode'),
      projectProxySchemeInput: document.getElementById('projectProxyScheme'),
      projectProxyHostInput: document.getElementById('projectProxyHost'),
      projectProxyPortInput: document.getElementById('projectProxyPort'),
      projectProxyUsernameInput: document.getElementById('projectProxyUsername'),
      projectProxyPasswordInput: document.getElementById('projectProxyPassword'),
      projectProxyTokenInput: document.getElementById('projectProxyToken'),
      projectVpnNameInput: document.getElementById('projectVpnName'),
      projectVpnEndpointInput: document.getElementById('projectVpnEndpoint'),
      projectVpnConfigPathInput: document.getElementById('projectVpnConfigPath'),
      projectVpnNotesInput: document.getElementById('projectVpnNotes'),
      projectFailoverDowntimeSecondsInput: document.getElementById('projectFailoverDowntimeSeconds'),
      botsNetworkModeInput: document.getElementById('botsNetworkMode'),
      botsProxySchemeInput: document.getElementById('botsProxyScheme'),
      botsProxyHostInput: document.getElementById('botsProxyHost'),
      botsProxyPortInput: document.getElementById('botsProxyPort'),
      botsProxyUsernameInput: document.getElementById('botsProxyUsername'),
      botsProxyPasswordInput: document.getElementById('botsProxyPassword'),
      botsProxyTokenInput: document.getElementById('botsProxyToken'),
      botsVpnNameInput: document.getElementById('botsVpnName'),
      botsVpnEndpointInput: document.getElementById('botsVpnEndpoint'),
      botsVpnConfigPathInput: document.getElementById('botsVpnConfigPath'),
      botsVpnNotesInput: document.getElementById('botsVpnNotes'),
      botsFailoverDowntimeSecondsInput: document.getElementById('botsFailoverDowntimeSeconds'),
      projectNetworkProfileIdInput: document.getElementById('projectNetworkProfileId'),
      botsNetworkProfileIdInput: document.getElementById('botsNetworkProfileId'),
      channelEditorNetworkModeInput: document.getElementById('channelEditorNetworkMode'),
      channelEditorProxySchemeInput: document.getElementById('channelEditorProxyScheme'),
      channelEditorProxyHostInput: document.getElementById('channelEditorProxyHost'),
      channelEditorProxyPortInput: document.getElementById('channelEditorProxyPort'),
      channelEditorProxyUsernameInput: document.getElementById('channelEditorProxyUsername'),
      channelEditorProxyPasswordInput: document.getElementById('channelEditorProxyPassword'),
      channelEditorProxyTokenInput: document.getElementById('channelEditorProxyToken'),
      channelEditorVpnNameInput: document.getElementById('channelEditorVpnName'),
      channelEditorVpnEndpointInput: document.getElementById('channelEditorVpnEndpoint'),
      channelEditorVpnConfigPathInput: document.getElementById('channelEditorVpnConfigPath'),
      channelEditorVpnNotesInput: document.getElementById('channelEditorVpnNotes'),
      channelEditorFailoverDowntimeSecondsInput: document.getElementById('channelEditorFailoverDowntimeSeconds'),
      channelEditorNetworkProfileIdInput: document.getElementById('channelEditorNetworkProfileId'),
      integrationNetworkProfileEditorModalEl: document.getElementById('integrationNetworkProfileEditorModal'),
    };

    function getProfilesData() {
      const source = typeof options.getProfilesData === 'function' ? options.getProfilesData() : [];
      return Array.isArray(source) ? source : [];
    }

    function getChannelsRegistry() {
      const registry = typeof options.getChannelsRegistry === 'function' ? options.getChannelsRegistry() : null;
      return registry instanceof Map ? registry : new Map();
    }

    function getIntegrationProfileModalElements() {
      const modalEl = elements.integrationNetworkProfileEditorModalEl;
      return {
        modalEl,
        proxySchemeInput: modalEl ? modalEl.querySelector('[data-integration-network-profile-proxy-scheme]') : null,
      };
    }

    function getRouteInputs(scope) {
      if (scope === 'project') {
        return {
          modeInput: elements.projectNetworkModeInput,
          profileSelect: elements.projectNetworkProfileIdInput,
          downtimeInput: elements.projectFailoverDowntimeSecondsInput,
          proxySchemeInput: elements.projectProxySchemeInput,
          proxyHostInput: elements.projectProxyHostInput,
          proxyPortInput: elements.projectProxyPortInput,
          proxyUsernameInput: elements.projectProxyUsernameInput,
          proxyPasswordInput: elements.projectProxyPasswordInput,
          proxyTokenInput: elements.projectProxyTokenInput,
          vpnNameInput: elements.projectVpnNameInput,
          vpnEndpointInput: elements.projectVpnEndpointInput,
          vpnConfigPathInput: elements.projectVpnConfigPathInput,
          vpnNotesInput: elements.projectVpnNotesInput,
        };
      }
      if (scope === 'bots') {
        return {
          modeInput: elements.botsNetworkModeInput,
          profileSelect: elements.botsNetworkProfileIdInput,
          downtimeInput: elements.botsFailoverDowntimeSecondsInput,
          proxySchemeInput: elements.botsProxySchemeInput,
          proxyHostInput: elements.botsProxyHostInput,
          proxyPortInput: elements.botsProxyPortInput,
          proxyUsernameInput: elements.botsProxyUsernameInput,
          proxyPasswordInput: elements.botsProxyPasswordInput,
          proxyTokenInput: elements.botsProxyTokenInput,
          vpnNameInput: elements.botsVpnNameInput,
          vpnEndpointInput: elements.botsVpnEndpointInput,
          vpnConfigPathInput: elements.botsVpnConfigPathInput,
          vpnNotesInput: elements.botsVpnNotesInput,
        };
      }
      return {
        modeInput: elements.channelEditorNetworkModeInput,
        profileSelect: elements.channelEditorNetworkProfileIdInput,
        downtimeInput: elements.channelEditorFailoverDowntimeSecondsInput,
        proxySchemeInput: elements.channelEditorProxySchemeInput,
        proxyHostInput: elements.channelEditorProxyHostInput,
        proxyPortInput: elements.channelEditorProxyPortInput,
        proxyUsernameInput: elements.channelEditorProxyUsernameInput,
        proxyPasswordInput: elements.channelEditorProxyPasswordInput,
        proxyTokenInput: elements.channelEditorProxyTokenInput,
        vpnNameInput: elements.channelEditorVpnNameInput,
        vpnEndpointInput: elements.channelEditorVpnEndpointInput,
        vpnConfigPathInput: elements.channelEditorVpnConfigPathInput,
        vpnNotesInput: elements.channelEditorVpnNotesInput,
      };
    }

    function getIntegrationNetworkProfileOptions() {
      return getProfilesData().map((item) => normalizeIntegrationNetworkProfile(item));
    }

    function findIntegrationNetworkProfile(profileId) {
      const normalizedId = String(profileId || '').trim();
      if (!normalizedId) {
        return null;
      }
      return getIntegrationNetworkProfileOptions().find((item) => item.id === normalizedId) || null;
    }

    function collectMultiSelectValues(selectEl) {
      if (!selectEl) {
        return [];
      }
      return Array.from(selectEl.selectedOptions || [])
        .map((option) => String(option?.value || '').trim())
        .filter((value) => value);
    }

    function enableMultiSelectToggle(selectEl) {
      if (!selectEl || selectEl.dataset.multiToggleReady === 'true') {
        return;
      }
      selectEl.dataset.multiToggleReady = 'true';
      selectEl.addEventListener('mousedown', (event) => {
        const option = event.target instanceof HTMLOptionElement ? event.target : null;
        if (!option) {
          return;
        }
        event.preventDefault();
        option.selected = !option.selected;
        selectEl.focus();
        selectEl.dispatchEvent(new Event('change', { bubbles: true }));
      });
    }

    function applyMultiSelectValues(selectEl, values) {
      if (!selectEl) {
        return;
      }
      const selected = new Set(normalizeProfileIds(values));
      Array.from(selectEl.options || []).forEach((option) => {
        option.selected = selected.has(option.value);
      });
    }

    function isVlessScheme(value) {
      return String(value || '').trim().toLowerCase() === 'vless';
    }

    function toggleProxyCredentialFields(scope) {
      let scheme = '';
      let authRows = [];
      let tokenRows = [];
      if (scope === 'project') {
        scheme = elements.projectProxySchemeInput?.value || '';
        authRows = Array.from(document.querySelectorAll('[data-proxy-auth="project"]'));
        tokenRows = Array.from(document.querySelectorAll('[data-proxy-token="project"]'));
      } else if (scope === 'bots') {
        scheme = elements.botsProxySchemeInput?.value || '';
        authRows = Array.from(document.querySelectorAll('[data-proxy-auth="bots"]'));
        tokenRows = Array.from(document.querySelectorAll('[data-proxy-token="bots"]'));
      } else if (scope === 'channel') {
        scheme = elements.channelEditorProxySchemeInput?.value || '';
        authRows = Array.from(document.querySelectorAll('[data-proxy-auth="channel"]'));
        tokenRows = Array.from(document.querySelectorAll('[data-proxy-token="channel"]'));
      } else if (scope === 'integration-profile') {
        const modal = getIntegrationProfileModalElements();
        scheme = modal.proxySchemeInput?.value || '';
        authRows = Array.from(modal.modalEl?.querySelectorAll('[data-proxy-auth="integration-profile"]') || []);
        tokenRows = Array.from(modal.modalEl?.querySelectorAll('[data-proxy-token="integration-profile"]') || []);
      }
      const showToken = isVlessScheme(scheme);
      authRows.forEach((element) => element.classList.toggle('d-none', showToken));
      tokenRows.forEach((element) => element.classList.toggle('d-none', !showToken));
    }

    function collectRouteFromInputs(scope) {
      const routeInputs = getRouteInputs(scope);
      const profileIds = collectMultiSelectValues(routeInputs.profileSelect);
      return normalizeNetworkRoute({
        mode: routeInputs.modeInput?.value || (scope === 'project' ? 'direct' : 'inherit'),
        profile_id: profileIds[0] || '',
        profile_ids: profileIds,
        failover_downtime_seconds: routeInputs.downtimeInput?.value || 120,
        proxy: {
          scheme: routeInputs.proxySchemeInput?.value || 'http',
          host: routeInputs.proxyHostInput?.value || '',
          port: routeInputs.proxyPortInput?.value || 8080,
          username: routeInputs.proxyUsernameInput?.value || '',
          password: routeInputs.proxyPasswordInput?.value || '',
          token: routeInputs.proxyTokenInput?.value || '',
        },
        vpn: {
          name: routeInputs.vpnNameInput?.value || '',
          endpoint: routeInputs.vpnEndpointInput?.value || '',
          config_path: routeInputs.vpnConfigPathInput?.value || '',
          notes: routeInputs.vpnNotesInput?.value || '',
        },
      }, scope !== 'project');
    }

    function applyRouteToInputs(scope, route) {
      const routeInputs = getRouteInputs(scope);
      const normalized = normalizeNetworkRoute(route, scope !== 'project');
      if (routeInputs.modeInput) routeInputs.modeInput.value = normalized.mode;
      if (routeInputs.profileSelect) applyMultiSelectValues(routeInputs.profileSelect, normalized.profile_ids);
      if (routeInputs.downtimeInput) routeInputs.downtimeInput.value = String(normalized.failover_downtime_seconds || 120);
      if (routeInputs.proxySchemeInput) routeInputs.proxySchemeInput.value = normalized.proxy.scheme;
      if (routeInputs.proxyHostInput) routeInputs.proxyHostInput.value = normalized.proxy.host;
      if (routeInputs.proxyPortInput) routeInputs.proxyPortInput.value = String(normalized.proxy.port || 8080);
      if (routeInputs.proxyUsernameInput) routeInputs.proxyUsernameInput.value = normalized.proxy.username;
      if (routeInputs.proxyPasswordInput) routeInputs.proxyPasswordInput.value = normalized.proxy.password;
      if (routeInputs.proxyTokenInput) routeInputs.proxyTokenInput.value = normalized.proxy.token || '';
      if (routeInputs.vpnNameInput) routeInputs.vpnNameInput.value = normalized.vpn.name;
      if (routeInputs.vpnEndpointInput) routeInputs.vpnEndpointInput.value = normalized.vpn.endpoint;
      if (routeInputs.vpnConfigPathInput) routeInputs.vpnConfigPathInput.value = normalized.vpn.config_path;
      if (routeInputs.vpnNotesInput) routeInputs.vpnNotesInput.value = normalized.vpn.notes;
      toggleNetworkRouteFields(scope);
    }

    function setChannelsManageText(kind, key, value) {
      document.querySelectorAll(`[data-channels-manage-${kind}="${key}"]`).forEach((element) => {
        if (element instanceof HTMLElement) {
          element.textContent = value;
        }
      });
    }

    function describeChannelsManageRoute(scope) {
      const route = collectRouteFromInputs(scope);
      const profileNames = (route.profile_ids || [])
        .map((id) => findIntegrationNetworkProfile(id))
        .filter((item) => Boolean(item))
        .map((item) => item.name || item.id);
      const routePrefix = scope === 'project' ? 'Проект' : 'Боты';
      if (route.mode === 'inherit') {
        return `${routePrefix}: наследуют проект`;
      }
      if (route.mode === 'profile') {
        return profileNames.length
          ? `${routePrefix}: ${profileNames.join(' → ')}`
          : `${routePrefix}: профиль не выбран`;
      }
      if (route.mode === 'proxy') {
        const host = route.proxy.host || 'host не указан';
        const scheme = String(route.proxy.scheme || 'proxy').toUpperCase();
        return `${routePrefix}: ${scheme} ${host}:${route.proxy.port || 8080}`;
      }
      if (route.mode === 'vpn') {
        const vpnName = route.vpn.name || route.vpn.endpoint || 'VPN';
        return `${routePrefix}: VPN ${vpnName}`;
      }
      return `${routePrefix}: без маршрутизации`;
    }

    function updateChannelsManageOverview() {
      const channelsRegistry = getChannelsRegistry();
      const channelCount = channelsRegistry.size;
      const activeCount = Array.from(channelsRegistry.values()).filter((channel) => channel?.is_active).length;
      const profileCount = getIntegrationNetworkProfileOptions().length;
      setChannelsManageText(
        'stat',
        'channels-count',
        `${channelCount} ${pluralizeRu(channelCount, 'канал', 'канала', 'каналов')}`,
      );
      setChannelsManageText(
        'stat',
        'active-count',
        `${activeCount} ${pluralizeRu(activeCount, 'активный', 'активных', 'активных')}`,
      );
      setChannelsManageText(
        'summary',
        'profiles-count',
        profileCount
          ? `${profileCount} ${pluralizeRu(profileCount, 'профиль', 'профиля', 'профилей')}`
          : 'Профили не созданы',
      );
      setChannelsManageText('summary', 'routes-project', describeChannelsManageRoute('project'));
      setChannelsManageText('summary', 'routes-bots', describeChannelsManageRoute('bots'));
    }

    function describeRouteSummary(scope) {
      const route = collectRouteFromInputs(scope);
      const profileNames = (route.profile_ids || [])
        .map((id) => findIntegrationNetworkProfile(id))
        .filter((item) => Boolean(item))
        .map((item) => item.name || item.id);
      const profileChainText = profileNames.length ? profileNames.join(' → ') : 'цепочка не задана';
      const ttlText = `${route.failover_downtime_seconds || 120} сек`;
      if (scope === 'project') {
        if (route.mode === 'profile') {
          if (!profileNames.length) {
            return 'Для проекта выбран режим «Профиль», но сам профиль ещё не указан или был удалён.';
          }
          return `Весь проект использует цепочку профилей: ${profileChainText}. При недоступности текущего маршрута переключение идёт на следующий, время блокировки — ${ttlText}.`;
        }
        if (route.mode === 'proxy') {
          const host = route.proxy.host || 'host не указан';
          return `Весь проект пойдёт через прокси ${host}:${route.proxy.port || 8080}. Эти настройки станут базовыми и для ботов, если ниже оставить наследование.`;
        }
        if (route.mode === 'vpn') {
          const name = route.vpn.name || 'VPN без названия';
          const endpoint = route.vpn.endpoint || 'endpoint не указан';
          return `Весь проект пойдёт через VPN «${name}» (${endpoint}). Укажите endpoint и путь к конфигу, чтобы инфраструктуре было понятно, какой туннель нужен.`;
        }
        return 'Проект работает напрямую без прокси/VPN. Чтобы вынести только ботов в отдельный маршрут, оставьте здесь «Без маршрутизации», а справа настройте отдельный прокси или VPN.';
      }
      if (scope === 'bots') {
        if (route.mode === 'inherit') {
          return 'Боты наследуют маршрут проекта. Это удобно, когда панели и ботам нужен один и тот же прокси/VPN.';
        }
        if (route.mode === 'profile') {
          if (!profileNames.length) {
            return 'Для подсистемы ботов выбран режим «Профиль», но профиль не найден.';
          }
          return `Боты используют цепочку профилей: ${profileChainText}. При ошибке соединения идёт автоматический failover, TTL недоступности — ${ttlText}.`;
        }
        if (route.mode === 'proxy') {
          const host = route.proxy.host || 'host не указан';
          return `Боты будут запускаться через отдельный прокси ${host}:${route.proxy.port || 8080}, а остальная панель сохранит маршрут из блока «Весь проект».`;
        }
        if (route.mode === 'vpn') {
          const name = route.vpn.name || 'VPN без названия';
          return `Боты будут запускаться через отдельный VPN «${name}». Это полезно, если только ботам нужен отдельный туннель.`;
        }
        return 'Боты будут работать напрямую, независимо от настроек проекта.';
      }
      if (route.mode === 'inherit') {
        return 'Этот бот наследует маршрут подсистемы ботов. Если там тоже стоит наследование, в итоге будет использован маршрут проекта.';
      }
      if (route.mode === 'profile') {
        if (!profileNames.length) {
          return 'Для этого бота выбран профиль, но сам профиль ещё не назначен.';
        }
        return `Этот бот использует цепочку профилей: ${profileChainText}. Переключение между профилями выполняется автоматически, TTL — ${ttlText}.`;
      }
      if (route.mode === 'proxy') {
        const host = route.proxy.host || 'host не указан';
        return `Только этот бот будет ходить через отдельный прокси ${host}:${route.proxy.port || 8080}. Это перекрывает общие настройки ботов.`;
      }
      if (route.mode === 'vpn') {
        const name = route.vpn.name || 'VPN без названия';
        return `Только этот бот будет работать через VPN «${name}». Это перекрывает общие настройки ботов.`;
      }
      return 'Этот бот будет работать напрямую и не использовать унаследованный прокси/VPN.';
    }

    function updateNetworkRouteProfilePreview(scope) {
      const previewEl = document.querySelector(`[data-network-route-profile-preview="${scope}"]`);
      if (!previewEl) {
        return;
      }
      const route = collectRouteFromInputs(scope);
      if (route.mode !== 'profile') {
        previewEl.textContent = 'Режим профилей сейчас не активен.';
        return;
      }
      const profileNames = (route.profile_ids || [])
        .map((id) => findIntegrationNetworkProfile(id))
        .filter((item) => Boolean(item))
        .map((item) => item.name || item.id);
      if (!profileNames.length) {
        previewEl.textContent = 'Цепочка пока не выбрана.';
        return;
      }
      previewEl.innerHTML = profileNames
        .map((name, index) => `${index + 1}. ${escapeHtml(name)}`)
        .join('<br>');
    }

    function updateNetworkRouteSummary(scope) {
      const summaryEl = document.querySelector(`[data-network-route-summary="${scope}"]`);
      if (summaryEl) {
        summaryEl.textContent = describeRouteSummary(scope);
      }
      updateNetworkRouteProfilePreview(scope);
      if (scope === 'project' || scope === 'bots') {
        updateChannelsManageOverview();
      }
    }

    function toggleNetworkRouteFields(scope) {
      const routeInputs = getRouteInputs(scope);
      const mode = routeInputs.modeInput?.value || (scope === 'project' ? 'direct' : 'inherit');
      document.querySelectorAll(`[data-network-route-profile="${scope}"]`).forEach((element) => {
        element.classList.toggle('d-none', mode !== 'profile');
      });
      document.querySelectorAll(`[data-network-route-proxy="${scope}"]`).forEach((element) => {
        element.classList.toggle('d-none', mode !== 'proxy');
      });
      document.querySelectorAll(`[data-network-route-vpn="${scope}"]`).forEach((element) => {
        element.classList.toggle('d-none', mode !== 'vpn');
      });
      toggleProxyCredentialFields(scope);
      updateNetworkRouteSummary(scope);
    }

    function renderIntegrationNetworkProfileSelectors() {
      const options = getIntegrationNetworkProfileOptions();
      [
        elements.projectNetworkProfileIdInput,
        elements.botsNetworkProfileIdInput,
        elements.channelEditorNetworkProfileIdInput,
      ].forEach((selectEl) => {
        if (!selectEl) {
          return;
        }
        const currentValues = collectMultiSelectValues(selectEl);
        selectEl.innerHTML = '';
        options.forEach((profile) => {
          const option = document.createElement('option');
          option.value = profile.id;
          option.textContent = `${profile.name || profile.id} · ${profile.mode === 'vpn' ? 'VPN' : 'Прокси'}`;
          selectEl.appendChild(option);
        });
        applyMultiSelectValues(selectEl, currentValues);
      });
      updateNetworkRouteProfilePreview('project');
      updateNetworkRouteProfilePreview('bots');
      updateNetworkRouteProfilePreview('channel');
    }

    function renderIntegrationNetworkSettings() {
      renderIntegrationNetworkProfileSelectors();
      applyRouteToInputs('project', state.integrationNetwork.project);
      applyRouteToInputs('bots', state.integrationNetwork.bots);
      enableMultiSelectToggle(elements.projectNetworkProfileIdInput);
      enableMultiSelectToggle(elements.botsNetworkProfileIdInput);
      enableMultiSelectToggle(elements.channelEditorNetworkProfileIdInput);
      if (elements.integrationNetworkStatusEl) {
        elements.integrationNetworkStatusEl.textContent = 'HTTP/SOCKS/VLESS прокси применяются к запросам панели и передаются отдельным процессам ботов.';
      }
      updateChannelsManageOverview();
    }

    async function saveIntegrationNetworkSettings() {
      const projectRoute = collectRouteFromInputs('project');
      const botsRoute = collectRouteFromInputs('bots');
      if (projectRoute.mode === 'profile' && !(projectRoute.profile_ids || []).length) {
        notify('Для маршрута «Весь проект» выберите хотя бы один профиль в цепочке failover.');
        return;
      }
      if (botsRoute.mode === 'profile' && !(botsRoute.profile_ids || []).length) {
        notify('Для маршрута «Подсистема ботов» выберите хотя бы один профиль в цепочке failover.');
        return;
      }
      if (projectRoute.mode === 'proxy' && projectRoute.proxy.scheme === 'vless' && !String(projectRoute.proxy.token || '').trim()) {
        notify('Для маршрута «Весь проект» в режиме VLESS заполните token.');
        return;
      }
      if (botsRoute.mode === 'proxy' && botsRoute.proxy.scheme === 'vless' && !String(botsRoute.proxy.token || '').trim()) {
        notify('Для маршрута «Подсистема ботов» в режиме VLESS заполните token.');
        return;
      }
      const payload = {
        integration_network: {
          project: projectRoute,
          bots: botsRoute,
        },
      };
      try {
        if (elements.integrationNetworkSaveBtn) {
          elements.integrationNetworkSaveBtn.disabled = true;
        }
        const response = await fetch('/settings', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok || data.success === false) {
          throw new Error(data.error || (`HTTP ${response.status}`));
        }
        state.integrationNetwork.project = payload.integration_network.project;
        state.integrationNetwork.bots = payload.integration_network.bots;
        renderIntegrationNetworkSettings();
        if (elements.integrationNetworkStatusEl) {
          elements.integrationNetworkStatusEl.textContent = 'Сетевые маршруты сохранены. Новые запуски ботов получат обновлённые параметры автоматически.';
        }
        notify('Сетевые маршруты сохранены.');
      } catch (error) {
        if (elements.integrationNetworkStatusEl) {
          elements.integrationNetworkStatusEl.textContent = 'Не удалось сохранить сетевые маршруты: ' + error.message;
        }
        notify('Не удалось сохранить сетевые маршруты: ' + error.message);
      } finally {
        if (elements.integrationNetworkSaveBtn) {
          elements.integrationNetworkSaveBtn.disabled = false;
        }
      }
    }

    function resetMissingIntegrationNetworkProfileSelections() {
      ['project', 'bots', 'channel'].forEach((scope) => {
        const route = collectRouteFromInputs(scope);
        if (route.mode === 'profile') {
          const routeInputs = getRouteInputs(scope);
          const validIds = (route.profile_ids || []).filter((id) => Boolean(findIntegrationNetworkProfile(id)));
          applyMultiSelectValues(routeInputs.profileSelect, validIds);
        }
        updateNetworkRouteSummary(scope);
      });
    }

    return {
      parsePlatformConfig,
      normalizeChannelWorkingHours,
      normalizeIntegrationNetworkProfile,
      getIntegrationNetworkProfileOptions,
      findIntegrationNetworkProfile,
      collectMultiSelectValues,
      applyMultiSelectValues,
      normalizeNetworkRoute,
      toggleProxyCredentialFields,
      applyRouteToInputs,
      collectRouteFromInputs,
      updateNetworkRouteSummary,
      toggleNetworkRouteFields,
      renderIntegrationNetworkSettings,
      saveIntegrationNetworkSettings,
      renderIntegrationNetworkProfileSelectors,
      resetMissingIntegrationNetworkProfileSelections,
      updateChannelsManageOverview,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsIntegrationNetworkRuntime) {
        return window.__settingsIntegrationNetworkRuntime;
      }
      const runtime = createRuntime(options);
      window.__settingsIntegrationNetworkRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsIntegrationNetworkRuntime = Object.freeze(api);
}());
