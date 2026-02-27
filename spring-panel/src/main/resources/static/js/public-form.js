(() => {
    const initial = window.PUBLIC_FORM_INITIAL || {};
    const channelRef = initial.channelRef;
    const form = document.getElementById('publicForm');
    const dynamicQuestionsContainer = document.querySelector('[data-role="dynamic-questions"]');
    const historyContainer = document.querySelector('[data-role="history"]');
    const statusLabel = document.querySelector('[data-role="status"]');
    const errorBox = document.querySelector('[data-role="error"]');
    const successBox = document.querySelector('[data-role="success"]');
    const submitButton = form?.querySelector('button[type="submit"]');

    if (!channelRef || !form || !historyContainer || !dynamicQuestionsContainer) {
        return;
    }

    const apiBase = `/api/public/forms/${encodeURIComponent(channelRef)}`;
    let activeQuestions = [];
    let captchaEnabled = false;
    let currentToken = initial.initialToken || null;
    let historyPollTimer = null;
    let answersTotalMaxLength = 6000;
    let sessionPollingEnabled = true;
    let sessionPollingIntervalMs = 15000;

    let uiLocale = 'auto';

    const I18N = {
        ru: {
            submitSending: 'Отправляем…',
            submitDefault: 'Отправить',
            historyEmpty: 'История пока пуста',
            questionDefault: 'Вопрос',
            selectPlaceholder: 'Выберите вариант',
            checkboxPlaceholder: 'Подтверждаю',
            invalidField: 'Поле «{field}» заполнено некорректно.',
            answersTooLong: 'Суммарный объём ответов превышает {max} символов.',
            confirmField: 'Подтвердите поле: {field}',
            fillField: 'Заполните поле: {field}',
            invalidEmail: 'Проверьте email в поле «{field}».',
            invalidPhone: 'Проверьте телефон в поле «{field}».',
            invalidOption: 'Выберите корректный вариант в поле «{field}».',
            minLength: 'Поле «{field}» должно содержать минимум {min} символов.',
            maxLength: 'Поле «{field}» превышает лимит {max} символов.',
            loadConfigFailed: 'Не удалось загрузить конфигурацию формы',
            loadFormFailed: 'Не удалось загрузить форму',
            retrySubmit: 'Повторная отправка ({attempt}/{attempts})…',
            temporaryServerError: 'Временная ошибка сервера',
            createSessionFailed: 'Не удалось создать обращение',
            createdSuccess: 'Обращение создано! Номер: {ticketId}. Сохраните токен: {token}. Мы ответим в этом окне.',
            dialogCreatedStatus: 'Диалог {ticketId} создан {createdAt}. Обновляйте страницу по этому токену.',
            unknownError: 'Произошла ошибка. Попробуйте позже.'
        },
        en: {
            submitSending: 'Sending…',
            submitDefault: 'Submit',
            historyEmpty: 'No messages yet',
            questionDefault: 'Question',
            selectPlaceholder: 'Select an option',
            checkboxPlaceholder: 'I confirm',
            invalidField: 'Field “{field}” is filled incorrectly.',
            answersTooLong: 'Total answers size exceeds {max} characters.',
            confirmField: 'Please confirm field: {field}',
            fillField: 'Please fill field: {field}',
            invalidEmail: 'Check email in field “{field}”.',
            invalidPhone: 'Check phone in field “{field}”.',
            invalidOption: 'Choose a valid option in field “{field}”.',
            minLength: 'Field “{field}” must contain at least {min} characters.',
            maxLength: 'Field “{field}” exceeds {max} characters.',
            loadConfigFailed: 'Failed to load form configuration',
            loadFormFailed: 'Failed to load form',
            retrySubmit: 'Retrying submit ({attempt}/{attempts})…',
            temporaryServerError: 'Temporary server error',
            createSessionFailed: 'Failed to create request',
            createdSuccess: 'Request created! Ticket: {ticketId}. Save your token: {token}. We will answer in this window.',
            dialogCreatedStatus: 'Dialog {ticketId} created {createdAt}. Keep this token URL to continue.',
            unknownError: 'Something went wrong. Please try again later.'
        }
    };

    function resolveLocale(value) {
        const normalized = String(value || '').trim().toLowerCase();
        if (normalized === 'ru' || normalized === 'en') {
            return normalized;
        }
        if (normalized === 'auto') {
            const browserLocale = String(window.navigator?.language || '').toLowerCase();
            return browserLocale.startsWith('ru') ? 'ru' : 'en';
        }
        return 'ru';
    }

    function t(key, params = {}) {
        const catalog = I18N[resolveLocale(uiLocale)] || I18N.ru;
        const fallback = I18N.ru[key] || key;
        const template = catalog[key] || fallback;
        return Object.entries(params).reduce((acc, [paramKey, value]) => {
            return acc.replaceAll(`{${paramKey}}`, String(value ?? ''));
        }, template);
    }


    function generateRequestId() {
        if (window.crypto?.randomUUID) {
            return window.crypto.randomUUID();
        }
        return `req_${Date.now()}_${Math.random().toString(16).slice(2)}`;
    }

    function ensureRequestId() {
        if (!form.dataset.requestId) {
            form.dataset.requestId = generateRequestId();
        }
        return form.dataset.requestId;
    }

    function getClientFingerprint() {
        const key = 'publicFormFingerprint';
        try {
            const existing = window.localStorage?.getItem(key);
            if (existing && existing.trim()) {
                return existing.trim();
            }
            const created = `fp_${generateRequestId()}`;
            window.localStorage?.setItem(key, created);
            return created;
        } catch (e) {
            return `fp_${generateRequestId()}`;
        }
    }

    function setTokenInUrl(token) {
        if (!token) {
            return;
        }
        try {
            const url = new URL(window.location.href);
            url.searchParams.set('token', token);
            window.history.replaceState({}, '', url.toString());
        } catch (e) {
            console.warn('Failed to update token in URL', e);
        }
    }

    function startHistoryPolling(token) {
        if (historyPollTimer) {
            clearInterval(historyPollTimer);
            historyPollTimer = null;
        }
        if (!token || !sessionPollingEnabled) {
            return;
        }
        historyPollTimer = window.setInterval(() => {
            loadSession(token);
        }, sessionPollingIntervalMs);
    }

    function showError(message) {
        errorBox.textContent = message;
        errorBox.classList.remove('d-none');
        successBox.classList.add('d-none');
    }

    function showSuccess(message) {
        successBox.textContent = message;
        successBox.classList.remove('d-none');
        errorBox.classList.add('d-none');
    }

    function clearAlerts() {
        errorBox.classList.add('d-none');
        successBox.classList.add('d-none');
    }

    function setSubmitting(isSubmitting, label) {
        if (!submitButton) {
            return;
        }
        submitButton.disabled = isSubmitting;
        submitButton.textContent = label || (isSubmitting ? t('submitSending') : t('submitDefault'));
    }

    function renderMessages(messages) {
        historyContainer.innerHTML = '';
        if (!messages || messages.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'text-muted text-center mt-5';
            empty.textContent = t('historyEmpty');
            historyContainer.appendChild(empty);
            return;
        }
        messages.forEach(msg => {
            const wrapper = document.createElement('div');
            wrapper.className = `chat-message ${msg.sender === 'user' ? 'user' : 'support'}`;
            const text = document.createElement('div');
            text.textContent = msg.message || '';
            wrapper.appendChild(text);
            if (msg.timestamp) {
                const meta = document.createElement('div');
                meta.className = 'text-muted small mt-2';
                meta.textContent = msg.timestamp;
                wrapper.appendChild(meta);
            }
            historyContainer.appendChild(wrapper);
        });
        historyContainer.scrollTop = historyContainer.scrollHeight;
    }

    function normalizeQuestion(question, index) {
        return {
            id: question.id || `q${index + 1}`,
            text: question.text || t('questionDefault'),
            type: (question.type || 'text').toLowerCase(),
            required: Boolean(question.required),
            placeholder: question.placeholder || '',
            options: Array.isArray(question.options) ? question.options : [],
            rows: Number(question.rows || 3),
            minLength: Number(question.minLength || 0),
            maxLength: Number(question.maxLength || 500),
            helpText: question.helpText || question.help_text || '',
        };
    }

    function makeQuestionControl(question) {
        if (question.type === 'textarea') {
            const el = document.createElement('textarea');
            el.className = 'form-control';
            el.rows = Number.isFinite(question.rows) ? Math.max(2, Math.min(question.rows, 10)) : 3;
            return el;
        }
        if (question.type === 'select') {
            const el = document.createElement('select');
            el.className = 'form-select';
            const placeholder = document.createElement('option');
            placeholder.value = '';
            placeholder.textContent = question.placeholder || t('selectPlaceholder');
            el.appendChild(placeholder);
            question.options.forEach(optionValue => {
                const option = document.createElement('option');
                option.value = optionValue;
                option.textContent = optionValue;
                el.appendChild(option);
            });
            return el;
        }
        if (question.type === 'checkbox') {
            const input = document.createElement('input');
            input.className = 'form-check-input';
            input.type = 'checkbox';
            return input;
        }
        const input = document.createElement('input');
        input.className = 'form-control';
        input.type = ['email', 'phone'].includes(question.type) ? (question.type === 'phone' ? 'tel' : 'email') : 'text';
        return input;
    }

    function renderQuestions(questions) {
        activeQuestions = questions
            .slice()
            .sort((a, b) => Number(a.order || 0) - Number(b.order || 0))
            .map(normalizeQuestion);
        dynamicQuestionsContainer.innerHTML = '';

        activeQuestions.forEach(question => {
            const wrapper = document.createElement('div');
            wrapper.className = 'mb-3';

            const label = document.createElement('label');
            label.className = 'form-label';
            label.textContent = question.text;

            const control = makeQuestionControl(question);
            control.name = `answer_${question.id}`;
            control.dataset.questionId = question.id;

            if (question.type === 'checkbox') {
                const checkboxWrap = document.createElement('div');
                checkboxWrap.className = 'form-check';
                checkboxWrap.appendChild(control);
                const checkboxLabel = document.createElement('label');
                checkboxLabel.className = 'form-check-label';
                checkboxLabel.textContent = question.placeholder || t('checkboxPlaceholder');
                checkboxWrap.appendChild(checkboxLabel);
                wrapper.appendChild(label);
                wrapper.appendChild(checkboxWrap);
            } else {
                wrapper.appendChild(label);
                if (question.placeholder && control.tagName !== 'SELECT') {
                    control.placeholder = question.placeholder;
                }
                if (question.maxLength > 0 && control.tagName !== 'SELECT') {
                    control.maxLength = question.maxLength;
                }
                if (question.minLength > 0) {
                    control.minLength = question.minLength;
                }
                wrapper.appendChild(control);
            }

            if (question.required) {
                control.required = true;
            }

            const invalid = document.createElement('div');
            invalid.className = 'invalid-feedback';
            invalid.textContent = t('invalidField', { field: question.text });
            wrapper.appendChild(invalid);

            if (question.helpText) {
                const hint = document.createElement('div');
                hint.className = 'form-text';
                hint.textContent = question.helpText;
                wrapper.appendChild(hint);
            }

            dynamicQuestionsContainer.appendChild(wrapper);
        });
    }

    function buildAnswers(formData) {
        const answers = {};
        activeQuestions.forEach(question => {
            if (question.type === 'checkbox') {
                answers[question.id] = formData.get(`answer_${question.id}`) ? 'true' : 'false';
                return;
            }
            const raw = formData.get(`answer_${question.id}`);
            const value = typeof raw === 'string' ? raw.trim() : '';
            if (value) {
                answers[question.id] = value;
            }
        });
        return answers;
    }

    function validateAnswers(answers) {
        const answersPayloadLength = Object.values(answers || {})
            .map((value) => (typeof value === 'string' ? value.length : 0))
            .reduce((sum, length) => sum + length, 0);
        if (answersPayloadLength > answersTotalMaxLength) {
            return t('answersTooLong', { max: answersTotalMaxLength });
        }
        for (const question of activeQuestions) {
            const value = answers[question.id] || '';
            if (question.type === 'checkbox') {
                const isChecked = value === 'true' || value === '1';
                if (question.required && !isChecked) {
                    return t('confirmField', { field: question.text });
                }
                continue;
            }
            if (question.required && !value) {
                return t('fillField', { field: question.text });
            }
            if (!value) {
                continue;
            }
            if (question.type === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
                return t('invalidEmail', { field: question.text });
            }
            if (question.type === 'phone' && !/^[+]?[-()\s0-9]{6,20}$/.test(value)) {
                return t('invalidPhone', { field: question.text });
            }
            if (question.type === 'select' && question.options.length > 0 && !question.options.includes(value)) {
                return t('invalidOption', { field: question.text });
            }
            if (question.minLength > 0 && value.length < question.minLength) {
                return t('minLength', { field: question.text, min: question.minLength });
            }
            if (question.maxLength > 0 && value.length > question.maxLength) {
                return t('maxLength', { field: question.text, max: question.maxLength });
            }
        }
        return null;
    }

    function renderCaptcha() {
        const container = document.querySelector('[data-role="captcha-container"]');
        if (!container) {
            return;
        }
        container.classList.toggle('d-none', !captchaEnabled);
        const input = container.querySelector('input[name="captchaToken"]');
        if (input) {
            input.required = captchaEnabled;
        }
    }

    async function loadConfig() {
        try {
            const response = await fetch(`${apiBase}/config`);
            const payload = await response.json();
            if (!response.ok || !payload.success) {
                throw new Error(payload.error || t('loadConfigFailed'));
            }
            captchaEnabled = Boolean(payload.captchaEnabled);
            const configuredAnswersLimit = Number.parseInt(payload.answersTotalMaxLength, 10);
            answersTotalMaxLength = Number.isFinite(configuredAnswersLimit)
                ? Math.min(50000, Math.max(200, configuredAnswersLimit))
                : 6000;
            sessionPollingEnabled = payload.sessionPollingEnabled !== false;
            uiLocale = payload.uiLocale || "auto";
            const configuredPollingInterval = Number.parseInt(payload.sessionPollingIntervalSeconds, 10);
            const safePollingIntervalSeconds = Number.isFinite(configuredPollingInterval)
                ? Math.min(300, Math.max(5, configuredPollingInterval))
                : 15;
            sessionPollingIntervalMs = safePollingIntervalSeconds * 1000;
            renderQuestions(payload.questions || []);
            renderCaptcha();
            if (currentToken) {
                startHistoryPolling(currentToken);
            }
        } catch (error) {
            showError(error.message || t('loadFormFailed'));
        }
    }

    async function loadSession(token) {
        if (!token) {
            return;
        }
        try {
            const response = await fetch(`${apiBase}/sessions/${encodeURIComponent(token)}`);
            if (!response.ok) {
                return;
            }
            const payload = await response.json();
            if (!payload.success) {
                return;
            }
            const nextToken = payload.session?.token;
            if (nextToken && nextToken !== currentToken) {
                currentToken = nextToken;
                setTokenInUrl(nextToken);
                startHistoryPolling(nextToken);
            }
            statusLabel.textContent = t('dialogCreatedStatus', { ticketId: payload.session.ticketId, createdAt: payload.session.createdAt || '' });
            renderMessages(payload.messages || []);
        } catch (e) {
            console.warn('Failed to load session', e);
        }
    }

    async function submitWithRetry(payload, attempts = 2) {
        let lastError;
        for (let attempt = 1; attempt <= attempts; attempt++) {
            setSubmitting(true, attempt > 1 ? t('retrySubmit', { attempt, attempts }) : t('submitSending'));
            try {
                const response = await fetch(`${apiBase}/sessions`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Public-Form-Fingerprint': getClientFingerprint()
                    },
                    body: JSON.stringify(payload)
                });
                const data = await response.json().catch(() => ({}));
                if (!response.ok || !data.success) {
                    if (response.status >= 500 && attempt < attempts) {
                        lastError = new Error(data.error || t('temporaryServerError'));
                        continue;
                    }
                    throw new Error(data.error || t('createSessionFailed'));
                }
                return data;
            } catch (error) {
                lastError = error;
                if (attempt >= attempts) {
                    throw error;
                }
            }
        }
        throw lastError || new Error(t('createSessionFailed'));
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        form.classList.add('was-validated');
        if (!form.checkValidity()) {
            return;
        }
        clearAlerts();
        const formData = new FormData(form);
        const answers = buildAnswers(formData);
        const answerError = validateAnswers(answers);
        if (answerError) {
            showError(answerError);
            return;
        }

        const payload = {
            clientName: (formData.get('clientName') || '').toString().trim(),
            clientContact: (formData.get('clientContact') || '').toString().trim(),
            message: (formData.get('message') || '').toString().trim(),
            answers,
            captchaToken: (formData.get('captchaToken') || '').toString().trim(),
            requestId: ensureRequestId(),
        };

        try {
            const data = await submitWithRetry(payload, 2);
            showSuccess(t('createdSuccess', { ticketId: data.ticketId, token: data.token }));
            statusLabel.textContent = t('dialogCreatedStatus', { ticketId: data.ticketId, createdAt: data.createdAt || '' });
            currentToken = data.token;
            setTokenInUrl(data.token);
            startHistoryPolling(data.token);
            await loadSession(data.token);
            form.dataset.requestId = '';
        } catch (e) {
            showError(e?.message || t('unknownError'));
        } finally {
            setSubmitting(false, t('submitDefault'));
        }
    });

    loadConfig();
    ensureRequestId();
    try {
        const urlToken = new URL(window.location.href).searchParams.get('token');
        if (urlToken) {
            currentToken = urlToken;
        }
    } catch (e) {
        console.warn('Failed to read token from URL', e);
    }
    if (currentToken) {
        loadSession(currentToken);
        startHistoryPolling(currentToken);
    }
})();
