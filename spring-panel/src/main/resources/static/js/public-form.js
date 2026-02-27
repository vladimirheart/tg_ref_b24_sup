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
        submitButton.textContent = label || (isSubmitting ? 'Отправляем…' : 'Отправить');
    }

    function renderMessages(messages) {
        historyContainer.innerHTML = '';
        if (!messages || messages.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'text-muted text-center mt-5';
            empty.textContent = 'История пока пуста';
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
            text: question.text || 'Вопрос',
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
            placeholder.textContent = question.placeholder || 'Выберите вариант';
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
                checkboxLabel.textContent = question.placeholder || 'Подтверждаю';
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
            invalid.textContent = `Поле «${question.text}» заполнено некорректно.`;
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
        for (const question of activeQuestions) {
            const value = answers[question.id] || '';
            if (question.type === 'checkbox') {
                const isChecked = value === 'true' || value === '1';
                if (question.required && !isChecked) {
                    return `Подтвердите поле: ${question.text}`;
                }
                continue;
            }
            if (question.required && !value) {
                return `Заполните поле: ${question.text}`;
            }
            if (!value) {
                continue;
            }
            if (question.type === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
                return `Проверьте email в поле «${question.text}».`;
            }
            if (question.type === 'phone' && !/^[+]?[-()\s0-9]{6,20}$/.test(value)) {
                return `Проверьте телефон в поле «${question.text}».`;
            }
            if (question.type === 'select' && question.options.length > 0 && !question.options.includes(value)) {
                return `Выберите корректный вариант в поле «${question.text}».`;
            }
            if (question.minLength > 0 && value.length < question.minLength) {
                return `Поле «${question.text}» должно содержать минимум ${question.minLength} символов.`;
            }
            if (question.maxLength > 0 && value.length > question.maxLength) {
                return `Поле «${question.text}» превышает лимит ${question.maxLength} символов.`;
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
                throw new Error(payload.error || 'Не удалось загрузить конфигурацию формы');
            }
            captchaEnabled = Boolean(payload.captchaEnabled);
            renderQuestions(payload.questions || []);
            renderCaptcha();
        } catch (error) {
            showError(error.message || 'Не удалось загрузить форму');
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
            statusLabel.textContent = `Диалог ${payload.session.ticketId} создан ${payload.session.createdAt || ''}`;
            renderMessages(payload.messages || []);
        } catch (e) {
            console.warn('Failed to load session', e);
        }
    }

    async function submitWithRetry(payload, attempts = 2) {
        let lastError;
        for (let attempt = 1; attempt <= attempts; attempt++) {
            setSubmitting(true, attempt > 1 ? `Повторная отправка (${attempt}/${attempts})…` : 'Отправляем…');
            try {
                const response = await fetch(`${apiBase}/sessions`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(payload)
                });
                const data = await response.json().catch(() => ({}));
                if (!response.ok || !data.success) {
                    if (response.status >= 500 && attempt < attempts) {
                        lastError = new Error(data.error || 'Временная ошибка сервера');
                        continue;
                    }
                    throw new Error(data.error || 'Не удалось создать обращение');
                }
                return data;
            } catch (error) {
                lastError = error;
                if (attempt >= attempts) {
                    throw error;
                }
            }
        }
        throw lastError || new Error('Не удалось создать обращение');
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
        };

        try {
            const data = await submitWithRetry(payload, 2);
            showSuccess(`Обращение создано! Номер: ${data.ticketId}. Сохраните токен: ${data.token}. Мы ответим в этом окне.`);
            statusLabel.textContent = `Диалог ${data.ticketId} создан ${data.createdAt || ''}. Обновляйте страницу по этому токену.`;
            await loadSession(data.token);
        } catch (e) {
            showError(e?.message || 'Произошла ошибка. Попробуйте позже.');
        } finally {
            setSubmitting(false, 'Отправить');
        }
    });

    loadConfig();
    if (initial.initialToken) {
        loadSession(initial.initialToken);
    }
})();
