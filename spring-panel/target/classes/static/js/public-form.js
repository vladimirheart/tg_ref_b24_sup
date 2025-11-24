(() => {
    const initial = window.PUBLIC_FORM_INITIAL || {};
    const channelRef = initial.channelRef;
    const form = document.getElementById('publicForm');
    const historyContainer = document.querySelector('[data-role="history"]');
    const statusLabel = document.querySelector('[data-role="status"]');
    const errorBox = document.querySelector('[data-role="error"]');
    const successBox = document.querySelector('[data-role="success"]');

    if (!channelRef || !form || !historyContainer) {
        return;
    }

    const apiBase = `/api/public/forms/${encodeURIComponent(channelRef)}`;

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

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        form.classList.add('was-validated');
        if (!form.checkValidity()) {
            return;
        }
        clearAlerts();
        const formData = new FormData(form);
        const payload = {
            clientName: formData.get('clientName') || '',
            clientContact: formData.get('clientContact') || '',
            message: formData.get('message') || ''
        };
        try {
            const response = await fetch(`${apiBase}/sessions`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });
            const data = await response.json();
            if (!response.ok || !data.success) {
                showError(data.error || 'Не удалось создать обращение');
                return;
            }
            showSuccess('Обращение создано! Сохраните токен: ' + data.token);
            statusLabel.textContent = `Диалог ${data.ticketId} создан ${data.createdAt || ''}`;
            renderMessages([{ sender: 'user', message: payload.message, timestamp: data.createdAt }]);
        } catch (e) {
            showError('Произошла ошибка. Попробуйте позже.');
        }
    });

    if (initial.initialToken) {
        loadSession(initial.initialToken);
    }
})();