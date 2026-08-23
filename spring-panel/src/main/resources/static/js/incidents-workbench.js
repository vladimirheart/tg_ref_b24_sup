(function () {
  const listNode = document.getElementById('incidentWorkbenchList');
  if (!listNode) {
    return;
  }

  const detailNode = document.getElementById('incidentWorkbenchDetail');
  const errorNode = document.getElementById('incidentWorkbenchError');
  const successNode = document.getElementById('incidentWorkbenchSuccess');
  const listMetaNode = document.getElementById('incidentWorkbenchListMeta');
  const opsNodes = {
    root: document.getElementById('incidentOpsSummary'),
    meta: document.getElementById('incidentOpsSummaryMeta'),
    active: document.getElementById('incidentOpsActive'),
    critical: document.getElementById('incidentOpsCritical'),
    criticalCard: document.getElementById('incidentOpsCriticalCard'),
    aged: document.getElementById('incidentOpsAged'),
    agedCard: document.getElementById('incidentOpsAgedCard'),
    failedRoutes: document.getElementById('incidentOpsFailedRoutes'),
    failedRoutesCard: document.getElementById('incidentOpsFailedRoutesCard'),
    created24h: document.getElementById('incidentOpsCreated24h'),
    resolved24h: document.getElementById('incidentOpsResolved24h'),
    mtta: document.getElementById('incidentOpsMtta'),
    mttr: document.getElementById('incidentOpsMttr')
  };
  const payloadModalElement = document.getElementById('incidentWorkbenchPayloadModal');
  const payloadContentNode = document.getElementById('incidentWorkbenchPayloadContent');
  const payloadModal = payloadModalElement && window.bootstrap ? new window.bootstrap.Modal(payloadModalElement) : null;

  const state = {
    incidents: [],
    selectedIncidentId: null,
    selectedIncident: null,
    transportOverview: null,

    listRequestSerial: 0,
    detailRequestSerial: 0,
    transportRequestSerial: 0,
    payloadRequestSerial: 0,
    ticketDebugRequestSerial: 0,
    incidentOpsSummaryRequestSerial: 0,

    successHideTimer: null,

	incidentDetailDirty: false,
	incidentDetailDrafts: new Map()
};

  const INCIDENT_STATUS_OPTIONS = Object.freeze([
    { value: 'open', label: 'Открыт' },
    { value: 'acknowledged', label: 'Принят' },
    { value: 'investigating', label: 'В работе' },
    { value: 'resolved', label: 'Решён' },
    { value: 'closed', label: 'Закрыт' }
  ]);

  function incidentStatusLabel(value) {
    const normalized = String(value || '').trim();
    return INCIDENT_STATUS_OPTIONS.find((item) => item.value === normalized)?.label || normalized || '—';
  }

  function renderIncidentStatusOptions(currentStatus) {
    return INCIDENT_STATUS_OPTIONS.map((item) => `
      <option value="${escapeHtml(item.value)}" ${item.value === currentStatus ? 'selected' : ''}>${escapeHtml(item.label)}</option>
    `).join('');
  }

  const filterNodes = {
    query: document.getElementById('incidentWorkbenchQuery'),
    status: document.getElementById('incidentWorkbenchStatusFilter'),
    severity: document.getElementById('incidentWorkbenchSeverityFilter'),
    signalType: document.getElementById('incidentWorkbenchSignalTypeFilter'),
    limit: document.getElementById('incidentWorkbenchLimitFilter')
  };

  const createNodes = {
    title: document.getElementById('incidentCreateTitle'),
    summary: document.getElementById('incidentCreateSummary'),
    severity: document.getElementById('incidentCreateSeverity'),
    status: document.getElementById('incidentCreateStatus'),
    owner: document.getElementById('incidentCreateOwner'),
    source: document.getElementById('incidentCreateSource'),
    relationType: document.getElementById('incidentCreateRelationType'),
    relationKey: document.getElementById('incidentCreateRelationKey'),
    watchers: document.getElementById('incidentCreateWatchers')
  };

  const transportNodes = {
		pane:
			document.getElementById(
				'incidentWorkbenchTransportPane'
			),

		status:
			document.getElementById(
				'transportWorkbenchStatus'
			),

		inboundList:
			document.getElementById(
				'transportWorkbenchInboundList'
			),
    outboundList: document.getElementById('transportWorkbenchOutboundList'),
    checkpointList: document.getElementById('transportWorkbenchCheckpointList'),
    incidentList: document.getElementById('transportWorkbenchIncidentList'),
    alertList: document.getElementById('transportWorkbenchAlertList'),
    operationList: document.getElementById('transportWorkbenchOperationList'),
    ticketDebug: document.getElementById('transportWorkbenchTicketDebug'),
    ticketId: document.getElementById('transportWorkbenchTicketId'),
    inboundFailed: document.getElementById('transportMetricInboundFailed'),
    inboundStale: document.getElementById('transportMetricInboundStale'),
    outboundFailed: document.getElementById('transportMetricOutboundFailed'),
    outboundBacklog: document.getElementById('transportMetricOutboundBacklog'),
    checkpointStale: document.getElementById('transportMetricCheckpointStale'),
    recentOps: document.getElementById('transportMetricRecentOps'),
    incidentCount: document.getElementById('transportMetricIncidentCount')
  };

  function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const headerName = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
    return token ? { [headerName]: token } : {};
  }

  async function requestJson(url, options = {}) {
    const {
        headers: optionHeaders = {},
        ...requestOptions
    } = options || {};

    const response = await fetch(url, {
        ...requestOptions,

        credentials: 'same-origin',

        headers: {
            'Accept': 'application/json',
            ...csrfHeaders(),
            ...optionHeaders
        }
    });

    if (!response.ok) {
        let message =
			`Ошибка запроса: HTTP ${response.status}`;

        try {
            const payload = await response.json();

            message =
                payload?.message ||
                payload?.error ||
                message;
        } catch (error) {
            // Оставляем fallback message.
        }

        throw new Error(message);
    }

    return response.json();
}

  function clearSuccessHideTimer() {
		if (!state.successHideTimer) {
			return;
		}

		window.clearTimeout(
			state.successHideTimer
		);

		state.successHideTimer = null;
	}

	function showError(message) {
		if (!errorNode) {
			return;
		}

		clearSuccessHideTimer();

		errorNode.textContent =
			String(message || 'Неизвестная ошибка');

		errorNode.classList.remove('d-none');

		successNode?.classList.add('d-none');
	}

	function showSuccess(message) {
		if (!successNode) {
			return;
		}

		clearSuccessHideTimer();

		successNode.textContent =
			String(message || 'Готово');

		successNode.classList.remove('d-none');

		errorNode?.classList.add('d-none');

		state.successHideTimer =
			window.setTimeout(() => {
				successNode.classList.add(
					'd-none'
				);

				state.successHideTimer = null;
			}, 3500);
	}
	async function runButtonAction(
		button,
		action
	) {
		if (
			!(button instanceof HTMLButtonElement) ||
			button.dataset.actionPending === 'true'
		) {
			return;
		}

		button.dataset.actionPending = 'true';

		button.setAttribute(
			'aria-disabled',
			'true'
		);

		button.setAttribute(
			'aria-busy',
			'true'
		);

		button.classList.add('disabled');

		try {
			await action();
		} finally {
			// Dynamic controls могут уже исчезнуть
			// после renderIncidentDetail /
			// renderTransportOverview.
			if (button.isConnected) {
				delete button.dataset.actionPending;

				button.removeAttribute(
					'aria-disabled'
				);

				button.removeAttribute(
					'aria-busy'
				);

				button.classList.remove(
					'disabled'
				);
			}
		}
	}
  function clearFeedback() {
    errorNode?.classList.add('d-none');
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function badgeClass(status) {
    switch (String(status || '').toLowerCase()) {
      case 'critical':
      case 'failed':
      case 'stale':
      case 'closed':
        return 'text-bg-danger';
      case 'high':
      case 'warning':
      case 'investigating':
      case 'processing':
        return 'text-bg-warning';
      case 'resolved':
      case 'published':
      case 'delivered':
      case 'healthy':
      case 'ok':
      case 'success':
        return 'text-bg-success';
      case 'acknowledged':
      case 'queued':
        return 'text-bg-info';
      default:
        return 'text-bg-secondary';
    }
  }

  function splitCommaList(value) {
    return String(value || '')
      .split(/[,\n;]+/)
      .map((item) => item.trim())
      .filter(Boolean);
  }

  function formatDate(value) {
    if (!value) {
        return '—';
    }

    const date =
        new Date(value);

    if (
        Number.isNaN(
            date.getTime()
        )
    ) {
        return String(value);
    }

    return date.toLocaleString('ru-RU');
}

  function selectedIncident() {
    return state.incidents.find((item) => Number(item.id) === Number(state.selectedIncidentId)) || null;
  }

function isIncidentStillSelected(incidentId) {
    const normalizedIncidentId =
        String(incidentId || '').trim();

    if (!normalizedIncidentId) {
        return false;
    }

    return (
        String(state.selectedIncidentId || '') ===
        normalizedIncidentId
    );
}

async function refreshIncidentIfStillSelected(
    incidentId
) {
    if (!isIncidentStillSelected(incidentId)) {
        return;
    }

    await loadIncidentDetail(incidentId);
}

const INCIDENT_DETAIL_FIELD_IDS = [
    'incidentDetailTitle',
    'incidentDetailSummary',
    'incidentDetailSeverity',
    'incidentDetailOwner',
    'incidentDetailSource',
    'incidentDetailDescription'
];

function isIncidentDetailField(element) {
    return (
        element instanceof Element &&
        INCIDENT_DETAIL_FIELD_IDS.includes(
            element.id
        )
    );
}

function captureIncidentDetailDraft(
    incidentId
) {
    if (!state.incidentDetailDirty) {
        return null;
    }

    const normalizedIncidentId =
        String(incidentId || '').trim();

    if (
        !normalizedIncidentId ||
        String(state.selectedIncidentId || '') !==
            normalizedIncidentId
    ) {
        return null;
    }

    const values = {};

    INCIDENT_DETAIL_FIELD_IDS.forEach(
        (fieldId) => {
            const field =
                document.getElementById(fieldId);

            if (
                field instanceof HTMLInputElement ||
                field instanceof HTMLTextAreaElement ||
                field instanceof HTMLSelectElement
            ) {
                values[fieldId] =
                    field.value;
            }
        }
    );

    return {
        incidentId: normalizedIncidentId,
        values
    };
}

function restoreIncidentDetailDraft(draft) {
    if (
        !draft ||
        String(state.selectedIncidentId || '') !==
            String(draft.incidentId || '')
    ) {
        return;
    }

    Object.entries(
        draft.values || {}
    ).forEach(([fieldId, value]) => {
        const field =
            document.getElementById(fieldId);

        if (
            field instanceof HTMLInputElement ||
            field instanceof HTMLTextAreaElement ||
            field instanceof HTMLSelectElement
        ) {
            field.value =
                String(value ?? '');
        }
    });

    state.incidentDetailDirty = true;
}

function rememberCurrentIncidentDetailDraft() {
    const incidentId =
        String(
            state.selectedIncidentId || ''
        ).trim();

    if (!incidentId) {
        return;
    }

    const draft =
        captureIncidentDetailDraft(
            incidentId
        );

    if (!draft) {
        return;
    }

    state.incidentDetailDrafts.set(
        incidentId,
        draft
    );
}

function storedIncidentDetailDraft(
    incidentId
) {
    const normalizedIncidentId =
        String(incidentId || '').trim();

    if (!normalizedIncidentId) {
        return null;
    }

    return (
        state.incidentDetailDrafts.get(
            normalizedIncidentId
        ) || null
    );
}

function clearStoredIncidentDetailDraft(
    incidentId
) {
    const normalizedIncidentId =
        String(incidentId || '').trim();

    if (!normalizedIncidentId) {
        return;
    }

    state.incidentDetailDrafts.delete(
        normalizedIncidentId
    );
}

function getIncidentDetailFocusKey(
    element
) {
    if (
        !(element instanceof Element) ||
        !detailNode.contains(element)
    ) {
        return '';
    }

    if (element.id) {
        return `id:${element.id}`;
    }

    const removeWatcherButton =
        element.closest(
            '[data-incident-remove-watcher]'
        );

    if (removeWatcherButton) {
        return (
            'watcher-remove:' +
            (
                removeWatcherButton.getAttribute(
                    'data-incident-remove-watcher'
                ) || ''
            )
        );
    }

    const redeliverRouteButton =
        element.closest(
            '[data-incident-redeliver-route]'
        );

    if (redeliverRouteButton) {
        return (
            'route-redeliver:' +
            (
                redeliverRouteButton.getAttribute(
                    'data-incident-redeliver-route'
                ) || ''
            )
        );
    }

    return '';
}

function restoreIncidentDetailFocus(
    focusKey
) {
    if (!focusKey) {
        return;
    }

    window.requestAnimationFrame(() => {
        let target = null;

        if (focusKey.startsWith('id:')) {
            target =
                document.getElementById(
                    focusKey.slice(3)
                );
        }

        if (
            !target &&
            focusKey.startsWith(
                'watcher-remove:'
            )
        ) {
            const watcherIdentity =
                focusKey.slice(
                    'watcher-remove:'.length
                );

            target = Array.from(
                detailNode.querySelectorAll(
                    '[data-incident-remove-watcher]'
                )
            ).find(
                (item) =>
                    item.getAttribute(
                        'data-incident-remove-watcher'
                    ) === watcherIdentity
            ) || null;

            // После удаления конкретной кнопки
            // уже нет — возвращаем пользователя
            // к полю добавления watcher.
            if (!target) {
                target =
                    document.getElementById(
                        'incidentWatcherInput'
                    );
            }
        }

        if (
            !target &&
            focusKey.startsWith(
                'route-redeliver:'
            )
        ) {
            const routeId =
                focusKey.slice(
                    'route-redeliver:'.length
                );

            target = Array.from(
                detailNode.querySelectorAll(
                    '[data-incident-redeliver-route]'
                )
            ).find(
                (item) =>
                    item.getAttribute(
                        'data-incident-redeliver-route'
                    ) === routeId
            ) || null;

            if (!target) {
                target =
                    document.getElementById(
                        'incidentRouteTarget'
                    );
            }
        }

        if (
            target instanceof HTMLElement &&
            detailNode.contains(target)
        ) {
            target.focus({
                preventScroll: true
            });
        }
    });
}

  function formatMetricNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) ? String(number) : '—';
  }

  function formatMetricMinutes(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
      return '—';
    }
    if (number < 60) {
      return `${number.toLocaleString('ru-RU', { maximumFractionDigits: 1 })} мин`;
    }
    const hours = number / 60;
    return `${hours.toLocaleString('ru-RU', { maximumFractionDigits: 1 })} ч`;
  }

  function toggleOpsAlert(card, active) {
    card?.classList.toggle('is-active', Boolean(active));
  }

  function renderIncidentOpsSummary(payload) {
    if (!opsNodes.root) {
      return;
    }

    const active = Number(payload?.active_count || 0);
    const critical = Number(payload?.critical_active_count || 0);
    const aged = Number(payload?.aged_open_count || 0);
    const failedRoutes = Number(payload?.failed_route_count || 0);

    if (opsNodes.active) opsNodes.active.textContent = formatMetricNumber(active);
    if (opsNodes.critical) opsNodes.critical.textContent = formatMetricNumber(critical);
    if (opsNodes.aged) opsNodes.aged.textContent = formatMetricNumber(aged);
    if (opsNodes.failedRoutes) opsNodes.failedRoutes.textContent = formatMetricNumber(failedRoutes);
    if (opsNodes.created24h) opsNodes.created24h.textContent = formatMetricNumber(payload?.created_24h_count);
    if (opsNodes.resolved24h) opsNodes.resolved24h.textContent = formatMetricNumber(payload?.resolved_24h_count);
    if (opsNodes.mtta) opsNodes.mtta.textContent = formatMetricMinutes(payload?.avg_ack_minutes_7d);
    if (opsNodes.mttr) opsNodes.mttr.textContent = formatMetricMinutes(payload?.avg_resolve_minutes_7d);

    toggleOpsAlert(opsNodes.criticalCard, critical > 0);
    toggleOpsAlert(opsNodes.agedCard, aged > 0);
    toggleOpsAlert(opsNodes.failedRoutesCard, failedRoutes > 0);

    if (opsNodes.meta) {
      const days = Number(payload?.duration_window_days || 7);
      const ackSamples = Number(payload?.ack_sample_count_7d || 0);
      const resolveSamples = Number(payload?.resolve_sample_count_7d || 0);
      opsNodes.meta.textContent = `Обновлено ${formatDate(payload?.generated_at)} · средние за ${days} дн. · выборка ${ackSamples}/${resolveSamples}`;
    }
  }

  async function loadIncidentOpsSummary() {
    if (!opsNodes.root) {
      return;
    }

    const requestSerial = ++state.incidentOpsSummaryRequestSerial;
    opsNodes.root.setAttribute('aria-busy', 'true');
    try {
      const payload = await requestJson('/api/incidents/ops-summary');
      if (requestSerial !== state.incidentOpsSummaryRequestSerial) {
        return;
      }
      renderIncidentOpsSummary(payload);
    } catch (error) {
      if (requestSerial !== state.incidentOpsSummaryRequestSerial) {
        return;
      }
      if (opsNodes.meta) {
        opsNodes.meta.textContent = `Сводка временно недоступна: ${error.message}`;
      }
    } finally {
      if (requestSerial === state.incidentOpsSummaryRequestSerial) {
        opsNodes.root.removeAttribute('aria-busy');
      }
    }
  }

  async function loadIncidents() {
    void loadIncidentOpsSummary();

    const requestSerial =
        ++state.listRequestSerial;

    // Фильтр или Refresh может сменить selection.
    // До этого сохраняем текущие core fields.
    rememberCurrentIncidentDetailDraft();

    state.incidentDetailDirty = false;

    clearFeedback();

    listNode.setAttribute(
        'aria-busy',
        'true'
    );

    listMetaNode.textContent =
        'Загрузка...';

    const params =
        new URLSearchParams();

    if (filterNodes.query?.value.trim()) {
        params.set(
            'query',
            filterNodes.query.value.trim()
        );
    }

    if (filterNodes.status?.value) {
        params.set(
            'status',
            filterNodes.status.value
        );
    }

    if (filterNodes.severity?.value) {
        params.set(
            'severity',
            filterNodes.severity.value
        );
    }

    if (filterNodes.signalType?.value) {
        params.set(
            'signal_type',
            filterNodes.signalType.value
        );
    }

    params.set(
        'limit',
        filterNodes.limit?.value || '100'
    );

    try {
        const payload =
            await requestJson(
                `/api/incidents?${params.toString()}`
            );

        if (
            requestSerial !==
            state.listRequestSerial
        ) {
            return;
        }

        state.incidents =
            Array.isArray(payload?.items)
                ? payload.items
                : [];

        if (
            !state.selectedIncidentId ||
            !state.incidents.some(
                (item) =>
                    Number(item.id) ===
                    Number(
                        state.selectedIncidentId
                    )
            )
        ) {
            state.selectedIncidentId =
                state.incidents[0]?.id ||
                null;
        }

        renderIncidentList();

        listMetaNode.textContent =
            `Всего: ${state.incidents.length}`;

        listNode.setAttribute(
            'aria-busy',
            'false'
        );
    } catch (error) {
        if (
            requestSerial !==
            state.listRequestSerial
        ) {
            return;
        }

        listMetaNode.textContent =
            'Не удалось загрузить список incidents';

        throw error;
    } finally {
        if (
            requestSerial ===
            state.listRequestSerial
        ) {
            listNode.setAttribute(
                'aria-busy',
                'false'
            );
        }
    }

    if (
        requestSerial !==
        state.listRequestSerial
    ) {
        return;
    }

    if (state.selectedIncidentId) {
        await loadIncidentDetail(
            state.selectedIncidentId
        );
    } else {
        state.selectedIncident = null;

        renderIncidentDetail();
    }
}

  function renderIncidentList() {
    const focusedTrigger =
        document.activeElement instanceof Element
            ? document.activeElement.closest(
                '.incident-list-row-button'
            )
            : null;

    const focusedIncidentId =
        focusedTrigger
            ?.closest('[data-incident-id]')
            ?.getAttribute('data-incident-id') || '';

    if (!state.incidents.length) {
        listNode.innerHTML =
            '<tr><td colspan="4" class="text-center text-muted py-4">Incidents не найдены</td></tr>';
        return;
    }

    listNode.innerHTML = state.incidents.map((item) => `
      <tr class="incident-list-row ${Number(item.id) === Number(state.selectedIncidentId) ? 'is-active' : ''}" data-incident-id="${escapeHtml(item.id)}">
        <td>
			<button
				type="button"
				class="incident-list-row-button"
				aria-label="Открыть incident ${escapeHtml(item.incident_key || '')}: ${escapeHtml(item.title || '')}"
				aria-current="${
					Number(item.id) === Number(state.selectedIncidentId)
						? 'true'
						: 'false'
				}"
			>
			<span class="d-block fw-semibold">
			  ${escapeHtml(item.incident_key || '—')}
			</span>
			<span class="d-block">
			  ${escapeHtml(item.title || '')}
			</span>
			<span class="d-block small text-muted">
			  ${escapeHtml(item.summary || '')}
			</span>
		  </button>
		</td>
        <td><span class="badge ${badgeClass(item.severity)}">${escapeHtml(item.severity || '—')}</span></td>
        <td><span class="badge ${badgeClass(item.status)}">${escapeHtml(incidentStatusLabel(item.status))}</span></td>
        <td class="small text-muted">${escapeHtml(formatDate(item.updated_at))}</td>
      </tr>
    `).join('');
		if (focusedIncidentId) {
		window.requestAnimationFrame(() => {
			const nextRow = Array.from(
				listNode.querySelectorAll('[data-incident-id]')
			).find(
				(row) =>
					row.getAttribute('data-incident-id') ===
					focusedIncidentId
			);

			const nextTrigger =
				nextRow?.querySelector(
					'.incident-list-row-button'
				);

			if (nextTrigger) {
				nextTrigger.focus({
					preventScroll: true
				});
			}
		});
	}
  }

  async function loadIncidentDetail(incidentId) {
    const normalizedIncidentId =
        String(incidentId || '').trim();

    if (!normalizedIncidentId) {
        return;
    }

    const requestSerial =
    ++state.detailRequestSerial;

	// Сначала сохраняем draft того incident,
	// который открыт прямо сейчас.
	rememberCurrentIncidentDetailDraft();

	state.incidentDetailDirty = false;

	// Теперь берём draft того incident,
	// который собираемся открыть.
	const detailDraft =
		storedIncidentDetailDraft(
			normalizedIncidentId
		);

	const detailFocusKey =
		getIncidentDetailFocusKey(
			document.activeElement
		);

	state.selectedIncidentId =
		normalizedIncidentId;

	detailNode.dataset.incidentId =
		normalizedIncidentId;

	// Старый detail больше нельзя использовать для действий,
	// пока загружается новый incident.
	state.selectedIncident = null;

    renderIncidentList();

    detailNode.setAttribute(
        'aria-busy',
        'true'
    );

    detailNode.innerHTML =
        '<div class="text-muted" role="status">Загрузка incident...</div>';

    try {
        const payload = await requestJson(
            `/api/incidents/${encodeURIComponent(normalizedIncidentId)}`
        );

        const isCurrentRequest =
            requestSerial === state.detailRequestSerial &&
            String(state.selectedIncidentId) ===
                normalizedIncidentId;

        if (!isCurrentRequest) {
            return;
        }

        state.selectedIncident =
			payload?.incident || null;

		renderIncidentDetail();

		restoreIncidentDetailDraft(
			detailDraft
		);

		restoreIncidentDetailFocus(
			detailFocusKey
		);
    } catch (error) {
        const isCurrentRequest =
            requestSerial === state.detailRequestSerial &&
            String(state.selectedIncidentId) ===
                normalizedIncidentId;

        // Ошибка старого запроса уже не относится
        // к текущему выбранному incident.
        if (!isCurrentRequest) {
            return;
        }

        state.selectedIncident = null;

        detailNode.innerHTML =
            '<div class="text-danger">Не удалось загрузить incident.</div>';

        throw error;
    } finally {
        const isCurrentRequest =
            requestSerial === state.detailRequestSerial &&
            String(state.selectedIncidentId) ===
                normalizedIncidentId;

        if (isCurrentRequest) {
            detailNode.setAttribute(
                'aria-busy',
                'false'
            );
        }
    }
}

  function renderKeyValue(label, value) {
    return `<div class="incident-surface-item"><div class="small text-muted mb-1">${escapeHtml(label)}</div><div>${escapeHtml(value || '—')}</div></div>`;
  }

  function renderIncidentDetail() {
    const incident = state.selectedIncident;

    if (!incident) {
        state.incidentDetailDirty = false;
        delete detailNode.dataset.incidentId;

        detailNode.innerHTML =
            '<div class="text-muted">Выберите incident слева, чтобы открыть workbench.</div>';

        return;
    }
    const relations = Array.isArray(incident.relations) ? incident.relations : [];
    const watchers = Array.isArray(incident.watchers) ? incident.watchers : [];
    const routes = Array.isArray(incident.routes) ? incident.routes : [];
    const events = Array.isArray(incident.events) ? incident.events : [];
    detailNode.innerHTML = `
      <div class="incident-kpi-grid mb-3">
        <div class="incident-kpi-card"><div class="incident-kpi-label">Номер</div><div class="incident-kpi-value">${escapeHtml(incident.incident_key || '—')}</div></div>
        <div class="incident-kpi-card incident-kpi-card--status">
          <label class="incident-kpi-label" for="incidentQuickStatus">Статус</label>
          <div class="incident-kpi-value incident-kpi-value--control">
            <select class="form-select form-select-sm incident-status-quick-select" id="incidentQuickStatus" aria-label="Статус incident ${escapeHtml(incident.incident_key || '')}">
              ${renderIncidentStatusOptions(incident.status)}
            </select>
          </div>
        </div>
        <div class="incident-kpi-card"><div class="incident-kpi-label">Приоритет</div><div class="incident-kpi-value">${escapeHtml(incident.severity || '—')}</div></div>
        <div class="incident-kpi-card"><div class="incident-kpi-label">Ошибки доставки</div><div class="incident-kpi-value">${escapeHtml(incident.failed_route_count || 0)}</div></div>
      </div>
      <div class="incident-detail-columns">
        <div class="d-grid gap-3">
          <section class="card">
            <div class="card-header"><strong>Основное</strong></div>
            <div class="card-body">
              <div class="incident-create-grid mb-3">
                <input type="text"
					   class="form-control form-control-sm"
					   id="incidentDetailTitle"
					   value="${escapeHtml(incident.title || '')}"
					   placeholder="Название"
					   aria-label="Название incident">

				<input type="text"
					   class="form-control form-control-sm"
					   id="incidentDetailSummary"
					   value="${escapeHtml(incident.summary || '')}"
					   placeholder="Краткое описание"
					   aria-label="Краткое описание incident">

				<select class="form-select form-select-sm"
						id="incidentDetailSeverity"
						aria-label="Severity incident">
                  ${['critical', 'high', 'medium', 'low'].map((value) => `<option value="${value}" ${value === incident.severity ? 'selected' : ''}>${value}</option>`).join('')}
                </select>
                <input type="text"
					   class="form-control form-control-sm"
					   id="incidentDetailOwner"
					   value="${escapeHtml(incident.owner || '')}"
					   placeholder="Ответственный"
					   aria-label="Ответственный за incident">

				<input type="text"
					   class="form-control form-control-sm"
					   id="incidentDetailSource"
					   value="${escapeHtml(incident.source || '')}"
					   placeholder="Источник"
					   aria-label="Источник incident">
              </div>
              <textarea class="form-control form-control-sm"
					  id="incidentDetailDescription"
					  rows="4"
					  placeholder="Описание и ход решения"
					  aria-label="Полное описание incident">${escapeHtml(incident.description || '')}</textarea>
            </div>
          </section>
          <section class="card">
            <div class="card-header"><strong>Связи</strong></div>
            <div class="card-body incident-meta-list">
              ${relations.length ? relations.map((relation) => renderKeyValue(relation.relation_type, `${relation.relation_label || relation.relation_key}${relation.primary ? ' · primary' : ''}`)).join('') : '<div class="text-muted">Связи ещё не добавлены.</div>'}
            </div>
          </section>
          <section class="card">
            <div class="card-header d-flex justify-content-between align-items-center gap-2">
              <strong>Хронология</strong>
              <button type="button" class="btn btn-sm btn-outline-secondary" id="incidentAddEventButton">Добавить note</button>
            </div>
            <div class="card-body">
              <div class="incident-create-grid mb-3">
                <input type="text"
					   class="form-control form-control-sm"
					   id="incidentEventType"
					   value="comment"
					   placeholder="event_type"
					   aria-label="Тип события incident">

				<input type="text"
					   class="form-control form-control-sm"
					   id="incidentEventText"
					   placeholder="Короткий update / runbook note"
					   aria-label="Текст события или runbook note">
              </div>
              <div class="incident-event-list">
                ${events.length ? events.slice().reverse().map((event) => `
                  <div class="incident-surface-item">
                    <div class="d-flex flex-wrap justify-content-between gap-2 mb-1">
                      <strong>${escapeHtml(event.event_type || 'event')}</strong>
                      <span class="small text-muted">${escapeHtml(formatDate(event.created_at))}</span>
                    </div>
                    <div class="incident-event-text mb-1">${escapeHtml(event.event_text || '')}</div>
                    <div class="small text-muted">actor: ${escapeHtml(event.actor || 'system')}</div>
                  </div>
                `).join('') : '<div class="text-muted">История incident пока пуста.</div>'}
              </div>
            </div>
          </section>
        </div>
        <div class="d-grid gap-3">
          <section class="card">
            <div class="card-header"><strong>Metadata</strong></div>
            <div class="card-body incident-meta-list">
              ${renderKeyValue('Signal type', incident.signal_type || '—')}
              ${renderKeyValue('Signal key', incident.signal_key || '—')}
              ${renderKeyValue('Создал', incident.created_by || '—')}
              ${renderKeyValue('Обновлён', formatDate(incident.updated_at))}
            </div>
          </section>
          <section class="card">
            <div class="card-header"><strong>Наблюдатели</strong></div>
            <div class="card-body">
              <div class="input-group input-group-sm mb-3">
                <input type="text"
					   class="form-control"
					   id="incidentWatcherInput"
					   placeholder="username"
					   aria-label="Username нового наблюдателя">
                <button type="button" class="btn btn-outline-secondary" id="incidentAddWatcherButton">Добавить watcher</button>
              </div>
              <div class="incident-watcher-list">
                ${watchers.length ? watchers.map((watcher) => `
                  <div class="incident-surface-item d-flex justify-content-between align-items-center gap-2">
                    <div>
                      <div class="fw-semibold">${escapeHtml(watcher.watcher_identity || '')}</div>
                      <div class="small text-muted">${escapeHtml(formatDate(watcher.added_at))}</div>
                    </div>
                    <button type="button" class="btn btn-sm btn-outline-danger" data-incident-remove-watcher="${escapeHtml(watcher.watcher_identity || '')}">Удалить</button>
                  </div>
                `).join('') : '<div class="text-muted">Watcher-ов пока нет.</div>'}
              </div>
            </div>
          </section>
          <section class="card">
            <div class="card-header"><strong>Маршруты доставки</strong></div>
            <div class="card-body">
              <div class="incident-route-grid mb-3">
                <select class="form-select form-select-sm"
						id="incidentRouteType"
						aria-label="Тип маршрута incident">
                  <option value="webhook">webhook</option>
                  <option value="user">user</option>
                  <option value="users">users</option>
                  <option value="department">department</option>
                  <option value="all_operators">all_operators</option>
                </select>
                <input type="text"
					   class="form-control form-control-sm"
					   id="incidentRouteTarget"
					   placeholder="target / usernames / URL / department"
					   aria-label="Получатель или target маршрута incident">

				<input type="text"
					   class="form-control form-control-sm"
					   id="incidentRouteNote"
					   placeholder="note"
					   aria-label="Комментарий к маршруту incident">
                <button type="button" class="btn btn-sm btn-outline-secondary" id="incidentAddRouteButton">Добавить route</button>
              </div>
              <div class="incident-route-list">
                ${routes.length ? routes.map((route) => `
                  <div class="incident-surface-item">
                    <div class="d-flex flex-wrap justify-content-between gap-2 mb-2">
                      <div>
                        <div class="fw-semibold">${escapeHtml(route.route_type || '')}</div>
                        <div class="small text-muted">${escapeHtml(route.route_target || '')}</div>
                      </div>
                      <div class="d-flex gap-2 align-items-center">
                        <span class="badge ${badgeClass(route.route_status || route.delivery?.status)}">${escapeHtml(route.route_status || route.delivery?.status || 'pending')}</span>
                        <button type="button" class="btn btn-sm btn-outline-secondary" data-incident-redeliver-route="${escapeHtml(route.id)}">Redeliver</button>
                      </div>
                    </div>
                    <div class="small text-muted mb-2">${escapeHtml(route.note || '')}</div>
                    <div class="small text-muted">delivery: ${escapeHtml(route.delivery?.status || '—')} · attempts=${escapeHtml(route.delivery?.attempt_count ?? 0)} · updated=${escapeHtml(formatDate(route.delivery?.updated_at))}</div>
                    ${route.delivery?.last_error ? `<pre class="incident-code mt-2 text-danger">${escapeHtml(route.delivery.last_error)}</pre>` : ''}
                  </div>
                `).join('') : '<div class="text-muted">Маршруты ещё не заданы.</div>'}
              </div>
            </div>
          </section>
        </div>
      </div>
    `;
	state.incidentDetailDirty = false;
  }

  async function saveIncident() {
    const incident = state.selectedIncident;
    if (!incident) return;
    const payload = {
      title: document.getElementById('incidentDetailTitle')?.value?.trim() || '',
      summary: document.getElementById('incidentDetailSummary')?.value?.trim() || '',
      severity: document.getElementById('incidentDetailSeverity')?.value || '',
      owner: document.getElementById('incidentDetailOwner')?.value?.trim() || '',
      source: document.getElementById('incidentDetailSource')?.value?.trim() || '',
      description: document.getElementById('incidentDetailDescription')?.value?.trim() || ''
    };
    await requestJson(
		`/api/incidents/${encodeURIComponent(incident.id)}`,
		{
			method: 'PATCH',
			headers: {
				'Content-Type':
					'application/json'
			},
			body: JSON.stringify(payload)
		}
	);

	state.incidentDetailDirty = false;

	clearStoredIncidentDetailDraft(
		incident.id
	);

	showSuccess('Incident обновлён');

	await loadIncidents();
  }

  async function updateIncidentStatus(nextStatus) {
    const incident = state.selectedIncident;
    if (!incident) {
      return;
    }

    const normalizedStatus = String(nextStatus || '').trim();
    if (!INCIDENT_STATUS_OPTIONS.some((item) => item.value === normalizedStatus)) {
      throw new Error('Неизвестный статус incident.');
    }
    if (normalizedStatus === incident.status) {
      return;
    }

    await requestJson(
      `/api/incidents/${encodeURIComponent(incident.id)}`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: normalizedStatus })
      }
    );

    incident.status = normalizedStatus;
    const listItem = state.incidents.find((item) => Number(item.id) === Number(incident.id));
    if (listItem) {
      listItem.status = normalizedStatus;
    }
    renderIncidentList();
    showSuccess(`Статус incident: ${incidentStatusLabel(normalizedStatus)}`);
    await refreshIncidentIfStillSelected(incident.id);
  }

  async function createIncident() {
    const title = createNodes.title?.value.trim();
    const relationKey = createNodes.relationKey?.value.trim();
    if (!title || !relationKey) {
      throw new Error('Для создания incident заполните title и relation key.');
    }
    const payload = {
      title,
      summary: createNodes.summary?.value.trim() || '',
      severity: createNodes.severity?.value || 'high',
      status: createNodes.status?.value || 'open',
      owner: createNodes.owner?.value.trim() || '',
      source: createNodes.source?.value.trim() || '',
      relations: [{
        relation_type: createNodes.relationType?.value || 'ticket',
        relation_key: relationKey,
        primary: true
      }],
      watchers: splitCommaList(createNodes.watchers?.value || '')
    };
    const response = await requestJson('/api/incidents', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    showSuccess('Incident создан');

	// До переключения на только что созданный
	// incident сохраняем несохранённые поля
	// текущего.
	rememberCurrentIncidentDetailDraft();

	state.incidentDetailDirty = false;

	state.selectedIncidentId =
		response?.incident?.id || null;

	await loadIncidents();
  }

  async function addIncidentEvent() {
    const incident = state.selectedIncident;
    if (!incident) return;
    const eventType = document.getElementById('incidentEventType')?.value?.trim() || 'comment';
    const eventText = document.getElementById('incidentEventText')?.value?.trim() || '';
    if (!eventText) {
      throw new Error('Введите текст runbook note.');
    }
    await requestJson(`/api/incidents/${encodeURIComponent(incident.id)}/events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ event_type: eventType, event_text: eventText })
    });
    showSuccess('Событие добавлено');

	await loadIncidents();
  }

  async function addWatcher() {
    const incident = state.selectedIncident;
    const watcher = document.getElementById('incidentWatcherInput')?.value?.trim() || '';
    if (!incident || !watcher) return;
    await requestJson(`/api/incidents/${encodeURIComponent(incident.id)}/watchers`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ watcher_identity: watcher })
    });
    showSuccess('Watcher добавлен');

	await refreshIncidentIfStillSelected(
		incident.id
	);
  }

  async function removeWatcher(watcher) {
    const incident = state.selectedIncident;
    if (!incident || !watcher) return;
    await requestJson(`/api/incidents/${encodeURIComponent(incident.id)}/watchers/${encodeURIComponent(watcher)}`, {
      method: 'DELETE'
    });
    showSuccess('Watcher удалён');

	await refreshIncidentIfStillSelected(
		incident.id
	);
  }

  async function addRoute() {
    const incident = state.selectedIncident;
    if (!incident) return;
    const routeType = document.getElementById('incidentRouteType')?.value || '';
    const routeTarget = document.getElementById('incidentRouteTarget')?.value?.trim() || '';
    const note = document.getElementById('incidentRouteNote')?.value?.trim() || '';
    if (!routeType || (!routeTarget && routeType !== 'all_operators')) {
      throw new Error('Укажите route type и target.');
    }
    await requestJson(`/api/incidents/${encodeURIComponent(incident.id)}/routes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ route_type: routeType, route_target: routeTarget, note })
    });
    showSuccess('Route добавлен');

	await refreshIncidentIfStillSelected(
		incident.id
	);
  }

  async function redeliverRoute(routeId) {
    const incident = state.selectedIncident;
    if (!incident || !routeId) return;
    await requestJson(`/api/incidents/${encodeURIComponent(incident.id)}/routes/${encodeURIComponent(routeId)}/redeliver`, {
      method: 'POST'
    });
    showSuccess(
		'Повторная доставка маршрута поставлена в очередь'
	);

	await refreshIncidentIfStillSelected(
		incident.id
	);
  }

  async function redeliverFailedRoutes() {
    const incident = state.selectedIncident;
    if (!incident) return;
    await requestJson(`/api/incidents/${encodeURIComponent(incident.id)}/routes/redeliver-failed?limit=25`, {
      method: 'POST'
    });
    showSuccess(
		'Маршруты с ошибкой поставлены на повторную доставку'
	);

	await refreshIncidentIfStillSelected(
		incident.id
	);
  }

  async function loadTransportOverview() {
    if (!transportNodes.inboundList) {
        return;
    }

    const requestSerial =
        ++state.transportRequestSerial;

    transportNodes.pane?.setAttribute(
        'aria-busy',
        'true'
    );

    if (transportNodes.status) {
        transportNodes.status.textContent =
            'Обновление данных integration recovery...';
    }

    try {
        const payload =
            await requestJson(
                '/api/analytics/integration-transport'
            );

        if (
            requestSerial !==
            state.transportRequestSerial
        ) {
            return;
        }

        state.transportOverview =
            payload;

        renderTransportOverview();

        if (transportNodes.status) {
            transportNodes.status.textContent =
                'Данные integration recovery обновлены.';
        }
    } catch (error) {
        if (
            requestSerial !==
            state.transportRequestSerial
        ) {
            return;
        }

        if (transportNodes.status) {
            transportNodes.status.textContent =
                'Не удалось обновить данные integration recovery.';
        }

        throw error;
    } finally {
        if (
            requestSerial ===
                state.transportRequestSerial
        ) {
            transportNodes.pane?.setAttribute(
                'aria-busy',
                'false'
            );
        }
    }
}

  function renderTransportOverview() {
    const focusedTransportControl =
        document.activeElement instanceof Element
            ? document.activeElement.closest(
                '[data-transport-focus-key]'
            )
            : null;

    const focusedTransportKey =
        focusedTransportControl?.getAttribute(
            'data-transport-focus-key'
        ) || '';

    const payload = state.transportOverview || {};
    const inbound = payload.inbound || {};
    const outbound = payload.outbound || {};
    const healthSnapshot = payload.health_snapshot || {};
    transportNodes.inboundFailed.textContent = String(inbound.failed ?? 0);
    transportNodes.inboundStale.textContent = String(inbound.stale_processing ?? 0);
    transportNodes.outboundFailed.textContent = String(outbound.failed ?? 0);
    transportNodes.outboundBacklog.textContent = String((outbound.queued ?? 0) + (outbound.processing ?? 0));
    transportNodes.checkpointStale.textContent = String(healthSnapshot.stale_checkpoint_count ?? 0);
    transportNodes.recentOps.textContent = String(healthSnapshot.recent_manual_operations ?? 0);
    transportNodes.incidentCount.textContent = String(Array.isArray(payload.transport_incidents) ? payload.transport_incidents.length : 0);

    renderTransportItems(transportNodes.inboundList, payload.recent_failed_inbound || [], 'inbound');
    renderTransportItems(transportNodes.outboundList, payload.recent_failed_outbound || [], 'outbound');
    renderCheckpointItems(payload.runtime_checkpoints || []);
    renderTransportIncidentItems(payload.transport_incidents || []);
    renderAlertItems(payload.alerts || []);
    renderOperationItems(payload.recent_operations || []);
	if (focusedTransportKey) {
    window.requestAnimationFrame(() => {
        const nextControl = Array.from(
            document.querySelectorAll(
                '[data-transport-focus-key]'
            )
        ).find(
            (item) =>
                item.getAttribute(
                    'data-transport-focus-key'
                ) === focusedTransportKey
        );

        if (nextControl instanceof HTMLElement) {
            nextControl.focus({
                preventScroll: true
            });
        }
    });
}
  }

  function renderTransportItems(node, items, mode) {
    if (!node) return;
    if (!Array.isArray(items) || !items.length) {
      node.innerHTML = `<div class="text-muted">Нет ${mode === 'inbound' ? 'replayable inbound' : 'requeueable outbound'} событий.</div>`;
      return;
    }
    node.innerHTML = items.map((item) => `
      <div class="transport-item-card">
        <div class="d-flex flex-wrap justify-content-between gap-2 mb-2">
          <div>
            <div class="fw-semibold">${escapeHtml(item.event_id || '—')}</div>
            <div class="small text-muted">${escapeHtml(item.event_kind || '')} · ticket=${escapeHtml(item.ticket_id || '—')}</div>
          </div>
          <div class="d-flex gap-2">
            <span class="badge ${badgeClass(item.status)}">${escapeHtml(item.status || '—')}</span>
            <button type="button"
					class="btn btn-sm btn-outline-secondary"
					data-transport-view="${mode}"
					data-event-id="${escapeHtml(item.event_id || '')}"
					data-transport-focus-key="${escapeHtml(
						'view:' +
						mode +
						':' +
						(item.event_id || '')
					)}">
				Payload
			</button>
            <button type="button"
					class="btn btn-sm btn-outline-primary"
					data-transport-action="${mode === 'inbound' ? 'replay' : 'requeue'}"
					data-event-id="${escapeHtml(item.event_id || '')}"
					data-transport-focus-key="${escapeHtml(
						'action:' +
						(mode === 'inbound'
							? 'replay'
							: 'requeue') +
						':' +
						(item.event_id || '')
					)}">
				${mode === 'inbound' ? 'Replay' : 'Requeue'}
			</button>
          </div>
        </div>
        <div class="small text-muted">attempts=${escapeHtml(item.attempt_count ?? 0)} · updated=${escapeHtml(formatDate(item.updated_at))}</div>
        ${item.last_error ? `<pre class="incident-code mt-2 text-danger">${escapeHtml(item.last_error)}</pre>` : ''}
      </div>
    `).join('');
  }

  function renderCheckpointItems(items) {
    if (!transportNodes.checkpointList) return;
    if (!Array.isArray(items) || !items.length) {
      transportNodes.checkpointList.innerHTML = '<div class="text-muted">Runtime checkpoints отсутствуют.</div>';
      return;
    }
    transportNodes.checkpointList.innerHTML = items.map((item) => `
      <div class="transport-item-card">
        <div class="d-flex flex-wrap justify-content-between gap-2 mb-2">
          <div>
            <div class="fw-semibold">${escapeHtml(item.worker_label || item.worker_key || '—')}</div>
            <div class="small text-muted">${escapeHtml(item.worker_key || '—')}</div>
          </div>
          <span class="badge ${badgeClass(item.health_status)}">${escapeHtml(item.health_status || 'unknown')}</span>
        </div>
        <div class="input-group input-group-sm mb-2">
          <input type="text"
			   class="form-control incident-code"
			   data-transport-checkpoint-input="${escapeHtml(item.worker_key || '')}"
			   data-transport-focus-key="${escapeHtml(
				   'checkpoint-input:' +
				   (item.worker_key || '')
			   )}"
			   value="${escapeHtml(item.cursor_text || '')}"
			   aria-label="Checkpoint cursor: ${escapeHtml(
				   item.worker_label ||
				   item.worker_key ||
				   'worker'
			   )}">
          <button type="button"
					class="btn btn-outline-secondary"
					data-transport-save-checkpoint="${escapeHtml(item.worker_key || '')}"
					data-transport-focus-key="${escapeHtml(
						'checkpoint-save:' +
						(item.worker_key || '')
					)}">
				Save
			</button>
          <button type="button"
					class="btn btn-outline-dark"
					data-transport-view="worker"
					data-worker-key="${escapeHtml(item.worker_key || '')}"
					data-transport-focus-key="${escapeHtml(
						'worker-view:' +
						(item.worker_key || '')
					)}">
				Inspect
			</button>
        </div>
        <div class="small text-muted">
          updated=${escapeHtml(formatDate(item.updated_at))} · age=${escapeHtml(item.age_minutes ?? '—')}m · stale-threshold=${escapeHtml(item.stale_threshold_minutes ?? '—')}m
        </div>
        <div class="small text-muted">
          source=${escapeHtml(item.source_table || '—')} · lag=${escapeHtml(item.cursor_lag ?? '—')} · max=${escapeHtml(item.source_max_cursor ?? '—')}
        </div>
      </div>
    `).join('');
  }

  function renderAlertItems(items) {
    if (!transportNodes.alertList) return;
    if (!Array.isArray(items) || !items.length) {
      transportNodes.alertList.innerHTML = '<div class="text-muted">Transport alerts отсутствуют.</div>';
      return;
    }
    transportNodes.alertList.innerHTML = items.map((item) => `
      <div class="transport-item-card">
        <div class="d-flex flex-wrap justify-content-between gap-2 mb-2">
          <div class="fw-semibold">${escapeHtml(item.key || 'alert')}</div>
          <span class="badge ${badgeClass(item.severity)}">${escapeHtml(item.severity || 'info')}</span>
        </div>
        <div>${escapeHtml(item.message || '')}</div>
        <div class="small text-muted mt-2">value=${escapeHtml(item.value ?? 0)} · threshold=${escapeHtml(item.threshold ?? 0)}</div>
      </div>
    `).join('');
  }

  function renderOperationItems(items) {
    if (!transportNodes.operationList) return;
    if (!Array.isArray(items) || !items.length) {
      transportNodes.operationList.innerHTML = '<div class="text-muted">Recovery audit trail пока пуст.</div>';
      return;
    }
    transportNodes.operationList.innerHTML = items.map((item) => `
      <div class="transport-item-card">
        <div class="d-flex flex-wrap justify-content-between gap-2 mb-2">
          <div>
            <div class="fw-semibold">${escapeHtml(item.summary_text || item.action_type || 'operation')}</div>
            <div class="small text-muted">${escapeHtml(item.action_type || '—')} · actor=${escapeHtml(item.actor || 'system')}</div>
          </div>
          <span class="badge ${badgeClass(item.result_status)}">${escapeHtml(item.result_status || 'success')}</span>
        </div>
        <div class="small text-muted">target=${escapeHtml(item.target_type || '—')} / ${escapeHtml(item.target_id || '—')} · ticket=${escapeHtml(item.ticket_id || '—')}</div>
        <div class="small text-muted">created=${escapeHtml(formatDate(item.created_at))}</div>
        ${item.details_json ? `<pre class="incident-code mt-2">${escapeHtml(item.details_json)}</pre>` : ''}
      </div>
    `).join('');
  }

  function renderTransportIncidentItems(items) {
    if (!transportNodes.incidentList) return;
    if (!Array.isArray(items) || !items.length) {
      transportNodes.incidentList.innerHTML = '<div class="text-muted">Transport incidents не обнаружены.</div>';
      return;
    }
    transportNodes.incidentList.innerHTML = items.map((item) => `
      <div class="transport-item-card">
        <div class="d-flex flex-wrap justify-content-between gap-2 mb-1">
          <div>
            <div class="fw-semibold">${escapeHtml(item.incident_key || '—')}</div>
            <div>${escapeHtml(item.title || '')}</div>
          </div>
          <div class="d-flex gap-2">
            <span class="badge ${badgeClass(item.severity)}">${escapeHtml(item.severity || '—')}</span>
            <span class="badge ${badgeClass(item.status)}">${escapeHtml(item.status || '—')}</span>
          </div>
        </div>
        <div class="small text-muted">${escapeHtml(item.summary || '')}</div>
      </div>
    `).join('');
  }

  function renderTicketDebug(payload) {
    if (!transportNodes.ticketDebug) return;
    if (!payload || payload.success !== true) {
      transportNodes.ticketDebug.innerHTML = '<div class="text-muted">Ticket debug ещё не загружен.</div>';
      return;
    }
    const inboundEvents = Array.isArray(payload.inbound_events) ? payload.inbound_events : [];
    const outboundEvents = Array.isArray(payload.outbound_events) ? payload.outbound_events : [];
    const incidents = Array.isArray(payload.related_incidents) ? payload.related_incidents : [];
    const operations = Array.isArray(payload.recent_operations) ? payload.recent_operations : [];
    transportNodes.ticketDebug.innerHTML = `
      <div class="transport-item-card">
        <div class="fw-semibold mb-2">Ticket ${escapeHtml(payload.ticket_id || '—')}</div>
        <div class="small text-muted mb-3">
          inbound=${escapeHtml(inboundEvents.length)} · outbound=${escapeHtml(outboundEvents.length)} · incidents=${escapeHtml(incidents.length)} · recent ops=${escapeHtml(operations.length)}
        </div>
        <div class="transport-columns">
          <div class="transport-log-list">
            <div class="fw-semibold">Inbound events</div>
            ${inboundEvents.length ? inboundEvents.map((item) => `
              <div class="incident-surface-item">
                <div class="d-flex flex-wrap justify-content-between gap-2">
                  <div>${escapeHtml(item.event_id || '—')}</div>
                  <span class="badge ${badgeClass(item.status)}">${escapeHtml(item.status || '—')}</span>
                </div>
                <div class="small text-muted">${escapeHtml(item.event_kind || '—')} · updated=${escapeHtml(formatDate(item.updated_at))}</div>
                ${item.last_error ? `<pre class="incident-code mt-2 text-danger">${escapeHtml(item.last_error)}</pre>` : ''}
              </div>
            `).join('') : '<div class="text-muted">Inbound events не найдены.</div>'}
          </div>
          <div class="transport-log-list">
            <div class="fw-semibold">Outbound events</div>
            ${outboundEvents.length ? outboundEvents.map((item) => `
              <div class="incident-surface-item">
                <div class="d-flex flex-wrap justify-content-between gap-2">
                  <div>${escapeHtml(item.event_id || '—')}</div>
                  <span class="badge ${badgeClass(item.status)}">${escapeHtml(item.status || '—')}</span>
                </div>
                <div class="small text-muted">${escapeHtml(item.event_kind || '—')} · updated=${escapeHtml(formatDate(item.updated_at))}</div>
                ${item.last_error ? `<pre class="incident-code mt-2 text-danger">${escapeHtml(item.last_error)}</pre>` : ''}
              </div>
            `).join('') : '<div class="text-muted">Outbound events не найдены.</div>'}
          </div>
        </div>
        <div class="transport-columns mt-3">
          <div class="transport-log-list">
            <div class="fw-semibold">Related incidents</div>
            ${incidents.length ? incidents.map((item) => `
              <div class="incident-surface-item">
                <div class="d-flex flex-wrap justify-content-between gap-2">
                  <div>${escapeHtml(item.incident_key || '—')}</div>
                  <span class="badge ${badgeClass(item.severity)}">${escapeHtml(item.severity || '—')}</span>
                </div>
                <div>${escapeHtml(item.title || '')}</div>
                <div class="small text-muted">${escapeHtml(item.status || '—')} · updated=${escapeHtml(formatDate(item.updated_at))}</div>
              </div>
            `).join('') : '<div class="text-muted">Связанные incidents не найдены.</div>'}
          </div>
          <div class="transport-log-list">
            <div class="fw-semibold">Recent operations</div>
            ${operations.length ? operations.map((item) => `
              <div class="incident-surface-item">
                <div class="d-flex flex-wrap justify-content-between gap-2">
                  <div>${escapeHtml(item.summary_text || item.action_type || 'operation')}</div>
                  <span class="badge ${badgeClass(item.result_status)}">${escapeHtml(item.result_status || 'success')}</span>
                </div>
                <div class="small text-muted">${escapeHtml(item.actor || 'system')} · ${escapeHtml(formatDate(item.created_at))}</div>
              </div>
            `).join('') : '<div class="text-muted">Recovery actions по ticket ещё не зафиксированы.</div>'}
          </div>
        </div>
      </div>
    `;
  }

  async function inspectTicketTransport() {
    // Любая новая попытка Inspect инвалидирует
    // предыдущий Ticket Debug request,
    // даже если новое поле уже очищено.
    const requestSerial =
        ++state.ticketDebugRequestSerial;

    const ticketId =
        transportNodes.ticketId?.value?.trim();

    if (!ticketId) {
        transportNodes.ticketDebug?.setAttribute(
            'aria-busy',
            'false'
        );

        throw new Error(
            'Укажите ticket id для targeted transport debug.'
        );
    }

    if (transportNodes.ticketDebug) {
        transportNodes.ticketDebug.setAttribute(
            'aria-busy',
            'true'
        );
    }

    try {
        const payload =
            await requestJson(
                `/api/analytics/integration-transport/tickets/${encodeURIComponent(ticketId)}/debug`
            );

        if (
            requestSerial !==
            state.ticketDebugRequestSerial
        ) {
            return;
        }

        renderTicketDebug(payload);

        showSuccess(
            `Transport debug для ticket ${ticketId} загружен`
        );
    } catch (error) {
        if (
            requestSerial !==
            state.ticketDebugRequestSerial
        ) {
            return;
        }

        throw error;
    } finally {
        if (
            requestSerial ===
                state.ticketDebugRequestSerial &&
            transportNodes.ticketDebug
        ) {
            transportNodes.ticketDebug.setAttribute(
                'aria-busy',
                'false'
            );
        }
    }
}

  async function showTransportPayload(mode, eventId) {
    const requestSerial =
        ++state.payloadRequestSerial;

    const url = mode === 'inbound'
        ? `/api/analytics/integration-transport/inbound-events/${encodeURIComponent(eventId)}`
        : mode === 'worker'
            ? `/api/analytics/integration-transport/workers/${encodeURIComponent(eventId)}`
            : `/api/analytics/integration-transport/outbox-events/${encodeURIComponent(eventId)}`;

    try {
        const payload =
            await requestJson(url);

        if (
            requestSerial !==
            state.payloadRequestSerial
        ) {
            return;
        }

        payloadContentNode.textContent =
            JSON.stringify(
                mode === 'worker'
                    ? payload
                    : (payload?.item || {}),
                null,
                2
            );

        payloadModal?.show();
    } catch (error) {
        if (
            requestSerial !==
            state.payloadRequestSerial
        ) {
            return;
        }

        throw error;
    }
}

  async function invokeTransportAction(url, successMessage) {
    await requestJson(url, { method: 'POST' });
    showSuccess(successMessage);
    await loadTransportOverview();
    await loadIncidents();
  }

  listNode.addEventListener('click', (event) => {
    const row = event.target.closest('[data-incident-id]');
    if (!row) return;
    const incidentId = row.getAttribute('data-incident-id');
    if (incidentId) {
      void loadIncidentDetail(incidentId).catch((error) => showError(error.message));
    }
  });

	const markIncidentDetailDirty = (
    event
) => {
    if (
        isIncidentDetailField(
            event.target
        )
    ) {
        state.incidentDetailDirty = true;
    }
};

detailNode.addEventListener(
    'input',
    markIncidentDetailDirty
);

detailNode.addEventListener(
    'change',
    (event) => {
        markIncidentDetailDirty(event);

        const statusSelect =
            event.target instanceof Element
                ? event.target.closest('#incidentQuickStatus')
                : null;

        if (!(statusSelect instanceof HTMLSelectElement)) {
            return;
        }

        const previousStatus =
            state.selectedIncident?.status || '';

        statusSelect.disabled = true;

        void updateIncidentStatus(
            statusSelect.value
        ).catch(
            (error) => {
                if (statusSelect.isConnected) {
                    statusSelect.value = previousStatus;
                }
                showError(error.message);
            }
        ).finally(() => {
            if (statusSelect.isConnected) {
                statusSelect.disabled = false;
            }
        });
    }
);

  detailNode.addEventListener(
    'click',
    (event) => {
        const removeWatcherButton =
            event.target.closest(
                '[data-incident-remove-watcher]'
            );

        if (removeWatcherButton) {
            void runButtonAction(
                removeWatcherButton,
                () =>
                    removeWatcher(
                        removeWatcherButton.getAttribute(
                            'data-incident-remove-watcher'
                        )
                    )
            ).catch(
                (error) =>
                    showError(error.message)
            );

            return;
        }

        const redeliverButton =
            event.target.closest(
                '[data-incident-redeliver-route]'
            );

        if (redeliverButton) {
            void runButtonAction(
                redeliverButton,
                () =>
                    redeliverRoute(
                        redeliverButton.getAttribute(
                            'data-incident-redeliver-route'
                        )
                    )
            ).catch(
                (error) =>
                    showError(error.message)
            );
        }
    }
);

  transportNodes.inboundList?.addEventListener('click', (event) => {
    const viewButton = event.target.closest('[data-transport-view="inbound"]');
    if (viewButton) {
      void showTransportPayload('inbound', viewButton.getAttribute('data-event-id')).catch((error) => showError(error.message));
      return;
    }
	const replayButton =
		event.target.closest(
			'[data-transport-action="replay"]'
		);

	if (replayButton) {
		void runButtonAction(
			replayButton,
			() =>
				invokeTransportAction(
					`/api/analytics/integration-transport/inbound-events/${encodeURIComponent(
						replayButton.getAttribute(
							'data-event-id'
						) || ''
					)}/replay`,
					'Inbound event отправлен на повторную обработку'
				)
		).catch(
			(error) =>
				showError(error.message)
		);
	}  });

  transportNodes.outboundList?.addEventListener('click', (event) => {
    const viewButton = event.target.closest('[data-transport-view="outbound"]');
    if (viewButton) {
      void showTransportPayload('outbound', viewButton.getAttribute('data-event-id')).catch((error) => showError(error.message));
      return;
    }
    const requeueButton =
		event.target.closest(
			'[data-transport-action="requeue"]'
		);

	if (requeueButton) {
		void runButtonAction(
			requeueButton,
			() =>
				invokeTransportAction(
					`/api/analytics/integration-transport/outbox-events/${encodeURIComponent(
						requeueButton.getAttribute(
							'data-event-id'
						) || ''
					)}/requeue`,
					'Outbound event повторно поставлен в очередь'
				)
		).catch(
			(error) =>
				showError(error.message)
		);
	}
  });

  transportNodes.checkpointList?.addEventListener('click', (event) => {
    const inspectButton = event.target.closest('[data-transport-view="worker"]');
    if (inspectButton) {
      void showTransportPayload('worker', inspectButton.getAttribute('data-worker-key')).catch((error) => showError(error.message));
      return;
    }
    const saveButton = event.target.closest('[data-transport-save-checkpoint]');
    if (!saveButton) return;
    const workerKey = saveButton.getAttribute('data-transport-save-checkpoint') || '';
    const input = document.querySelector(`[data-transport-checkpoint-input="${CSS.escape(workerKey)}"]`);
    const cursorText = input ? input.value : '';
		void runButtonAction(
		saveButton,
		() =>
			invokeTransportAction(
				`/api/analytics/integration-transport/checkpoints/${encodeURIComponent(
					workerKey
				)}?cursor_text=${encodeURIComponent(
					cursorText || ''
				)}`,
				'Checkpoint обновлён'
			)
	).catch(
		(error) =>
			showError(error.message)
	);
  });

  document.getElementById('incidentWorkbenchApplyFilters')?.addEventListener('click', () => void loadIncidents().catch((error) => showError(error.message)));
  document.getElementById('incidentWorkbenchRefresh')?.addEventListener('click', () => void loadIncidents().catch((error) => showError(error.message)));
  document
		.getElementById(
			'incidentWorkbenchCreate'
		)
		?.addEventListener(
			'click',
			(event) => {
				void runButtonAction(
					event.currentTarget,
					createIncident
				).catch(
					(error) =>
						showError(error.message)
				);
			}
		);

	document
		.getElementById(
			'incidentWorkbenchSaveIncident'
		)
		?.addEventListener(
			'click',
			(event) => {
				void runButtonAction(
					event.currentTarget,
					saveIncident
				).catch(
					(error) =>
						showError(error.message)
				);
			}
		);

	document
		.getElementById(
			'incidentWorkbenchRedeliverFailedRoutes'
		)
		?.addEventListener(
			'click',
			(event) => {
				void runButtonAction(
					event.currentTarget,
					redeliverFailedRoutes
				).catch(
					(error) =>
						showError(error.message)
				);
			}
		);
  detailNode.addEventListener(
    'click',
    (event) => {
        const button =
            event.target.closest('button');

        if (!button) {
            return;
        }

        if (
            button.id ===
            'incidentAddEventButton'
        ) {
            void runButtonAction(
                button,
                addIncidentEvent
            ).catch(
                (error) =>
                    showError(error.message)
            );

            return;
        }

        if (
            button.id ===
            'incidentAddWatcherButton'
        ) {
            void runButtonAction(
                button,
                addWatcher
            ).catch(
                (error) =>
                    showError(error.message)
            );

            return;
        }

        if (
            button.id ===
            'incidentAddRouteButton'
        ) {
            void runButtonAction(
                button,
                addRoute
            ).catch(
                (error) =>
                    showError(error.message)
            );
        }
    }
);

  document.getElementById('transportWorkbenchRefresh')?.addEventListener('click', () => void loadTransportOverview().catch((error) => showError(error.message)));
  document
		.getElementById(
			'transportWorkbenchReplayFailed'
		)
		?.addEventListener(
			'click',
			(event) => {
				void runButtonAction(
					event.currentTarget,
					() =>
						invokeTransportAction(
							'/api/analytics/integration-transport/inbound-events/replay-failed?limit=25',
							'Повторная обработка failed inbound batch запущена'
						)
				).catch(
					(error) =>
						showError(error.message)
				);
			}
		);

	document
		.getElementById(
			'transportWorkbenchRequeueFailed'
		)
		?.addEventListener(
			'click',
			(event) => {
				void runButtonAction(
					event.currentTarget,
					() =>
						invokeTransportAction(
							'/api/analytics/integration-transport/outbox-events/requeue-failed?limit=25',
							'Повторная постановка failed outbound batch в очередь запущена'
						)
				).catch(
					(error) =>
						showError(error.message)
				);
			}
		);
  document.getElementById('transportWorkbenchReplayTicket')?.addEventListener('click', () => {
    const ticketId = transportNodes.ticketId?.value?.trim();
    if (!ticketId) {
      showError('Укажите ticket id для targeted inbound replay.');
      return;
    }
    void runButtonAction(
		event.currentTarget,
		() =>
			invokeTransportAction(
				`/api/analytics/integration-transport/tickets/${encodeURIComponent(ticketId)}/replay-inbound?limit=25`,
				'Запрошен inbound replay для ticket'
			)
	).catch(
		(error) =>
			showError(error.message)
	);
  });
  document.getElementById('transportWorkbenchRequeueTicket')?.addEventListener('click', () => {
    const ticketId = transportNodes.ticketId?.value?.trim();
    if (!ticketId) {
      showError('Укажите ticket id для targeted outbound requeue.');
      return;
    }
    void runButtonAction(
		event.currentTarget,
		() =>
			invokeTransportAction(
				`/api/analytics/integration-transport/tickets/${encodeURIComponent(ticketId)}/requeue-outbound?limit=25`,
				'Запрошен outbound requeue для ticket'
			)
	).catch(
		(error) =>
			showError(error.message)
	);
  });
  document.getElementById('transportWorkbenchInspectTicket')?.addEventListener('click', () => void inspectTicketTransport().catch((error) => showError(error.message)));

	payloadModalElement?.addEventListener(
    'hidden.bs.modal',
    () => {
        // Закрытие modal означает, что ожидаемый
        // payload пользователю больше не нужен.
        state.payloadRequestSerial += 1;
    }
);

document
    .getElementById('incidentWorkbenchTransportTab')
    ?.addEventListener(
        'hide.bs.tab',
        () => {
            // Не позволяем запросам из уже покинутой
            // вкладки менять текущий UI.
            state.payloadRequestSerial += 1;
            state.ticketDebugRequestSerial += 1;

            transportNodes.ticketDebug?.setAttribute(
                'aria-busy',
                'false'
            );
        }
    );
	
  void loadIncidents().catch((error) => showError(error.message));
  if (transportNodes.inboundList) {
    void loadTransportOverview().catch((error) => showError(error.message));
  }
})();
