(function () {
  const root = document.getElementById('public-form-root');
  if (!root) {
    return;
  }

  const initial = window.PUBLIC_FORM_INITIAL || {};
  const rawChannelRef = root.dataset.channelRef
    || initial.channelRef
    || root.dataset.channelId
    || (initial.channelId != null ? String(initial.channelId) : '');
  const channelRef = (rawChannelRef || '').trim();
  if (!channelRef) {
    console.error('Не указан идентификатор канала для веб-формы.');
    return;
  }

  const state = {
    channelRef,
    channelId: Number(root.dataset.channelId || initial.channelId || 0) || null,
    channelName: root.dataset.channelName || initial.channelName || '',
    initialToken: root.dataset.initialToken || initial.initialToken || '',
    config: null,
    answers: {},
    answersByField: {},
    inputs: new Map(),
    currentToken: null,
    pollingTimer: null,
    isSubmitting: false,
  };

  const statusBox = document.createElement('div');
  statusBox.className = 'alert d-none';
  root.appendChild(statusBox);

  const formCard = document.createElement('div');
  formCard.className = 'form-card';
  root.appendChild(formCard);

  const chatCard = document.createElement('div');
  chatCard.className = 'chat-card d-none';
  root.appendChild(chatCard);

  let chatHistoryEl = null;
  let chatStatusEl = null;
  let chatHeaderInfoEl = null;
  let chatFormEl = null;
  let chatInputEl = null;
  let chatErrorEl = null;

  fetchConfig();

  async function fetchConfig() {
    try {
      showStatus('Загружаем форму...', false);
      const response = await fetch(`/api/public/forms/${encodeURIComponent(state.channelRef)}/config`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      if (data && data.success === false) {
        throw new Error(data.error || 'Не удалось получить конфигурацию');
      }
      state.config = data;
      state.answers = {};
      state.answersByField = {};
      renderForm();
      clearStatus();
      if (state.initialToken) {
        await loadSession(state.initialToken);
      }
    } catch (error) {
      showStatus(error && error.message ? error.message : 'Не удалось загрузить форму', true);
    }
  }

  function showStatus(message, isError) {
    if (!message) {
      clearStatus();
      return;
    }
    statusBox.textContent = message;
    statusBox.classList.remove('d-none', 'alert-success', 'alert-danger');
    statusBox.classList.add(isError ? 'alert-danger' : 'alert-success');
  }

  function clearStatus() {
    statusBox.textContent = '';
    statusBox.classList.add('d-none');
  }

  function renderForm() {
    formCard.innerHTML = '';
    state.inputs.clear();
    state.answers = {};
    state.answersByField = {};

    if (!state.config || !Array.isArray(state.config.questions)) {
      const fallback = document.createElement('p');
      fallback.className = 'text-muted mb-0';
      fallback.textContent = 'Форма недоступна.';
      formCard.appendChild(fallback);
      return;
    }

    const title = document.createElement('h4');
    title.className = 'mb-3';
    title.textContent = 'Шаг 1. Ответьте на вопросы';
    formCard.appendChild(title);

    const sortedQuestions = state.config.questions
      .slice()
      .sort((a, b) => (Number(a.order) || 0) - (Number(b.order) || 0));

    for (const question of sortedQuestions) {
      const group = document.createElement('div');
      group.className = 'question-group';
      const label = document.createElement('label');
      label.className = 'form-label';
      label.textContent = question.text || 'Вопрос';
      group.appendChild(label);

      if (question.type === 'preset') {
        const select = document.createElement('select');
        select.className = 'form-select';
        select.dataset.questionId = question.id;
        if (question.preset && question.preset.field) {
          select.dataset.presetField = question.preset.field;
        }
        select.addEventListener('change', () => {
          const value = select.value.trim();
          if (value) {
            state.answers[question.id] = value;
          } else {
            delete state.answers[question.id];
          }
          const field = select.dataset.presetField;
          if (field) {
            if (value) {
              state.answersByField[field] = value;
            } else {
              delete state.answersByField[field];
            }
          }
          updatePresetSelects();
        });
        group.appendChild(select);
        state.inputs.set(question.id, select);
      } else {
        const textarea = document.createElement('textarea');
        textarea.className = 'form-control';
        textarea.rows = 2;
        textarea.dataset.questionId = question.id;
        textarea.placeholder = 'Введите ответ';
        textarea.addEventListener('input', () => {
          const value = textarea.value.trim();
          if (value) {
            state.answers[question.id] = value;
          } else {
            delete state.answers[question.id];
          }
        });
        group.appendChild(textarea);
        state.inputs.set(question.id, textarea);
      }

      formCard.appendChild(group);
    }

    const separator = document.createElement('hr');
    separator.className = 'my-4';
    formCard.appendChild(separator);

    const contactRow = document.createElement('div');
    contactRow.className = 'row g-3';

    const nameCol = document.createElement('div');
    nameCol.className = 'col-12 col-md-6';
    const nameLabel = document.createElement('label');
    nameLabel.className = 'form-label';
    nameLabel.textContent = 'Ваше имя';
    const nameInput = document.createElement('input');
    nameInput.type = 'text';
    nameInput.className = 'form-control';
    nameInput.placeholder = 'Например, Иван Иванов';
    nameInput.id = 'public-form-client-name';
    nameCol.appendChild(nameLabel);
    nameCol.appendChild(nameInput);

    const contactCol = document.createElement('div');
    contactCol.className = 'col-12 col-md-6';
    const contactLabel = document.createElement('label');
    contactLabel.className = 'form-label';
    contactLabel.textContent = 'Контакт для связи (телефон или почта)';
    const contactInput = document.createElement('input');
    contactInput.type = 'text';
    contactInput.className = 'form-control';
    contactInput.placeholder = '+7 (999) 000-00-00';
    contactInput.id = 'public-form-client-contact';
    contactCol.appendChild(contactLabel);
    contactCol.appendChild(contactInput);

    contactRow.appendChild(nameCol);
    contactRow.appendChild(contactCol);
    formCard.appendChild(contactRow);

    const messageGroup = document.createElement('div');
    messageGroup.className = 'question-group mt-3';
    const messageLabel = document.createElement('label');
    messageLabel.className = 'form-label';
    messageLabel.textContent = 'Шаг 2. Опишите проблему';
    const messageInput = document.createElement('textarea');
    messageInput.className = 'form-control';
    messageInput.rows = 4;
    messageInput.required = true;
    messageInput.placeholder = 'Расскажите, что произошло';
    messageInput.id = 'public-form-problem';
    messageGroup.appendChild(messageLabel);
    messageGroup.appendChild(messageInput);
    formCard.appendChild(messageGroup);

    const submitBtn = document.createElement('button');
    submitBtn.type = 'button';
    submitBtn.className = 'btn btn-primary';
    submitBtn.textContent = 'Отправить обращение';
    submitBtn.addEventListener('click', async () => {
      if (state.isSubmitting) {
        return;
      }
      const problemText = messageInput.value.trim();
      if (!problemText) {
        showStatus('Опишите проблему перед отправкой.', true);
        messageInput.focus();
        return;
      }
      const answersPayload = collectAnswers();
      const payload = {
        message: problemText,
        answers: answersPayload,
        client_name: nameInput.value.trim(),
        contact: contactInput.value.trim(),
      };
      try {
        state.isSubmitting = true;
        submitBtn.disabled = true;
        showStatus('Отправляем обращение...', false);
        const response = await fetch(`/api/public/forms/${encodeURIComponent(state.channelRef)}/sessions`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await response.json();
        if (!response.ok || (data && data.success === false)) {
          throw new Error((data && data.error) || `HTTP ${response.status}`);
        }
        clearStatus();
        if (data && data.token) {
          switchToChat();
          await loadSession(data.token);
          if (window.history && window.history.replaceState) {
            const url = new URL(window.location.href);
            url.searchParams.set('dialog', data.token);
            window.history.replaceState(null, '', url.toString());
          }
        }
      } catch (error) {
        showStatus(error && error.message ? error.message : 'Не удалось отправить обращение', true);
      } finally {
        state.isSubmitting = false;
        submitBtn.disabled = false;
      }
    });

    const footer = document.createElement('div');
    footer.className = 'mt-4 d-flex justify-content-end';
    footer.appendChild(submitBtn);
    formCard.appendChild(footer);

    updatePresetSelects();
  }

  function collectAnswers() {
    const payload = {};
    for (const [questionId, input] of state.inputs.entries()) {
      if (!questionId) {
        continue;
      }
      let value = '';
      if (input instanceof HTMLSelectElement) {
        value = input.value.trim();
      } else if (input instanceof HTMLTextAreaElement || input instanceof HTMLInputElement) {
        value = input.value.trim();
      }
      if (value) {
        payload[questionId] = value;
      }
    }
    return payload;
  }

  function updatePresetSelects() {
    if (!state.config) {
      return;
    }
    const questions = state.config.questions || [];
    for (const question of questions) {
      if (question.type !== 'preset') {
        continue;
      }
      const select = state.inputs.get(question.id);
      if (!(select instanceof HTMLSelectElement)) {
        continue;
      }
      const previousValue = state.answers[question.id] || '';
      const options = buildPresetOptions(question);
      populateSelect(select, options, question, previousValue);
    }
  }

  function buildPresetOptions(question) {
    const rawOptions = Array.isArray(question.options) ? question.options : [];
    const excluded = new Set(
      Array.isArray(question.excluded_options)
        ? question.excluded_options.map((value) => String(value))
        : []
    );
    const available = [];
    for (const option of rawOptions) {
      const value = String(option);
      if (!value || excluded.has(value)) {
        continue;
      }
      const deps = question.option_dependencies && question.option_dependencies[value];
      if (dependenciesSatisfied(deps)) {
        available.push(value);
      }
    }
    return available;
  }

  function dependenciesSatisfied(deps) {
    if (!deps || typeof deps !== 'object') {
      return true;
    }
    const checkField = (field, expected) => {
      if (!expected || !expected.length) {
        return true;
      }
      const answer = state.answersByField[field];
      if (!answer) {
        return false;
      }
      return expected.includes(answer);
    };

    if (Array.isArray(deps.business) && !checkField('business', deps.business)) {
      return false;
    }
    if (Array.isArray(deps.location_type) && !checkField('location_type', deps.location_type)) {
      return false;
    }
    if (Array.isArray(deps.city) && !checkField('city', deps.city)) {
      return false;
    }

    if (Array.isArray(deps.paths) && deps.paths.length) {
      const hasMatch = deps.paths.some((path) => {
        if (!path || typeof path !== 'object') {
          return false;
        }
        const businessOk = !path.business || state.answersByField.business === path.business;
        const typeOk = !path.location_type || state.answersByField.location_type === path.location_type;
        const cityOk = !path.city || state.answersByField.city === path.city;
        return businessOk && typeOk && cityOk;
      });
      if (!hasMatch) {
        return false;
      }
    }
    return true;
  }

  function hasPendingDependencies(question) {
    if (!question || !question.option_dependencies) {
      return false;
    }
    const dependencies = Object.values(question.option_dependencies);
    return dependencies.some((deps) => {
      if (!deps || typeof deps !== 'object') {
        return false;
      }
      const needsBusiness = Array.isArray(deps.business) && deps.business.length;
      const needsType = Array.isArray(deps.location_type) && deps.location_type.length;
      const needsCity = Array.isArray(deps.city) && deps.city.length;
      const needsPath = Array.isArray(deps.paths) && deps.paths.length;
      if (!needsBusiness && !needsType && !needsCity && !needsPath) {
        return false;
      }
      if (needsBusiness && !state.answersByField.business) {
        return true;
      }
      if (needsType && !state.answersByField.location_type) {
        return true;
      }
      if (needsCity && !state.answersByField.city) {
        return true;
      }
      if (needsPath) {
        return !deps.paths.some((path) => {
          if (!path || typeof path !== 'object') {
            return false;
          }
          if (path.business && state.answersByField.business !== path.business) {
            return false;
          }
          if (path.location_type && state.answersByField.location_type !== path.location_type) {
            return false;
          }
          if (path.city && state.answersByField.city !== path.city) {
            return false;
          }
          return true;
        });
      }
      return false;
    });
  }

  function populateSelect(select, options, question, previousValue) {
    while (select.firstChild) {
      select.removeChild(select.firstChild);
    }

    const placeholder = document.createElement('option');
    placeholder.value = '';
    if (options.length) {
      placeholder.textContent = 'Выберите вариант';
    } else if (hasPendingDependencies(question)) {
      placeholder.textContent = 'Сначала заполните предыдущие поля';
    } else {
      placeholder.textContent = 'Доступных вариантов нет';
    }
    select.appendChild(placeholder);

    if (!options.length) {
      select.disabled = true;
      if (question && question.preset && question.preset.field) {
        delete state.answersByField[question.preset.field];
      }
      delete state.answers[question.id];
      select.value = '';
      return;
    }

    select.disabled = false;
    for (const value of options) {
      const option = document.createElement('option');
      option.value = value;
      option.textContent = value;
      select.appendChild(option);
    }

    if (previousValue && options.includes(previousValue)) {
      select.value = previousValue;
    } else {
      select.value = '';
      if (question && question.preset && question.preset.field) {
        delete state.answersByField[question.preset.field];
      }
      delete state.answers[question.id];
    }
  }

  function switchToChat() {
    formCard.classList.add('d-none');
    chatCard.classList.remove('d-none');
    if (!chatCard.innerHTML) {
      renderChatSkeleton();
    }
  }

  function renderChatSkeleton() {
    chatCard.innerHTML = '';
    const header = document.createElement('div');
    header.className = 'px-4 py-3 border-bottom';

    const headerRow = document.createElement('div');
    headerRow.className = 'd-flex flex-column flex-md-row align-items-md-center justify-content-between gap-2';

    const titleWrap = document.createElement('div');
    const title = document.createElement('h5');
    title.className = 'mb-0';
    title.textContent = 'Диалог';
    chatStatusEl = document.createElement('small');
    chatStatusEl.className = 'text-muted d-block';
    titleWrap.appendChild(title);
    titleWrap.appendChild(chatStatusEl);

    chatHeaderInfoEl = document.createElement('div');
    chatHeaderInfoEl.className = 'text-muted small';

    headerRow.appendChild(titleWrap);
    headerRow.appendChild(chatHeaderInfoEl);
    header.appendChild(headerRow);
    chatCard.appendChild(header);

    chatHistoryEl = document.createElement('div');
    chatHistoryEl.className = 'chat-history';
    chatCard.appendChild(chatHistoryEl);

    const footer = document.createElement('div');
    footer.className = 'chat-footer';
    chatFormEl = document.createElement('form');
    chatFormEl.className = 'd-flex flex-column gap-2';
    chatInputEl = document.createElement('textarea');
    chatInputEl.className = 'form-control';
    chatInputEl.rows = 3;
    chatInputEl.placeholder = 'Введите сообщение для поддержки';
    chatInputEl.required = true;

    chatErrorEl = document.createElement('div');
    chatErrorEl.className = 'text-danger small';
    chatErrorEl.hidden = true;

    const actionsRow = document.createElement('div');
    actionsRow.className = 'd-flex justify-content-between align-items-center gap-2';
    actionsRow.appendChild(chatErrorEl);

    const sendBtn = document.createElement('button');
    sendBtn.type = 'submit';
    sendBtn.className = 'btn btn-primary ms-auto';
    sendBtn.textContent = 'Отправить';

    actionsRow.appendChild(sendBtn);
    chatFormEl.appendChild(chatInputEl);
    chatFormEl.appendChild(actionsRow);
    footer.appendChild(chatFormEl);
    chatCard.appendChild(footer);

    chatFormEl.addEventListener('submit', async (event) => {
      event.preventDefault();
      const message = chatInputEl.value.trim();
      if (!message) {
        chatErrorEl.textContent = 'Введите сообщение';
        chatErrorEl.hidden = false;
        return;
      }
      chatErrorEl.hidden = true;
      chatFormEl.classList.add('opacity-75');
      chatInputEl.disabled = true;
      sendBtn.disabled = true;
      try {
        const response = await fetch(`/api/public/forms/${encodeURIComponent(state.channelRef)}/sessions/${encodeURIComponent(state.currentToken)}/messages`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message }),
        });
        const data = await response.json();
        if (!response.ok || (data && data.success === false)) {
          throw new Error((data && data.error) || `HTTP ${response.status}`);
        }
        chatInputEl.value = '';
        await refreshSession();
      } catch (error) {
        chatErrorEl.textContent = error && error.message ? error.message : 'Не удалось отправить сообщение';
        chatErrorEl.hidden = false;
      } finally {
        chatFormEl.classList.remove('opacity-75');
        chatInputEl.disabled = false;
        sendBtn.disabled = false;
        chatInputEl.focus();
      }
    });
  }

  async function loadSession(token) {
    try {
      const response = await fetch(`/api/public/forms/${encodeURIComponent(state.channelRef)}/sessions/${encodeURIComponent(token)}`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      if (data && data.success === false) {
        throw new Error(data.error || 'Не удалось получить диалог');
      }
      const session = data.session;
      if (!session) {
        throw new Error('Диалог не найден');
      }
      state.currentToken = session.token;
      switchToChat();
      renderChatSession(session);
      startPolling();
      if (window.history && window.history.replaceState) {
        const url = new URL(window.location.href);
        url.searchParams.set('dialog', session.token);
        window.history.replaceState(null, '', url.toString());
      }
    } catch (error) {
      showStatus(error && error.message ? error.message : 'Не удалось загрузить диалог', true);
    }
  }

  async function refreshSession() {
    if (!state.currentToken) {
      return;
    }
    try {
      const response = await fetch(`/api/public/forms/${encodeURIComponent(state.channelRef)}/sessions/${encodeURIComponent(state.currentToken)}`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      if (data && data.success === false) {
        throw new Error(data.error || 'Не удалось обновить диалог');
      }
      if (data && data.session) {
        renderChatSession(data.session);
      }
    } catch (error) {
      console.warn('Не удалось обновить диалог:', error);
    }
  }

  function startPolling() {
    if (state.pollingTimer) {
      clearInterval(state.pollingTimer);
    }
    state.pollingTimer = window.setInterval(refreshSession, 10000);
  }

  function renderChatSession(session) {
    if (!chatHistoryEl) {
      renderChatSkeleton();
    }
    const title = chatCard.querySelector('h5');
    if (title) {
      title.textContent = `Диалог #${session.ticket_id || ''}`;
    }
    if (chatStatusEl) {
      chatStatusEl.textContent = `Статус: ${translateStatus(session.status)}`;
    }
    if (chatHeaderInfoEl) {
      const updated = session.last_active_at || (session.messages && session.messages.length ? session.messages[session.messages.length - 1].timestamp : null);
      chatHeaderInfoEl.textContent = updated ? `Обновлено: ${formatDateTime(updated)}` : '';
    }

    if (!Array.isArray(session.messages) || !session.messages.length) {
      chatHistoryEl.innerHTML = '';
      const empty = document.createElement('div');
      empty.className = 'empty-state';
      empty.textContent = 'Сообщения ещё не отправлялись.';
      chatHistoryEl.appendChild(empty);
      return;
    }

    chatHistoryEl.innerHTML = '';
    for (const message of session.messages) {
      const bubble = document.createElement('div');
      const sender = (message.sender || '').toLowerCase();
      bubble.className = `chat-message ${sender === 'user' ? 'user' : 'support'}`;
      const text = document.createElement('div');
      text.className = 'fw-medium';
      text.textContent = message.message || '';
      bubble.appendChild(text);
      const meta = document.createElement('div');
      meta.className = 'text-muted small mt-1';
      meta.textContent = formatDateTime(message.timestamp);
      bubble.appendChild(meta);
      chatHistoryEl.appendChild(bubble);
    }
    chatHistoryEl.scrollTop = chatHistoryEl.scrollHeight;
  }

  function translateStatus(status) {
    const normalized = (status || '').toLowerCase();
    switch (normalized) {
      case 'resolved':
        return 'Закрыт';
      case 'pending':
      case 'open':
        return 'Открыт';
      default:
        return status || '—';
    }
  }

  function formatDateTime(value) {
    if (!value) {
      return '';
    }
    try {
      const date = new Date(value);
      if (!Number.isNaN(date.getTime())) {
        return date.toLocaleString('ru-RU', {
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        });
      }
    } catch (error) {
      return String(value);
    }
    return String(value);
  }
})();
