2026-02-03 09:12:14.009+03:00 INFO  [background-preinit] o.h.validator.internal.util.Version - HV000001: Hibernate Validator 8.0.1.Final
2026-02-03 09:12:14.115+03:00 INFO  [main] c.e.p.config.EnvDefaultsInitializer - Applied default SQLite paths for missing APP_DB_* variables: {APP_DB_USERS=C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\users.db, APP_DB_KNOWLEDGE=C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\knowledge_base.db, APP_DB_CLIENTS=C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\clients.db, APP_DB_OBJECT_PASSPORTS=C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\object_passports.db, APP_DB_OBJECTS=C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\objects.db, APP_DB_SETTINGS=C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\settings.db, APP_DB_BOT=C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\bot_database.db, APP_DB_TICKETS=C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\tickets.db}
2026-02-03 09:12:14.126+03:00 WARN  [main] c.e.p.config.EnvDefaultsInitializer - Environment variable APP_DB_OBJECT_PASSPORTS points to missing file C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\object_passports.db, falling back to defaults.
2026-02-03 09:12:14.129+03:00 INFO  [main] c.e.p.config.EnvDefaultsInitializer - Applied default SQLite paths for missing APP_DB_* variables: {APP_DB_OBJECT_PASSPORTS=C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\object_passports.db}
2026-02-03 09:12:14.137+03:00 INFO  [main] com.example.panel.PanelApplication - Starting PanelApplication using Java 17.0.17 with PID 9604 (C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\target\classes started by SinicinVV in C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel)
2026-02-03 09:12:14.140+03:00 INFO  [main] com.example.panel.PanelApplication - No active profile set, falling back to 1 default profile: "default"
2026-02-03 09:12:15.956+03:00 INFO  [main] o.s.d.r.c.RepositoryConfigurationDelegate - Bootstrapping Spring Data JPA repositories in DEFAULT mode.
2026-02-03 09:12:16.247+03:00 INFO  [main] o.s.d.r.c.RepositoryConfigurationDelegate - Finished Spring Data repository scanning in 276 ms. Found 31 JPA repository interfaces.
2026-02-03 09:12:18.162+03:00 INFO  [main] o.s.b.w.e.tomcat.TomcatWebServer - Tomcat initialized with port 8080 (http)
2026-02-03 09:12:18.184+03:00 INFO  [main] o.a.coyote.http11.Http11NioProtocol - Initializing ProtocolHandler ["http-nio-8080"]
2026-02-03 09:12:18.191+03:00 INFO  [main] o.a.catalina.core.StandardService - Starting service [Tomcat]
2026-02-03 09:12:18.192+03:00 INFO  [main] o.a.catalina.core.StandardEngine - Starting Servlet engine: [Apache Tomcat/10.1.20]
2026-02-03 09:12:18.360+03:00 INFO  [main] o.a.c.c.C.[Tomcat].[localhost].[/] - Initializing Spring embedded WebApplicationContext
2026-02-03 09:12:18.363+03:00 INFO  [main] o.s.b.w.s.c.ServletWebServerApplicationContext - Root WebApplicationContext: initialization completed in 4108 ms
2026-02-03 09:12:18.611+03:00 INFO  [main] c.e.p.c.SqliteDataSourceConfiguration - Using SQLite database at C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\tickets.db
2026-02-03 09:12:19.161+03:00 INFO  [main] org.flywaydb.core.FlywayExecutor - Database: jdbc:sqlite:C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\tickets.db (SQLite 3.43)
2026-02-03 09:12:19.269+03:00 INFO  [main] o.f.core.internal.command.DbValidate - Successfully validated 14 migrations (execution time 00:00.063s)
2026-02-03 09:12:19.284+03:00 INFO  [main] o.f.core.internal.command.DbMigrate - Current version of schema "main": 13
2026-02-03 09:12:19.291+03:00 INFO  [main] o.f.core.internal.command.DbMigrate - Schema "main" is up to date. No migration necessary.
2026-02-03 09:12:19.431+03:00 INFO  [main] o.h.jpa.internal.util.LogHelper - HHH000204: Processing PersistenceUnitInfo [name: default]
2026-02-03 09:12:19.544+03:00 INFO  [main] org.hibernate.Version - HHH000412: Hibernate ORM core version 6.4.4.Final
2026-02-03 09:12:19.629+03:00 INFO  [main] o.h.c.i.RegionFactoryInitiator - HHH000026: Second-level cache disabled
2026-02-03 09:12:20.107+03:00 INFO  [main] o.s.o.j.p.SpringPersistenceUnitInfo - No LoadTimeWeaver setup: ignoring JPA class transformer
2026-02-03 09:12:23.223+03:00 INFO  [main] o.h.e.t.j.p.i.JtaPlatformInitiator - HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
2026-02-03 09:12:23.230+03:00 INFO  [main] o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-02-03 09:12:23.480+03:00 INFO  [main] c.e.p.service.DatabaseHealthService - Spring panel is using SQLite database at: C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\tickets.db (tickets) and C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\users.db (users)
2026-02-03 09:12:23.651+03:00 INFO  [main] c.e.p.service.SharedConfigService - Using shared config directory at C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\config\shared
2026-02-03 09:12:25.636+03:00 INFO  [main] c.e.p.c.BotSqliteDataSourceConfiguration - Using BOT SQLite database at C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\bot_database.db
2026-02-03 09:12:26.052+03:00 INFO  [main] c.e.p.c.UsersSqliteDataSourceConfiguration - Using USERS SQLite database at C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\users.db
2026-02-03 09:12:26.133+03:00 WARN  [main] o.s.b.a.o.j.JpaBaseConfiguration$JpaWebConfiguration - spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-02-03 09:12:27.066+03:00 INFO  [main] o.s.s.web.DefaultSecurityFilterChain - Will secure any request with [org.springframework.security.web.session.DisableEncodeUrlFilter@67add4c9, org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@491afa9b, org.springframework.security.web.context.SecurityContextHolderFilter@3644d12a, org.springframework.security.web.header.HeaderWriterFilter@290ebbbe, org.springframework.web.filter.CorsFilter@147a8d7c, org.springframework.security.web.csrf.CsrfFilter@57f8a3cf, com.example.panel.security.SecurityHeadersFilter@22d477c2, org.springframework.security.web.authentication.logout.LogoutFilter@d56d9f6, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter@76e56b17, org.springframework.security.web.session.ConcurrentSessionFilter@6d985720, org.springframework.security.web.savedrequest.RequestCacheAwareFilter@4f9e32f2, org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@432e242d, org.springframework.security.web.authentication.AnonymousAuthenticationFilter@60bd11f7, org.springframework.security.web.session.SessionManagementFilter@5ebd3708, org.springframework.security.web.access.ExceptionTranslationFilter@6cc91654, org.springframework.security.web.access.intercept.AuthorizationFilter@20c1cb0c]
2026-02-03 09:12:27.613+03:00 INFO  [main] o.s.d.j.r.query.QueryEnhancerFactory - Hibernate is in classpath; If applicable, HQL parser will be used.
2026-02-03 09:12:29.315+03:00 INFO  [main] o.a.coyote.http11.Http11NioProtocol - Starting ProtocolHandler ["http-nio-8080"]
2026-02-03 09:12:29.336+03:00 INFO  [main] o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8080 (http) with context path ''
2026-02-03 09:12:29.365+03:00 INFO  [main] com.example.panel.PanelApplication - Started PanelApplication in 16.581 seconds (process running for 17.482)
2026-02-03 09:12:29.377+03:00 INFO  [main] c.e.p.s.AdditionalServicesHealthService - Bot database directory is available: C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\bot_databases
2026-02-03 09:12:29.379+03:00 INFO  [main] c.e.p.s.AdditionalServicesHealthService - Bot runtime is available at C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\java-bot
2026-02-03 09:12:29.515+03:00 INFO  [main] c.e.p.s.AdditionalServicesHealthService - Telegram bot process status: stopped
2026-02-03 09:12:29.516+03:00 INFO  [main] c.e.p.s.AdditionalServicesHealthService - VK bot process status: stopped
2026-02-03 09:12:29.527+03:00 INFO  [main] c.e.p.s.DatabaseBootstrapService - Clients database ensured at C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\clients.db
2026-02-03 09:12:29.534+03:00 INFO  [main] c.e.p.s.DatabaseBootstrapService - Knowledge base database ensured at C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\knowledge_base.db
2026-02-03 09:12:29.541+03:00 INFO  [main] c.e.p.s.DatabaseBootstrapService - Objects database ensured at C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\spring-panel\objects.db
2026-02-03 09:12:29.856+03:00 INFO  [main] c.e.p.service.BotDatabaseRegistry - Bot database ready for channel 1 at C:\Users\SinicinVV\Documents\tg_bot\tg_ref_b24_sup-main\bot_databases\bot-1.db
2026-02-03 09:12:38.557+03:00 INFO  [http-nio-8080-exec-2] o.a.c.c.C.[Tomcat].[localhost].[/] - Initializing Spring DispatcherServlet 'dispatcherServlet'
2026-02-03 09:12:38.559+03:00 INFO  [http-nio-8080-exec-2] o.s.web.servlet.DispatcherServlet - Initializing Servlet 'dispatcherServlet'
2026-02-03 09:12:38.568+03:00 INFO  [http-nio-8080-exec-2] o.s.web.servlet.DispatcherServlet - Completed initialization in 3 ms
2026-02-03 09:12:39.797+03:00 ERROR [http-nio-8080-exec-2] org.thymeleaf.TemplateEngine - [THYMELEAF][http-nio-8080-exec-2] Exception processing template "clients/profile": An error happened during template parsing (template: "
    const clientUserId = /*[[${profile.header.userId}]]*/ 0;
    const avatarImg = document.querySelector('.zoomable-avatar');
    const avatarModalEl = document.getElementById('avatarModal');
    const avatarModalImage = document.getElementById('avatarModalImage');
    let avatarModalInstance = null;

    if (avatarImg && avatarModalEl && avatarModalImage) {
        avatarImg.addEventListener('click', () => {
            avatarModalImage.src = avatarImg.dataset.fullsrc || avatarImg.src;
            if (!avatarModalInstance) {
                avatarModalInstance = new bootstrap.Modal(avatarModalEl);
            }
            avatarModalInstance.show();
        });
    }

    const blacklistModalEl = document.getElementById('blacklistModal');
    const blacklistForm = document.getElementById('blacklistForm');
    const blacklistReason = document.getElementById('blacklistReason');
    const blacklistUserPlaceholder = blacklistModalEl?.querySelector('[data-blacklist-user-id]');
    const blacklistSubmit = blacklistModalEl?.querySelector('[data-blacklist-submit]');
    const blacklistSubmitText = blacklistModalEl?.querySelector('[data-blacklist-submit-text]');
    const blacklistLoading = blacklistModalEl?.querySelector('[data-blacklist-loading]');
    const blacklistError = blacklistModalEl?.querySelector('[data-blacklist-feedback="error"]');
    const blacklistSuccess = blacklistModalEl?.querySelector('[data-blacklist-feedback="success"]');
    let blacklistModalInstance = null;

    function resetBlacklistFeedback() {
        if (blacklistError) {
            blacklistError.classList.add('d-none');
            blacklistError.textContent = '';
        }
        if (blacklistSuccess) {
            blacklistSuccess.classList.add('d-none');
            blacklistSuccess.textContent = '';
        }
    }

    window.openBlacklistModal = function (button) {
        if (!blacklistModalEl) return;
        const userId = button?.dataset?.blacklistUserId;
        if (blacklistUserPlaceholder) {
            blacklistUserPlaceholder.textContent = userId || '';
        }
        blacklistModalEl.dataset.userId = userId || '';
        if (blacklistReason) {
            blacklistReason.value = '';
        }
        resetBlacklistFeedback();
        if (!blacklistModalInstance) {
            blacklistModalInstance = new bootstrap.Modal(blacklistModalEl);
        }
        blacklistModalInstance.show();
    };

    if (blacklistForm) {
        blacklistForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const userId = blacklistModalEl?.dataset?.userId;
            if (!userId) return;
            resetBlacklistFeedback();
            if (blacklistSubmit) {
                blacklistSubmit.disabled = true;
            }
            if (blacklistLoading) {
                blacklistLoading.classList.remove('d-none');
            }
            try {
                const formData = new FormData();
                formData.append('user_id', userId);
                formData.append('reason', blacklistReason?.value?.trim() || '');
                const response = await fetch('/api/blacklist/add', {
                    method: 'POST',
                    body: formData
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    const message = payload.error || 'Не удалось добавить в blacklist';
                    if (blacklistError) {
                        blacklistError.textContent = message;
                        blacklistError.classList.remove('d-none');
                    }
                    return;
                }
                if (blacklistSuccess) {
                    blacklistSuccess.textContent = payload.message || 'Клиент заблокирован';
                    blacklistSuccess.classList.remove('d-none');
                }
                setTimeout(() => window.location.reload(), 600);
            } catch (error) {
                if (blacklistError) {
                    blacklistError.textContent = 'Ошибка соединения. Попробуйте ещё раз.';
                    blacklistError.classList.remove('d-none');
                }
            } finally {
                if (blacklistSubmit) {
                    blacklistSubmit.disabled = false;
                }
                if (blacklistLoading) {
                    blacklistLoading.classList.add('d-none');
                }
            }
        });
    }

    window.removeBlacklist = async function (button) {
        const userId = button?.dataset?.blacklistUserId;
        if (!userId) return;
        if (!confirm('Разблокировать клиента?')) {
            return;
        }
        const formData = new FormData();
        formData.append('user_id', userId);
        const response = await fetch('/api/blacklist/remove', {
            method: 'POST',
            body: formData
        });
        const payload = await response.json();
        if (response.ok && payload.ok) {
            window.location.reload();
        } else {
            alert(payload.error || 'Не удалось снять блокировку');
        }
    };

    const clientNameForm = document.getElementById('clientNameForm');
    const clientNameInput = document.getElementById('clientNameInput');
    const clientNameDisplay = document.getElementById('clientNameDisplay');
    const clientNameError = document.getElementById('clientNameError');

    if (clientNameForm) {
        clientNameForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!clientNameInput) return;
            if (clientNameError) {
                clientNameError.classList.add('d-none');
                clientNameError.textContent = '';
            }
            try {
                const response = await fetch(`/api/clients/${clientUserId}/name`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ client_name: clientNameInput.value || '' })
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    throw new Error(payload.error || 'Ошибка сохранения имени');
                }
                if (clientNameDisplay) {
                    clientNameDisplay.textContent = payload.client_name || '—';
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById('clientNameModal'));
                if (modal) {
                    modal.hide();
                }
            } catch (error) {
                if (clientNameError) {
                    clientNameError.textContent = error?.message || 'Ошибка соединения';
                    clientNameError.classList.remove('d-none');
                }
            }
        });
    }

    const clientStatusForm = document.getElementById('clientStatusForm');
    const clientStatusSelect = document.getElementById('clientStatusSelect');
    const clientStatusDisplay = document.getElementById('clientStatusDisplay');
    const clientStatusError = document.getElementById('clientStatusError');

    if (clientStatusForm) {
        clientStatusForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!clientStatusSelect) return;
            if (clientStatusError) {
                clientStatusError.classList.add('d-none');
                clientStatusError.textContent = '';
            }
            try {
                const response = await fetch(`/api/clients/${clientUserId}/status`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ client_status: clientStatusSelect.value || '' })
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    throw new Error(payload.error || 'Ошибка сохранения статуса');
                }
                if (clientStatusDisplay) {
                    clientStatusDisplay.textContent = payload.client_status || '—';
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById('clientStatusModal'));
                if (modal) {
                    modal.hide();
                }
            } catch (error) {
                if (clientStatusError) {
                    clientStatusError.textContent = error?.message || 'Ошибка соединения';
                    clientStatusError.classList.remove('d-none');
                }
            }
        });
    }

    function ensureManualPhonesPlaceholderHidden() {
        const tbody = document.getElementById('manualPhonesTbody');
        if (!tbody) return;
        const placeholder = tbody.querySelector('tr td[colspan="4"]');
        if (placeholder) {
            placeholder.parentElement?.remove();
        }
    }

    window.addManualPhone = async function () {
        const phoneInput = document.getElementById('newPhoneInput');
        const labelInput = document.getElementById('newPhoneLabelInput');
        const phone = (phoneInput?.value || '').trim();
        const label = (labelInput?.value || '').trim();
        if (!phone) {
            alert('Введите телефон');
            return;
        }
        try {
            const response = await fetch(`/api/clients/${clientUserId}/phones`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ phone, label })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось добавить телефон');
            }
            const tbody = document.getElementById('manualPhonesTbody');
            if (tbody) {
                ensureManualPhonesPlaceholderHidden();
                const row = document.createElement('tr');
                row.dataset.id = payload.id;
                row.innerHTML = `
                    <td><strong>${payload.phone}</strong></td>
                    <td><input class="form-control form-control-sm phone-label-input" value="${payload.label || ''}" placeholder="личный/рабочий/…"></td>
                    <td><small class="text-muted">${payload.created_at || '—'}</small></td>
                    <td class="text-end">
                        <button class="btn btn-sm btn-outline-primary" type="button" data-user-id="${clientUserId}" data-phone-id="${payload.id}" onclick="savePhoneLabel(this)">?</button>
                        <button class="btn btn-sm btn-outline-danger" type="button" data-user-id="${clientUserId}" data-phone-id="${payload.id}" onclick="archivePhone(this)">?</button>
                    </td>
                `;
                tbody.prepend(row);
            }
            if (phoneInput) phoneInput.value = '';
            if (labelInput) labelInput.value = '';
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    window.savePhoneLabel = async function (button) {
        const userId = button?.dataset?.userId;
        const phoneId = button?.dataset?.phoneId;
        const row = button?.closest('tr');
        const input = row?.querySelector('.phone-label-input');
        if (!userId || !phoneId || !input) return;
        try {
            const response = await fetch(`/api/clients/${userId}/phones/${phoneId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ label: input.value || '' })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось сохранить метку');
            }
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    window.archivePhone = async function (button) {
        const userId = button?.dataset?.userId;
        const phoneId = button?.dataset?.phoneId;
        if (!userId || !phoneId) return;
        if (!confirm('Убрать телефон из активных?')) return;
        try {
            const response = await fetch(`/api/clients/${userId}/phones/${phoneId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ active: false })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось убрать телефон');
            }
            const row = button.closest('tr');
            if (row) {
                row.remove();
            }
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    function normalizeMessageSender(sender) {
        const value = String(sender || '').toLowerCase();
        if (value.includes('support') || value.includes('operator') || value.includes('admin')) {
            return 'support';
        }
        return 'user';
    }

    function formatMessageTimestamp(value) {
        if (!value) return '';
        const numeric = typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value;
        const parsed = new Date(numeric);
        if (!Number.isNaN(parsed.getTime())) {
            const day = String(parsed.getDate()).padStart(2, '0');
            const month = String(parsed.getMonth() + 1).padStart(2, '0');
            const year = parsed.getFullYear();
            return `${day}:${month}:${year}`;
        }
        return value;
    }

    function renderClientHistory(messages) {
        const container = document.getElementById('clientHistoryMessages');
        if (!container) return;
        if (!Array.isArray(messages) || messages.length === 0) {
            container.innerHTML = '<div class="text-muted">Сообщения не найдены.</div>';
            return;
        }
        container.innerHTML = messages.map((msg) => {
            const senderType = normalizeMessageSender(msg.sender);
            const timestamp = formatMessageTimestamp(msg.timestamp);
            const bodyText = msg.message ? msg.message.replace(/\n/g, '<br>') : '';
            const fallbackType = msg.messageType && !bodyText ? `[${msg.messageType}]` : '';
            const body = bodyText || fallbackType || '—';
            let attachment = '';
            if (msg.attachment) {
                attachment = `<div class="small"><a href="${msg.attachment}" target="_blank" rel="noopener">Вложение</a></div>`;`;
            }
            return `
                <div class="chat-message ${senderType}">
                    <div class="d-flex justify-content-between small text-muted mb-1">
                        <span>${msg.sender || 'Пользователь'}</span>
                        <span>${timestamp}</span>
                    </div>
                    <div>${body}</div>
                    ${attachment}
                </div>
            `;
        }).join('');
    }

    let clientHistoryModalInstance = null;
    let clientHistoryTicketId = null;

    window.openClientHistory = async function (button) {
        const ticketId = button?.dataset?.ticketId;
        const channelId = button?.dataset?.channelId;
        if (!ticketId) return;
        clientHistoryTicketId = ticketId;
        const meta = document.getElementById('clientHistoryMeta');
        if (meta) {
            meta.textContent = `ID заявки: ${ticketId}`;
        }
        const container = document.getElementById('clientHistoryMessages');
        if (container) {
            container.innerHTML = '<div class="text-muted">Загрузка истории...</div>';
        }
        if (!clientHistoryModalInstance) {
            clientHistoryModalInstance = new bootstrap.Modal(document.getElementById('clientHistoryModal'));
        }
        clientHistoryModalInstance.show();

        try {
            const query = channelId ? `?channelId=${encodeURIComponent(channelId)}` : '';
            const response = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}${query}`, { credentials: 'same-origin' });
            const payload = await response.json();
            if (!response.ok) {
                throw new Error(payload?.error || 'Не удалось загрузить историю');
            }
            renderClientHistory(payload.history || []);
        } catch (error) {
            if (container) {
                container.innerHTML = `<div class="text-danger">${error?.message || 'Ошибка соединения'}</div>`;
            }
        }
    };

    const ticketsSearch = document.getElementById('clientTicketsSearch');
    if (ticketsSearch) {
        ticketsSearch.addEventListener('input', () => {
            const query = ticketsSearch.value.trim().toLowerCase();
            let visibleCount = 0;
            document.querySelectorAll('#clientTicketsAccordion .accordion-item').forEach((item) => {
                const haystack = (item.dataset.searchValue || '').toLowerCase();
                const matches = !query || haystack.includes(query);
                item.classList.toggle('d-none', !matches);
                if (matches) {
                    visibleCount += 1;
                }
            });
            const emptyIndicator = document.getElementById('clientTicketsEmpty');
            if (emptyIndicator) {
                emptyIndicator.style.display = visibleCount ? 'none' : '';
            }
        });
    }

    function initSearch(inputId, selector, options = {}) {
        const input = document.getElementById(inputId);
        if (!input) return;
        const items = Array.from(document.querySelectorAll(selector));
        if (!items.length) return;

        const groupContainers = {};
        if (options.groupContainerSelector) {
            document.querySelectorAll(options.groupContainerSelector).forEach((el) => {
                const key = el.dataset.groupContainer;
                if (key) groupContainers[key] = el;
            });
        }

        const emptyIndicator = options.emptyIndicatorId ? document.getElementById(options.emptyIndicatorId) : null;

        const applyFilter = () => {
            const query = input.value.trim().toLowerCase();
            let visibleCount = 0;
            const visibleByGroup = {};
            items.forEach((item) => {
                const value = (item.dataset.searchValue || '').toLowerCase();
                const groupKey = item.dataset.groupKey || '';
                const matches = !query || value.includes(query);
                item.classList.toggle('d-none', !matches);
                if (matches) {
                    visibleCount += 1;
                    visibleByGroup[groupKey] = (visibleByGroup[groupKey] || 0) + 1;
                }
            });

            Object.entries(groupContainers).forEach(([key, container]) => {
                const hasVisible = (visibleByGroup[key] || 0) > 0;
                container.classList.toggle('d-none', !hasVisible);
            });

            if (emptyIndicator) {
                emptyIndicator.style.display = visibleCount ? 'none' : '';
            }
        };

        input.addEventListener('input', applyFilter);
        applyFilter();
    }

    initSearch('clientAnalyticsSearch', '#clientAnalyticsList [data-search-value]', {
        groupContainerSelector: '#clientAnalyticsList [data-group-container]',
        emptyIndicatorId: 'clientAnalyticsEmpty',
    });
" - line 2, col 58)
org.thymeleaf.exceptions.TemplateInputException: An error happened during template parsing (template: "
    const clientUserId = /*[[${profile.header.userId}]]*/ 0;
    const avatarImg = document.querySelector('.zoomable-avatar');
    const avatarModalEl = document.getElementById('avatarModal');
    const avatarModalImage = document.getElementById('avatarModalImage');
    let avatarModalInstance = null;

    if (avatarImg && avatarModalEl && avatarModalImage) {
        avatarImg.addEventListener('click', () => {
            avatarModalImage.src = avatarImg.dataset.fullsrc || avatarImg.src;
            if (!avatarModalInstance) {
                avatarModalInstance = new bootstrap.Modal(avatarModalEl);
            }
            avatarModalInstance.show();
        });
    }

    const blacklistModalEl = document.getElementById('blacklistModal');
    const blacklistForm = document.getElementById('blacklistForm');
    const blacklistReason = document.getElementById('blacklistReason');
    const blacklistUserPlaceholder = blacklistModalEl?.querySelector('[data-blacklist-user-id]');
    const blacklistSubmit = blacklistModalEl?.querySelector('[data-blacklist-submit]');
    const blacklistSubmitText = blacklistModalEl?.querySelector('[data-blacklist-submit-text]');
    const blacklistLoading = blacklistModalEl?.querySelector('[data-blacklist-loading]');
    const blacklistError = blacklistModalEl?.querySelector('[data-blacklist-feedback="error"]');
    const blacklistSuccess = blacklistModalEl?.querySelector('[data-blacklist-feedback="success"]');
    let blacklistModalInstance = null;

    function resetBlacklistFeedback() {
        if (blacklistError) {
            blacklistError.classList.add('d-none');
            blacklistError.textContent = '';
        }
        if (blacklistSuccess) {
            blacklistSuccess.classList.add('d-none');
            blacklistSuccess.textContent = '';
        }
    }

    window.openBlacklistModal = function (button) {
        if (!blacklistModalEl) return;
        const userId = button?.dataset?.blacklistUserId;
        if (blacklistUserPlaceholder) {
            blacklistUserPlaceholder.textContent = userId || '';
        }
        blacklistModalEl.dataset.userId = userId || '';
        if (blacklistReason) {
            blacklistReason.value = '';
        }
        resetBlacklistFeedback();
        if (!blacklistModalInstance) {
            blacklistModalInstance = new bootstrap.Modal(blacklistModalEl);
        }
        blacklistModalInstance.show();
    };

    if (blacklistForm) {
        blacklistForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const userId = blacklistModalEl?.dataset?.userId;
            if (!userId) return;
            resetBlacklistFeedback();
            if (blacklistSubmit) {
                blacklistSubmit.disabled = true;
            }
            if (blacklistLoading) {
                blacklistLoading.classList.remove('d-none');
            }
            try {
                const formData = new FormData();
                formData.append('user_id', userId);
                formData.append('reason', blacklistReason?.value?.trim() || '');
                const response = await fetch('/api/blacklist/add', {
                    method: 'POST',
                    body: formData
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    const message = payload.error || 'Не удалось добавить в blacklist';
                    if (blacklistError) {
                        blacklistError.textContent = message;
                        blacklistError.classList.remove('d-none');
                    }
                    return;
                }
                if (blacklistSuccess) {
                    blacklistSuccess.textContent = payload.message || 'Клиент заблокирован';
                    blacklistSuccess.classList.remove('d-none');
                }
                setTimeout(() => window.location.reload(), 600);
            } catch (error) {
                if (blacklistError) {
                    blacklistError.textContent = 'Ошибка соединения. Попробуйте ещё раз.';
                    blacklistError.classList.remove('d-none');
                }
            } finally {
                if (blacklistSubmit) {
                    blacklistSubmit.disabled = false;
                }
                if (blacklistLoading) {
                    blacklistLoading.classList.add('d-none');
                }
            }
        });
    }

    window.removeBlacklist = async function (button) {
        const userId = button?.dataset?.blacklistUserId;
        if (!userId) return;
        if (!confirm('Разблокировать клиента?')) {
            return;
        }
        const formData = new FormData();
        formData.append('user_id', userId);
        const response = await fetch('/api/blacklist/remove', {
            method: 'POST',
            body: formData
        });
        const payload = await response.json();
        if (response.ok && payload.ok) {
            window.location.reload();
        } else {
            alert(payload.error || 'Не удалось снять блокировку');
        }
    };

    const clientNameForm = document.getElementById('clientNameForm');
    const clientNameInput = document.getElementById('clientNameInput');
    const clientNameDisplay = document.getElementById('clientNameDisplay');
    const clientNameError = document.getElementById('clientNameError');

    if (clientNameForm) {
        clientNameForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!clientNameInput) return;
            if (clientNameError) {
                clientNameError.classList.add('d-none');
                clientNameError.textContent = '';
            }
            try {
                const response = await fetch(`/api/clients/${clientUserId}/name`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ client_name: clientNameInput.value || '' })
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    throw new Error(payload.error || 'Ошибка сохранения имени');
                }
                if (clientNameDisplay) {
                    clientNameDisplay.textContent = payload.client_name || '—';
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById('clientNameModal'));
                if (modal) {
                    modal.hide();
                }
            } catch (error) {
                if (clientNameError) {
                    clientNameError.textContent = error?.message || 'Ошибка соединения';
                    clientNameError.classList.remove('d-none');
                }
            }
        });
    }

    const clientStatusForm = document.getElementById('clientStatusForm');
    const clientStatusSelect = document.getElementById('clientStatusSelect');
    const clientStatusDisplay = document.getElementById('clientStatusDisplay');
    const clientStatusError = document.getElementById('clientStatusError');

    if (clientStatusForm) {
        clientStatusForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!clientStatusSelect) return;
            if (clientStatusError) {
                clientStatusError.classList.add('d-none');
                clientStatusError.textContent = '';
            }
            try {
                const response = await fetch(`/api/clients/${clientUserId}/status`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ client_status: clientStatusSelect.value || '' })
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    throw new Error(payload.error || 'Ошибка сохранения статуса');
                }
                if (clientStatusDisplay) {
                    clientStatusDisplay.textContent = payload.client_status || '—';
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById('clientStatusModal'));
                if (modal) {
                    modal.hide();
                }
            } catch (error) {
                if (clientStatusError) {
                    clientStatusError.textContent = error?.message || 'Ошибка соединения';
                    clientStatusError.classList.remove('d-none');
                }
            }
        });
    }

    function ensureManualPhonesPlaceholderHidden() {
        const tbody = document.getElementById('manualPhonesTbody');
        if (!tbody) return;
        const placeholder = tbody.querySelector('tr td[colspan="4"]');
        if (placeholder) {
            placeholder.parentElement?.remove();
        }
    }

    window.addManualPhone = async function () {
        const phoneInput = document.getElementById('newPhoneInput');
        const labelInput = document.getElementById('newPhoneLabelInput');
        const phone = (phoneInput?.value || '').trim();
        const label = (labelInput?.value || '').trim();
        if (!phone) {
            alert('Введите телефон');
            return;
        }
        try {
            const response = await fetch(`/api/clients/${clientUserId}/phones`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ phone, label })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось добавить телефон');
            }
            const tbody = document.getElementById('manualPhonesTbody');
            if (tbody) {
                ensureManualPhonesPlaceholderHidden();
                const row = document.createElement('tr');
                row.dataset.id = payload.id;
                row.innerHTML = `
                    <td><strong>${payload.phone}</strong></td>
                    <td><input class="form-control form-control-sm phone-label-input" value="${payload.label || ''}" placeholder="личный/рабочий/…"></td>
                    <td><small class="text-muted">${payload.created_at || '—'}</small></td>
                    <td class="text-end">
                        <button class="btn btn-sm btn-outline-primary" type="button" data-user-id="${clientUserId}" data-phone-id="${payload.id}" onclick="savePhoneLabel(this)">?</button>
                        <button class="btn btn-sm btn-outline-danger" type="button" data-user-id="${clientUserId}" data-phone-id="${payload.id}" onclick="archivePhone(this)">?</button>
                    </td>
                `;
                tbody.prepend(row);
            }
            if (phoneInput) phoneInput.value = '';
            if (labelInput) labelInput.value = '';
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    window.savePhoneLabel = async function (button) {
        const userId = button?.dataset?.userId;
        const phoneId = button?.dataset?.phoneId;
        const row = button?.closest('tr');
        const input = row?.querySelector('.phone-label-input');
        if (!userId || !phoneId || !input) return;
        try {
            const response = await fetch(`/api/clients/${userId}/phones/${phoneId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ label: input.value || '' })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось сохранить метку');
            }
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    window.archivePhone = async function (button) {
        const userId = button?.dataset?.userId;
        const phoneId = button?.dataset?.phoneId;
        if (!userId || !phoneId) return;
        if (!confirm('Убрать телефон из активных?')) return;
        try {
            const response = await fetch(`/api/clients/${userId}/phones/${phoneId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ active: false })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось убрать телефон');
            }
            const row = button.closest('tr');
            if (row) {
                row.remove();
            }
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    function normalizeMessageSender(sender) {
        const value = String(sender || '').toLowerCase();
        if (value.includes('support') || value.includes('operator') || value.includes('admin')) {
            return 'support';
        }
        return 'user';
    }

    function formatMessageTimestamp(value) {
        if (!value) return '';
        const numeric = typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value;
        const parsed = new Date(numeric);
        if (!Number.isNaN(parsed.getTime())) {
            const day = String(parsed.getDate()).padStart(2, '0');
            const month = String(parsed.getMonth() + 1).padStart(2, '0');
            const year = parsed.getFullYear();
            return `${day}:${month}:${year}`;
        }
        return value;
    }

    function renderClientHistory(messages) {
        const container = document.getElementById('clientHistoryMessages');
        if (!container) return;
        if (!Array.isArray(messages) || messages.length === 0) {
            container.innerHTML = '<div class="text-muted">Сообщения не найдены.</div>';
            return;
        }
        container.innerHTML = messages.map((msg) => {
            const senderType = normalizeMessageSender(msg.sender);
            const timestamp = formatMessageTimestamp(msg.timestamp);
            const bodyText = msg.message ? msg.message.replace(/\n/g, '<br>') : '';
            const fallbackType = msg.messageType && !bodyText ? `[${msg.messageType}]` : '';
            const body = bodyText || fallbackType || '—';
            let attachment = '';
            if (msg.attachment) {
                attachment = `<div class="small"><a href="${msg.attachment}" target="_blank" rel="noopener">Вложение</a></div>`;`;
            }
            return `
                <div class="chat-message ${senderType}">
                    <div class="d-flex justify-content-between small text-muted mb-1">
                        <span>${msg.sender || 'Пользователь'}</span>
                        <span>${timestamp}</span>
                    </div>
                    <div>${body}</div>
                    ${attachment}
                </div>
            `;
        }).join('');
    }

    let clientHistoryModalInstance = null;
    let clientHistoryTicketId = null;

    window.openClientHistory = async function (button) {
        const ticketId = button?.dataset?.ticketId;
        const channelId = button?.dataset?.channelId;
        if (!ticketId) return;
        clientHistoryTicketId = ticketId;
        const meta = document.getElementById('clientHistoryMeta');
        if (meta) {
            meta.textContent = `ID заявки: ${ticketId}`;
        }
        const container = document.getElementById('clientHistoryMessages');
        if (container) {
            container.innerHTML = '<div class="text-muted">Загрузка истории...</div>';
        }
        if (!clientHistoryModalInstance) {
            clientHistoryModalInstance = new bootstrap.Modal(document.getElementById('clientHistoryModal'));
        }
        clientHistoryModalInstance.show();

        try {
            const query = channelId ? `?channelId=${encodeURIComponent(channelId)}` : '';
            const response = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}${query}`, { credentials: 'same-origin' });
            const payload = await response.json();
            if (!response.ok) {
                throw new Error(payload?.error || 'Не удалось загрузить историю');
            }
            renderClientHistory(payload.history || []);
        } catch (error) {
            if (container) {
                container.innerHTML = `<div class="text-danger">${error?.message || 'Ошибка соединения'}</div>`;
            }
        }
    };

    const ticketsSearch = document.getElementById('clientTicketsSearch');
    if (ticketsSearch) {
        ticketsSearch.addEventListener('input', () => {
            const query = ticketsSearch.value.trim().toLowerCase();
            let visibleCount = 0;
            document.querySelectorAll('#clientTicketsAccordion .accordion-item').forEach((item) => {
                const haystack = (item.dataset.searchValue || '').toLowerCase();
                const matches = !query || haystack.includes(query);
                item.classList.toggle('d-none', !matches);
                if (matches) {
                    visibleCount += 1;
                }
            });
            const emptyIndicator = document.getElementById('clientTicketsEmpty');
            if (emptyIndicator) {
                emptyIndicator.style.display = visibleCount ? 'none' : '';
            }
        });
    }

    function initSearch(inputId, selector, options = {}) {
        const input = document.getElementById(inputId);
        if (!input) return;
        const items = Array.from(document.querySelectorAll(selector));
        if (!items.length) return;

        const groupContainers = {};
        if (options.groupContainerSelector) {
            document.querySelectorAll(options.groupContainerSelector).forEach((el) => {
                const key = el.dataset.groupContainer;
                if (key) groupContainers[key] = el;
            });
        }

        const emptyIndicator = options.emptyIndicatorId ? document.getElementById(options.emptyIndicatorId) : null;

        const applyFilter = () => {
            const query = input.value.trim().toLowerCase();
            let visibleCount = 0;
            const visibleByGroup = {};
            items.forEach((item) => {
                const value = (item.dataset.searchValue || '').toLowerCase();
                const groupKey = item.dataset.groupKey || '';
                const matches = !query || value.includes(query);
                item.classList.toggle('d-none', !matches);
                if (matches) {
                    visibleCount += 1;
                    visibleByGroup[groupKey] = (visibleByGroup[groupKey] || 0) + 1;
                }
            });

            Object.entries(groupContainers).forEach(([key, container]) => {
                const hasVisible = (visibleByGroup[key] || 0) > 0;
                container.classList.toggle('d-none', !hasVisible);
            });

            if (emptyIndicator) {
                emptyIndicator.style.display = visibleCount ? 'none' : '';
            }
        };

        input.addEventListener('input', applyFilter);
        applyFilter();
    }

    initSearch('clientAnalyticsSearch', '#clientAnalyticsList [data-search-value]', {
        groupContainerSelector: '#clientAnalyticsList [data-group-container]',
        emptyIndicatorId: 'clientAnalyticsEmpty',
    });
" - line 2, col 58)
	at org.thymeleaf.templateparser.text.AbstractTextTemplateParser.parse(AbstractTextTemplateParser.java:178)
	at org.thymeleaf.templateparser.text.AbstractTextTemplateParser.parseString(AbstractTextTemplateParser.java:113)
	at org.thymeleaf.engine.TemplateManager.parseString(TemplateManager.java:452)
	at org.thymeleaf.standard.inline.AbstractStandardInliner.inlineSwitchTemplateMode(AbstractStandardInliner.java:153)
	at org.thymeleaf.standard.inline.AbstractStandardInliner.inline(AbstractStandardInliner.java:114)
	at org.thymeleaf.standard.processor.StandardInliningTextProcessor.doProcess(StandardInliningTextProcessor.java:62)
	at org.thymeleaf.processor.text.AbstractTextProcessor.process(AbstractTextProcessor.java:57)
	at org.thymeleaf.util.ProcessorConfigurationUtils$TextProcessorWrapper.process(ProcessorConfigurationUtils.java:749)
	at org.thymeleaf.engine.ProcessorTemplateHandler.handleText(ProcessorTemplateHandler.java:560)
	at org.thymeleaf.engine.Text.beHandled(Text.java:97)
	at org.thymeleaf.engine.TemplateModel.process(TemplateModel.java:136)
	at org.thymeleaf.engine.TemplateManager.parseAndProcess(TemplateManager.java:661)
	at org.thymeleaf.TemplateEngine.process(TemplateEngine.java:1103)
	at org.thymeleaf.TemplateEngine.process(TemplateEngine.java:1077)
	at org.thymeleaf.spring6.view.ThymeleafView.renderFragment(ThymeleafView.java:372)
	at org.thymeleaf.spring6.view.ThymeleafView.render(ThymeleafView.java:192)
	at org.springframework.web.servlet.DispatcherServlet.render(DispatcherServlet.java:1431)
	at org.springframework.web.servlet.DispatcherServlet.processDispatchResult(DispatcherServlet.java:1167)
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1106)
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:979)
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
	at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:903)
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:564)
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885)
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:206)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:110)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108)
	at org.springframework.security.web.FilterChainProxy.lambda$doFilterInternal$3(FilterChainProxy.java:231)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:365)
	at org.springframework.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:100)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:131)
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:85)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:100)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:179)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:151)
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:129)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:227)
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:221)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:107)
	at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:93)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at com.example.panel.security.SecurityHeadersFilter.doFilterInternal(SecurityHeadersFilter.java:22)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.csrf.CsrfFilter.doFilterInternal(CsrfFilter.java:117)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.web.filter.CorsFilter.doFilterInternal(CorsFilter.java:91)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90)
	at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:82)
	at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:69)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:62)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:233)
	at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:191)
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113)
	at org.springframework.web.servlet.handler.HandlerMappingIntrospector.lambda$createCacheFilter$3(HandlerMappingIntrospector.java:195)
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113)
	at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74)
	at org.springframework.security.config.annotation.web.configuration.WebMvcSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebMvcSecurityConfiguration.java:230)
	at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:352)
	at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:268)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.session.web.http.SessionRepositoryFilter.doFilterInternal(SessionRepositoryFilter.java:142)
	at org.springframework.session.web.http.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:82)
	at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:352)
	at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:268)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:167)
	at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:90)
	at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:482)
	at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:115)
	at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:93)
	at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:74)
	at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:344)
	at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:391)
	at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63)
	at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:896)
	at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1736)
	at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52)
	at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1191)
	at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:659)
	at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: org.thymeleaf.templateparser.text.TextParseException: (Line = 2, Column = 58) Incomplete structure: " 0;
    const avatarImg = document.querySelector('.zoomable-avatar');
    const avatarModalEl = document.getElementById('avatarModal');
    const avatarModalImage = document.getElementById('avatarModalImage');
    let avatarModalInstance = null;

    if (avatarImg && avatarModalEl && avatarModalImage) {
        avatarImg.addEventListener('click', () => {
            avatarModalImage.src = avatarImg.dataset.fullsrc || avatarImg.src;
            if (!avatarModalInstance) {
                avatarModalInstance = new bootstrap.Modal(avatarModalEl);
            }
            avatarModalInstance.show();
        });
    }

    const blacklistModalEl = document.getElementById('blacklistModal');
    const blacklistForm = document.getElementById('blacklistForm');
    const blacklistReason = document.getElementById('blacklistReason');
    const blacklistUserPlaceholder = blacklistModalEl?.querySelector('[data-blacklist-user-id]');
    const blacklistSubmit = blacklistModalEl?.querySelector('[data-blacklist-submit]');
    const blacklistSubmitText = blacklistModalEl?.querySelector('[data-blacklist-submit-text]');
    const blacklistLoading = blacklistModalEl?.querySelector('[data-blacklist-loading]');
    const blacklistError = blacklistModalEl?.querySelector('[data-blacklist-feedback="error"]');
    const blacklistSuccess = blacklistModalEl?.querySelector('[data-blacklist-feedback="success"]');
    let blacklistModalInstance = null;

    function resetBlacklistFeedback() {
        if (blacklistError) {
            blacklistError.classList.add('d-none');
            blacklistError.textContent = '';
        }
        if (blacklistSuccess) {
            blacklistSuccess.classList.add('d-none');
            blacklistSuccess.textContent = '';
        }
    }

    window.openBlacklistModal = function (button) {
        if (!blacklistModalEl) return;
        const userId = button?.dataset?.blacklistUserId;
        if (blacklistUserPlaceholder) {
            blacklistUserPlaceholder.textContent = userId || '';
        }
        blacklistModalEl.dataset.userId = userId || '';
        if (blacklistReason) {
            blacklistReason.value = '';
        }
        resetBlacklistFeedback();
        if (!blacklistModalInstance) {
            blacklistModalInstance = new bootstrap.Modal(blacklistModalEl);
        }
        blacklistModalInstance.show();
    };

    if (blacklistForm) {
        blacklistForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const userId = blacklistModalEl?.dataset?.userId;
            if (!userId) return;
            resetBlacklistFeedback();
            if (blacklistSubmit) {
                blacklistSubmit.disabled = true;
            }
            if (blacklistLoading) {
                blacklistLoading.classList.remove('d-none');
            }
            try {
                const formData = new FormData();
                formData.append('user_id', userId);
                formData.append('reason', blacklistReason?.value?.trim() || '');
                const response = await fetch('/api/blacklist/add', {
                    method: 'POST',
                    body: formData
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    const message = payload.error || 'Не удалось добавить в blacklist';
                    if (blacklistError) {
                        blacklistError.textContent = message;
                        blacklistError.classList.remove('d-none');
                    }
                    return;
                }
                if (blacklistSuccess) {
                    blacklistSuccess.textContent = payload.message || 'Клиент заблокирован';
                    blacklistSuccess.classList.remove('d-none');
                }
                setTimeout(() => window.location.reload(), 600);
            } catch (error) {
                if (blacklistError) {
                    blacklistError.textContent = 'Ошибка соединения. Попробуйте ещё раз.';
                    blacklistError.classList.remove('d-none');
                }
            } finally {
                if (blacklistSubmit) {
                    blacklistSubmit.disabled = false;
                }
                if (blacklistLoading) {
                    blacklistLoading.classList.add('d-none');
                }
            }
        });
    }

    window.removeBlacklist = async function (button) {
        const userId = button?.dataset?.blacklistUserId;
        if (!userId) return;
        if (!confirm('Разблокировать клиента?')) {
            return;
        }
        const formData = new FormData();
        formData.append('user_id', userId);
        const response = await fetch('/api/blacklist/remove', {
            method: 'POST',
            body: formData
        });
        const payload = await response.json();
        if (response.ok && payload.ok) {
            window.location.reload();
        } else {
            alert(payload.error || 'Не удалось снять блокировку');
        }
    };

    const clientNameForm = document.getElementById('clientNameForm');
    const clientNameInput = document.getElementById('clientNameInput');
    const clientNameDisplay = document.getElementById('clientNameDisplay');
    const clientNameError = document.getElementById('clientNameError');

    if (clientNameForm) {
        clientNameForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!clientNameInput) return;
            if (clientNameError) {
                clientNameError.classList.add('d-none');
                clientNameError.textContent = '';
            }
            try {
                const response = await fetch(`/api/clients/${clientUserId}/name`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ client_name: clientNameInput.value || '' })
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    throw new Error(payload.error || 'Ошибка сохранения имени');
                }
                if (clientNameDisplay) {
                    clientNameDisplay.textContent = payload.client_name || '—';
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById('clientNameModal'));
                if (modal) {
                    modal.hide();
                }
            } catch (error) {
                if (clientNameError) {
                    clientNameError.textContent = error?.message || 'Ошибка соединения';
                    clientNameError.classList.remove('d-none');
                }
            }
        });
    }

    const clientStatusForm = document.getElementById('clientStatusForm');
    const clientStatusSelect = document.getElementById('clientStatusSelect');
    const clientStatusDisplay = document.getElementById('clientStatusDisplay');
    const clientStatusError = document.getElementById('clientStatusError');

    if (clientStatusForm) {
        clientStatusForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!clientStatusSelect) return;
            if (clientStatusError) {
                clientStatusError.classList.add('d-none');
                clientStatusError.textContent = '';
            }
            try {
                const response = await fetch(`/api/clients/${clientUserId}/status`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ client_status: clientStatusSelect.value || '' })
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    throw new Error(payload.error || 'Ошибка сохранения статуса');
                }
                if (clientStatusDisplay) {
                    clientStatusDisplay.textContent = payload.client_status || '—';
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById('clientStatusModal'));
                if (modal) {
                    modal.hide();
                }
            } catch (error) {
                if (clientStatusError) {
                    clientStatusError.textContent = error?.message || 'Ошибка соединения';
                    clientStatusError.classList.remove('d-none');
                }
            }
        });
    }

    function ensureManualPhonesPlaceholderHidden() {
        const tbody = document.getElementById('manualPhonesTbody');
        if (!tbody) return;
        const placeholder = tbody.querySelector('tr td[colspan="4"]');
        if (placeholder) {
            placeholder.parentElement?.remove();
        }
    }

    window.addManualPhone = async function () {
        const phoneInput = document.getElementById('newPhoneInput');
        const labelInput = document.getElementById('newPhoneLabelInput');
        const phone = (phoneInput?.value || '').trim();
        const label = (labelInput?.value || '').trim();
        if (!phone) {
            alert('Введите телефон');
            return;
        }
        try {
            const response = await fetch(`/api/clients/${clientUserId}/phones`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ phone, label })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось добавить телефон');
            }
            const tbody = document.getElementById('manualPhonesTbody');
            if (tbody) {
                ensureManualPhonesPlaceholderHidden();
                const row = document.createElement('tr');
                row.dataset.id = payload.id;
                row.innerHTML = `
                    <td><strong>${payload.phone}</strong></td>
                    <td><input class="form-control form-control-sm phone-label-input" value="${payload.label || ''}" placeholder="личный/рабочий/…"></td>
                    <td><small class="text-muted">${payload.created_at || '—'}</small></td>
                    <td class="text-end">
                        <button class="btn btn-sm btn-outline-primary" type="button" data-user-id="${clientUserId}" data-phone-id="${payload.id}" onclick="savePhoneLabel(this)">?</button>
                        <button class="btn btn-sm btn-outline-danger" type="button" data-user-id="${clientUserId}" data-phone-id="${payload.id}" onclick="archivePhone(this)">?</button>
                    </td>
                `;
                tbody.prepend(row);
            }
            if (phoneInput) phoneInput.value = '';
            if (labelInput) labelInput.value = '';
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    window.savePhoneLabel = async function (button) {
        const userId = button?.dataset?.userId;
        const phoneId = button?.dataset?.phoneId;
        const row = button?.closest('tr');
        const input = row?.querySelector('.phone-label-input');
        if (!userId || !phoneId || !input) return;
        try {
            const response = await fetch(`/api/clients/${userId}/phones/${phoneId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ label: input.value || '' })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось сохранить метку');
            }
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    window.archivePhone = async function (button) {
        const userId = button?.dataset?.userId;
        const phoneId = button?.dataset?.phoneId;
        if (!userId || !phoneId) return;
        if (!confirm('Убрать телефон из активных?')) return;
        try {
            const response = await fetch(`/api/clients/${userId}/phones/${phoneId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ active: false })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось убрать телефон');
            }
            const row = button.closest('tr');
            if (row) {
                row.remove();
            }
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    function normalizeMessageSender(sender) {
        const value = String(sender || '').toLowerCase();
        if (value.includes('support') || value.includes('operator') || value.includes('admin')) {
            return 'support';
        }
        return 'user';
    }

    function formatMessageTimestamp(value) {
        if (!value) return '';
        const numeric = typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value;
        const parsed = new Date(numeric);
        if (!Number.isNaN(parsed.getTime())) {
            const day = String(parsed.getDate()).padStart(2, '0');
            const month = String(parsed.getMonth() + 1).padStart(2, '0');
            const year = parsed.getFullYear();
            return `${day}:${month}:${year}`;
        }
        return value;
    }

    function renderClientHistory(messages) {
        const container = document.getElementById('clientHistoryMessages');
        if (!container) return;
        if (!Array.isArray(messages) || messages.length === 0) {
            container.innerHTML = '<div class="text-muted">Сообщения не найдены.</div>';
            return;
        }
        container.innerHTML = messages.map((msg) => {
            const senderType = normalizeMessageSender(msg.sender);
            const timestamp = formatMessageTimestamp(msg.timestamp);
            const bodyText = msg.message ? msg.message.replace(/\n/g, '<br>') : '';
            const fallbackType = msg.messageType && !bodyText ? `[${msg.messageType}]` : '';
            const body = bodyText || fallbackType || '—';
            let attachment = '';
            if (msg.attachment) {
                attachment = `<div class="small"><a href="${msg.attachment}" target="_blank" rel="noopener">Вложение</a></div>`;`;
            }
            return `
                <div class="chat-message ${senderType}">
                    <div class="d-flex justify-content-between small text-muted mb-1">
                        <span>${msg.sender || 'Пользователь'}</span>
                        <span>${timestamp}</span>
                    </div>
                    <div>${body}</div>
                    ${attachment}
                </div>
            `;
        }).join('');
    }

    let clientHistoryModalInstance = null;
    let clientHistoryTicketId = null;

    window.openClientHistory = async function (button) {
        const ticketId = button?.dataset?.ticketId;
        const channelId = button?.dataset?.channelId;
        if (!ticketId) return;
        clientHistoryTicketId = ticketId;
        const meta = document.getElementById('clientHistoryMeta');
        if (meta) {
            meta.textContent = `ID заявки: ${ticketId}`;
        }
        const container = document.getElementById('clientHistoryMessages');
        if (container) {
            container.innerHTML = '<div class="text-muted">Загрузка истории...</div>';
        }
        if (!clientHistoryModalInstance) {
            clientHistoryModalInstance = new bootstrap.Modal(document.getElementById('clientHistoryModal'));
        }
        clientHistoryModalInstance.show();

        try {
            const query = channelId ? `?channelId=${encodeURIComponent(channelId)}` : '';
            const response = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}${query}`, { credentials: 'same-origin' });
            const payload = await response.json();
            if (!response.ok) {
                throw new Error(payload?.error || 'Не удалось загрузить историю');
            }
            renderClientHistory(payload.history || []);
        } catch (error) {
            if (container) {
                container.innerHTML = `<div class="text-danger">${error?.message || 'Ошибка соединения'}</div>`;
            }
        }
    };

    const ticketsSearch = document.getElementById('clientTicketsSearch');
    if (ticketsSearch) {
        ticketsSearch.addEventListener('input', () => {
            const query = ticketsSearch.value.trim().toLowerCase();
            let visibleCount = 0;
            document.querySelectorAll('#clientTicketsAccordion .accordion-item').forEach((item) => {
                const haystack = (item.dataset.searchValue || '').toLowerCase();
                const matches = !query || haystack.includes(query);
                item.classList.toggle('d-none', !matches);
                if (matches) {
                    visibleCount += 1;
                }
            });
            const emptyIndicator = document.getElementById('clientTicketsEmpty');
            if (emptyIndicator) {
                emptyIndicator.style.display = visibleCount ? 'none' : '';
            }
        });
    }

    function initSearch(inputId, selector, options = {}) {
        const input = document.getElementById(inputId);
        if (!input) return;
        const items = Array.from(document.querySelectorAll(selector));
        if (!items.length) return;

        const groupContainers = {};
        if (options.groupContainerSelector) {
            document.querySelectorAll(options.groupContainerSelector).forEach((el) => {
                const key = el.dataset.groupContainer;
                if (key) groupContainers[key] = el;
            });
        }

        const emptyIndicator = options.emptyIndicatorId ? document.getElementById(options.emptyIndicatorId) : null;

        const applyFilter = () => {
            const query = input.value.trim().toLowerCase();
            let visibleCount = 0;
            const visibleByGroup = {};
            items.forEach((item) => {
                const value = (item.dataset.searchValue || '').toLowerCase();
                const groupKey = item.dataset.groupKey || '';
                const matches = !query || value.includes(query);
                item.classList.toggle('d-none', !matches);
                if (matches) {
                    visibleCount += 1;
                    visibleByGroup[groupKey] = (visibleByGroup[groupKey] || 0) + 1;
                }
            });

            Object.entries(groupContainers).forEach(([key, container]) => {
                const hasVisible = (visibleByGroup[key] || 0) > 0;
                container.classList.toggle('d-none', !hasVisible);
            });

            if (emptyIndicator) {
                emptyIndicator.style.display = visibleCount ? 'none' : '';
            }
        };

        input.addEventListener('input', applyFilter);
        applyFilter();
    }

    initSearch('clientAnalyticsSearch', '#clientAnalyticsList [data-search-value]', {
        groupContainerSelector: '#clientAnalyticsList [data-group-container]',
        emptyIndicatorId: 'clientAnalyticsEmpty',
    });
"
	at org.thymeleaf.templateparser.text.TextParser.parseDocument(TextParser.java:209)
	at org.thymeleaf.templateparser.text.TextParser.parse(TextParser.java:100)
	at org.thymeleaf.templateparser.text.AbstractTextTemplateParser.parse(AbstractTextTemplateParser.java:169)
	... 125 common frames omitted
2026-02-03 09:12:39.953+03:00 INFO  [http-nio-8080-exec-4] c.e.p.c.NotificationApiController - Unread notifications requested by admin: 0 unread
2026-02-03 09:12:40.027+03:00 ERROR [http-nio-8080-exec-2] o.a.c.c.C.[.[.[.[dispatcherServlet] - Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: org.thymeleaf.exceptions.TemplateInputException: An error happened during template parsing (template: "
    const clientUserId = /*[[${profile.header.userId}]]*/ 0;
    const avatarImg = document.querySelector('.zoomable-avatar');
    const avatarModalEl = document.getElementById('avatarModal');
    const avatarModalImage = document.getElementById('avatarModalImage');
    let avatarModalInstance = null;

    if (avatarImg && avatarModalEl && avatarModalImage) {
        avatarImg.addEventListener('click', () => {
            avatarModalImage.src = avatarImg.dataset.fullsrc || avatarImg.src;
            if (!avatarModalInstance) {
                avatarModalInstance = new bootstrap.Modal(avatarModalEl);
            }
            avatarModalInstance.show();
        });
    }

    const blacklistModalEl = document.getElementById('blacklistModal');
    const blacklistForm = document.getElementById('blacklistForm');
    const blacklistReason = document.getElementById('blacklistReason');
    const blacklistUserPlaceholder = blacklistModalEl?.querySelector('[data-blacklist-user-id]');
    const blacklistSubmit = blacklistModalEl?.querySelector('[data-blacklist-submit]');
    const blacklistSubmitText = blacklistModalEl?.querySelector('[data-blacklist-submit-text]');
    const blacklistLoading = blacklistModalEl?.querySelector('[data-blacklist-loading]');
    const blacklistError = blacklistModalEl?.querySelector('[data-blacklist-feedback="error"]');
    const blacklistSuccess = blacklistModalEl?.querySelector('[data-blacklist-feedback="success"]');
    let blacklistModalInstance = null;

    function resetBlacklistFeedback() {
        if (blacklistError) {
            blacklistError.classList.add('d-none');
            blacklistError.textContent = '';
        }
        if (blacklistSuccess) {
            blacklistSuccess.classList.add('d-none');
            blacklistSuccess.textContent = '';
        }
    }

    window.openBlacklistModal = function (button) {
        if (!blacklistModalEl) return;
        const userId = button?.dataset?.blacklistUserId;
        if (blacklistUserPlaceholder) {
            blacklistUserPlaceholder.textContent = userId || '';
        }
        blacklistModalEl.dataset.userId = userId || '';
        if (blacklistReason) {
            blacklistReason.value = '';
        }
        resetBlacklistFeedback();
        if (!blacklistModalInstance) {
            blacklistModalInstance = new bootstrap.Modal(blacklistModalEl);
        }
        blacklistModalInstance.show();
    };

    if (blacklistForm) {
        blacklistForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const userId = blacklistModalEl?.dataset?.userId;
            if (!userId) return;
            resetBlacklistFeedback();
            if (blacklistSubmit) {
                blacklistSubmit.disabled = true;
            }
            if (blacklistLoading) {
                blacklistLoading.classList.remove('d-none');
            }
            try {
                const formData = new FormData();
                formData.append('user_id', userId);
                formData.append('reason', blacklistReason?.value?.trim() || '');
                const response = await fetch('/api/blacklist/add', {
                    method: 'POST',
                    body: formData
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    const message = payload.error || 'Не удалось добавить в blacklist';
                    if (blacklistError) {
                        blacklistError.textContent = message;
                        blacklistError.classList.remove('d-none');
                    }
                    return;
                }
                if (blacklistSuccess) {
                    blacklistSuccess.textContent = payload.message || 'Клиент заблокирован';
                    blacklistSuccess.classList.remove('d-none');
                }
                setTimeout(() => window.location.reload(), 600);
            } catch (error) {
                if (blacklistError) {
                    blacklistError.textContent = 'Ошибка соединения. Попробуйте ещё раз.';
                    blacklistError.classList.remove('d-none');
                }
            } finally {
                if (blacklistSubmit) {
                    blacklistSubmit.disabled = false;
                }
                if (blacklistLoading) {
                    blacklistLoading.classList.add('d-none');
                }
            }
        });
    }

    window.removeBlacklist = async function (button) {
        const userId = button?.dataset?.blacklistUserId;
        if (!userId) return;
        if (!confirm('Разблокировать клиента?')) {
            return;
        }
        const formData = new FormData();
        formData.append('user_id', userId);
        const response = await fetch('/api/blacklist/remove', {
            method: 'POST',
            body: formData
        });
        const payload = await response.json();
        if (response.ok && payload.ok) {
            window.location.reload();
        } else {
            alert(payload.error || 'Не удалось снять блокировку');
        }
    };

    const clientNameForm = document.getElementById('clientNameForm');
    const clientNameInput = document.getElementById('clientNameInput');
    const clientNameDisplay = document.getElementById('clientNameDisplay');
    const clientNameError = document.getElementById('clientNameError');

    if (clientNameForm) {
        clientNameForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!clientNameInput) return;
            if (clientNameError) {
                clientNameError.classList.add('d-none');
                clientNameError.textContent = '';
            }
            try {
                const response = await fetch(`/api/clients/${clientUserId}/name`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ client_name: clientNameInput.value || '' })
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    throw new Error(payload.error || 'Ошибка сохранения имени');
                }
                if (clientNameDisplay) {
                    clientNameDisplay.textContent = payload.client_name || '—';
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById('clientNameModal'));
                if (modal) {
                    modal.hide();
                }
            } catch (error) {
                if (clientNameError) {
                    clientNameError.textContent = error?.message || 'Ошибка соединения';
                    clientNameError.classList.remove('d-none');
                }
            }
        });
    }

    const clientStatusForm = document.getElementById('clientStatusForm');
    const clientStatusSelect = document.getElementById('clientStatusSelect');
    const clientStatusDisplay = document.getElementById('clientStatusDisplay');
    const clientStatusError = document.getElementById('clientStatusError');

    if (clientStatusForm) {
        clientStatusForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!clientStatusSelect) return;
            if (clientStatusError) {
                clientStatusError.classList.add('d-none');
                clientStatusError.textContent = '';
            }
            try {
                const response = await fetch(`/api/clients/${clientUserId}/status`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ client_status: clientStatusSelect.value || '' })
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    throw new Error(payload.error || 'Ошибка сохранения статуса');
                }
                if (clientStatusDisplay) {
                    clientStatusDisplay.textContent = payload.client_status || '—';
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById('clientStatusModal'));
                if (modal) {
                    modal.hide();
                }
            } catch (error) {
                if (clientStatusError) {
                    clientStatusError.textContent = error?.message || 'Ошибка соединения';
                    clientStatusError.classList.remove('d-none');
                }
            }
        });
    }

    function ensureManualPhonesPlaceholderHidden() {
        const tbody = document.getElementById('manualPhonesTbody');
        if (!tbody) return;
        const placeholder = tbody.querySelector('tr td[colspan="4"]');
        if (placeholder) {
            placeholder.parentElement?.remove();
        }
    }

    window.addManualPhone = async function () {
        const phoneInput = document.getElementById('newPhoneInput');
        const labelInput = document.getElementById('newPhoneLabelInput');
        const phone = (phoneInput?.value || '').trim();
        const label = (labelInput?.value || '').trim();
        if (!phone) {
            alert('Введите телефон');
            return;
        }
        try {
            const response = await fetch(`/api/clients/${clientUserId}/phones`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ phone, label })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось добавить телефон');
            }
            const tbody = document.getElementById('manualPhonesTbody');
            if (tbody) {
                ensureManualPhonesPlaceholderHidden();
                const row = document.createElement('tr');
                row.dataset.id = payload.id;
                row.innerHTML = `
                    <td><strong>${payload.phone}</strong></td>
                    <td><input class="form-control form-control-sm phone-label-input" value="${payload.label || ''}" placeholder="личный/рабочий/…"></td>
                    <td><small class="text-muted">${payload.created_at || '—'}</small></td>
                    <td class="text-end">
                        <button class="btn btn-sm btn-outline-primary" type="button" data-user-id="${clientUserId}" data-phone-id="${payload.id}" onclick="savePhoneLabel(this)">?</button>
                        <button class="btn btn-sm btn-outline-danger" type="button" data-user-id="${clientUserId}" data-phone-id="${payload.id}" onclick="archivePhone(this)">?</button>
                    </td>
                `;
                tbody.prepend(row);
            }
            if (phoneInput) phoneInput.value = '';
            if (labelInput) labelInput.value = '';
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    window.savePhoneLabel = async function (button) {
        const userId = button?.dataset?.userId;
        const phoneId = button?.dataset?.phoneId;
        const row = button?.closest('tr');
        const input = row?.querySelector('.phone-label-input');
        if (!userId || !phoneId || !input) return;
        try {
            const response = await fetch(`/api/clients/${userId}/phones/${phoneId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ label: input.value || '' })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось сохранить метку');
            }
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    window.archivePhone = async function (button) {
        const userId = button?.dataset?.userId;
        const phoneId = button?.dataset?.phoneId;
        if (!userId || !phoneId) return;
        if (!confirm('Убрать телефон из активных?')) return;
        try {
            const response = await fetch(`/api/clients/${userId}/phones/${phoneId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ active: false })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось убрать телефон');
            }
            const row = button.closest('tr');
            if (row) {
                row.remove();
            }
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    function normalizeMessageSender(sender) {
        const value = String(sender || '').toLowerCase();
        if (value.includes('support') || value.includes('operator') || value.includes('admin')) {
            return 'support';
        }
        return 'user';
    }

    function formatMessageTimestamp(value) {
        if (!value) return '';
        const numeric = typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value;
        const parsed = new Date(numeric);
        if (!Number.isNaN(parsed.getTime())) {
            const day = String(parsed.getDate()).padStart(2, '0');
            const month = String(parsed.getMonth() + 1).padStart(2, '0');
            const year = parsed.getFullYear();
            return `${day}:${month}:${year}`;
        }
        return value;
    }

    function renderClientHistory(messages) {
        const container = document.getElementById('clientHistoryMessages');
        if (!container) return;
        if (!Array.isArray(messages) || messages.length === 0) {
            container.innerHTML = '<div class="text-muted">Сообщения не найдены.</div>';
            return;
        }
        container.innerHTML = messages.map((msg) => {
            const senderType = normalizeMessageSender(msg.sender);
            const timestamp = formatMessageTimestamp(msg.timestamp);
            const bodyText = msg.message ? msg.message.replace(/\n/g, '<br>') : '';
            const fallbackType = msg.messageType && !bodyText ? `[${msg.messageType}]` : '';
            const body = bodyText || fallbackType || '—';
            let attachment = '';
            if (msg.attachment) {
                attachment = `<div class="small"><a href="${msg.attachment}" target="_blank" rel="noopener">Вложение</a></div>`;`;
            }
            return `
                <div class="chat-message ${senderType}">
                    <div class="d-flex justify-content-between small text-muted mb-1">
                        <span>${msg.sender || 'Пользователь'}</span>
                        <span>${timestamp}</span>
                    </div>
                    <div>${body}</div>
                    ${attachment}
                </div>
            `;
        }).join('');
    }

    let clientHistoryModalInstance = null;
    let clientHistoryTicketId = null;

    window.openClientHistory = async function (button) {
        const ticketId = button?.dataset?.ticketId;
        const channelId = button?.dataset?.channelId;
        if (!ticketId) return;
        clientHistoryTicketId = ticketId;
        const meta = document.getElementById('clientHistoryMeta');
        if (meta) {
            meta.textContent = `ID заявки: ${ticketId}`;
        }
        const container = document.getElementById('clientHistoryMessages');
        if (container) {
            container.innerHTML = '<div class="text-muted">Загрузка истории...</div>';
        }
        if (!clientHistoryModalInstance) {
            clientHistoryModalInstance = new bootstrap.Modal(document.getElementById('clientHistoryModal'));
        }
        clientHistoryModalInstance.show();

        try {
            const query = channelId ? `?channelId=${encodeURIComponent(channelId)}` : '';
            const response = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}${query}`, { credentials: 'same-origin' });
            const payload = await response.json();
            if (!response.ok) {
                throw new Error(payload?.error || 'Не удалось загрузить историю');
            }
            renderClientHistory(payload.history || []);
        } catch (error) {
            if (container) {
                container.innerHTML = `<div class="text-danger">${error?.message || 'Ошибка соединения'}</div>`;
            }
        }
    };

    const ticketsSearch = document.getElementById('clientTicketsSearch');
    if (ticketsSearch) {
        ticketsSearch.addEventListener('input', () => {
            const query = ticketsSearch.value.trim().toLowerCase();
            let visibleCount = 0;
            document.querySelectorAll('#clientTicketsAccordion .accordion-item').forEach((item) => {
                const haystack = (item.dataset.searchValue || '').toLowerCase();
                const matches = !query || haystack.includes(query);
                item.classList.toggle('d-none', !matches);
                if (matches) {
                    visibleCount += 1;
                }
            });
            const emptyIndicator = document.getElementById('clientTicketsEmpty');
            if (emptyIndicator) {
                emptyIndicator.style.display = visibleCount ? 'none' : '';
            }
        });
    }

    function initSearch(inputId, selector, options = {}) {
        const input = document.getElementById(inputId);
        if (!input) return;
        const items = Array.from(document.querySelectorAll(selector));
        if (!items.length) return;

        const groupContainers = {};
        if (options.groupContainerSelector) {
            document.querySelectorAll(options.groupContainerSelector).forEach((el) => {
                const key = el.dataset.groupContainer;
                if (key) groupContainers[key] = el;
            });
        }

        const emptyIndicator = options.emptyIndicatorId ? document.getElementById(options.emptyIndicatorId) : null;

        const applyFilter = () => {
            const query = input.value.trim().toLowerCase();
            let visibleCount = 0;
            const visibleByGroup = {};
            items.forEach((item) => {
                const value = (item.dataset.searchValue || '').toLowerCase();
                const groupKey = item.dataset.groupKey || '';
                const matches = !query || value.includes(query);
                item.classList.toggle('d-none', !matches);
                if (matches) {
                    visibleCount += 1;
                    visibleByGroup[groupKey] = (visibleByGroup[groupKey] || 0) + 1;
                }
            });

            Object.entries(groupContainers).forEach(([key, container]) => {
                const hasVisible = (visibleByGroup[key] || 0) > 0;
                container.classList.toggle('d-none', !hasVisible);
            });

            if (emptyIndicator) {
                emptyIndicator.style.display = visibleCount ? 'none' : '';
            }
        };

        input.addEventListener('input', applyFilter);
        applyFilter();
    }

    initSearch('clientAnalyticsSearch', '#clientAnalyticsList [data-search-value]', {
        groupContainerSelector: '#clientAnalyticsList [data-group-container]',
        emptyIndicatorId: 'clientAnalyticsEmpty',
    });
" - line 2, col 58)] with root cause
org.thymeleaf.templateparser.text.TextParseException: (Line = 2, Column = 58) Incomplete structure: " 0;
    const avatarImg = document.querySelector('.zoomable-avatar');
    const avatarModalEl = document.getElementById('avatarModal');
    const avatarModalImage = document.getElementById('avatarModalImage');
    let avatarModalInstance = null;

    if (avatarImg && avatarModalEl && avatarModalImage) {
        avatarImg.addEventListener('click', () => {
            avatarModalImage.src = avatarImg.dataset.fullsrc || avatarImg.src;
            if (!avatarModalInstance) {
                avatarModalInstance = new bootstrap.Modal(avatarModalEl);
            }
            avatarModalInstance.show();
        });
    }

    const blacklistModalEl = document.getElementById('blacklistModal');
    const blacklistForm = document.getElementById('blacklistForm');
    const blacklistReason = document.getElementById('blacklistReason');
    const blacklistUserPlaceholder = blacklistModalEl?.querySelector('[data-blacklist-user-id]');
    const blacklistSubmit = blacklistModalEl?.querySelector('[data-blacklist-submit]');
    const blacklistSubmitText = blacklistModalEl?.querySelector('[data-blacklist-submit-text]');
    const blacklistLoading = blacklistModalEl?.querySelector('[data-blacklist-loading]');
    const blacklistError = blacklistModalEl?.querySelector('[data-blacklist-feedback="error"]');
    const blacklistSuccess = blacklistModalEl?.querySelector('[data-blacklist-feedback="success"]');
    let blacklistModalInstance = null;

    function resetBlacklistFeedback() {
        if (blacklistError) {
            blacklistError.classList.add('d-none');
            blacklistError.textContent = '';
        }
        if (blacklistSuccess) {
            blacklistSuccess.classList.add('d-none');
            blacklistSuccess.textContent = '';
        }
    }

    window.openBlacklistModal = function (button) {
        if (!blacklistModalEl) return;
        const userId = button?.dataset?.blacklistUserId;
        if (blacklistUserPlaceholder) {
            blacklistUserPlaceholder.textContent = userId || '';
        }
        blacklistModalEl.dataset.userId = userId || '';
        if (blacklistReason) {
            blacklistReason.value = '';
        }
        resetBlacklistFeedback();
        if (!blacklistModalInstance) {
            blacklistModalInstance = new bootstrap.Modal(blacklistModalEl);
        }
        blacklistModalInstance.show();
    };

    if (blacklistForm) {
        blacklistForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const userId = blacklistModalEl?.dataset?.userId;
            if (!userId) return;
            resetBlacklistFeedback();
            if (blacklistSubmit) {
                blacklistSubmit.disabled = true;
            }
            if (blacklistLoading) {
                blacklistLoading.classList.remove('d-none');
            }
            try {
                const formData = new FormData();
                formData.append('user_id', userId);
                formData.append('reason', blacklistReason?.value?.trim() || '');
                const response = await fetch('/api/blacklist/add', {
                    method: 'POST',
                    body: formData
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    const message = payload.error || 'Не удалось добавить в blacklist';
                    if (blacklistError) {
                        blacklistError.textContent = message;
                        blacklistError.classList.remove('d-none');
                    }
                    return;
                }
                if (blacklistSuccess) {
                    blacklistSuccess.textContent = payload.message || 'Клиент заблокирован';
                    blacklistSuccess.classList.remove('d-none');
                }
                setTimeout(() => window.location.reload(), 600);
            } catch (error) {
                if (blacklistError) {
                    blacklistError.textContent = 'Ошибка соединения. Попробуйте ещё раз.';
                    blacklistError.classList.remove('d-none');
                }
            } finally {
                if (blacklistSubmit) {
                    blacklistSubmit.disabled = false;
                }
                if (blacklistLoading) {
                    blacklistLoading.classList.add('d-none');
                }
            }
        });
    }

    window.removeBlacklist = async function (button) {
        const userId = button?.dataset?.blacklistUserId;
        if (!userId) return;
        if (!confirm('Разблокировать клиента?')) {
            return;
        }
        const formData = new FormData();
        formData.append('user_id', userId);
        const response = await fetch('/api/blacklist/remove', {
            method: 'POST',
            body: formData
        });
        const payload = await response.json();
        if (response.ok && payload.ok) {
            window.location.reload();
        } else {
            alert(payload.error || 'Не удалось снять блокировку');
        }
    };

    const clientNameForm = document.getElementById('clientNameForm');
    const clientNameInput = document.getElementById('clientNameInput');
    const clientNameDisplay = document.getElementById('clientNameDisplay');
    const clientNameError = document.getElementById('clientNameError');

    if (clientNameForm) {
        clientNameForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!clientNameInput) return;
            if (clientNameError) {
                clientNameError.classList.add('d-none');
                clientNameError.textContent = '';
            }
            try {
                const response = await fetch(`/api/clients/${clientUserId}/name`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ client_name: clientNameInput.value || '' })
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    throw new Error(payload.error || 'Ошибка сохранения имени');
                }
                if (clientNameDisplay) {
                    clientNameDisplay.textContent = payload.client_name || '—';
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById('clientNameModal'));
                if (modal) {
                    modal.hide();
                }
            } catch (error) {
                if (clientNameError) {
                    clientNameError.textContent = error?.message || 'Ошибка соединения';
                    clientNameError.classList.remove('d-none');
                }
            }
        });
    }

    const clientStatusForm = document.getElementById('clientStatusForm');
    const clientStatusSelect = document.getElementById('clientStatusSelect');
    const clientStatusDisplay = document.getElementById('clientStatusDisplay');
    const clientStatusError = document.getElementById('clientStatusError');

    if (clientStatusForm) {
        clientStatusForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!clientStatusSelect) return;
            if (clientStatusError) {
                clientStatusError.classList.add('d-none');
                clientStatusError.textContent = '';
            }
            try {
                const response = await fetch(`/api/clients/${clientUserId}/status`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ client_status: clientStatusSelect.value || '' })
                });
                const payload = await response.json();
                if (!response.ok || !payload.ok) {
                    throw new Error(payload.error || 'Ошибка сохранения статуса');
                }
                if (clientStatusDisplay) {
                    clientStatusDisplay.textContent = payload.client_status || '—';
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById('clientStatusModal'));
                if (modal) {
                    modal.hide();
                }
            } catch (error) {
                if (clientStatusError) {
                    clientStatusError.textContent = error?.message || 'Ошибка соединения';
                    clientStatusError.classList.remove('d-none');
                }
            }
        });
    }

    function ensureManualPhonesPlaceholderHidden() {
        const tbody = document.getElementById('manualPhonesTbody');
        if (!tbody) return;
        const placeholder = tbody.querySelector('tr td[colspan="4"]');
        if (placeholder) {
            placeholder.parentElement?.remove();
        }
    }

    window.addManualPhone = async function () {
        const phoneInput = document.getElementById('newPhoneInput');
        const labelInput = document.getElementById('newPhoneLabelInput');
        const phone = (phoneInput?.value || '').trim();
        const label = (labelInput?.value || '').trim();
        if (!phone) {
            alert('Введите телефон');
            return;
        }
        try {
            const response = await fetch(`/api/clients/${clientUserId}/phones`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ phone, label })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось добавить телефон');
            }
            const tbody = document.getElementById('manualPhonesTbody');
            if (tbody) {
                ensureManualPhonesPlaceholderHidden();
                const row = document.createElement('tr');
                row.dataset.id = payload.id;
                row.innerHTML = `
                    <td><strong>${payload.phone}</strong></td>
                    <td><input class="form-control form-control-sm phone-label-input" value="${payload.label || ''}" placeholder="личный/рабочий/…"></td>
                    <td><small class="text-muted">${payload.created_at || '—'}</small></td>
                    <td class="text-end">
                        <button class="btn btn-sm btn-outline-primary" type="button" data-user-id="${clientUserId}" data-phone-id="${payload.id}" onclick="savePhoneLabel(this)">?</button>
                        <button class="btn btn-sm btn-outline-danger" type="button" data-user-id="${clientUserId}" data-phone-id="${payload.id}" onclick="archivePhone(this)">?</button>
                    </td>
                `;
                tbody.prepend(row);
            }
            if (phoneInput) phoneInput.value = '';
            if (labelInput) labelInput.value = '';
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    window.savePhoneLabel = async function (button) {
        const userId = button?.dataset?.userId;
        const phoneId = button?.dataset?.phoneId;
        const row = button?.closest('tr');
        const input = row?.querySelector('.phone-label-input');
        if (!userId || !phoneId || !input) return;
        try {
            const response = await fetch(`/api/clients/${userId}/phones/${phoneId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ label: input.value || '' })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось сохранить метку');
            }
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    window.archivePhone = async function (button) {
        const userId = button?.dataset?.userId;
        const phoneId = button?.dataset?.phoneId;
        if (!userId || !phoneId) return;
        if (!confirm('Убрать телефон из активных?')) return;
        try {
            const response = await fetch(`/api/clients/${userId}/phones/${phoneId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ active: false })
            });
            const payload = await response.json();
            if (!response.ok || !payload.ok) {
                throw new Error(payload.error || 'Не удалось убрать телефон');
            }
            const row = button.closest('tr');
            if (row) {
                row.remove();
            }
        } catch (error) {
            alert(error?.message || 'Ошибка соединения');
        }
    };

    function normalizeMessageSender(sender) {
        const value = String(sender || '').toLowerCase();
        if (value.includes('support') || value.includes('operator') || value.includes('admin')) {
            return 'support';
        }
        return 'user';
    }

    function formatMessageTimestamp(value) {
        if (!value) return '';
        const numeric = typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value;
        const parsed = new Date(numeric);
        if (!Number.isNaN(parsed.getTime())) {
            const day = String(parsed.getDate()).padStart(2, '0');
            const month = String(parsed.getMonth() + 1).padStart(2, '0');
            const year = parsed.getFullYear();
            return `${day}:${month}:${year}`;
        }
        return value;
    }

    function renderClientHistory(messages) {
        const container = document.getElementById('clientHistoryMessages');
        if (!container) return;
        if (!Array.isArray(messages) || messages.length === 0) {
            container.innerHTML = '<div class="text-muted">Сообщения не найдены.</div>';
            return;
        }
        container.innerHTML = messages.map((msg) => {
            const senderType = normalizeMessageSender(msg.sender);
            const timestamp = formatMessageTimestamp(msg.timestamp);
            const bodyText = msg.message ? msg.message.replace(/\n/g, '<br>') : '';
            const fallbackType = msg.messageType && !bodyText ? `[${msg.messageType}]` : '';
            const body = bodyText || fallbackType || '—';
            let attachment = '';
            if (msg.attachment) {
                attachment = `<div class="small"><a href="${msg.attachment}" target="_blank" rel="noopener">Вложение</a></div>`;`;
            }
            return `
                <div class="chat-message ${senderType}">
                    <div class="d-flex justify-content-between small text-muted mb-1">
                        <span>${msg.sender || 'Пользователь'}</span>
                        <span>${timestamp}</span>
                    </div>
                    <div>${body}</div>
                    ${attachment}
                </div>
            `;
        }).join('');
    }

    let clientHistoryModalInstance = null;
    let clientHistoryTicketId = null;

    window.openClientHistory = async function (button) {
        const ticketId = button?.dataset?.ticketId;
        const channelId = button?.dataset?.channelId;
        if (!ticketId) return;
        clientHistoryTicketId = ticketId;
        const meta = document.getElementById('clientHistoryMeta');
        if (meta) {
            meta.textContent = `ID заявки: ${ticketId}`;
        }
        const container = document.getElementById('clientHistoryMessages');
        if (container) {
            container.innerHTML = '<div class="text-muted">Загрузка истории...</div>';
        }
        if (!clientHistoryModalInstance) {
            clientHistoryModalInstance = new bootstrap.Modal(document.getElementById('clientHistoryModal'));
        }
        clientHistoryModalInstance.show();

        try {
            const query = channelId ? `?channelId=${encodeURIComponent(channelId)}` : '';
            const response = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}${query}`, { credentials: 'same-origin' });
            const payload = await response.json();
            if (!response.ok) {
                throw new Error(payload?.error || 'Не удалось загрузить историю');
            }
            renderClientHistory(payload.history || []);
        } catch (error) {
            if (container) {
                container.innerHTML = `<div class="text-danger">${error?.message || 'Ошибка соединения'}</div>`;
            }
        }
    };

    const ticketsSearch = document.getElementById('clientTicketsSearch');
    if (ticketsSearch) {
        ticketsSearch.addEventListener('input', () => {
            const query = ticketsSearch.value.trim().toLowerCase();
            let visibleCount = 0;
            document.querySelectorAll('#clientTicketsAccordion .accordion-item').forEach((item) => {
                const haystack = (item.dataset.searchValue || '').toLowerCase();
                const matches = !query || haystack.includes(query);
                item.classList.toggle('d-none', !matches);
                if (matches) {
                    visibleCount += 1;
                }
            });
            const emptyIndicator = document.getElementById('clientTicketsEmpty');
            if (emptyIndicator) {
                emptyIndicator.style.display = visibleCount ? 'none' : '';
            }
        });
    }

    function initSearch(inputId, selector, options = {}) {
        const input = document.getElementById(inputId);
        if (!input) return;
        const items = Array.from(document.querySelectorAll(selector));
        if (!items.length) return;

        const groupContainers = {};
        if (options.groupContainerSelector) {
            document.querySelectorAll(options.groupContainerSelector).forEach((el) => {
                const key = el.dataset.groupContainer;
                if (key) groupContainers[key] = el;
            });
        }

        const emptyIndicator = options.emptyIndicatorId ? document.getElementById(options.emptyIndicatorId) : null;

        const applyFilter = () => {
            const query = input.value.trim().toLowerCase();
            let visibleCount = 0;
            const visibleByGroup = {};
            items.forEach((item) => {
                const value = (item.dataset.searchValue || '').toLowerCase();
                const groupKey = item.dataset.groupKey || '';
                const matches = !query || value.includes(query);
                item.classList.toggle('d-none', !matches);
                if (matches) {
                    visibleCount += 1;
                    visibleByGroup[groupKey] = (visibleByGroup[groupKey] || 0) + 1;
                }
            });

            Object.entries(groupContainers).forEach(([key, container]) => {
                const hasVisible = (visibleByGroup[key] || 0) > 0;
                container.classList.toggle('d-none', !hasVisible);
            });

            if (emptyIndicator) {
                emptyIndicator.style.display = visibleCount ? 'none' : '';
            }
        };

        input.addEventListener('input', applyFilter);
        applyFilter();
    }

    initSearch('clientAnalyticsSearch', '#clientAnalyticsList [data-search-value]', {
        groupContainerSelector: '#clientAnalyticsList [data-group-container]',
        emptyIndicatorId: 'clientAnalyticsEmpty',
    });
"
	at org.thymeleaf.templateparser.text.TextParser.parseDocument(TextParser.java:209)
	at org.thymeleaf.templateparser.text.TextParser.parse(TextParser.java:100)
	at org.thymeleaf.templateparser.text.AbstractTextTemplateParser.parse(AbstractTextTemplateParser.java:169)
	at org.thymeleaf.templateparser.text.AbstractTextTemplateParser.parseString(AbstractTextTemplateParser.java:113)
	at org.thymeleaf.engine.TemplateManager.parseString(TemplateManager.java:452)
	at org.thymeleaf.standard.inline.AbstractStandardInliner.inlineSwitchTemplateMode(AbstractStandardInliner.java:153)
	at org.thymeleaf.standard.inline.AbstractStandardInliner.inline(AbstractStandardInliner.java:114)
	at org.thymeleaf.standard.processor.StandardInliningTextProcessor.doProcess(StandardInliningTextProcessor.java:62)
	at org.thymeleaf.processor.text.AbstractTextProcessor.process(AbstractTextProcessor.java:57)
	at org.thymeleaf.util.ProcessorConfigurationUtils$TextProcessorWrapper.process(ProcessorConfigurationUtils.java:749)
	at org.thymeleaf.engine.ProcessorTemplateHandler.handleText(ProcessorTemplateHandler.java:560)
	at org.thymeleaf.engine.Text.beHandled(Text.java:97)
	at org.thymeleaf.engine.TemplateModel.process(TemplateModel.java:136)
	at org.thymeleaf.engine.TemplateManager.parseAndProcess(TemplateManager.java:661)
	at org.thymeleaf.TemplateEngine.process(TemplateEngine.java:1103)
	at org.thymeleaf.TemplateEngine.process(TemplateEngine.java:1077)
	at org.thymeleaf.spring6.view.ThymeleafView.renderFragment(ThymeleafView.java:372)
	at org.thymeleaf.spring6.view.ThymeleafView.render(ThymeleafView.java:192)
	at org.springframework.web.servlet.DispatcherServlet.render(DispatcherServlet.java:1431)
	at org.springframework.web.servlet.DispatcherServlet.processDispatchResult(DispatcherServlet.java:1167)
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1106)
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:979)
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
	at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:903)
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:564)
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885)
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:206)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:110)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108)
	at org.springframework.security.web.FilterChainProxy.lambda$doFilterInternal$3(FilterChainProxy.java:231)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:365)
	at org.springframework.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:100)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:131)
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:85)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:100)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:179)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:151)
	at org.springframework.security.web.session.ConcurrentSessionFilter.doFilter(ConcurrentSessionFilter.java:129)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:227)
	at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:221)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:107)
	at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:93)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at com.example.panel.security.SecurityHeadersFilter.doFilterInternal(SecurityHeadersFilter.java:22)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.csrf.CsrfFilter.doFilterInternal(CsrfFilter.java:117)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.web.filter.CorsFilter.doFilterInternal(CorsFilter.java:91)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90)
	at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:82)
	at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:69)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:62)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:374)
	at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:233)
	at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:191)
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113)
	at org.springframework.web.servlet.handler.HandlerMappingIntrospector.lambda$createCacheFilter$3(HandlerMappingIntrospector.java:195)
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113)
	at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74)
	at org.springframework.security.config.annotation.web.configuration.WebMvcSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebMvcSecurityConfiguration.java:230)
	at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:352)
	at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:268)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.session.web.http.SessionRepositoryFilter.doFilterInternal(SessionRepositoryFilter.java:142)
	at org.springframework.session.web.http.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:82)
	at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:352)
	at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:268)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201)
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:175)
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:150)
	at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:167)
	at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:90)
	at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:482)
	at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:115)
	at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:93)
	at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:74)
	at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:344)
	at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:391)
	at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63)
	at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:896)
	at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1736)
	at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52)
	at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1191)
	at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:659)
	at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63)
	at java.base/java.lang.Thread.run(Thread.java:840)
