(function () {
  if (window.SettingsPartnerContactsRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const state = {
      drafts: [],
      draftCounter: 0,
      edits: new Map(),
      phoneCounter: 0,
      emailCounter: 0,
      personCounter: 0,
      detailsState: new Map(),
      editorState: null,
    };

    const elements = {
      sections: new Map(),
      editorModalEl: document.getElementById('partnerContactEditorModal'),
    };

    document.querySelectorAll('[data-partner-contacts-section]').forEach((section) => {
      if (!(section instanceof HTMLElement)) return;
      const scope = section.dataset.partnerContactsSection;
      if (!scope) return;
      const container = section.querySelector('[data-partner-contacts-list]');
      const emptyPlaceholder = section.querySelector('[data-partner-contacts-empty]');
      elements.sections.set(scope, {
        section,
        container,
        emptyPlaceholder,
      });
    });

    const editor = {
      body: elements.editorModalEl
        ? elements.editorModalEl.querySelector('[data-partner-contact-editor-body]')
        : null,
      title: elements.editorModalEl
        ? elements.editorModalEl.querySelector('[data-partner-contact-editor-title]')
        : null,
      actions: elements.editorModalEl
        ? elements.editorModalEl.querySelector('[data-partner-contact-editor-actions]')
        : null,
    };

    function getParameterData() {
      const value = typeof options.getParameterData === 'function' ? options.getParameterData() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getParameterStates() {
      const value = typeof options.getParameterStates === 'function' ? options.getParameterStates() : null;
      return Array.isArray(value) && value.length ? value : ['Активен'];
    }

    function getPartnerContactTypes() {
      const value = typeof options.getPartnerContactTypes === 'function' ? options.getPartnerContactTypes() : null;
      return value && typeof value === 'object' ? value : { partner: 'Партнёр', ka: 'КА' };
    }

    function getPartnerContactPhoneTypes() {
      const value = typeof options.getPartnerContactPhoneTypes === 'function' ? options.getPartnerContactPhoneTypes() : null;
      return value && typeof value === 'object'
        ? value
        : { work: 'Рабочий', personal: 'Личный', fax: 'Факс', support: 'Саппорт' };
    }

    function getPartnerContactEmailTypes() {
      const value = typeof options.getPartnerContactEmailTypes === 'function' ? options.getPartnerContactEmailTypes() : null;
      return value && typeof value === 'object'
        ? value
        : { work: 'Рабочий', personal: 'Личный', common: 'Общий', support: 'Саппорт' };
    }

    function getPartnerContactStateClassMap() {
      const value = typeof options.getPartnerContactStateClassMap === 'function'
        ? options.getPartnerContactStateClassMap()
        : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getPartnerContactStateClasses() {
      return Object.values(getPartnerContactStateClassMap());
    }

    function escapeHtml(value) {
      if (typeof options.escapeHtml === 'function') {
        return options.escapeHtml(value);
      }
      return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
    }

    function popup(message) {
      if (typeof options.showPopup === 'function') {
        options.showPopup(message);
        return;
      }
      console.log(message);
    }

    function syncParameterData(data) {
      if (typeof options.syncParameterData === 'function') {
        options.syncParameterData(data);
      }
    }

    function rerenderParameters() {
      if (typeof options.renderParameters === 'function') {
        options.renderParameters({ forceBodies: true });
      }
    }

    function requestClose(source) {
      if (typeof options.requestSettingsModalClose === 'function') {
        options.requestSettingsModalClose(source);
      }
    }

    function deleteParameter(id, type, trigger) {
      if (typeof options.deleteParameter === 'function') {
        options.deleteParameter(id, type, trigger);
      }
    }

    function restoreParameter(id, type, trigger) {
      if (typeof options.restoreParameter === 'function') {
        options.restoreParameter(id, type, trigger);
      }
    }

    function cssEscape(value) {
      if (window.CSS && typeof window.CSS.escape === 'function') {
        return window.CSS.escape(String(value));
      }
      return String(value).replace(/["\\]/g, '\\$&');
    }

    function findPartnerContactCardByKey(key) {
      if (!key) return null;
      let selector = '';
      if (key.startsWith('draft:')) {
        const draftId = key.slice('draft:'.length);
        selector = `[data-partner-contact-card][data-partner-contact-draft="true"][data-partner-contact-draft-id="${cssEscape(
          draftId
        )}"]`;
      } else if (key.startsWith('item:')) {
        const id = key.slice('item:'.length);
        selector = `[data-partner-contact-card][data-partner-contact-draft="false"][data-partner-contact-id="${cssEscape(id)}"]`;
      }
      if (!selector) return null;
      return document.querySelector(selector);
    }

    function setPartnerContactCardVisibility(key, visible) {
      const card = findPartnerContactCardByKey(key);
      if (!card) return;
      const col = card.closest('.col');
      const target = col || card;
      if (visible) {
        target.classList.remove('d-none');
        target.removeAttribute('data-partner-contact-hidden');
      } else {
        target.classList.add('d-none');
        target.setAttribute('data-partner-contact-hidden', 'true');
      }
    }

    function clearPartnerContactEditorActions() {
      if (!editor.actions) return;
      editor.actions.innerHTML = '';
      editor.actions.classList.add('d-none');
    }

    function syncPartnerContactEditorActions(card) {
      if (!editor.actions) return;
      editor.actions.innerHTML = '';
      if (!card) {
        editor.actions.classList.add('d-none');
        return;
      }
      const actionsContainer = card.querySelector('[data-partner-contact-actions]');
      if (!actionsContainer) {
        editor.actions.classList.add('d-none');
        return;
      }
      const key = card.dataset.partnerContactKey || actionsContainer.dataset.partnerContactActionsOwner || '';
      const buttons = Array.from(actionsContainer.children || []);
      if (!buttons.length) {
        editor.actions.classList.add('d-none');
        return;
      }
      const fragment = document.createDocumentFragment();
      buttons.forEach((button) => {
        const clone = button.cloneNode(true);
        if (key) {
          clone.dataset.partnerContactActionTarget = key;
        }
        fragment.appendChild(clone);
      });
      editor.actions.appendChild(fragment);
      editor.actions.classList.remove('d-none');
      actionsContainer.classList.add('d-none');
    }

    function updatePartnerContactEditorTitleByKey(key) {
      if (!editor.title) return;
      if (!key) {
        editor.title.textContent = 'Карточка контакта';
        return;
      }
      const items = getPartnerContactRenderItems();
      const item = items.find((entry) => getPartnerContactItemKey(entry) === key);
      const displayName = getPartnerContactDisplayName(item);
      editor.title.textContent = displayName
        ? `Карточка контакта • ${displayName}`
        : 'Карточка контакта';
    }

    function renderPartnerContactModalContentByKey(key) {
      if (!editor.body || !key) return false;
      const items = getPartnerContactRenderItems();
      const item = items.find((entry) => getPartnerContactItemKey(entry) === key);
      if (!item) {
        editor.body.innerHTML =
          '<div class="alert alert-warning mb-0">Контакт не найден или был удалён.</div>';
        clearPartnerContactEditorActions();
        return false;
      }
      const html = renderPartnerContactCard(item, { mode: 'editor' });
      const temp = document.createElement('div');
      temp.innerHTML = html.trim();
      const col = temp.firstElementChild;
      let card = col ? col.querySelector('[data-partner-contact-card]') : temp.querySelector('[data-partner-contact-card]');
      if (!card) {
        editor.body.innerHTML =
          '<div class="alert alert-warning mb-0">Не удалось отобразить карточку контакта.</div>';
        clearPartnerContactEditorActions();
        return false;
      }
      card = card.cloneNode(true);
      card.classList.add('partner-contact-card--modal');
      card.classList.remove('h-100');
      const keyValue = getPartnerContactItemKey(item);
      if (keyValue) {
        state.detailsState.set(keyValue, 'expanded');
      }
      card.querySelectorAll('[data-partner-contact-details]').forEach((section) => {
        section.classList.remove('is-collapsed');
        section.classList.add('is-expanded');
        const toggle = section.querySelector('[data-partner-contact-details-toggle]');
        if (toggle) {
          toggle.setAttribute('aria-expanded', 'true');
        }
      });
      const modalOpenBtn = card.querySelector('[data-partner-contact-open-modal]');
      if (modalOpenBtn) {
        modalOpenBtn.setAttribute('disabled', 'true');
        modalOpenBtn.classList.add('disabled');
      }
      editor.body.innerHTML = '';
      editor.body.appendChild(card);
      syncPartnerContactEditorActions(card);
      card
        .querySelectorAll('[data-partner-contact-comm-field="value"][data-comm-type="phone"]')
        .forEach((input) => updatePhoneInputFormatting(input));
      card
        .querySelectorAll('[data-partner-contact-comm-field="value"][data-comm-type="email"]')
        .forEach((input) => validateEmailInput(input));
      card
        .querySelectorAll('[data-partner-contact-person-field="phone"]')
        .forEach((input) => updatePhoneInputFormatting(input));
      card
        .querySelectorAll('[data-partner-contact-person-field="email"]')
        .forEach((input) => validateEmailInput(input));
      return true;
    }

    function preparePartnerContactEditorByKey(key) {
      if (!elements.editorModalEl || !key) return false;
      state.editorState = { key };
      setPartnerContactCardVisibility(key, false);
      const rendered = renderPartnerContactModalContentByKey(key);
      if (!rendered) {
        state.editorState = null;
        setPartnerContactCardVisibility(key, true);
        return false;
      }
      updatePartnerContactEditorTitleByKey(key);
      return true;
    }

    function preparePartnerContactEditor(card) {
      if (!elements.editorModalEl || !card) return false;
      const key = getPartnerContactCardKey(card);
      if (!key) return false;
      return preparePartnerContactEditorByKey(key);
    }

    function preparePartnerContactEditorSettingsTrigger(trigger) {
      const card = trigger instanceof HTMLElement ? trigger.closest('[data-partner-contact-card]') : null;
      return preparePartnerContactEditor(card);
    }

    function preparePartnerContactDraftSettingsTrigger(trigger) {
      const scope = trigger instanceof HTMLElement ? trigger.dataset.partnerContactAdd : null;
      const initial = {};
      if (scope) {
        initial.contact_type = scope;
      }
      const draft = addPartnerContactDraft(initial);
      const key = draft ? getPartnerContactItemKey(draft) : null;
      if (!key) {
        return false;
      }
      const prepared = preparePartnerContactEditorByKey(key);
      if (!prepared && draft && typeof draft.draftId === 'string') {
        removePartnerContactDraft(draft.draftId);
      }
      return prepared;
    }

    function refreshPartnerContactPersonRow(row) {
      if (!row) return;
      const titleEl = row.querySelector('[data-partner-contact-person-title]');
      const subtitleEl = row.querySelector('[data-partner-contact-person-subtitle]');
      const fullNameInput = row.querySelector('[data-partner-contact-person-field="full_name"]');
      const positionInput = row.querySelector('[data-partner-contact-person-field="position"]');
      if (titleEl && fullNameInput) {
        const fullName = fullNameInput.value.trim();
        titleEl.textContent = fullName || 'Без имени';
      }
      if (subtitleEl && positionInput) {
        const position = positionInput.value.trim();
        subtitleEl.textContent = position;
        subtitleEl.classList.toggle('d-none', !position);
      }
      const topBadge = row.querySelector('[data-partner-contact-person-top-badge]');
      if (topBadge) {
        const toggle = row.querySelector('[data-partner-contact-person-field="is_top"]');
        const isChecked = toggle ? toggle.checked : false;
        topBadge.classList.toggle('d-none', !isChecked);
      }
    }

    function getPartnerContactItemKey(item) {
      if (!item || typeof item !== 'object') {
        return null;
      }
      if (item.isDraft) {
        if (item.draftId) {
          return `draft:${item.draftId}`;
        }
        return null;
      }
      if (Number.isFinite(item.id)) {
        return `item:${item.id}`;
      }
      if (item.id) {
        return `item:${item.id}`;
      }
      return null;
    }

    function getPartnerContactCardKey(card) {
      if (!card || !card.dataset.partnerContactCard) return null;
      if (card.dataset.partnerContactDraft === 'true') {
        const draftId = card.dataset.partnerContactDraftId;
        return draftId ? `draft:${draftId}` : null;
      }
      const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
      if (Number.isFinite(id)) {
        return `item:${id}`;
      }
      return null;
    }

    function getPartnerContactDisplayName(item) {
      if (!item || typeof item !== 'object') return '';
      const internalName = typeof item.internal_name === 'string' ? item.internal_name.trim() : '';
      if (internalName) {
        return internalName;
      }
      const value = typeof item.value === 'string' ? item.value.trim() : '';
      return value;
    }

    function refreshPartnerContactCardTitle(card) {
      if (!card) return;
      const title = card.querySelector('[data-partner-contact-title]');
      if (!title) return;
      const data = getPartnerContactCardData(card);
      const displayName = getPartnerContactDisplayName(data);
      title.textContent = displayName || 'Без названия';
      const key = getPartnerContactCardKey(card);
      if (state.editorState && state.editorState.key === key) {
        updatePartnerContactEditorTitleByKey(key);
      }
    }

    function updatePartnerContactCardHeaderState(card, stateValue) {
      if (!card) return;
      const header = card.querySelector('[data-partner-contact-card-header]');
      if (!header) return;
      const stateClasses = getPartnerContactStateClasses();
      if (stateClasses.length) {
        header.classList.remove(...stateClasses);
      }
      const className = getPartnerContactStateClassMap()[stateValue] || '';
      if (className) {
        header.classList.add(className);
      }
    }

    function updatePartnerContactServedSummary(card, data) {
      if (!card) return;
      const summary = card.querySelector('[data-partner-contact-served-summary]');
      if (!summary) return;
      const source = data || getPartnerContactCardData(card);
      if (!source) {
        summary.textContent = '';
        return;
      }
      const served = Array.isArray(source.served_legal_entities) ? source.served_legal_entities : [];
      if (!served.length) {
        summary.textContent = '';
        return;
      }
      const labels = served
        .map((entry) => {
          if (!entry || typeof entry !== 'object') return '';
          const id = Number.isFinite(entry.id) ? entry.id : Number.parseInt(entry.id, 10);
          const name = typeof entry.name === 'string' ? entry.name.trim() : '';
          if (name) return name;
          if (Number.isFinite(id)) return `ID ${id}`;
          return '';
        })
        .filter((label) => label);
      summary.textContent = labels.length ? `Выбрано: ${labels.join(', ')}` : '';
    }

    function generatePartnerContactCommKey(prefix) {
      if (prefix === 'phone') {
        state.phoneCounter += 1;
        return `${prefix}-${state.phoneCounter}`;
      }
      state.emailCounter += 1;
      return `${prefix}-${state.emailCounter}`;
    }

    function updatePartnerContactPersonCounterFromKey(key) {
      if (typeof key !== 'string') return;
      const match = key.match(/^person-(\d+)$/);
      if (!match) return;
      const value = Number.parseInt(match[1], 10);
      if (Number.isFinite(value) && value > state.personCounter) {
        state.personCounter = value;
      }
    }

    function generatePartnerContactPersonKey() {
      state.personCounter += 1;
      return `person-${state.personCounter}`;
    }

    function extractDigits(value) {
      if (typeof value === 'string' || typeof value === 'number') {
        return String(value).replace(/\D+/g, '');
      }
      return '';
    }

    const PHONE_COUNTRY_FORMATS = [
      { code: '375', prefix: '+375', mask: ' (##) ###-##-##' },
      { code: '380', prefix: '+380', mask: ' (##) ###-##-##' },
      { code: '7', prefix: '+7', mask: ' (###) ###-##-##' },
      { code: '1', prefix: '+1', mask: ' (###) ###-####' },
    ];
    const EMAIL_VALIDATION_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    function applyPhoneMask(mask, digits) {
      if (!digits) {
        return '';
      }
      let result = '';
      let index = 0;
      for (const char of mask) {
        if (char === '#') {
          if (index < digits.length) {
            result += digits[index];
            index += 1;
          } else {
            break;
          }
        } else if (index > 0 || digits.length > 0) {
          result += char;
        }
      }
      if (index < digits.length) {
        const remainder = digits.slice(index);
        if (remainder) {
          result += (result && !result.endsWith(' ') ? ' ' : '') + remainder;
        }
      }
      return result;
    }

    function formatPhoneValue(value) {
      const digits = extractDigits(value);
      if (!digits) {
        return '';
      }
      for (const format of PHONE_COUNTRY_FORMATS) {
        if (digits.startsWith(format.code)) {
          const rest = digits.slice(format.code.length);
          const maskedRest = rest ? applyPhoneMask(format.mask, rest) : '';
          return (format.prefix + maskedRest).trim();
        }
      }
      const prefix = `+${digits[0]}`;
      const rest = digits.slice(1);
      const maskedRest = rest ? applyPhoneMask(' (###) ###-##-##', rest) : '';
      return (prefix + maskedRest).trim();
    }

    function updatePhoneInputFormatting(input) {
      if (!input) return;
      const digits = extractDigits(input.value || input.dataset.phoneDigits || '');
      input.dataset.phoneDigits = digits;
      input.value = formatPhoneValue(digits);
    }

    function enforceExtensionDigits(input) {
      if (!input) return;
      const digits = extractDigits(input.value);
      input.value = digits;
    }

    function validateEmailInput(input) {
      if (!input) return;
      const value = typeof input.value === 'string' ? input.value.trim() : '';
      const isValid = !value || EMAIL_VALIDATION_REGEX.test(value);
      input.classList.toggle('is-invalid', !isValid);
      if (typeof input.setCustomValidity === 'function') {
        input.setCustomValidity(
          isValid ? '' : 'Введите корректный e-mail: необходим символ @ и доменное имя.'
        );
      }
    }

    function createBlankPartnerContactPersonEntry() {
      return {
        key: generatePartnerContactPersonKey(),
        full_name: '',
        position: '',
        phone: '',
        email: '',
        is_top: false,
      };
    }

    function normalizePartnerContactCommList(rawList, kind) {
      const allowed = kind === 'phone' ? getPartnerContactPhoneTypes() : getPartnerContactEmailTypes();
      const allowedKeys = Object.keys(allowed);
      const defaultType = allowedKeys.includes('work') ? 'work' : allowedKeys[0] || '';
      if (!Array.isArray(rawList)) {
        return [];
      }
      return rawList
        .map((item) => {
          let value = '';
          let type = defaultType;
          let key = '';
          let extension = '';
          if (item && typeof item === 'object') {
            const rawType = typeof item.type === 'string' ? item.type.trim() : '';
            if (rawType && Object.prototype.hasOwnProperty.call(allowed, rawType)) {
              type = rawType;
            }
            if (typeof item.value === 'string' || typeof item.value === 'number') {
              value = String(item.value).trim();
            }
            if (kind === 'phone' && (typeof item.extension === 'string' || typeof item.extension === 'number')) {
              extension = String(item.extension).trim();
            }
            if (typeof item.key === 'string' && item.key.trim()) {
              key = item.key.trim();
            }
          } else if (typeof item === 'string' || typeof item === 'number') {
            value = String(item).trim();
          }
          if (!value) {
            return null;
          }
          if (!Object.prototype.hasOwnProperty.call(allowed, type)) {
            type = defaultType;
          }
          if (!key) {
            key = generatePartnerContactCommKey(kind);
          }
          if (kind === 'phone') {
            value = value.replace(/\D+/g, '');
            extension = extension.replace(/\D+/g, '');
            return { key, type, value, extension };
          }
          return { key, type, value };
        })
        .filter((entry) => entry);
    }

    function normalizeBooleanValue(value) {
      if (typeof value === 'string') {
        const normalized = value.trim().toLowerCase();
        if (!normalized) return false;
        return (
          normalized === '1' ||
          normalized === 'true' ||
          normalized === 'yes' ||
          normalized === 'y' ||
          normalized === 'on'
        );
      }
      return Boolean(value);
    }

    function normalizePartnerContactPeople(rawExtra) {
      const extra = rawExtra && typeof rawExtra === 'object' ? rawExtra : {};
      const list = Array.isArray(extra.contacts) ? extra.contacts : [];
      const seen = new Set();
      return list
        .map((entry) => {
          if (!entry || typeof entry !== 'object') {
            return null;
          }
          const fullName = typeof entry.full_name === 'string' ? entry.full_name.trim() : '';
          const position = typeof entry.position === 'string' ? entry.position.trim() : '';
          const phone = typeof entry.phone === 'string' ? entry.phone.trim() : '';
          const email = typeof entry.email === 'string' ? entry.email.trim() : '';
          let key = typeof entry.key === 'string' ? entry.key.trim() : '';
          if (key) {
            if (seen.has(key)) {
              key = '';
            } else {
              updatePartnerContactPersonCounterFromKey(key);
            }
          }
          if (!key) {
            key = generatePartnerContactPersonKey();
          }
          seen.add(key);
          return {
            key,
            full_name: fullName,
            position,
            phone,
            email,
            is_top: normalizeBooleanValue(entry.is_top),
          };
        })
        .filter((entry) => entry);
    }

    function clonePartnerContactPeople(list) {
      if (!Array.isArray(list)) {
        return [];
      }
      return list
        .map((item) => {
          if (!item || typeof item !== 'object') {
            return null;
          }
          let key = typeof item.key === 'string' ? item.key : '';
          if (key) {
            updatePartnerContactPersonCounterFromKey(key);
          } else {
            key = generatePartnerContactPersonKey();
          }
          return {
            key,
            full_name: typeof item.full_name === 'string' ? item.full_name : '',
            position: typeof item.position === 'string' ? item.position : '',
            phone: typeof item.phone === 'string' ? item.phone : '',
            email: typeof item.email === 'string' ? item.email : '',
            is_top: normalizeBooleanValue(item.is_top),
          };
        })
        .filter((entry) => entry);
    }

    function normalizePartnerContactServedEntities(rawExtra) {
      const extra = rawExtra && typeof rawExtra === 'object' ? rawExtra : {};
      const result = [];
      const seen = new Set();

      const addEntry = (rawId, rawName) => {
        const parsed = Number.parseInt(rawId, 10);
        if (!Number.isFinite(parsed) || parsed <= 0 || seen.has(parsed)) {
          return;
        }
        seen.add(parsed);
        const name = typeof rawName === 'string' ? rawName.trim() : '';
        result.push({ id: parsed, name });
      };

      if (Array.isArray(extra.served_legal_entities)) {
        extra.served_legal_entities.forEach((entry) => {
          if (!entry || typeof entry !== 'object') return;
          addEntry(entry.id, entry.name || entry.label || entry.title || entry.served_legal_entity_name);
        });
      }

      if (Array.isArray(extra.served_legal_entity_ids)) {
        const names = Array.isArray(extra.served_legal_entity_names) ? extra.served_legal_entity_names : [];
        extra.served_legal_entity_ids.forEach((id, index) => {
          addEntry(id, names[index]);
        });
      }

      if (Object.prototype.hasOwnProperty.call(extra, 'served_legal_entity_id')) {
        addEntry(extra.served_legal_entity_id, extra.served_legal_entity_name);
      }

      return result;
    }

    function normalizePartnerContactExtra(rawExtra, { value } = {}) {
      const extra = rawExtra && typeof rawExtra === 'object' ? rawExtra : {};
      const rawType = typeof extra.contact_type === 'string' ? extra.contact_type.trim() : '';
      const contactType = Object.prototype.hasOwnProperty.call(getPartnerContactTypes(), rawType)
        ? rawType
        : 'partner';
      const phones = normalizePartnerContactCommList(Array.isArray(extra.phones) ? extra.phones : [], 'phone');
      const emails = normalizePartnerContactCommList(Array.isArray(extra.emails) ? extra.emails : [], 'email');
      const contacts = normalizePartnerContactPeople(extra);
      const innDigits = extractDigits(extra.inn);
      const innValue = innDigits || (typeof extra.inn === 'string' ? extra.inn.trim() : '');
      const legalEntityValue = typeof extra.legal_entity === 'string' && extra.legal_entity.trim()
        ? extra.legal_entity.trim()
        : typeof value === 'string'
          ? value.trim()
          : '';
      const servedEntities = normalizePartnerContactServedEntities(extra);
      const firstServed = servedEntities.length ? servedEntities[0] : null;
      const internalName = typeof extra.internal_name === 'string' ? extra.internal_name.trim() : '';
      return {
        contact_type: contactType,
        phones,
        emails,
        served_legal_entities: servedEntities,
        served_legal_entity_id: firstServed ? firstServed.id : null,
        served_legal_entity_name: firstServed ? firstServed.name : '',
        served_legal_entity_ids: servedEntities.map((entry) => entry.id),
        served_legal_entity_names: servedEntities.map((entry) => entry.name).filter((name) => name),
        legal_entity: legalEntityValue,
        internal_name: internalName,
        inn: innValue,
        contacts,
      };
    }

    function createBlankPartnerContactCommEntry(kind) {
      const allowed = kind === 'phone' ? getPartnerContactPhoneTypes() : getPartnerContactEmailTypes();
      const allowedKeys = Object.keys(allowed);
      const defaultType = allowedKeys.includes('work') ? 'work' : allowedKeys[0] || '';
      const entry = { key: generatePartnerContactCommKey(kind), type: defaultType, value: '' };
      if (kind === 'phone') {
        entry.extension = '';
      }
      return entry;
    }

    function clonePartnerContactCommList(list) {
      if (!Array.isArray(list)) {
        return [];
      }
      return list.map((item) => ({
        key: item.key,
        type: item.type,
        value: item.value,
        extension: typeof item.extension === 'string' ? item.extension : '',
      }));
    }

    function clonePartnerContactServedEntities(list) {
      if (!Array.isArray(list)) {
        return [];
      }
      return list
        .map((item) => {
          if (!item || typeof item !== 'object') return null;
          const id = Number.isFinite(item.id) ? item.id : Number.parseInt(item.id, 10);
          if (!Number.isFinite(id) || id <= 0) return null;
          return {
            id,
            name: typeof item.name === 'string' ? item.name : '',
          };
        })
        .filter((entry) => entry);
    }

    function clonePartnerContactItem(item) {
      if (!item || typeof item !== 'object') {
        return null;
      }
      const servedEntities = clonePartnerContactServedEntities(item.served_legal_entities);
      let servedId = Number.isFinite(item.served_legal_entity_id)
        ? item.served_legal_entity_id
        : Number.parseInt(item.served_legal_entity_id, 10);
      servedId = Number.isFinite(servedId) && servedId > 0 ? servedId : null;
      let servedName = typeof item.served_legal_entity_name === 'string' ? item.served_legal_entity_name : '';
      if (!servedEntities.length && Number.isFinite(servedId)) {
        servedEntities.push({ id: servedId, name: servedName });
      }
      if (servedEntities.length) {
        const first = servedEntities[0];
        if (first) {
          if (!Number.isFinite(servedId)) {
            servedId = first.id;
          }
          if (!servedName) {
            servedName = first.name;
          }
        }
      }
      return {
        id: item.id,
        draftId: item.draftId,
        isDraft: Boolean(item.isDraft),
        value: typeof item.value === 'string' ? item.value : '',
        state: typeof item.state === 'string' ? item.state : getParameterStates()[0],
        contact_type: Object.prototype.hasOwnProperty.call(getPartnerContactTypes(), item.contact_type)
          ? item.contact_type
          : 'partner',
        phones: clonePartnerContactCommList(item.phones),
        emails: clonePartnerContactCommList(item.emails),
        contacts: clonePartnerContactPeople(item.contacts),
        served_legal_entities: servedEntities,
        served_legal_entity_id: Number.isFinite(servedId) ? servedId : null,
        served_legal_entity_name: typeof servedName === 'string' ? servedName : '',
        legal_entity: typeof item.legal_entity === 'string' ? item.legal_entity : '',
        internal_name: typeof item.internal_name === 'string' ? item.internal_name : '',
        inn: typeof item.inn === 'string' ? item.inn : '',
        is_deleted: Boolean(item.is_deleted),
        usage_count: Number.isFinite(item.usage_count) ? item.usage_count : 0,
      };
    }

    function getPartnerContactLegalEntityOptions() {
      const items = Array.isArray(getParameterData().legal_entity) ? getParameterData().legal_entity : [];
      const optionsList = [];
      items.forEach((item) => {
        if (!item || item.is_deleted) return;
        const label = typeof item.value === 'string' ? item.value.trim() : '';
        if (!label) return;
        const id = Number.isFinite(item.id) ? item.id : Number.parseInt(item.id, 10);
        if (!Number.isFinite(id)) return;
        optionsList.push({ id, label });
      });
      return optionsList;
    }

    function getPartnerContactBaseItems() {
      const items = Array.isArray(getParameterData().partner_contact) ? getParameterData().partner_contact : [];
      return items
        .map((item) => {
          if (!item) return null;
          return clonePartnerContactItem({
            id: Number.isFinite(item.id) ? item.id : Number.parseInt(item.id, 10),
            value: typeof item.value === 'string' ? item.value : '',
            state: typeof item.state === 'string' ? item.state : getParameterStates()[0],
            contact_type: item.contact_type,
            phones: item.phones,
            emails: item.emails,
            contacts: item.contacts,
            served_legal_entities: item.served_legal_entities,
            served_legal_entity_id: item.served_legal_entity_id,
            served_legal_entity_name: item.served_legal_entity_name,
            served_legal_entity_ids: item.served_legal_entity_ids,
            served_legal_entity_names: item.served_legal_entity_names,
            legal_entity: typeof item.legal_entity === 'string' ? item.legal_entity : '',
            internal_name: typeof item.internal_name === 'string' ? item.internal_name : '',
            inn: typeof item.inn === 'string' ? item.inn : '',
            is_deleted: Boolean(item.is_deleted),
            usage_count: Number.isFinite(Number(item.usage_count)) ? Number(item.usage_count) : 0,
          });
        })
        .filter((entry) => entry && Number.isFinite(entry.id));
    }

    function ensurePartnerContactEdit(id) {
      if (!Number.isFinite(id)) return null;
      if (!state.edits.has(id)) {
        const baseItems = getPartnerContactBaseItems();
        const base = baseItems.find((item) => item && item.id === id);
        state.edits.set(id, base ? clonePartnerContactItem(base) : null);
      }
      const current = state.edits.get(id);
      if (!current) {
        return null;
      }
      if (!Array.isArray(current.phones)) {
        current.phones = [];
      }
      if (!Array.isArray(current.emails)) {
        current.emails = [];
      }
      if (!Array.isArray(current.served_legal_entities)) {
        current.served_legal_entities = [];
      }
      if (!Array.isArray(current.contacts)) {
        current.contacts = [];
      }
      return current;
    }

    function getPartnerContactRenderItems() {
      const baseItems = getPartnerContactBaseItems();
      const rendered = baseItems.map((item) => {
        const id = item.id;
        const edit = ensurePartnerContactEdit(id);
        if (edit) {
          const clone = clonePartnerContactItem(edit);
          clone.id = id;
          clone.isDraft = false;
          clone.is_deleted = item.is_deleted;
          clone.usage_count = item.usage_count;
          return clone;
        }
        item.isDraft = false;
        return item;
      });
      state.drafts.forEach((draft) => {
        const clone = clonePartnerContactItem(draft);
        if (!clone) return;
        clone.isDraft = true;
        clone.draftId = draft.draftId;
        rendered.push(clone);
      });
      return rendered;
    }

    function buildPartnerContactTypeSelect(selected, disabled) {
      const types = getPartnerContactTypes();
      const effective = Object.prototype.hasOwnProperty.call(types, selected)
        ? selected
        : 'partner';
      const disabledAttr = disabled ? ' disabled' : '';
      const optionsHtml = Object.keys(types)
        .map((key) => {
          const label = types[key];
          const isSelected = key === effective;
          return `<option value="${key}"${isSelected ? ' selected' : ''}>${escapeHtml(label)}</option>`;
        })
        .join('');
      return `<select class="form-select form-select-sm" data-partner-contact-field="contact_type"${disabledAttr}>${optionsHtml}</select>`;
    }

    function buildPartnerContactStateControl(item, disabled) {
      const parameterStates = getParameterStates();
      const effective = parameterStates.includes(item.state) ? item.state : parameterStates[0];
      const optionsHtml = parameterStates.map((stateValue) => {
        const isSelected = stateValue === effective;
        return `<option value="${escapeHtml(stateValue)}"${isSelected ? ' selected' : ''}>${escapeHtml(stateValue)}</option>`;
      }).join('');
      const disabledAttr = disabled ? ' disabled' : '';
      return `<select class="form-select form-select-sm" data-partner-contact-field="state"${disabledAttr}>${optionsHtml}</select>`;
    }

    function buildPartnerContactServedSelect(item, disabled) {
      const disabledAttr = disabled ? ' disabled' : '';
      const optionsList = getPartnerContactLegalEntityOptions();
      const servedEntities = Array.isArray(item.served_legal_entities) ? item.served_legal_entities : [];
      const selectedIds = new Set();
      const labelById = new Map();

      servedEntities.forEach((entry) => {
        if (!entry || typeof entry !== 'object') return;
        const parsed = Number.isFinite(entry.id) ? entry.id : Number.parseInt(entry.id, 10);
        if (!Number.isFinite(parsed) || parsed <= 0) return;
        selectedIds.add(parsed);
        const label = typeof entry.name === 'string' && entry.name.trim() ? entry.name.trim() : `ID ${parsed}`;
        labelById.set(parsed, label);
      });

      const fallbackId = Number.isFinite(item.served_legal_entity_id)
        ? item.served_legal_entity_id
        : Number.parseInt(item.served_legal_entity_id, 10);
      if (Number.isFinite(fallbackId) && fallbackId > 0 && !selectedIds.has(fallbackId)) {
        selectedIds.add(fallbackId);
        const fallbackName = typeof item.served_legal_entity_name === 'string' && item.served_legal_entity_name.trim()
          ? item.served_legal_entity_name.trim()
          : `ID ${fallbackId}`;
        labelById.set(fallbackId, fallbackName);
      }

      const entries = [];
      optionsList.forEach(({ id, label }) => {
        const isSelected = selectedIds.has(id);
        if (isSelected) {
          selectedIds.delete(id);
        }
        labelById.set(id, label);
        entries.push(
          `<option value="${id}" data-label="${escapeHtml(label)}"${isSelected ? ' selected' : ''}>${escapeHtml(label)}</option>`
        );
      });

      selectedIds.forEach((id) => {
        const fallbackLabel = labelById.get(id) || `ID ${id}`;
        entries.push(
          `<option value="${id}" data-label="${escapeHtml(fallbackLabel)}" selected>${escapeHtml(fallbackLabel)} • не найдено</option>`
        );
      });

      if (!entries.length) {
        return '<div class="text-muted small">Нет доступных ЮЛ</div>';
      }

      const size = Math.min(Math.max(entries.length, 4), 10);
      return `<select class="form-select form-select-sm partner-contact-served-select" data-partner-contact-field="served_legal_entities" multiple size="${size}"${disabledAttr}>${entries.join('')}</select>`;
    }

    function buildPartnerContactCommRows(entries, kind, disabled) {
      if (!Array.isArray(entries) || !entries.length) {
        return '<div class="text-muted small" data-partner-contact-empty>Нет данных</div>';
      }
      const allowed = kind === 'phone' ? getPartnerContactPhoneTypes() : getPartnerContactEmailTypes();
      return entries
        .map((entry) => {
          const rawValue = typeof entry.value === 'string' ? entry.value : '';
          const digitsValue = kind === 'phone' ? extractDigits(rawValue) : rawValue;
          const value = kind === 'phone' ? formatPhoneValue(digitsValue) : rawValue;
          const extensionValue = kind === 'phone' && typeof entry.extension === 'string' ? extractDigits(entry.extension) : '';
          const type = Object.prototype.hasOwnProperty.call(allowed, entry.type) ? entry.type : Object.keys(allowed)[0];
          const disabledAttr = disabled ? ' disabled' : '';
          const optionsHtml = Object.keys(allowed)
            .map((key) => {
              const label = allowed[key];
              const isSelected = key === type;
              return `<option value="${key}"${isSelected ? ' selected' : ''}>${escapeHtml(label)}</option>`;
            })
            .join('');
          const removeBtn = disabled
            ? ''
            : `<button class="btn btn-outline-danger" type="button" data-partner-contact-remove-comm data-comm-type="${kind}" data-entry-key="${escapeHtml(entry.key)}" title="Удалить">🗑</button>`;
          if (kind === 'phone') {
            return `
              <div class="input-group input-group-sm mb-2" data-partner-contact-comm-row data-comm-type="${kind}" data-entry-key="${escapeHtml(entry.key)}">
                <select class="form-select form-select-sm" data-partner-contact-comm-field="type" data-comm-type="${kind}" data-entry-key="${escapeHtml(entry.key)}"${disabledAttr}>${optionsHtml}</select>
                <input type="text" class="form-control" data-partner-contact-comm-field="value" data-comm-type="${kind}" data-entry-key="${escapeHtml(entry.key)}" data-phone-digits="${escapeHtml(digitsValue)}" value="${escapeHtml(value)}" placeholder="Номер" inputmode="numeric"${disabledAttr}>
                <span class="input-group-text">доб.</span>
                <input type="text" class="form-control" data-partner-contact-comm-field="extension" data-comm-type="${kind}" data-entry-key="${escapeHtml(entry.key)}" value="${escapeHtml(extensionValue)}" placeholder="Доб. номер" inputmode="numeric"${disabledAttr}>
                ${removeBtn}
              </div>
            `;
          }
          return `
            <div class="input-group input-group-sm mb-2" data-partner-contact-comm-row data-comm-type="${kind}" data-entry-key="${escapeHtml(entry.key)}">
              <select class="form-select form-select-sm" data-partner-contact-comm-field="type" data-comm-type="${kind}" data-entry-key="${escapeHtml(entry.key)}"${disabledAttr}>${optionsHtml}</select>
              <input type="email" class="form-control" data-partner-contact-comm-field="value" data-comm-type="${kind}" data-entry-key="${escapeHtml(entry.key)}" value="${escapeHtml(value)}" placeholder="E-mail"${disabledAttr}>
              ${removeBtn}
            </div>
          `;
        })
        .join('');
    }

    function getSortedPartnerContactPeople(entries) {
      if (!Array.isArray(entries) || !entries.length) {
        return [];
      }
      return entries
        .map((person, index) => ({
          person,
          index,
          isTop: normalizeBooleanValue(person && person.is_top),
          name: person && typeof person.full_name === 'string' ? person.full_name.trim() : '',
        }))
        .sort((a, b) => {
          if (a.isTop !== b.isTop) {
            return a.isTop ? -1 : 1;
          }
          const nameCompare = a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
          if (nameCompare !== 0) {
            return nameCompare;
          }
          return a.index - b.index;
        })
        .map(({ person }) => person);
    }

    function buildPartnerContactPeople(entries, disabled) {
      if (!Array.isArray(entries) || !entries.length) {
        return '<div class="text-muted small" data-partner-contact-person-empty>Контакты не добавлены</div>';
      }
      const sortedEntries = getSortedPartnerContactPeople(entries);
      const maxVisible = 2;
      const peopleCards = sortedEntries
        .map((person, index) => {
          if (!person || typeof person !== 'object') {
            return '';
          }
          const key = typeof person.key === 'string' ? person.key : '';
          const fullName = typeof person.full_name === 'string' ? person.full_name : '';
          const position = typeof person.position === 'string' ? person.position : '';
          const phone = typeof person.phone === 'string' ? person.phone : '';
          const email = typeof person.email === 'string' ? person.email : '';
          const phoneDigits = extractDigits(phone);
          const formattedPhone = formatPhoneValue(phone);
          const disabledAttr = disabled ? ' disabled' : '';
          const isTop = normalizeBooleanValue(person.is_top);
          const topBadgeClass = isTop
            ? 'badge bg-warning text-dark'
            : 'badge bg-warning text-dark d-none';
          const topToggle = `
            <div class="form-check form-switch mb-0">
              <input class="form-check-input" type="checkbox" data-partner-contact-person-field="is_top"${isTop ? ' checked' : ''}${disabledAttr}>
              <label class="form-check-label small">ТОП</label>
            </div>
          `;
          const removeBtn = disabled
            ? ''
            : `<button class="btn btn-outline-danger btn-sm" type="button" data-partner-contact-remove-person data-person-key="${escapeHtml(
                key
              )}">Удалить</button>`;
          const titleText = escapeHtml(fullName || `Контакт ${index + 1}`);
          const subtitleClass = position
            ? 'partner-contact-person-card__subtitle'
            : 'partner-contact-person-card__subtitle d-none';
          const subtitleText = escapeHtml(position || '');
          return `
            <div class="partner-contact-person partner-contact-person-card" data-partner-contact-person="${escapeHtml(key)}">
              <div class="partner-contact-person-card__header">
                <div>
                  <div class="d-flex align-items-center gap-2">
                    <div class="partner-contact-person-card__title" data-partner-contact-person-title>${titleText}</div>
                    <span class="${topBadgeClass}" data-partner-contact-person-top-badge>ТОП</span>
                  </div>
                  <div class="${subtitleClass}" data-partner-contact-person-subtitle>${subtitleText}</div>
                </div>
                <div class="partner-contact-person__actions d-flex flex-column align-items-end gap-2">
                  ${topToggle}
                  ${removeBtn}
                </div>
              </div>
              <div class="partner-contact-person-card__body">
                <div class="row g-3">
                  <div class="col-12 col-lg-6">
                    <label class="form-label">ФИО</label>
                    <input type="text" class="form-control form-control-sm" data-partner-contact-person-field="full_name" value="${escapeHtml(
                      fullName
                    )}" placeholder="Иванов Иван Иванович"${disabledAttr}>
                  </div>
                  <div class="col-12 col-lg-6">
                    <label class="form-label">Должность</label>
                    <input type="text" class="form-control form-control-sm" data-partner-contact-person-field="position" value="${escapeHtml(
                      position
                    )}" placeholder="Например, менеджер"${disabledAttr}>
                  </div>
                  <div class="col-12 col-lg-6">
                    <label class="form-label">Телефон</label>
                    <input type="text" class="form-control form-control-sm" data-partner-contact-person-field="phone" value="${escapeHtml(
                      formattedPhone
                    )}" placeholder="Например, +7 999 123-45-67" inputmode="tel" data-phone-digits="${escapeHtml(
                      phoneDigits
                    )}"${disabledAttr}>
                  </div>
                  <div class="col-12 col-lg-6">
                    <label class="form-label">E-mail</label>
                    <input type="email" class="form-control form-control-sm" data-partner-contact-person-field="email" value="${escapeHtml(
                      email
                    )}" placeholder="example@company.com"${disabledAttr}>
                  </div>
                </div>
              </div>
            </div>
          `;
        })
        .filter(Boolean);
      const visibleCards = peopleCards.slice(0, maxVisible).join('');
      const hiddenCards = peopleCards.slice(maxVisible).join('');
      if (!hiddenCards) {
        return visibleCards;
      }
      const extraCount = sortedEntries.length - maxVisible;
      const collapsedText = `Показать ещё ${formatPartnerContactCount(extraCount)}`;
      const expandedText = 'Скрыть дополнительные контакты';
      return `
        <div class="partner-contact-people-wrapper" data-partner-contact-people-wrapper>
          ${visibleCards}
          <div class="partner-contact-people-extra mt-3 d-none" data-partner-contact-people-extra>${hiddenCards}</div>
          <div class="mt-3">
            <button class="btn btn-link btn-sm px-0 partner-contact-people-toggle" type="button" data-partner-contact-people-toggle aria-expanded="false" data-collapsed-text="${escapeHtml(
              collapsedText
            )}" data-expanded-text="${escapeHtml(expandedText)}">${escapeHtml(collapsedText)}</button>
          </div>
        </div>
      `;
    }

    function formatPartnerContactCount(count) {
      const value = Number.parseInt(count, 10);
      if (!Number.isFinite(value) || value <= 0) {
        return '0 контактов';
      }
      const abs = Math.abs(value);
      const mod10 = abs % 10;
      const mod100 = abs % 100;
      if (mod10 === 1 && mod100 !== 11) {
        return `${abs} контакт`;
      }
      if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) {
        return `${abs} контакта`;
      }
      return `${abs} контактов`;
    }

    function getPartnerContactServedSummaryText(item) {
      if (!item || typeof item !== 'object') return '';
      const names = [];
      const servedEntities = Array.isArray(item.served_legal_entities) ? item.served_legal_entities : [];
      servedEntities.forEach((entry) => {
        if (!entry || typeof entry !== 'object') return;
        const name = typeof entry.name === 'string' ? entry.name.trim() : '';
        const id = Number.isFinite(entry.id) ? entry.id : Number.parseInt(entry.id, 10);
        if (name) {
          names.push(name);
        } else if (Number.isFinite(id) && id > 0) {
          names.push(`ID ${id}`);
        }
      });
      if (!names.length) {
        const fallbackName = typeof item.served_legal_entity_name === 'string' ? item.served_legal_entity_name.trim() : '';
        const fallbackId = Number.isFinite(item.served_legal_entity_id)
          ? item.served_legal_entity_id
          : Number.parseInt(item.served_legal_entity_id, 10);
        if (fallbackName) {
          names.push(fallbackName);
        } else if (Number.isFinite(fallbackId) && fallbackId > 0) {
          names.push(`ID ${fallbackId}`);
        }
      }
      return names.join(', ');
    }

    function buildPartnerContactSummaryContacts(item) {
      const contacts = getSortedPartnerContactPeople(Array.isArray(item.contacts) ? item.contacts : []);
      const normalized = contacts
        .map((person, index) => {
          if (!person || typeof person !== 'object') return null;
          const fullName = typeof person.full_name === 'string' ? person.full_name.trim() : '';
          const position = typeof person.position === 'string' ? person.position.trim() : '';
          const phone = typeof person.phone === 'string' ? person.phone.trim() : '';
          const formattedPhone = formatPhoneValue(phone);
          const email = typeof person.email === 'string' ? person.email.trim() : '';
          if (!fullName && !position && !phone && !email) return null;
          return {
            title: fullName || `Контакт ${index + 1}`,
            position,
            phone: formattedPhone,
            email,
            is_top: normalizeBooleanValue(person.is_top),
          };
        })
        .filter((entry) => entry);
      const limited = normalized.slice(0, 2);
      const items = limited.map((person) => {
        const metaLines = [];
        if (person.position) {
          metaLines.push(`<div class="partner-contact-summary-contact-meta">${escapeHtml(person.position)}</div>`);
        }
        const detailParts = [];
        if (person.phone) {
          detailParts.push(`<span><i class="bi bi-telephone-fill me-1"></i>${escapeHtml(person.phone)}</span>`);
        }
        if (person.email) {
          detailParts.push(`<span><i class="bi bi-envelope-fill me-1"></i>${escapeHtml(person.email)}</span>`);
        }
        if (detailParts.length) {
          metaLines.push(
            `<div class="partner-contact-summary-contact-meta">${detailParts.join('<span class="text-muted">•</span>')}</div>`
          );
        }
        const topBadge = person.is_top
          ? ' <span class="badge bg-warning text-dark ms-1">ТОП</span>'
          : '';
        return `
          <li class="partner-contact-summary-contact">
            <div class="partner-contact-summary-contact-title">${escapeHtml(person.title)}${topBadge}</div>
            ${metaLines.join('')}
          </li>
        `;
      });
      if (normalized.length > limited.length) {
        const remainder = normalized.length - limited.length;
        items.push(
          `<li class="partner-contact-summary-contact-meta text-muted">Ещё ${formatPartnerContactCount(remainder)}</li>`
        );
      }
      if (items.length) {
        return items.join('');
      }
      const fallbackEntries = [];
      const phones = Array.isArray(item.phones) ? item.phones : [];
      phones.forEach((entry) => {
        if (!entry || typeof entry !== 'object') return;
        const value = typeof entry.value === 'string' ? extractDigits(entry.value) : '';
        if (!value) return;
        const extension = typeof entry.extension === 'string' ? extractDigits(entry.extension) : '';
        fallbackEntries.push({ kind: 'phone', value, extension });
      });
      const emails = Array.isArray(item.emails) ? item.emails : [];
      emails.forEach((entry) => {
        if (!entry || typeof entry !== 'object') return;
        const value = typeof entry.value === 'string' ? entry.value.trim() : '';
        if (!value) return;
        fallbackEntries.push({ kind: 'email', value });
      });
      const limitedFallback = fallbackEntries.slice(0, 2);
      if (limitedFallback.length) {
        const rendered = limitedFallback
          .map((entry) => {
            const icon = entry.kind === 'phone'
              ? '<i class="bi bi-telephone-fill me-1"></i>'
              : '<i class="bi bi-envelope-fill me-1"></i>';
            const label = entry.kind === 'phone' ? 'Телефон' : 'E-mail';
            if (entry.kind === 'phone') {
              const formatted = formatPhoneValue(entry.value);
              if (!formatted) {
                return '';
              }
              const extensionPart = entry.extension
                ? `<span class="text-muted">•</span><span> доб. ${escapeHtml(entry.extension)}</span>`
                : '';
              return `
                <li class="partner-contact-summary-contact">
                  <div class="partner-contact-summary-contact-title">${label}</div>
                  <div class="partner-contact-summary-contact-meta">${icon}${escapeHtml(formatted)}${extensionPart}</div>
                </li>
              `;
            }
            return `
              <li class="partner-contact-summary-contact">
                <div class="partner-contact-summary-contact-title">${label}</div>
                <div class="partner-contact-summary-contact-meta">${icon}${escapeHtml(entry.value)}</div>
              </li>
            `;
          })
          .join('');
        if (fallbackEntries.length > limitedFallback.length) {
          const remainder = fallbackEntries.length - limitedFallback.length;
          return (
            rendered +
            `<li class="partner-contact-summary-contact-meta text-muted">Ещё ${formatPartnerContactCount(remainder)}</li>`
          );
        }
        return rendered;
      }
      return '<li class="partner-contact-summary-empty">Контактные данные не указаны</li>';
    }

    function renderPartnerContactCard(item, options = {}) {
      if (!item) return '';
      const mode = options.mode === 'editor' ? 'editor' : 'summary';
      const isDraft = Boolean(item.isDraft);
      const isDeleted = Boolean(item.is_deleted);
      const disabled = !isDraft && isDeleted;
      const idValue = item && item.id != null ? String(item.id) : '';
      const cardClasses = ['card', 'shadow-sm', 'partner-contact-card', 'h-100'];
      if (mode === 'summary') {
        cardClasses.push('partner-contact-card--summary');
      }
      if (isDeleted) {
        cardClasses.push('border-danger', 'opacity-75');
      }
      let datasetAttrs;
      if (isDraft) {
        const draftId = typeof item.draftId === 'string' ? item.draftId : '';
        const attrs = ['data-partner-contact-card="true"', 'data-partner-contact-draft="true"'];
        if (draftId) {
          attrs.push(`data-partner-contact-draft-id="${escapeHtml(draftId)}"`);
        }
        datasetAttrs = attrs.join(' ');
      } else {
        const attrs = ['data-partner-contact-card="true"', 'data-partner-contact-draft="false"', 'data-param-type="partner_contact"'];
        if (idValue) {
          attrs.push(`data-partner-contact-id="${escapeHtml(idValue)}"`, `data-param-id="${escapeHtml(idValue)}"`);
        }
        if (isDeleted) {
          attrs.push('data-partner-contact-deleted="true"');
        }
        datasetAttrs = attrs.join(' ');
      }
      const types = getPartnerContactTypes();
      const parameterStates = getParameterStates();
      const typeLabel = types[item.contact_type] || types.partner;
      const stateValue = typeof item.state === 'string' ? item.state : parameterStates[0];
      const titleValue = typeof item.value === 'string' ? item.value.trim() : '';
      const displayName = getPartnerContactDisplayName(item);
      const servedSummaryText = getPartnerContactServedSummaryText(item);
      const innValue = typeof item.inn === 'string' ? item.inn.trim() : '';

      if (mode === 'summary') {
        const contactsSummary = buildPartnerContactSummaryContacts(item);
        const ariaLabel = displayName
          ? `Открыть карточку контакта «${displayName}»`
          : 'Открыть карточку контакта';
        const idBadge = !isDraft && idValue ? `<span class="badge bg-secondary">ID: ${escapeHtml(idValue)}</span>` : '';
        const draftBadge = isDraft ? '<span class="badge bg-success">Черновик</span>' : '';
        const deletedBadge = isDeleted ? '<span class="badge text-bg-warning">Удалено</span>' : '';
        return `
          <div class="col">
            <div class="${cardClasses.join(' ')}" ${datasetAttrs} data-partner-contact-open-modal role="button" tabindex="0" aria-label="${escapeHtml(ariaLabel)}">
              <div class="card-body">
                <div class="partner-contact-summary-head">
                  <div>
                    <div class="partner-contact-summary-title" data-partner-contact-title>${escapeHtml((displayName || titleValue || '').trim() || 'Без названия')}</div>
                    <div class="partner-contact-summary-meta" data-partner-contact-subtitle>${escapeHtml(typeLabel)} • ${escapeHtml(stateValue)}</div>
                  </div>
                  <div class="d-flex flex-column align-items-end gap-1">
                    ${draftBadge}
                    ${idBadge}
                    ${deletedBadge}
                  </div>
                </div>
                <div>
                  <div class="partner-contact-summary-label small text-muted text-uppercase">Юридическое лицо</div>
                  <div class="partner-contact-summary-value">${escapeHtml(titleValue || '—')}</div>
                </div>
                <div>
                  <div class="partner-contact-summary-label small text-muted text-uppercase">ИНН</div>
                  <div class="partner-contact-summary-value">${escapeHtml(innValue || '—')}</div>
                </div>
                <div>
                  <div class="partner-contact-summary-label small text-muted text-uppercase">Контакты</div>
                  <ul class="partner-contact-summary-list mb-0">
                    ${contactsSummary}
                  </ul>
                </div>
                ${servedSummaryText ? `
                <div>
                  <div class="partner-contact-summary-label small text-muted text-uppercase">Обслуживает</div>
                  <div class="partner-contact-summary-value">${escapeHtml(servedSummaryText)}</div>
                </div>` : ''}
                <div class="mt-auto text-end">
                  <span class="text-primary small d-inline-flex align-items-center gap-1">
                    Подробнее
                    <i class="bi bi-arrow-right-short fs-5 mb-0"></i>
                  </span>
                </div>
              </div>
            </div>
          </div>
        `;
      }

      const typeSelect = buildPartnerContactTypeSelect(item.contact_type, disabled);
      const stateControl = buildPartnerContactStateControl(item, disabled);
      const servedSelect = buildPartnerContactServedSelect(item, disabled);
      const contactsSection = buildPartnerContactPeople(item.contacts, disabled);
      const primaryLabel = isDraft ? 'Создать' : 'Сохранить';
      const primaryClass = isDraft ? 'btn-success' : 'btn-primary';
      const primaryDisabled = disabled ? ' disabled' : '';
      const actionButtons = [`<button class="btn ${primaryClass} btn-sm" type="button" data-partner-contact-action="save"${primaryDisabled}>${primaryLabel}</button>`];
      if (isDraft) {
        actionButtons.push(
          '<button class="btn btn-outline-secondary btn-sm" type="button" data-partner-contact-action="cancel">Отмена</button>'
        );
      } else if (isDeleted) {
        actionButtons.push(
          '<button class="btn btn-outline-success btn-sm" type="button" data-partner-contact-action="restore">Восстановить</button>'
        );
      } else {
        actionButtons.push(
          '<button class="btn btn-outline-secondary btn-sm" type="button" data-partner-contact-action="reset">Отменить изменения</button>'
        );
        actionButtons.push(
          '<button class="btn btn-outline-danger btn-sm" type="button" data-partner-contact-action="delete">Удалить</button>'
        );
      }
      const idBadge = !isDraft && idValue ? `<span class="badge bg-secondary ms-auto">ID: ${escapeHtml(idValue)}</span>` : '';
      const addPersonBtn = disabled
        ? ''
        : '<button class="btn btn-outline-success btn-sm" type="button" data-partner-contact-add-person>+ Контакт</button>';
      const servedWrapperClass = item.contact_type === 'ka' ? '' : ' d-none';
      const internalNameValue = typeof item.internal_name === 'string' ? item.internal_name : '';
      const baseKey =
        getPartnerContactItemKey(item) ||
        (isDraft && item.draftId
          ? `draft:${item.draftId}`
          : Number.isFinite(item.id)
            ? `item:${item.id}`
            : null);
      if (baseKey && !state.detailsState.has(baseKey)) {
        state.detailsState.set(baseKey, 'expanded');
      }
      const isDetailsCollapsed = baseKey ? state.detailsState.get(baseKey) === 'collapsed' : false;
      const detailsClasses = ['partner-contact-details', isDetailsCollapsed ? 'is-collapsed' : 'is-expanded'];
      const detailsAria = isDetailsCollapsed ? 'false' : 'true';
      const deletedHint = isDeleted
        ? '<div class="alert alert-warning small mt-3 mb-0">Контакт помечен как удалённый. Восстановите запись, чтобы редактировать её.</div>'
        : '';
      const headerStateClass = getPartnerContactStateClassMap()[stateValue] || '';
      const cardDatasetAttrs = baseKey
        ? `${datasetAttrs} data-partner-contact-key="${escapeHtml(baseKey)}"`
        : datasetAttrs;
      const actionsAttributes = ['data-partner-contact-actions'];
      if (baseKey) {
        actionsAttributes.push(`data-partner-contact-actions-owner="${escapeHtml(baseKey)}"`);
      }
      const actionsAttrString = actionsAttributes.join(' ');
      return `
        <div class="col">
          <div class="${cardClasses.join(' ')}" ${cardDatasetAttrs}>
            <div class="card-header d-flex justify-content-between align-items-start gap-2 ${headerStateClass}" data-partner-contact-card-header>
              <div>
                <div class="fw-semibold partner-contact-card__title" data-partner-contact-title>${escapeHtml((displayName || titleValue || '').trim() || 'Без названия')}</div>
                <div class="small partner-contact-card__subtitle" data-partner-contact-subtitle>${escapeHtml(typeLabel)} • ${escapeHtml(stateValue)}</div>
              </div>
              <div class="d-flex align-items-start gap-2">
                ${idBadge}
                <button class="btn btn-outline-primary btn-sm" type="button" data-partner-contact-open-modal title="Открыть в модальном окне">
                  <i class="bi bi-arrows-fullscreen"></i>
                </button>
              </div>
            </div>
            <div class="card-body d-flex flex-column gap-3">
              <div class="row g-3 align-items-end">
                <div class="col-12 col-lg-4 col-xl-3">
                  <label class="form-label">Тип</label>
                  ${typeSelect}
                </div>
                <div class="col-12 col-lg-4 col-xl-3">
                  <label class="form-label">Статус</label>
                  ${stateControl}
                </div>
                <div class="col-12 col-lg-4 col-xl-6">
                  <label class="form-label">Внутреннее название</label>
                  <input type="text" class="form-control" data-partner-contact-field="internal_name" value="${escapeHtml((internalNameValue || '').trim())}" placeholder="Например, Ключевой партнёр"${disabled ? ' disabled' : ''}>
                  <div class="form-text">Отображается в панели и отчётах.</div>
                </div>
              </div>
              <div>
                <div class="row g-3">
                  <div class="col-sm-6">
                    <label class="form-label">ЮЛ</label>
                    <input type="text" class="form-control" data-partner-contact-field="value" value="${escapeHtml(item.value || '')}" placeholder="Например, ООО «Партнёр»"${disabled ? ' disabled' : ''}>
                    <div class="form-text">Название юридического лица контакта.</div>
                  </div>
                  <div class="col-sm-6">
                    <label class="form-label">ИНН</label>
                    <input type="text" class="form-control" data-partner-contact-field="inn" value="${escapeHtml(innValue)}" placeholder="12 цифр" inputmode="numeric"${disabled ? ' disabled' : ''}>
                    <div class="form-text">Укажите ИНН юридического лица.</div>
                  </div>
                </div>
              </div>
              <div class="${servedWrapperClass}" data-partner-contact-served-wrapper>
                <label class="form-label mb-1">Какое ЮЛ обслуживает</label>
                ${servedSelect}
                <div class="form-text">Можно выбрать несколько ЮЛ.</div>
                <div class="partner-contact-served-summary" data-partner-contact-served-summary>${escapeHtml(servedSummaryText)}</div>
              </div>
              <div class="${detailsClasses.join(' ')}" data-partner-contact-details${baseKey ? ` data-details-key="${escapeHtml(baseKey)}"` : ''}>
                <div class="partner-contact-details__header">
                  <button class="partner-contact-details__toggle" type="button" data-partner-contact-details-toggle aria-expanded="${detailsAria}">
                    <span class="partner-contact-details__title">Контакты и должности</span>
                    <i class="bi bi-chevron-down partner-contact-details__chevron"></i>
                  </button>
                </div>
                <div class="partner-contact-details__body">
                  <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-2">
                    <label class="form-label mb-0">Контакты</label>
                    ${addPersonBtn}
                  </div>
                  <div data-partner-contact-people>${contactsSection}</div>
                </div>
              </div>
              <div class="d-flex flex-wrap gap-2 align-items-center" ${actionsAttrString}>
                ${actionButtons.join('\n')}
              </div>
              ${deletedHint}
            </div>
          </div>
        </div>
      `;
    }

    function getPartnerContactScopeKey(item) {
      const type = item && typeof item.contact_type === 'string' ? item.contact_type : '';
      const normalizedType = type === 'ka' ? 'partner' : type;
      if (normalizedType && elements.sections.has(normalizedType)) {
        return normalizedType;
      }
      if (elements.sections.has('partner')) {
        return 'partner';
      }
      const iterator = elements.sections.keys();
      const first = iterator.next();
      return first && !first.done ? first.value : null;
    }

    function renderPartnerContacts() {
      if (!elements.sections.size) return;
      const items = getPartnerContactRenderItems();
      const activeKeys = new Set();
      items.forEach((item) => {
        const key = getPartnerContactItemKey(item);
        if (key) {
          activeKeys.add(key);
          if (!state.detailsState.has(key)) {
            state.detailsState.set(key, 'expanded');
          }
        }
      });
      Array.from(state.detailsState.keys()).forEach((key) => {
        if (!activeKeys.has(key)) {
          state.detailsState.delete(key);
        }
      });
      const grouped = new Map();
      items.forEach((item) => {
        const scope = getPartnerContactScopeKey(item);
        if (!scope) return;
        if (!grouped.has(scope)) {
          grouped.set(scope, []);
        }
        grouped.get(scope).push(item);
      });
      elements.sections.forEach(({ container, emptyPlaceholder }, scope) => {
        if (!container) return;
        const scopedItems = grouped.get(scope) || [];
        if (!scopedItems.length) {
          container.innerHTML = '';
          if (emptyPlaceholder) {
            emptyPlaceholder.classList.remove('d-none');
          }
          return;
        }
        container.innerHTML = scopedItems
          .map((item) => renderPartnerContactCard(item, { mode: 'summary' }))
          .join('');
        container
          .querySelectorAll('[data-partner-contact-comm-field="value"][data-comm-type="phone"]')
          .forEach((input) => updatePhoneInputFormatting(input));
        container
          .querySelectorAll('[data-partner-contact-comm-field="value"][data-comm-type="email"]')
          .forEach((input) => validateEmailInput(input));
        container
          .querySelectorAll('[data-partner-contact-person-field="phone"]')
          .forEach((input) => updatePhoneInputFormatting(input));
        container
          .querySelectorAll('[data-partner-contact-person-field="email"]')
          .forEach((input) => validateEmailInput(input));
        if (emptyPlaceholder) {
          emptyPlaceholder.classList.add('d-none');
        }
      });
      if (state.editorState && state.editorState.key) {
        const key = state.editorState.key;
        setPartnerContactCardVisibility(key, false);
        const rendered = renderPartnerContactModalContentByKey(key);
        if (!rendered && elements.editorModalEl) {
          requestClose(editor.body);
        } else {
          updatePartnerContactEditorTitleByKey(key);
        }
      }
    }

    function renderPartnerContactsSettingsModal() {
      renderPartnerContacts();
    }

    function resetPartnerContactEditorSettingsModal() {
      if (state.editorState && state.editorState.key) {
        const key = state.editorState.key;
        if (key.startsWith('draft:')) {
          const draftId = key.slice('draft:'.length);
          const exists = state.drafts.some((draft) => draft.draftId === draftId);
          if (exists) {
            removePartnerContactDraft(draftId);
          }
        } else {
          setPartnerContactCardVisibility(key, true);
        }
      }
      if (editor.body) {
        editor.body.innerHTML =
          '<div class="text-center text-muted py-5">Выберите контакт из списка для редактирования.</div>';
      }
      clearPartnerContactEditorActions();
      state.editorState = null;
      renderPartnerContacts();
    }

    function resetPartnerContactsSettingsModal() {
      state.drafts = [];
      state.draftCounter = 0;
      state.edits.clear();
      state.detailsState.clear();
      renderPartnerContacts();
    }

    function clearPartnerContactEdits() {
      state.edits.clear();
    }

    function addPartnerContactDraft(initial = {}) {
      state.draftCounter += 1;
      const servedEntities = Array.isArray(initial.served_legal_entities) && initial.served_legal_entities.length
        ? clonePartnerContactServedEntities(initial.served_legal_entities)
        : [];
      let servedId = Number.isFinite(initial.served_legal_entity_id)
        ? initial.served_legal_entity_id
        : Number.parseInt(initial.served_legal_entity_id, 10);
      servedId = Number.isFinite(servedId) && servedId > 0 ? servedId : null;
      let servedName = typeof initial.served_legal_entity_name === 'string' ? initial.served_legal_entity_name : '';
      if (!servedEntities.length && Number.isFinite(servedId)) {
        servedEntities.push({ id: servedId, name: servedName });
      }
      if (servedEntities.length) {
        const first = servedEntities[0];
        if (first) {
          if (!Number.isFinite(servedId) || servedId <= 0) {
            servedId = Number.isFinite(first.id) ? first.id : servedId;
          }
          if (!servedName && typeof first.name === 'string') {
            servedName = first.name;
          }
        }
      }
      const draft = {
        draftId: `draft-${state.draftCounter}`,
        isDraft: true,
        value: typeof initial.value === 'string' ? initial.value : '',
        state: getParameterStates()[0],
        contact_type: initial.contact_type && Object.prototype.hasOwnProperty.call(getPartnerContactTypes(), initial.contact_type)
          ? initial.contact_type
          : 'partner',
        phones: Array.isArray(initial.phones) && initial.phones.length
          ? clonePartnerContactCommList(initial.phones)
          : [],
        emails: Array.isArray(initial.emails) && initial.emails.length
          ? clonePartnerContactCommList(initial.emails)
          : [],
        contacts: Array.isArray(initial.contacts) && initial.contacts.length
          ? clonePartnerContactPeople(initial.contacts)
          : [createBlankPartnerContactPersonEntry()],
        served_legal_entities: servedEntities,
        served_legal_entity_id: Number.isFinite(servedId) ? servedId : null,
        served_legal_entity_name: typeof servedName === 'string' ? servedName : '',
        legal_entity: typeof initial.legal_entity === 'string' ? initial.legal_entity : '',
        internal_name: typeof initial.internal_name === 'string' ? initial.internal_name : '',
        inn: typeof initial.inn === 'string' ? extractDigits(initial.inn) || initial.inn : '',
        is_deleted: false,
        usage_count: 0,
      };
      state.drafts.push(draft);
      renderPartnerContacts();
      return draft;
    }

    function removePartnerContactDraft(draftId) {
      const index = state.drafts.findIndex((draft) => draft.draftId === draftId);
      if (index === -1) return;
      const removed = state.drafts.splice(index, 1)[0];
      if (removed) {
        const key = getPartnerContactItemKey(removed);
        if (key) {
          state.detailsState.delete(key);
        }
      }
      renderPartnerContacts();
    }

    function updatePartnerContactDraftValue(draftId, field, value) {
      const draft = state.drafts.find((item) => item.draftId === draftId);
      if (!draft) return;
      if (field === 'value') {
        draft.value = value;
        draft.legal_entity = value;
      } else if (field === 'internal_name') {
        draft.internal_name = value;
      } else if (field === 'inn') {
        const digits = extractDigits(value);
        draft.inn = digits || (typeof value === 'string' ? value.trim() : '');
      } else if (field === 'state') {
        draft.state = getParameterStates().includes(value) ? value : draft.state;
      } else if (field === 'contact_type') {
        draft.contact_type = Object.prototype.hasOwnProperty.call(getPartnerContactTypes(), value) ? value : 'partner';
        if (draft.contact_type !== 'ka') {
          draft.served_legal_entity_id = null;
          draft.served_legal_entity_name = '';
          draft.served_legal_entities = [];
        }
      } else if (field === 'served_legal_entities') {
        const list = Array.isArray(value) ? value : [];
        const normalized = clonePartnerContactServedEntities(list);
        draft.served_legal_entities = normalized;
        const first = normalized[0];
        draft.served_legal_entity_id = first ? first.id : null;
        draft.served_legal_entity_name = first ? first.name : '';
      } else if (field === 'served_legal_entity_id') {
        draft.served_legal_entity_id = value;
      } else if (field === 'served_legal_entity_name') {
        draft.served_legal_entity_name = value;
      }
    }

    function updatePartnerContactDraftComm(draftId, kind, entryKey, field, value) {
      const draft = state.drafts.find((item) => item.draftId === draftId);
      if (!draft) return;
      const list = kind === 'phone' ? draft.phones : draft.emails;
      if (!Array.isArray(list)) return;
      const entry = list.find((item) => item.key === entryKey);
      if (!entry) return;
      if (field === 'type') {
        const allowed = kind === 'phone' ? getPartnerContactPhoneTypes() : getPartnerContactEmailTypes();
        entry.type = Object.prototype.hasOwnProperty.call(allowed, value) ? value : entry.type;
      } else if (field === 'value') {
        entry.value = kind === 'phone' ? extractDigits(value) : value;
      } else if (field === 'extension' && kind === 'phone') {
        entry.extension = extractDigits(value);
      }
    }

    function modifyPartnerContactDraftComm(draftId, kind, action, entryKey) {
      const draft = state.drafts.find((item) => item.draftId === draftId);
      if (!draft) return;
      const list = kind === 'phone' ? draft.phones : draft.emails;
      if (!Array.isArray(list)) return;
      if (action === 'add') {
        list.push(createBlankPartnerContactCommEntry(kind));
      } else if (action === 'remove') {
        const index = list.findIndex((item) => item.key === entryKey);
        if (index !== -1) {
          list.splice(index, 1);
        }
      }
    }

    function updatePartnerContactEditField(id, field, value) {
      const edit = ensurePartnerContactEdit(id);
      if (!edit) return;
      if (field === 'value') {
        edit.value = value;
        edit.legal_entity = value;
      } else if (field === 'internal_name') {
        edit.internal_name = value;
      } else if (field === 'inn') {
        const digits = extractDigits(value);
        edit.inn = digits || (typeof value === 'string' ? value.trim() : '');
      } else if (field === 'state') {
        edit.state = getParameterStates().includes(value) ? value : edit.state;
      } else if (field === 'contact_type') {
        edit.contact_type = Object.prototype.hasOwnProperty.call(getPartnerContactTypes(), value) ? value : edit.contact_type;
        if (edit.contact_type !== 'ka') {
          edit.served_legal_entity_id = null;
          edit.served_legal_entity_name = '';
          edit.served_legal_entities = [];
        }
      } else if (field === 'served_legal_entities') {
        const list = Array.isArray(value) ? value : [];
        const normalized = clonePartnerContactServedEntities(list);
        edit.served_legal_entities = normalized;
        const first = normalized[0];
        edit.served_legal_entity_id = first ? first.id : null;
        edit.served_legal_entity_name = first ? first.name : '';
      } else if (field === 'served_legal_entity_id') {
        edit.served_legal_entity_id = value;
      } else if (field === 'served_legal_entity_name') {
        edit.served_legal_entity_name = value;
      }
    }

    function updatePartnerContactEditComm(id, kind, entryKey, field, value) {
      const edit = ensurePartnerContactEdit(id);
      if (!edit) return;
      const list = kind === 'phone' ? edit.phones : edit.emails;
      if (!Array.isArray(list)) return;
      const entry = list.find((item) => item.key === entryKey);
      if (!entry) return;
      if (field === 'type') {
        const allowed = kind === 'phone' ? getPartnerContactPhoneTypes() : getPartnerContactEmailTypes();
        entry.type = Object.prototype.hasOwnProperty.call(allowed, value) ? value : entry.type;
      } else if (field === 'value') {
        entry.value = kind === 'phone' ? extractDigits(value) : value;
      } else if (field === 'extension' && kind === 'phone') {
        entry.extension = extractDigits(value);
      }
    }

    function modifyPartnerContactEditComm(id, kind, action, entryKey) {
      const edit = ensurePartnerContactEdit(id);
      if (!edit) return;
      const list = kind === 'phone' ? edit.phones : edit.emails;
      if (!Array.isArray(list)) return;
      if (action === 'add') {
        list.push(createBlankPartnerContactCommEntry(kind));
      } else if (action === 'remove') {
        const index = list.findIndex((item) => item.key === entryKey);
        if (index !== -1) {
          list.splice(index, 1);
        }
      }
    }

    function updatePartnerContactDraftPerson(draftId, personKey, field, value) {
      const draft = state.drafts.find((item) => item.draftId === draftId);
      if (!draft || !Array.isArray(draft.contacts)) return;
      const person = draft.contacts.find((entry) => entry.key === personKey);
      if (!person) return;
      if (field === 'full_name' || field === 'position') {
        person[field] = value;
      } else if (field === 'phone') {
        person.phone = value;
      } else if (field === 'email') {
        person.email = value;
      } else if (field === 'is_top') {
        person.is_top = Boolean(value);
      }
    }

    function updatePartnerContactEditPerson(id, personKey, field, value) {
      const edit = ensurePartnerContactEdit(id);
      if (!edit || !Array.isArray(edit.contacts)) return;
      const person = edit.contacts.find((entry) => entry.key === personKey);
      if (!person) return;
      if (field === 'full_name' || field === 'position') {
        person[field] = value;
      } else if (field === 'phone') {
        person.phone = value;
      } else if (field === 'email') {
        person.email = value;
      } else if (field === 'is_top') {
        person.is_top = Boolean(value);
      }
    }

    function modifyPartnerContactDraftPerson(draftId, action, personKey) {
      const draft = state.drafts.find((item) => item.draftId === draftId);
      if (!draft) return;
      if (!Array.isArray(draft.contacts)) {
        draft.contacts = [];
      }
      if (action === 'add') {
        draft.contacts.unshift(createBlankPartnerContactPersonEntry());
      } else if (action === 'remove') {
        const index = draft.contacts.findIndex((entry) => entry.key === personKey);
        if (index !== -1) {
          draft.contacts.splice(index, 1);
        }
      }
    }

    function modifyPartnerContactEditPerson(id, action, personKey) {
      const edit = ensurePartnerContactEdit(id);
      if (!edit) return;
      if (!Array.isArray(edit.contacts)) {
        edit.contacts = [];
      }
      if (action === 'add') {
        edit.contacts.unshift(createBlankPartnerContactPersonEntry());
      } else if (action === 'remove') {
        const index = edit.contacts.findIndex((entry) => entry.key === personKey);
        if (index !== -1) {
          edit.contacts.splice(index, 1);
        }
      }
    }

    function resetPartnerContactEdit(id) {
      if (!Number.isFinite(id)) return;
      if (!state.edits.has(id)) return;
      state.edits.delete(id);
      renderPartnerContacts();
    }

    function getPartnerContactCardData(card) {
      if (!card || !card.dataset.partnerContactCard) return null;
      if (card.dataset.partnerContactDraft === 'true') {
        const draftId = card.dataset.partnerContactDraftId;
        const draft = state.drafts.find((item) => item.draftId === draftId);
        return draft ? clonePartnerContactItem(draft) : null;
      }
      const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
      if (!Number.isFinite(id)) return null;
      const edit = ensurePartnerContactEdit(id);
      return edit ? clonePartnerContactItem(edit) : null;
    }

    function sanitizePartnerContactPayload(data) {
      const result = { contact_type: data.contact_type };
      result.value = typeof data.value === 'string' ? data.value.trim() : '';
      result.state = typeof data.state === 'string' ? data.state : getParameterStates()[0];
      result.inn = extractDigits(data.inn) || (typeof data.inn === 'string' ? data.inn.trim() : '');
      const fallbackPhoneType = Object.keys(getPartnerContactPhoneTypes())[0] || 'work';
      result.phones = Array.isArray(data.phones)
        ? data.phones
            .map((item) => {
              const valueDigits = extractDigits(item && item.value);
              const extensionDigits = extractDigits(item && item.extension);
              const typeKey = item && item.type;
              const type = Object.prototype.hasOwnProperty.call(getPartnerContactPhoneTypes(), typeKey)
                ? typeKey
                : fallbackPhoneType;
              return {
                type,
                value: valueDigits,
                extension: extensionDigits,
              };
            })
            .filter((item) => item.value)
        : [];
      const fallbackEmailType = Object.keys(getPartnerContactEmailTypes())[0] || 'work';
      result.emails = Array.isArray(data.emails)
        ? data.emails
            .map((item) => {
              const typeKey = item && item.type;
              const type = Object.prototype.hasOwnProperty.call(getPartnerContactEmailTypes(), typeKey)
                ? typeKey
                : fallbackEmailType;
              return {
                type,
                value: typeof item.value === 'string' ? item.value.trim() : '',
              };
            })
            .filter((item) => item.value)
        : [];
      result.contacts = Array.isArray(data.contacts)
        ? data.contacts
            .map((person) => ({
              full_name: typeof person.full_name === 'string' ? person.full_name.trim() : '',
              position: typeof person.position === 'string' ? person.position.trim() : '',
              phone: typeof person.phone === 'string' ? person.phone.trim() : '',
              email: typeof person.email === 'string' ? person.email.trim() : '',
              is_top: Boolean(person.is_top),
            }))
            .filter((person) => person.full_name || person.position || person.phone || person.email)
        : [];
      if (!result.phones.length && Array.isArray(result.contacts)) {
        result.contacts.forEach((person) => {
          const digits = extractDigits(person.phone);
          if (digits) {
            result.phones.push({ type: fallbackPhoneType, value: digits, extension: '' });
          }
        });
      }
      if (!result.emails.length && Array.isArray(result.contacts)) {
        result.contacts.forEach((person) => {
          if (person.email) {
            result.emails.push({ type: fallbackEmailType, value: person.email });
          }
        });
      }
      result.internal_name = typeof data.internal_name === 'string' ? data.internal_name.trim() : '';
      const servedEntities = Array.isArray(data.served_legal_entities)
        ? data.served_legal_entities
            .map((entry) => {
              if (!entry || typeof entry !== 'object') return null;
              const id = Number.isFinite(entry.id) ? entry.id : Number.parseInt(entry.id, 10);
              if (!Number.isFinite(id) || id <= 0) return null;
              const name = typeof entry.name === 'string' ? entry.name.trim() : '';
              return { id, name };
            })
            .filter((entry) => entry)
        : [];
      result.served_legal_entities = servedEntities;
      result.served_legal_entity_ids = servedEntities.map((entry) => entry.id);
      result.served_legal_entity_names = servedEntities.map((entry) => entry.name).filter((name) => name);
      const explicitServedId = Number.isFinite(data.served_legal_entity_id)
        ? data.served_legal_entity_id
        : Number.parseInt(data.served_legal_entity_id, 10);
      const fallbackServed = servedEntities[0];
      const normalizedServedId = Number.isFinite(explicitServedId) && explicitServedId > 0
        ? explicitServedId
        : fallbackServed
          ? fallbackServed.id
          : null;
      const explicitServedName = typeof data.served_legal_entity_name === 'string' ? data.served_legal_entity_name : '';
      const normalizedServedName = fallbackServed ? fallbackServed.name : explicitServedName;
      result.served_legal_entity_id = Number.isFinite(normalizedServedId) ? normalizedServedId : null;
      result.served_legal_entity_name = normalizedServedName || '';
      return result;
    }

    async function handlePartnerContactSave(card, trigger) {
      const isDraft = card && card.dataset.partnerContactDraft === 'true';
      const data = getPartnerContactCardData(card);
      if (!data) {
        popup('Не удалось получить данные контакта');
        return;
      }
      const payload = sanitizePartnerContactPayload(data);
      if (!payload.value) {
        popup('Поле «ЮЛ» не может быть пустым');
        return;
      }
      const servedIds = Array.isArray(payload.served_legal_entity_ids) ? payload.served_legal_entity_ids : [];
      if (payload.contact_type === 'ka' && !servedIds.length) {
        popup('Для типа «КА» необходимо выбрать одно или несколько обслуживаемых ЮЛ');
        return;
      }
      const invalidEmail = Array.isArray(payload.emails)
        ? payload.emails.find((item) => item && item.value && !EMAIL_VALIDATION_REGEX.test(item.value))
        : null;
      if (invalidEmail) {
        popup('Проверьте e-mail: необходимо указать символ @ и доменное имя.');
        return;
      }

      const originalText = trigger.textContent;
      trigger.disabled = true;
      trigger.textContent = '...';

      try {
        let response;
        if (isDraft) {
          const requestPayload = Object.assign({
            param_type: 'partner_contact',
            value: payload.value,
            state: payload.state,
          }, payload);
          response = await fetch('/api/settings/parameters', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestPayload),
          });
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (!Number.isFinite(id)) {
            throw new Error('Не удалось определить идентификатор контакта');
          }
          const requestPayload = {
            value: payload.value,
            contact_type: payload.contact_type,
            phones: payload.phones,
            emails: payload.emails,
            contacts: payload.contacts,
            internal_name: payload.internal_name,
            inn: payload.inn,
            served_legal_entities: payload.served_legal_entities,
            served_legal_entity_ids: payload.served_legal_entity_ids,
            served_legal_entity_names: payload.served_legal_entity_names,
            served_legal_entity_id: payload.served_legal_entity_id,
            served_legal_entity_name: payload.served_legal_entity_name,
            state: payload.state,
          };
          response = await fetch(`/api/settings/parameters/${id}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestPayload),
          });
        }
        const result = await response.json();
        if (!response.ok || result.success === false) {
          throw new Error(result.error || 'Ошибка сохранения контакта');
        }
        if (isDraft) {
          const draftId = card.dataset.partnerContactDraftId;
          if (draftId) {
            const index = state.drafts.findIndex((item) => item.draftId === draftId);
            if (index !== -1) {
              state.drafts.splice(index, 1);
            }
          }
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            state.edits.delete(id);
          }
        }
        syncParameterData((result && result.data) || getParameterData());
        rerenderParameters();
        if (elements.editorModalEl && elements.editorModalEl.contains(card)) {
          requestClose(card);
        }
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        trigger.disabled = false;
        trigger.textContent = originalText;
      }
    }

    function handleDocumentClick(event) {
      const detailsToggleBtn = event.target.closest('[data-partner-contact-details-toggle]');
      if (detailsToggleBtn) {
        const container = detailsToggleBtn.closest('[data-partner-contact-details]');
        if (!container) return;
        let key = container.dataset.detailsKey;
        if (!key) {
          const card = container.closest('[data-partner-contact-card]');
          key = getPartnerContactCardKey(card);
        }
        const isCollapsed = container.classList.contains('is-collapsed');
        if (isCollapsed) {
          container.classList.remove('is-collapsed');
          container.classList.add('is-expanded');
          detailsToggleBtn.setAttribute('aria-expanded', 'true');
          if (key) {
            state.detailsState.set(key, 'expanded');
          }
        } else {
          container.classList.add('is-collapsed');
          container.classList.remove('is-expanded');
          detailsToggleBtn.setAttribute('aria-expanded', 'false');
          if (key) {
            state.detailsState.set(key, 'collapsed');
          }
        }
        event.preventDefault();
        return;
      }

      const peopleToggleBtn = event.target.closest('[data-partner-contact-people-toggle]');
      if (peopleToggleBtn) {
        const wrapper = peopleToggleBtn.closest('[data-partner-contact-people-wrapper]');
        if (!wrapper) return;
        const hiddenContainer = wrapper.querySelector('[data-partner-contact-people-extra]');
        if (!hiddenContainer) return;
        const isHidden = hiddenContainer.classList.contains('d-none');
        if (isHidden) {
          hiddenContainer.classList.remove('d-none');
          peopleToggleBtn.setAttribute('aria-expanded', 'true');
          const expandedText = peopleToggleBtn.dataset.expandedText;
          if (expandedText) {
            peopleToggleBtn.textContent = expandedText;
          }
        } else {
          hiddenContainer.classList.add('d-none');
          peopleToggleBtn.setAttribute('aria-expanded', 'false');
          const collapsedText = peopleToggleBtn.dataset.collapsedText;
          if (collapsedText) {
            peopleToggleBtn.textContent = collapsedText;
          }
        }
        return;
      }

      const partnerContactActionBtn = event.target.closest('[data-partner-contact-action]');
      if (partnerContactActionBtn) {
        let card = partnerContactActionBtn.closest('[data-partner-contact-card]');
        if (!card) {
          const targetKey = partnerContactActionBtn.dataset.partnerContactActionTarget;
          if (targetKey) {
            if (editor.body) {
              const selector = `[data-partner-contact-card][data-partner-contact-key="${cssEscape(targetKey)}"]`;
              card = editor.body.querySelector(selector);
            }
            if (!card) {
              card = findPartnerContactCardByKey(targetKey);
            }
          }
        }
        if (!card) return;
        const action = partnerContactActionBtn.dataset.partnerContactAction;
        if (action === 'save') {
          handlePartnerContactSave(card, partnerContactActionBtn);
        } else if (action === 'cancel') {
          const draftId = card.dataset.partnerContactDraftId;
          if (draftId) {
            removePartnerContactDraft(draftId);
          }
        } else if (action === 'reset') {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            resetPartnerContactEdit(id);
          }
        } else if (action === 'delete') {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            deleteParameter(id, 'partner_contact', partnerContactActionBtn);
          }
        } else if (action === 'restore') {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            restoreParameter(id, 'partner_contact', partnerContactActionBtn);
          }
        }
        return;
      }

      const partnerContactAddCommBtn = event.target.closest('[data-partner-contact-add-comm]');
      if (partnerContactAddCommBtn) {
        const card = partnerContactAddCommBtn.closest('[data-partner-contact-card]');
        if (!card) return;
        const kind = partnerContactAddCommBtn.dataset.commType;
        if (!kind) return;
        if (card.dataset.partnerContactDraft === 'true') {
          modifyPartnerContactDraftComm(card.dataset.partnerContactDraftId, kind, 'add');
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            modifyPartnerContactEditComm(id, kind, 'add');
          }
        }
        renderPartnerContacts();
        return;
      }

      const partnerContactAddPersonBtn = event.target.closest('[data-partner-contact-add-person]');
      if (partnerContactAddPersonBtn) {
        const card = partnerContactAddPersonBtn.closest('[data-partner-contact-card]');
        if (!card) return;
        if (card.dataset.partnerContactDraft === 'true') {
          modifyPartnerContactDraftPerson(card.dataset.partnerContactDraftId, 'add');
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            modifyPartnerContactEditPerson(id, 'add');
          }
        }
        renderPartnerContacts();
        return;
      }

      const partnerContactRemovePersonBtn = event.target.closest('[data-partner-contact-remove-person]');
      if (partnerContactRemovePersonBtn) {
        const card = partnerContactRemovePersonBtn.closest('[data-partner-contact-card]');
        if (!card) return;
        const personKey = partnerContactRemovePersonBtn.dataset.personKey;
        if (!personKey) return;
        if (card.dataset.partnerContactDraft === 'true') {
          modifyPartnerContactDraftPerson(card.dataset.partnerContactDraftId, 'remove', personKey);
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            modifyPartnerContactEditPerson(id, 'remove', personKey);
          }
        }
        renderPartnerContacts();
        return;
      }

      const partnerContactRemoveCommBtn = event.target.closest('[data-partner-contact-remove-comm]');
      if (partnerContactRemoveCommBtn) {
        const card = partnerContactRemoveCommBtn.closest('[data-partner-contact-card]');
        if (!card) return;
        const kind = partnerContactRemoveCommBtn.dataset.commType;
        const entryKey = partnerContactRemoveCommBtn.dataset.entryKey;
        if (!kind || !entryKey) return;
        if (card.dataset.partnerContactDraft === 'true') {
          modifyPartnerContactDraftComm(card.dataset.partnerContactDraftId, kind, 'remove', entryKey);
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            modifyPartnerContactEditComm(id, kind, 'remove', entryKey);
          }
        }
        renderPartnerContacts();
      }
    }

    function handleDocumentInput(event) {
      const card = event.target.closest('[data-partner-contact-card]');
      if (!card) return;
      const personField = event.target.dataset.partnerContactPersonField;
      if (personField) {
        const row = event.target.closest('[data-partner-contact-person]');
        if (!row) return;
        const personKey = row.dataset.partnerContactPerson;
        if (!personKey) return;
        let value;
        if (personField === 'is_top') {
          value = event.target.checked;
        } else {
          value = event.target.value;
        }
        if (personField === 'phone') {
          updatePhoneInputFormatting(event.target);
          value = event.target.dataset.phoneDigits || extractDigits(event.target.value);
        } else if (personField === 'email') {
          validateEmailInput(event.target);
          value = typeof event.target.value === 'string' ? event.target.value.trim() : '';
        }
        if (card.dataset.partnerContactDraft === 'true') {
          updatePartnerContactDraftPerson(card.dataset.partnerContactDraftId, personKey, personField, value);
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            updatePartnerContactEditPerson(id, personKey, personField, value);
          }
        }
        refreshPartnerContactPersonRow(row);
        return;
      }
      const field = event.target.dataset.partnerContactField;
      if (field === 'value' || field === 'internal_name' || field === 'inn') {
        let value = event.target.value;
        if (field === 'inn') {
          const digits = extractDigits(value);
          event.target.value = digits;
          value = digits;
        }
        if (card.dataset.partnerContactDraft === 'true') {
          updatePartnerContactDraftValue(card.dataset.partnerContactDraftId, field, value);
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            updatePartnerContactEditField(id, field, value);
          }
        }
        if (field === 'value' || field === 'internal_name') {
          refreshPartnerContactCardTitle(card);
        }
        return;
      }
      const commField = event.target.dataset.partnerContactCommField;
      if (commField === 'value' || commField === 'extension') {
        const kind = event.target.dataset.commType;
        const entryKey = event.target.dataset.entryKey;
        if (!kind || !entryKey) return;
        let value = event.target.value;
        if (kind === 'phone') {
          if (commField === 'value') {
            updatePhoneInputFormatting(event.target);
            value = event.target.dataset.phoneDigits || extractDigits(event.target.value);
          } else {
            enforceExtensionDigits(event.target);
            value = event.target.value;
          }
        } else if (kind === 'email' && commField === 'value') {
          validateEmailInput(event.target);
        }
        if (card.dataset.partnerContactDraft === 'true') {
          updatePartnerContactDraftComm(card.dataset.partnerContactDraftId, kind, entryKey, commField, value);
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            updatePartnerContactEditComm(id, kind, entryKey, commField, value);
          }
        }
      }
    }

    function handleDocumentChange(event) {
      const card = event.target.closest('[data-partner-contact-card]');
      if (!card) return;
      const personField = event.target.dataset.partnerContactPersonField;
      if (personField === 'is_top') {
        const row = event.target.closest('[data-partner-contact-person]');
        if (!row) return;
        const personKey = row.dataset.partnerContactPerson;
        if (!personKey) return;
        const value = event.target.checked;
        if (card.dataset.partnerContactDraft === 'true') {
          updatePartnerContactDraftPerson(card.dataset.partnerContactDraftId, personKey, personField, value);
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            updatePartnerContactEditPerson(id, personKey, personField, value);
          }
        }
        refreshPartnerContactPersonRow(row);
        return;
      }
      const field = event.target.dataset.partnerContactField;
      if (field === 'contact_type') {
        const value = event.target.value;
        if (card.dataset.partnerContactDraft === 'true') {
          updatePartnerContactDraftValue(card.dataset.partnerContactDraftId, field, value);
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            updatePartnerContactEditField(id, field, value);
          }
        }
        const wrapper = card.querySelector('[data-partner-contact-served-wrapper]');
        if (wrapper) {
          wrapper.classList.toggle('d-none', value !== 'ka');
          const select = wrapper.querySelector('[data-partner-contact-field="served_legal_entities"]');
          if (select && value !== 'ka') {
            Array.from(select.options).forEach((option) => {
              option.selected = false;
            });
          }
        }
        updatePartnerContactServedSummary(card);
        const data = getPartnerContactCardData(card);
        const subtitle = card.querySelector('[data-partner-contact-subtitle]');
        const types = getPartnerContactTypes();
        const parameterStates = getParameterStates();
        if (subtitle && data) {
          const typeLabel = types[data.contact_type] || types.partner;
          subtitle.textContent = `${typeLabel} • ${data.state || parameterStates[0]}`;
        }
        updatePartnerContactCardHeaderState(card, data ? data.state || parameterStates[0] : parameterStates[0]);
        return;
      }
      if (field === 'state') {
        const stateValue = event.target.value;
        if (card.dataset.partnerContactDraft === 'true') {
          updatePartnerContactDraftValue(card.dataset.partnerContactDraftId, field, stateValue);
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            updatePartnerContactEditField(id, field, stateValue);
          }
        }
        const data = getPartnerContactCardData(card);
        const subtitle = card.querySelector('[data-partner-contact-subtitle]');
        const types = getPartnerContactTypes();
        if (subtitle && data) {
          const typeLabel = types[data.contact_type] || types.partner;
          subtitle.textContent = `${typeLabel} • ${data.state || getParameterStates()[0]}`;
        }
        updatePartnerContactCardHeaderState(card, data ? data.state || stateValue : stateValue);
        return;
      }
      if (field === 'served_legal_entities') {
        const select = event.target;
        const selections = Array.from(select.selectedOptions)
          .map((option) => {
            const id = Number.parseInt(option.value, 10);
            if (!Number.isFinite(id) || id <= 0) return null;
            const label = option.dataset.label || option.textContent || '';
            return { id, name: label };
          })
          .filter((entry) => entry);
        if (card.dataset.partnerContactDraft === 'true') {
          updatePartnerContactDraftValue(card.dataset.partnerContactDraftId, 'served_legal_entities', selections);
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            updatePartnerContactEditField(id, 'served_legal_entities', selections);
          }
        }
        updatePartnerContactServedSummary(card, { served_legal_entities: selections });
        return;
      }
      if (field === 'served_legal_entity_id') {
        const select = event.target;
        const option = select.options[select.selectedIndex];
        const label = option ? option.dataset.label || option.textContent : '';
        const idValue = select.value ? Number.parseInt(select.value, 10) : null;
        if (card.dataset.partnerContactDraft === 'true') {
          updatePartnerContactDraftValue(card.dataset.partnerContactDraftId, field, Number.isFinite(idValue) ? idValue : null);
          updatePartnerContactDraftValue(card.dataset.partnerContactDraftId, 'served_legal_entity_name', label || '');
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            updatePartnerContactEditField(id, field, Number.isFinite(idValue) ? idValue : null);
            updatePartnerContactEditField(id, 'served_legal_entity_name', label || '');
          }
        }
        updatePartnerContactServedSummary(card);
        return;
      }
      const commField = event.target.dataset.partnerContactCommField;
      if (commField === 'type') {
        const kind = event.target.dataset.commType;
        const entryKey = event.target.dataset.entryKey;
        if (!kind || !entryKey) return;
        const value = event.target.value;
        if (card.dataset.partnerContactDraft === 'true') {
          updatePartnerContactDraftComm(card.dataset.partnerContactDraftId, kind, entryKey, commField, value);
        } else {
          const id = Number.parseInt(card.dataset.partnerContactId || card.dataset.paramId, 10);
          if (Number.isFinite(id)) {
            updatePartnerContactEditComm(id, kind, entryKey, commField, value);
          }
        }
      }
    }

    function bindEvents() {
      document.addEventListener('click', handleDocumentClick);
      document.addEventListener('input', handleDocumentInput);
      document.addEventListener('change', handleDocumentChange);
    }

    bindEvents();

    return {
      clearPartnerContactEdits,
      normalizePartnerContactExtra,
      preparePartnerContactDraftSettingsTrigger,
      preparePartnerContactEditorSettingsTrigger,
      renderPartnerContacts,
      renderPartnerContactsSettingsModal,
      resetPartnerContactEditorSettingsModal,
      resetPartnerContactsSettingsModal,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsPartnerContactsRuntime) {
        return window.__settingsPartnerContactsRuntime;
      }
      const runtime = createRuntime(options);
      window.SettingsPageCallbackRegistry?.registerMany({
        preparePartnerContactEditorSettingsTrigger: runtime.preparePartnerContactEditorSettingsTrigger,
        preparePartnerContactDraftSettingsTrigger: runtime.preparePartnerContactDraftSettingsTrigger,
        renderPartnerContactsSettingsModal: runtime.renderPartnerContactsSettingsModal,
        resetPartnerContactEditorSettingsModal: runtime.resetPartnerContactEditorSettingsModal,
        resetPartnerContactsSettingsModal: runtime.resetPartnerContactsSettingsModal,
      });
      window.__settingsPartnerContactsRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsPartnerContactsRuntime = Object.freeze(api);
}());
