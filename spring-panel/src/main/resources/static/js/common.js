// static/common.js

function getCookieValue(name) {
    if (typeof document === 'undefined') {
        return '';
    }
    const cookies = document.cookie ? document.cookie.split(';') : [];
    const encodedName = encodeURIComponent(name) + '=';
    for (const cookie of cookies) {
        const trimmed = cookie.trim();
        if (trimmed.startsWith(encodedName)) {
            return decodeURIComponent(trimmed.slice(encodedName.length));
        }
    }
    return '';
}

function getCsrfTokenFromDom() {
  // 1) meta
  const meta = document.querySelector('meta[name="_csrf"]');
  if (meta && meta.content) return meta.content;

  // 2) hidden input
  const input = document.querySelector('input[name="_csrf"]');
  if (input && input.value) return input.value;

  return '';
}

function attachCsrfToken(init = {}) {
  const method = (init.method || 'GET').toUpperCase();
  if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    return init;
  }

  const token = getCsrfTokenFromDom() || getCookieValue('XSRF-TOKEN');
  if (!token) return init;

  const headers = new Headers(init.headers || {});
  headers.set('X-XSRF-TOKEN', token);

  // важно: чтобы SESSION cookie точно уходила
  const credentials = init.credentials || 'same-origin';

  return { ...init, headers, credentials };
}

if (typeof window !== 'undefined' && window.fetch) {
    const originalFetch = window.fetch.bind(window);
    window.fetch = (input, init) => originalFetch(input, attachCsrfToken(init));
}

// Функция для показа уведомлений (например, об ошибках или успехе)
function showNotification(message, type = 'info', containerId = 'notification-container') {
    let overlay = document.getElementById(containerId);
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = containerId;
        document.body.appendChild(overlay);
    }

    overlay.innerHTML = '';
    overlay.style.position = 'fixed';
    overlay.style.inset = '0';
    overlay.style.zIndex = '2147483647';
    overlay.style.display = 'flex';
    overlay.style.alignItems = 'center';
    overlay.style.justifyContent = 'center';
    overlay.style.padding = '1rem';
    overlay.style.background = 'rgba(15, 23, 42, 0.38)';
    overlay.style.pointerEvents = 'auto';

    const popup = document.createElement('div');
    popup.style.width = 'min(92vw, 520px)';
    popup.style.background = '#ffffff';
    popup.style.border = '1px solid rgba(15, 23, 42, 0.15)';
    popup.style.borderRadius = '0.9rem';
    popup.style.boxShadow = '0 1rem 2rem rgba(15, 23, 42, 0.28)';
    popup.style.overflow = 'hidden';

    const header = document.createElement('div');
    header.style.display = 'flex';
    header.style.alignItems = 'center';
    header.style.justifyContent = 'space-between';
    header.style.padding = '0.8rem 1rem';
    header.style.gap = '0.75rem';

    const tone = type === 'error'
        ? { title: '\u041E\u0448\u0438\u0431\u043A\u0430', bg: '#fee2e2', fg: '#991b1b' }
        : type === 'warning'
            ? { title: '\u0412\u043D\u0438\u043C\u0430\u043D\u0438\u0435', bg: '#fef3c7', fg: '#92400e' }
            : type === 'success'
                ? { title: '\u0423\u0441\u043F\u0435\u0448\u043D\u043E', bg: '#dcfce7', fg: '#166534' }
                : { title: '\u0421\u043E\u043E\u0431\u0449\u0435\u043D\u0438\u0435', bg: '#dbeafe', fg: '#1e3a8a' };
    header.style.background = tone.bg;
    header.style.color = tone.fg;

    const title = document.createElement('strong');
    title.textContent = tone.title;
    header.appendChild(title);

    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'btn-close';
    closeBtn.setAttribute('aria-label', 'Close');
    header.appendChild(closeBtn);

    const body = document.createElement('div');
    body.style.padding = '1rem';
    body.style.lineHeight = '1.45';
    body.style.color = '#0f172a';
    if (message instanceof Node) {
        body.appendChild(message);
    } else {
        body.innerHTML = String(message || '');
    }

    const footer = document.createElement('div');
    footer.style.display = 'flex';
    footer.style.justifyContent = 'flex-end';
    footer.style.padding = '0 1rem 1rem';
    const okBtn = document.createElement('button');
    okBtn.type = 'button';
    okBtn.className = 'btn btn-primary btn-sm';
    okBtn.textContent = '\u041E\u041A';
    footer.appendChild(okBtn);

    popup.appendChild(header);
    popup.appendChild(body);
    popup.appendChild(footer);
    overlay.appendChild(popup);

    let closed = false;
    const closePopup = () => {
        if (closed) return;
        closed = true;
        overlay.remove();
    };

    const autoHideMs = (type === 'error' || type === 'warning') ? 4500 : 3000;
    const timer = setTimeout(closePopup, autoHideMs);
    const closeAndClear = () => {
        clearTimeout(timer);
        closePopup();
    };

    closeBtn.addEventListener('click', closeAndClear);
    okBtn.addEventListener('click', closeAndClear);
    overlay.addEventListener('click', (event) => {
        if (event.target === overlay) {
            closeAndClear();
        }
    }, { once: true });
}

function showPopup(message, type = 'info', containerId = 'notification-container') {
    showNotification(message, type, containerId);
}

// Функция для экспорта данных (универсальная)
async function exportDataGeneric(url, format, filters = {}, exportFiltered = false, filenamePrefix = 'export') {
    const exportBtn = document.activeElement; // Предполагаем, что вызвано кнопкой
    let originalText = '';
    let originalHTML = '';

    if (exportBtn) {
        originalHTML = exportBtn.innerHTML;
        exportBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Экспорт...';
        exportBtn.disabled = true;
    }

    try {
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                format: format,
                filters: filters,
                exportFiltered: exportFiltered
            })
        });

        if (response.ok) {
            const blob = await response.blob();
            const downloadUrl = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = downloadUrl;
            const date = new Date().toISOString().slice(0, 10);
            const extension = format === 'xlsx' ? 'xlsx' : 'csv';
            a.download = `${filenamePrefix}_${date}.${extension}`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(downloadUrl);
            document.body.removeChild(a);
        } else {
            const errorText = await response.text();
            throw new Error(errorText || 'Ошибка экспорта данных');
        }
    } catch (error) {
        console.error('Export error:', error);
        showNotification(`Ошибка при экспорте данных: ${error.message}`, 'error');
    } finally {
        if (exportBtn) {
            exportBtn.innerHTML = originalHTML;
            exportBtn.disabled = false;
        }
    }
}

// Функция для инициализации Select2 на элементах
function initializeSelect2(selectorOrElements, options = {}) {
    const defaultOptions = {
        placeholder: "Выберите значения",
        allowClear: true,
        language: "ru"
    };
    const finalOptions = { ...defaultOptions, ...options };

    if (typeof selectorOrElements === 'string') {
        $(selectorOrElements).select2(finalOptions);
    } else if (selectorOrElements instanceof HTMLElement) {
        $(selectorOrElements).select2(finalOptions);
    } else if (selectorOrElements instanceof NodeList || Array.isArray(selectorOrElements)) {
        selectorOrElements.forEach(el => {
            if (el instanceof HTMLElement) {
                $(el).select2(finalOptions);
            }
        });
    }
}

function select2CheckboxTemplate(data) {
    if (!data || !data.id) {
        return data && data.text ? data.text : '';
    }

    const wrapper = document.createElement('span');
    wrapper.className = 'select2-checkbox-option';

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.disabled = true;
    checkbox.checked = Boolean(data.selected);
    checkbox.className = 'form-check-input';

    const label = document.createElement('span');
    label.className = 'select2-checkbox-label';
    label.textContent = data.text || '';

    wrapper.appendChild(checkbox);
    wrapper.appendChild(label);

    return wrapper;
}

function select2SelectionTemplate(data) {
    if (!data) return '';
    return data.text || data.id || '';
}

function syncSelect2CheckboxState(event) {
    if (!event || !event.params || !event.params.data) return;
    const resultId = event.params.data._resultId;
    if (!resultId) return;
    const optionEl = document.getElementById(resultId);
    if (!optionEl) return;
    const checkbox = optionEl.querySelector('input[type="checkbox"]');
    if (checkbox) {
        checkbox.checked = event.type === 'select2:select';
    }
}

function initSelect2WithCheckboxes(elements, options = {}) {
    if (typeof window === 'undefined' || !window.jQuery) return;
    const $ = window.jQuery;
    const $elements = elements && elements.jquery ? elements : $(elements);
    if (!$elements.length) return;

    const baseOptions = {
        width: '100%',
        allowClear: true,
        language: 'ru'
    };

    $elements.each(function initSelect2Instance() {
        const $select = $(this);
        const isMultiple = Boolean($select.prop('multiple'));
        const config = {
            ...baseOptions,
            placeholder: $select.attr('data-placeholder') || $select.attr('placeholder') || 'Выберите...'
        };

        if (options.dropdownParent) {
            config.dropdownParent = options.dropdownParent;
        }

        if (isMultiple) {
            config.closeOnSelect = false;
            config.templateResult = select2CheckboxTemplate;
            config.templateSelection = select2SelectionTemplate;
        }

        if (options.extraConfig && typeof options.extraConfig === 'object') {
            Object.assign(config, options.extraConfig);
        }

        $select.select2(config);

        if (isMultiple) {
            $select.on('select2:select select2:unselect', syncSelect2CheckboxState);
        }
    });
}

window.initSelect2WithCheckboxes = initSelect2WithCheckboxes;

// Функция для создания бейджа фильтра
function createFilterBadge(label, value, filterKey, filterValue = null) {
    const badge = document.createElement('span');
    badge.className = 'badge bg-primary filter-badge';
    // Экранируем одинарные кавычки в filterValue для onclick
    const escapedFilterValue = filterValue ? filterValue.replace(/'/g, "\\'") : '';
    badge.innerHTML = `${label}: ${value} 
        <span style="cursor:pointer; margin-left:5px;" onclick="removeFilter('${filterKey}', '${escapedFilterValue}')">×</span>`;
    return badge;
}

// Функция для получения текстового представления фильтра по количеству заявок
function getTicketCountLabel(value) {
    const labels = {
        '1': '1 заявка',
        '2': '2+ заявки',
        '5': '5+ заявок',
        '10': '10+ заявок'
    };
    return labels[value] || value;
}

// --- Функции, специфичные для аналитики, но вынесенные для модульности ---

// Функция для обновления всех карточек статистики на странице аналитики
// Требует наличия соответствующих элементов в DOM с атрибутами data-stats-card
function updateAnalyticsStatsCards(originalRecords, filteredRecords) {
    try {
        // Всего записей (из оригинальных данных)
        const totalRecords = originalRecords.length;
        const totalRecordsElement = document.querySelector('[data-stats-card="total-records"] .card-title') ||
                                    document.querySelector('[data-stats-card="total-clients"] .card-title');
        if (totalRecordsElement) totalRecordsElement.textContent = totalRecords;

        // Всего заявок (из оригинальных данных)
        let totalTicketsOverall = 0;
        originalRecords.forEach(row => {
            const cnt = parseInt(row.getAttribute('data-cnt'));
            if (!isNaN(cnt)) totalTicketsOverall += cnt;
        });
        const totalTicketsElement = document.querySelector('[data-stats-card="total-tickets"] .card-title');
        if (totalTicketsElement) totalTicketsElement.textContent = totalTicketsOverall;

        // Отфильтровано записей/клиентов
        const filteredRecordsCount = filteredRecords.length;
        const filteredRecordsElement = document.querySelector('[data-stats-card="filtered-records"] .card-title') ||
                                       document.querySelector('[data-stats-card="filtered-clients"] .card-title');
        if (filteredRecordsElement) filteredRecordsElement.textContent = filteredRecordsCount;

        // Отфильтровано заявок
        let filteredTicketsCount = 0;
        filteredRecords.forEach(row => {
            const cnt = parseInt(row.getAttribute('data-cnt'));
            if (!isNaN(cnt)) filteredTicketsCount += cnt;
        });
        const filteredTicketsElement = document.querySelector('[data-stats-card="filtered-tickets"] .card-title');
        if (filteredTicketsElement) filteredTicketsElement.textContent = filteredTicketsCount;
        
        // Также обновляем итог в таблице
        const tableTotalElement = document.getElementById('totalTicketsCount');
        if (tableTotalElement) tableTotalElement.textContent = filteredTicketsCount;

        // Обновляем счетчики под таблицей
        const visibleRecordsCountElement = document.getElementById('visibleRecordsCount');
        const totalRecordsCountElement = document.getElementById('totalRecordsCount');
        
        if (totalRecordsCountElement) totalRecordsCountElement.textContent = totalRecords;

        // Логика пагинации: показываем диапазон
        // Предполагаем, что эти переменные доступны глобально или передаются
        const pageSizeSelect = document.getElementById('pageSizeSelect');
        const pageSize = pageSizeSelect ? parseInt(pageSizeSelect.value) : 20;
        // Используем разные переменные для разных страниц, как в HTML
        const currentPageAnalytics = window.analyticsCurrentPage || 1;
        const currentPageClients = window.analyticsClientsCurrentPage || 1;
        // Определяем, на какой странице мы находимся, проверяя наличие таблиц
        const isAnalyticsPage = !!document.getElementById('analyticsTable');
        const isClientsPage = !!document.getElementById('clientsTable');
        const currentPage = isAnalyticsPage ? currentPageAnalytics : (isClientsPage ? currentPageClients : 1);

        const startIndex = (currentPage - 1) * pageSize + 1;
        const endIndex = Math.min(startIndex + pageSize - 1, filteredRecordsCount);
        
        if (visibleRecordsCountElement) {
            if (filteredRecordsCount > 0) {
                 visibleRecordsCountElement.textContent = `${startIndex}-${endIndex}`;
            } else {
                 visibleRecordsCountElement.textContent = '0';
            }
        }

    } catch (error) {
        console.error("Ошибка при обновлении карточек статистики:", error);
    }
}

function updateAnalyticsStatsCards(originalRecords, filteredRecords) {
    try {
        // Всего записей (из оригинальных данных)
        const totalRecords = originalRecords.length; // 0
        // ...
        const totalRecordsElement = document.querySelector('[data-stats-card="total-records"] .card-title');
        if (totalRecordsElement) totalRecordsElement.textContent = totalRecords; // Установит 0

        // Всего заявок (из оригинальных данных)
        let totalTicketsOverall = 0;
        originalRecords.forEach(row => { // Цикл по пустому массиву
            totalTicketsOverall += parseInt(row.getAttribute('data-cnt')) || 0;
        }); // totalTicketsOverall останется 0
        // ...
        if (totalTicketsElement) totalTicketsElement.textContent = totalTicketsOverall; // Установит 0

        // Отфильтровано записей
        const filteredRecordsCount = filteredRecords.length; // 0
        // ...
        if (filteredRecordsElement) filteredRecordsElement.textContent = filteredRecordsCount; // Установит 0

        // Отфильтровано заявок
        let filteredTicketsCount = 0;
        filteredRecords.forEach(row => { // Цикл по пустому массиву
            filteredTicketsCount += parseInt(row.getAttribute('data-cnt')) || 0;
        }); // filteredTicketsCount останется 0
        // ...
        if (filteredTicketsElement) filteredTicketsElement.textContent = filteredTicketsCount; // Установит 0

        // Также обновляем итог в таблице
        if (tableTotalElement) tableTotalElement.textContent = filteredTicketsCount; // Установит 0

        // Обновляем счетчики под таблицей
        if (totalRecordsCountElement) totalRecordsCountElement.textContent = totalRecords; // Установит 0
        // ...
        if (visibleRecordsCountElement) {
             // ...
             visibleRecordsCountElement.textContent = '0'; // Установит 0
        }

    } catch (error) {
        console.error("Ошибка при обновлении карточек статистики:", error);
    }
}

if (typeof window !== 'undefined' && !window.__customAlertPatched) {
    const nativeAlert = window.alert ? window.alert.bind(window) : null;
    window.alert = function(message) {
        if (typeof showNotification !== 'function') {
            return nativeAlert ? nativeAlert(message) : void 0;
        }
        const text = message instanceof Node ? message.textContent : String(message ?? '');
        let type = 'info';
        if (/❌|ошиб/i.test(text)) {
            type = 'error';
        } else if (/✅|успех/i.test(text)) {
            type = 'success';
        } else if (/⚠️|предупрежд/i.test(text)) {
            type = 'warning';
        }
        showPopup(message, type);
    };
    window.__customAlertPatched = true;
}

// Экспортируем функции в глобальную область видимости
window.CommonUtils = {
    showPopup,
    showNotification,
    exportDataGeneric,
    initializeSelect2,
    createFilterBadge,
    getTicketCountLabel,
    updateAnalyticsStatsCards
};

// Lightweight ripple effect for all Bootstrap-like buttons in non-React pages.
(function initGlobalButtonRipple() {
    if (typeof document === 'undefined') return;
    const BUTTON_SELECTOR = '.btn';
    document.addEventListener('click', (event) => {
        const button = event.target.closest(BUTTON_SELECTOR);
        if (!button || button.disabled) return;
        const rect = button.getBoundingClientRect();
        const size = Math.max(rect.width, rect.height) * 1.9;
        const wave = document.createElement('span');
        wave.className = 'btn-ripple-wave';
        wave.style.width = `${size}px`;
        wave.style.height = `${size}px`;
        wave.style.left = `${event.clientX - rect.left - size / 2}px`;
        wave.style.top = `${event.clientY - rect.top - size / 2}px`;
        button.appendChild(wave);
        window.setTimeout(() => {
            wave.remove();
        }, 700);
    });
})();
