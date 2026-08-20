(function () {
  if (window.DialogsMyDialogsRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements =
        options.elements || {};

    const pinnedSection =
        elements.panel?.querySelector(
            '[data-my-dialogs-pinned-section]'
        ) || null;

    const pinnedList =
        elements.panel?.querySelector(
            '[data-my-dialogs-pinned-list]'
        ) || null;

    const operatorIdentity =
        String(
            document.body?.dataset
                ?.operatorIdentity ||
            'anonymous'
        ).trim() || 'anonymous';

    const pinnedStorageKey =
        `iguana:dialogs:my-dialog-pins:${operatorIdentity}`;

    function getPinnedDialogIds() {
        try {
            const parsed =
                JSON.parse(
                    localStorage.getItem(
                        pinnedStorageKey
                    ) || '[]'
                );

            if (!Array.isArray(parsed)) {
                return [];
            }

            return Array.from(
                new Set(
                    parsed
                        .map(
                            (value) =>
                                String(
                                    value || ''
                                ).trim()
                        )
                        .filter(Boolean)
                )
            );
        } catch (error) {
            return [];
        }
    }

    function setPinnedDialogIds(ids) {
        try {
            const normalized =
                Array.from(
                    new Set(
                        (
                            Array.isArray(ids)
                                ? ids
                                : []
                        )
                            .map(
                                (value) =>
                                    String(
                                        value || ''
                                    ).trim()
                            )
                            .filter(Boolean)
                    )
                );

            localStorage.setItem(
                pinnedStorageKey,
                JSON.stringify(
                    normalized
                )
            );
        } catch (error) {
            // localStorage недоступен —
            // список продолжает работать
            // без persistence.
        }
    }

    function togglePinnedDialog(
        ticketId
    ) {
        const normalizedTicketId =
            String(
                ticketId || ''
            ).trim();

        if (!normalizedTicketId) {
            return;
        }

        const pinnedIds =
            getPinnedDialogIds();

        const existingIndex =
            pinnedIds.indexOf(
                normalizedTicketId
            );

        if (existingIndex >= 0) {
            pinnedIds.splice(
                existingIndex,
                1
            );
        } else {
            pinnedIds.push(
                normalizedTicketId
            );
        }

        setPinnedDialogIds(
            pinnedIds
        );

        renderMyDialogsPanel();
    }

    function escapeHtml(value) {
      return typeof options.escapeHtml === 'function'
        ? options.escapeHtml(value)
        : String(value ?? '');
    }

    function getMyDialogsState() {
      const state = options.getMyDialogsState?.();
      return state && typeof state === 'object'
        ? state
        : { new: [], unanswered: [], inWork: [] };
    }

    function setMyDialogsState(state) {
      options.setMyDialogsState?.(state && typeof state === 'object'
        ? state
        : { new: [], unanswered: [], inWork: [] });
    }

    function normalizeMyDialogsCollection(items) {
      return Array.isArray(items)
        ? items
          .filter((item) => item && typeof item === 'object')
          .map(normalizeMyDialogItem)
        : [];
    }

    function normalizeMyDialogItem(item) {
      const source = item && typeof item === 'object' ? item : {};
      const location = String(
        source.location
        || [source.city, source.locationName || source.location_name].filter(Boolean).join(', ')
        || ''
      ).trim();
      return {
        ...source,
        ticketId: String(source.ticketId || source.ticket_id || '').trim(),
        requestNumber: String(source.requestNumber || source.request_number || source.displayTicketNumber || '').trim(),
        clientName: String(source.clientName || source.client_name || source.displayClientName || source.username || '').trim(),
        location,
        channelName: String(source.channelName || source.channel_name || source.channelLabel || source.channel || '').trim(),
        problem: String(source.problem || source.problemSafe || '').trim(),
        statusKey: String(source.statusKey || source.status_key || '').trim(),
        unreadCount: Number(source.unreadCount ?? source.unread_count ?? 0) || 0,
        rawResponsible: resolveResponsibleRawFromItem(source),
        responsible: String(source.responsible || '').trim(),
        lastMessageSender: String(source.lastMessageSender || source.last_message_sender || '').trim(),
        lastMessageTimestamp: String(source.lastMessageTimestamp || source.last_message_timestamp || '').trim(),
        userId: String(source.userId || source.user_id || '').trim(),
      };
    }

    function resolveResponsibleRawFromItem(item) {
      return String(
        item?.rawResponsible
        || item?.raw_responsible
        || item?.responsibleRaw
        || item?.responsible
        || ''
      ).trim();
    }

    function resolveRowResponsibleRaw(row) {
      return String(row?.dataset?.responsibleRaw || row?.dataset?.responsible || '').trim();
    }

    function normalizeMyDialogsState(payload) {
      const source = payload && typeof payload === 'object' ? payload : {};
      setMyDialogsState({
        new: normalizeMyDialogsCollection(source.new || source.new_unassigned || source.newUnassigned),
        unanswered: normalizeMyDialogsCollection(source.unanswered),
        inWork: normalizeMyDialogsCollection(source.in_work || source.inWork),
      });
    }

    function isMyDialogItemUnanswered(dialog) {
      const unreadCount = Number(dialog?.unreadCount ?? dialog?.unread_count ?? 0) || 0;
      return unreadCount > 0;
    }

    function isMyDialogItemClosed(dialog) {
      const statusKey = String(dialog?.statusKey || dialog?.status_key || '').trim().toLowerCase();
      return statusKey === 'closed' || statusKey === 'auto_closed';
    }

    function isMyDialogItemNewUnassigned(dialog) {
      const statusKey = String(dialog?.statusKey || dialog?.status_key || '').trim().toLowerCase();
      const rawResponsible = resolveResponsibleRawFromItem(dialog);
      return !rawResponsible && (statusKey === 'new' || statusKey === 'auto_processing');
    }

    function buildMyDialogStateFromRow(row) {
      if (!row) return null;
      const responsibleRaw = resolveRowResponsibleRaw(row);
      if (!options.isOwnedByCurrentOperator?.(responsibleRaw) || options.isResolvedRow?.(row) === true) {
        return null;
      }
      return {
        ticketId: String(row.dataset.ticketId || '').trim(),
        requestNumber: String(row.dataset.requestNumber || '').trim(),
        clientName: String(row.dataset.client || '').trim(),
        location: String(row.dataset.location || '').trim(),
        channelName: String(row.dataset.channel || '').trim(),
        problem: String(row.dataset.problem || '').trim(),
        statusKey: String(row.dataset.statusKey || '').trim(),
        unreadCount: Number(row.dataset.unread) || 0,
        rawResponsible: responsibleRaw,
        responsible: String(row.dataset.responsible || '').trim(),
        lastMessageSender: String(row.dataset.lastMessageSender || '').trim(),
        lastMessageTimestamp: String(row.dataset.lastMessageTimestamp || '').trim(),
        userId: String(row.dataset.userId || '').trim(),
      };
    }

    function syncMyDialogsStateFromTable() {
      const nextState = {
        new: [],
        unanswered: [],
        inWork: [],
      };
      (options.rowsList?.() || []).forEach((row) => {
        const dialog = buildMyDialogStateFromRow(row);
        const statusKey = String(row?.dataset?.statusKey || '').trim().toLowerCase();
        const rawResponsible = resolveRowResponsibleRaw(row);
        const isUnassigned = !String(row?.dataset?.responsible || '').trim() || !rawResponsible;
        if (dialog && dialog.ticketId && !isMyDialogItemClosed(dialog)) {
          if (isMyDialogItemUnanswered(dialog)) {
            nextState.unanswered.push(dialog);
          } else {
            nextState.inWork.push(dialog);
          }
          return;
        }
        if (!row || !isUnassigned) {
          return;
        }
        if (!isMyDialogItemNewUnassigned({
          statusKey,
          rawResponsible,
        })) {
          return;
        }
        nextState.new.push({
          ticketId: String(row.dataset.ticketId || '').trim(),
          requestNumber: String(row.dataset.requestNumber || '').trim(),
          clientName: String(row.dataset.client || '').trim(),
          location: String(row.dataset.location || '').trim(),
          channelName: String(row.dataset.channel || '').trim(),
          problem: String(row.dataset.problem || '').trim(),
          statusKey,
          unreadCount: Number(row.dataset.unread) || 0,
          rawResponsible: rawResponsible,
          responsible: String(row.dataset.responsible || '').trim(),
          lastMessageSender: String(row.dataset.lastMessageSender || '').trim(),
          lastMessageTimestamp: String(row.dataset.lastMessageTimestamp || '').trim(),
          userId: String(row.dataset.userId || '').trim(),
        });
      });
      setMyDialogsState(nextState);
    }

    function formatMyDialogLastActivity(dialog) {
      const sender = String(dialog?.lastMessageSender || '').trim();
      const timestamp = options.formatTimestamp?.(dialog?.lastMessageTimestamp || '', { includeTime: true, fallback: '' }) || '';
      if (sender && timestamp && timestamp !== '—') {
        return `${sender} · ${timestamp}`;
      }
      if (timestamp && timestamp !== '—') {
        return timestamp;
      }
      if (sender) {
        return sender;
      }
      return 'Активность пока не зафиксирована';
    }

    function renderMyDialogItem(
		dialog,
		{
			pinned = false,
		} = {}
	) {
		const ticketId =
			String(
				dialog?.ticketId || ''
			).trim();

		if (!ticketId) {
			return '';
		}

		const requestNumber =
			String(
				dialog?.requestNumber || ''
			).trim();

		const title =
			requestNumber ||
			ticketId;

		const clientName =
			String(
				dialog?.clientName ||
				dialog?.username ||
				'Клиент'
			).trim();

		const locationName =
			String(
				dialog?.location || ''
			).trim();

		const channelName =
			String(
				dialog?.channelName ||
				dialog?.channel ||
				'Без канала'
			).trim();

		const unreadCount =
			Number(
				dialog?.unreadCount ??
				dialog?.unread_count ??
				0
			) || 0;

		const isActive =
			String(
				options
					.getActiveDialogTicketId
					?.() || ''
			).trim() === ticketId;

		const lastActivity =
			formatMyDialogLastActivity(
				dialog
			);

		const problem =
			String(
				dialog?.problem || ''
			).trim();

		const metaParts =
			[
				clientName,
				channelName,
			].filter(Boolean);

		const pinLabel =
			pinned
				? 'Открепить'
				: 'Закрепить';

		return `
			<div class="
				dialog-my-dialog-item-shell
				${pinned ? 'is-pinned' : ''}
			">
				<button
					type="button"
					class="
						dialog-my-dialog-item
						${isActive ? 'is-active' : ''}
					"
					data-my-dialog-ticket-id="${escapeHtml(ticketId)}"
					aria-current="${isActive ? 'true' : 'false'}"
				>
					<div class="dialog-my-dialog-item-head">
						<div class="dialog-my-dialog-item-title">
							№ ${escapeHtml(title)}
						</div>

						<span class="
							badge
							dialog-unread-count
							${unreadCount > 0 ? '' : 'd-none'}
						">
							${unreadCount}
						</span>
					</div>

					<div class="dialog-my-dialog-item-meta">
						${metaParts
							.map(
								(part) =>
									`<span>${escapeHtml(part)}</span>`
							)
							.join('')}
					</div>

					${
						locationName
							? `
								<div class="dialog-my-dialog-item-last">
									Ресторан:
									${escapeHtml(locationName)}
								</div>
							`
							: ''
					}

					<div class="dialog-my-dialog-item-last">
						${escapeHtml(
							problem ||
							lastActivity
						)}
					</div>

					${
						problem
							? `
								<div class="dialog-my-dialog-item-last">
									${escapeHtml(lastActivity)}
								</div>
							`
							: ''
					}
				</button>

				<button
					type="button"
					class="
						dialog-my-dialog-pin
						${pinned ? 'is-pinned' : ''}
					"
					data-my-dialog-pin-ticket-id="${escapeHtml(ticketId)}"
					aria-pressed="${pinned ? 'true' : 'false'}"
					aria-label="${pinLabel} диалог № ${escapeHtml(title)}"
					title="${pinLabel}"
				>
					<span aria-hidden="true">📌</span>
				</button>
			</div>
		`;
	}

    function renderMyDialogsPanel() {
		if (
			!elements.panel ||
			!elements.newList ||
			!elements.unansweredList ||
			!elements.inWorkList
		) {
			return;
		}

		const focusedElement =
			document.activeElement
				instanceof Element
				? document.activeElement
				: null;

		const focusedPin =
			focusedElement?.closest(
				'[data-my-dialog-pin-ticket-id]'
			);

		const focusedDialog =
			focusedElement?.closest(
				'[data-my-dialog-ticket-id]'
			);

		const focusedControlType =
			focusedPin
				? 'pin'
				: (
					focusedDialog
						? 'dialog'
						: ''
				);

		const focusedTicketId =
			focusedPin?.getAttribute(
				'data-my-dialog-pin-ticket-id'
			) ||
			focusedDialog?.getAttribute(
				'data-my-dialog-ticket-id'
			) ||
			'';

		const state =
			getMyDialogsState();

		const newDialogs =
			normalizeMyDialogsCollection(
				state.new
			);

		const unanswered =
			normalizeMyDialogsCollection(
				state.unanswered
			);

		const inWork =
			normalizeMyDialogsCollection(
				state.inWork
			);

		const activeById =
			new Map();

		[
			...newDialogs,
			...unanswered,
			...inWork,
		].forEach(
			(dialog) => {
				const ticketId =
					String(
						dialog?.ticketId || ''
					).trim();

				if (
					ticketId &&
					!activeById.has(ticketId)
				) {
					activeById.set(
						ticketId,
						dialog
					);
				}
			}
		);

		const pinnedIds =
			getPinnedDialogIds();

		const pinnedDialogs =
			pinnedIds
				.map(
					(ticketId) =>
						activeById.get(
							ticketId
						)
				)
				.filter(Boolean);

		const activePinnedIds =
			new Set(
				pinnedDialogs.map(
					(dialog) =>
						String(
							dialog.ticketId
						)
				)
			);

		const regularNew =
			newDialogs.filter(
				(dialog) =>
					!activePinnedIds.has(
						String(
							dialog.ticketId
						)
					)
			);

		const regularUnanswered =
			unanswered.filter(
				(dialog) =>
					!activePinnedIds.has(
						String(
							dialog.ticketId
						)
					)
			);

		const regularInWork =
			inWork.filter(
				(dialog) =>
					!activePinnedIds.has(
						String(
							dialog.ticketId
						)
					)
			);

		if (pinnedList) {
			pinnedList.innerHTML =
				pinnedDialogs
					.map(
						(dialog) =>
							renderMyDialogItem(
								dialog,
								{
									pinned: true,
								}
							)
					)
					.join('');
		}

		pinnedSection?.classList.toggle(
			'd-none',
			pinnedDialogs.length === 0
		);

		elements.newList.innerHTML =
			regularNew
				.map(
					(dialog) =>
						renderMyDialogItem(
							dialog
						)
				)
				.join('');

		elements.unansweredList.innerHTML =
			regularUnanswered
				.map(
					(dialog) =>
						renderMyDialogItem(
							dialog
						)
				)
				.join('');

		elements.inWorkList.innerHTML =
			regularInWork
				.map(
					(dialog) =>
						renderMyDialogItem(
							dialog
						)
				)
				.join('');

		const totalActive =
			activeById.size;

		if (elements.count) {
			elements.count.textContent =
				`(${totalActive})`;
		}

		elements.newSection
			?.classList.toggle(
				'd-none',
				regularNew.length === 0
			);

		elements.unansweredSection
			?.classList.toggle(
				'd-none',
				regularUnanswered.length === 0
			);

		elements.inWorkSection
			?.classList.toggle(
				'd-none',
				regularInWork.length === 0
			);

		elements.empty
			?.classList.toggle(
				'd-none',
				totalActive > 0
			);

		if (
			focusedTicketId &&
			focusedControlType
		) {
			window.requestAnimationFrame(
				() => {
					const attributeName =
						focusedControlType ===
						'pin'
							? 'data-my-dialog-pin-ticket-id'
							: 'data-my-dialog-ticket-id';

					const nextTrigger =
						Array.from(
							elements.panel
								.querySelectorAll(
									`[${attributeName}]`
								)
						).find(
							(item) =>
								item.getAttribute(
									attributeName
								) ===
								focusedTicketId
						);

					nextTrigger?.focus({
						preventScroll: true,
					});
				}
			);
		}
	}

    function bindPanelEvents() {
      if (!elements.panel || elements.panel.dataset.myDialogsBound === 'true') return;
      elements.panel.dataset.myDialogsBound = 'true';
      elements.panel.addEventListener(
		'click',
		(event) => {
			const target =
				event.target
					instanceof Element
					? event.target
					: null;

			const pinTrigger =
				target?.closest(
					'[data-my-dialog-pin-ticket-id]'
				);

			if (pinTrigger) {
				event.preventDefault();
				event.stopPropagation();

				const ticketId =
					String(
						pinTrigger.getAttribute(
							'data-my-dialog-pin-ticket-id'
						) || ''
					).trim();

				togglePinnedDialog(
					ticketId
				);

				return;
			}

			const trigger =
				target?.closest(
					'[data-my-dialog-ticket-id]'
				);

			if (!trigger) {
				return;
			}

			event.preventDefault();

			const ticketId =
				String(
					trigger.getAttribute(
						'data-my-dialog-ticket-id'
					) || ''
				).trim();

			if (!ticketId) {
				return;
			}

			const row =
				typeof options.findRowByTicketId ===
				'function'
					? options.findRowByTicketId(
						ticketId
					)
					: null;

			options.setActiveDialogRow?.(
				row,
				{
					ensureVisible: true,
				}
			);

			options.openDialogSurface?.(
				ticketId,
				row,
				{
					source:
						'manual_open',
				}
			);
		}
	);
    }

    return {
      normalizeMyDialogsCollection,
      resolveResponsibleRawFromItem,
      resolveRowResponsibleRaw,
      normalizeMyDialogsState,
      isMyDialogItemUnanswered,
      isMyDialogItemClosed,
      isMyDialogItemNewUnassigned,
      buildMyDialogStateFromRow,
      syncMyDialogsStateFromTable,
      formatMyDialogLastActivity,
      renderMyDialogItem,
      renderMyDialogsPanel,
      bindPanelEvents,
    };
  }

  window.DialogsMyDialogsRuntime = {
    createRuntime,
  };
})();
