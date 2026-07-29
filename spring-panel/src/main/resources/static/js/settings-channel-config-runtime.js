(function () {
  if (window.SettingsChannelConfigRuntime) {
    return;
  }

  function defaultChannelPanelNotifications() {
    return {
      routing: {
        department: '',
        targetMode: 'all_operators',
        deliveryMode: 'all',
        employeeUsernames: [],
        excludeUsernames: [],
      },
      events: {
        newPublicAppeal: true,
        firstResponseOverdue: false,
      },
    };
  }

  function defaultChannelAssignmentRouting() {
    return {
      enabled: false,
      mode: 'disabled',
      assignResponsibleOnCreate: false,
      strategy: 'round_robin',
      operatorUsername: '',
      operatorUsernames: [],
      department: '',
    };
  }

  function createDefaultQuestionsCfg() {
    return {
      schemaVersion: 1,
      enabled: true,
      captchaEnabled: false,
      rateLimitEnabled: null,
      rateLimitWindowSeconds: null,
      rateLimitMaxRequests: null,
      disabledStatus: 404,
      successInstruction: '',
      responseEtaMinutes: null,
      fields: [],
      assignmentRouting: defaultChannelAssignmentRouting(),
      panelNotifications: defaultChannelPanelNotifications(),
    };
  }

  function parseChannelNotificationList(raw) {
    if (Array.isArray(raw)) {
      return raw
        .map((value) => String(value || '').trim().toLowerCase())
        .filter(Boolean);
    }
    if (typeof raw === 'string') {
      return raw
        .split(/[\s,;]+/)
        .map((value) => String(value || '').trim().toLowerCase())
        .filter(Boolean);
    }
    return [];
  }

  function serializeChannelNotificationList(raw) {
    return Array.from(new Set(parseChannelNotificationList(raw)));
  }

  function parseDeliverySettings(raw) {
    if (!raw) {
      return {};
    }
    if (typeof raw === 'string') {
      try {
        const parsed = JSON.parse(raw);
        return parsed && typeof parsed === 'object' ? parsed : {};
      } catch (error) {
        return {};
      }
    }
    if (typeof raw === 'object') {
      return Object.assign({}, raw);
    }
    return {};
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

  function parseQuestionsCfg(raw) {
    if (!raw) {
      return createDefaultQuestionsCfg();
    }
    if (Array.isArray(raw)) {
      const defaults = createDefaultQuestionsCfg();
      return {
        ...defaults,
        fields: raw,
      };
    }
    if (typeof raw === 'string') {
      try {
        return parseQuestionsCfg(JSON.parse(raw));
      } catch (error) {
        return createDefaultQuestionsCfg();
      }
    }
    if (typeof raw !== 'object') {
      return createDefaultQuestionsCfg();
    }

    const disabledStatusRaw = Number.parseInt(raw.disabledStatus || 404, 10);
    const defaults = defaultChannelPanelNotifications();
    const assignmentDefaults = defaultChannelAssignmentRouting();
    const legacyAlertQueue = raw.alertQueue && typeof raw.alertQueue === 'object'
      ? raw.alertQueue
      : {};
    const assignmentRoutingRaw = raw.assignmentRouting && typeof raw.assignmentRouting === 'object'
      ? raw.assignmentRouting
      : {};
    const panelNotificationsRaw = raw.panelNotifications && typeof raw.panelNotifications === 'object'
      ? raw.panelNotifications
      : null;
    const routingSource = panelNotificationsRaw?.routing && typeof panelNotificationsRaw.routing === 'object'
      ? panelNotificationsRaw.routing
      : (panelNotificationsRaw || {});
    const legacyMode = String(legacyAlertQueue.targetMode || '').trim().toLowerCase();
    const targetModeRaw = String(routingSource.targetMode || '').trim().toLowerCase();
    const resolvedTargetMode = ['all_operators', 'department_all', 'employees_only', 'department_except'].includes(targetModeRaw)
      ? targetModeRaw
      : (['department_all', 'employees_only', 'department_except'].includes(legacyMode) ? legacyMode : defaults.routing.targetMode);
    const deliveryModeRaw = String(routingSource.deliveryMode || '').trim().toLowerCase();
    const resolvedDeliveryMode = ['all', 'online_only_fallback_all'].includes(deliveryModeRaw)
      ? deliveryModeRaw
      : defaults.routing.deliveryMode;
    const eventSource = panelNotificationsRaw?.events && typeof panelNotificationsRaw.events === 'object'
      ? panelNotificationsRaw.events
      : {};
    const assignmentModeRaw = String(assignmentRoutingRaw.mode || '').trim().toLowerCase();
    const assignmentMode = ['single_operator', 'operator_pool', 'department_queue'].includes(assignmentModeRaw)
      ? assignmentModeRaw
      : assignmentDefaults.mode;
    const assignmentEnabled = Boolean(assignmentRoutingRaw.enabled) && assignmentMode !== 'disabled';
    const assignmentStrategyRaw = String(assignmentRoutingRaw.strategy || '').trim().toLowerCase();
    const assignmentStrategy = ['least_loaded', 'hash_by_ticket'].includes(assignmentStrategyRaw)
      ? assignmentStrategyRaw
      : assignmentDefaults.strategy;

    return {
      schemaVersion: Number.parseInt(raw.schemaVersion || 1, 10) || 1,
      enabled: raw.enabled !== false,
      captchaEnabled: Boolean(raw.captchaEnabled),
      rateLimitEnabled: typeof raw.rateLimitEnabled === 'boolean' ? raw.rateLimitEnabled : null,
      rateLimitWindowSeconds: Number.isFinite(Number.parseInt(raw.rateLimitWindowSeconds, 10))
        ? Number.parseInt(raw.rateLimitWindowSeconds, 10)
        : null,
      rateLimitMaxRequests: Number.isFinite(Number.parseInt(raw.rateLimitMaxRequests, 10))
        ? Number.parseInt(raw.rateLimitMaxRequests, 10)
        : null,
      disabledStatus: disabledStatusRaw === 410 ? 410 : 404,
      successInstruction: String(raw.successInstruction || '').trim(),
      responseEtaMinutes: Number.isFinite(Number.parseInt(raw.responseEtaMinutes, 10))
        ? Math.max(0, Math.min(10080, Number.parseInt(raw.responseEtaMinutes, 10)))
        : null,
      fields: Array.isArray(raw.fields) ? raw.fields : [],
      assignmentRouting: {
        enabled: assignmentEnabled,
        mode: assignmentEnabled ? assignmentMode : 'disabled',
        assignResponsibleOnCreate: assignmentEnabled
          ? Boolean(assignmentRoutingRaw.assignResponsibleOnCreate)
          : false,
        strategy: assignmentStrategy,
        operatorUsername: String(assignmentRoutingRaw.operatorUsername || '').trim().toLowerCase(),
        operatorUsernames: parseChannelNotificationList(assignmentRoutingRaw.operatorUsernames),
        department: String(assignmentRoutingRaw.department || '').trim(),
      },
      panelNotifications: {
        routing: {
          department: String(routingSource.department || legacyAlertQueue.department || '').trim(),
          targetMode: resolvedTargetMode,
          deliveryMode: resolvedDeliveryMode,
          employeeUsernames: parseChannelNotificationList(routingSource.employeeUsernames ?? legacyAlertQueue.employeeUsernames),
          excludeUsernames: parseChannelNotificationList(routingSource.excludeUsernames ?? legacyAlertQueue.excludeUsernames),
        },
        events: {
          newPublicAppeal: typeof eventSource.newPublicAppeal === 'boolean'
            ? eventSource.newPublicAppeal
            : (typeof legacyAlertQueue.enabled === 'boolean' ? legacyAlertQueue.enabled : defaults.events.newPublicAppeal),
          firstResponseOverdue: typeof eventSource.firstResponseOverdue === 'boolean'
            ? eventSource.firstResponseOverdue
            : defaults.events.firstResponseOverdue,
        },
      },
    };
  }

  function createRuntime() {
    return {
      defaultChannelAssignmentRouting,
      defaultChannelPanelNotifications,
      parseChannelNotificationList,
      serializeChannelNotificationList,
      parseDeliverySettings,
      parsePlatformConfig,
      parseQuestionsCfg,
    };
  }

  const api = {
    mount() {
      return createRuntime();
    },
  };

  window.SettingsChannelConfigRuntime = Object.freeze(api);
}());
