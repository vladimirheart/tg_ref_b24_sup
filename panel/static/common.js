// static/common.js

// Функция для показа уведомлений (например, об ошибках или успехе)
function showNotification(message, type = 'info', containerId = 'notification-container') {
    // Убедимся, что контейнер для уведомлений существует
    let container = document.getElementById(containerId);
    if (!container) {
        container = document.createElement('div');
        container.id = containerId;
    }

    // Всегда перемещаем контейнер уведомлений в конец <body>,
    // чтобы он гарантированно располагался поверх всех наложений.
    document.body.appendChild(container);

    container.style.position = 'fixed';
    container.style.inset = '0';
    // Делаем уведомление поверх модальных окон Bootstrap (кастомный z-index модалок — 99999)
    container.style.zIndex = '2147483647';
    container.style.display = 'flex';
    container.style.flexDirection = 'column';
    container.style.alignItems = 'center';
    container.style.justifyContent = 'center';
    container.style.gap = '0.75rem';
    container.style.pointerEvents = 'none';
    container.style.padding = '1rem';

    const alertClass = type === 'error' ? 'alert-danger' :
                       type === 'success' ? 'alert-success' :
                       type === 'warning' ? 'alert-warning' : 'alert-info';

    const alertEl = document.createElement('div');
    alertEl.className = `alert ${alertClass} alert-dismissible fade show shadow`;
    alertEl.setAttribute('role', 'alert');
    alertEl.style.pointerEvents = 'auto';
    alertEl.style.minWidth = 'min(90vw, 320px)';
    alertEl.style.maxWidth = 'min(90vw, 480px)';
    alertEl.style.margin = '0 auto';
    alertEl.style.textAlign = 'center';

    const messageWrap = document.createElement('div');
    messageWrap.className = 'notification-message';
    if (message instanceof Node) {
        messageWrap.appendChild(message);
    } else {
        messageWrap.innerHTML = message;
    }
    alertEl.appendChild(messageWrap);

    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'btn-close';
    closeBtn.setAttribute('aria-label', 'Close');
    alertEl.appendChild(closeBtn);

    container.appendChild(alertEl);

    let dismissed = false;
    const removeAlert = () => {
        if (dismissed) return;
        clearTimeout(autoHideTimer);
        dismissed = true;
        alertEl.classList.remove('show');
        const cleanup = () => {
            alertEl.remove();
            if (!container.hasChildNodes()) {
                container.remove();
            }
        };

        if (alertEl.classList.contains('fade')) {
            alertEl.addEventListener('transitionend', cleanup, { once: true });
        } else {
            cleanup();
        }
    };

    const autoHideTimer = setTimeout(removeAlert, 2000);

    closeBtn.addEventListener('click', (event) => {
        event.preventDefault();
        removeAlert();
    });

    alertEl.addEventListener('contextmenu', (event) => {
        event.preventDefault();
        removeAlert();
    });

    alertEl.addEventListener('pointerdown', (event) => {
        if (event.button === 2) {
            event.preventDefault();
            removeAlert();
        }
    });
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
        showNotification(message, type);
    };
    window.__customAlertPatched = true;
}

// Экспортируем функции в глобальную область видимости
window.CommonUtils = {
    showNotification,
    exportDataGeneric,
    initializeSelect2,
    createFilterBadge,
    getTicketCountLabel,
    updateAnalyticsStatsCards
};
