// panel/static/column-reorder.js
(function () {
  const tables = Array.from(document.querySelectorAll('table[data-column-reorderable]'));
  if (!tables.length) {
    return;
  }

  const STORAGE_PREFIX = 'iguana:table-columns:';

  function storageKey(table) {
    const explicit = table.dataset.reorderKey;
    if (explicit && explicit.trim()) {
      return STORAGE_PREFIX + explicit.trim();
    }
    if (table.id) {
      return STORAGE_PREFIX + table.id;
    }
    return STORAGE_PREFIX + (location.pathname || 'default');
  }

  function coerceOrder(order, size) {
    const normalized = [];
    const seen = new Set();
    (Array.isArray(order) ? order : []).forEach((value) => {
      const index = Number(value);
      if (Number.isInteger(index) && index >= 0 && index < size && !seen.has(index)) {
        seen.add(index);
        normalized.push(index);
      }
    });
    for (let i = 0; i < size; i += 1) {
      if (!seen.has(i)) {
        normalized.push(i);
      }
    }
    return normalized;
  }

  function reorderRow(row, order) {
    if (!row || !order || !order.length) return;
    const cells = Array.from(row.children);
    if (!cells.length) return;
    const fragment = document.createDocumentFragment();
    order.forEach((index) => {
      const cell = cells[index];
      if (cell) {
        fragment.appendChild(cell);
      }
    });
    row.appendChild(fragment);
  }

  function applyColumnOrder(table, order) {
    if (!table || !order) return;
    const headerRows = table.tHead ? Array.from(table.tHead.rows) : [];
    headerRows.forEach((row) => reorderRow(row, order));
    const bodies = table.tBodies ? Array.from(table.tBodies) : [];
    bodies.forEach((body) => Array.from(body.rows).forEach((row) => reorderRow(row, order)));
    if (table.tFoot) {
      Array.from(table.tFoot.rows).forEach((row) => reorderRow(row, order));
    }
  }

  function reapplyStoredOrder(table) {
    if (!table) return;
    if (!Array.isArray(table.__columnOrder) || !table.__columnOrder.length) return;
    applyColumnOrder(table, table.__columnOrder);
  }

  function persistOrder(table) {
    const key = storageKey(table);
    const order = table.__columnOrder;
    if (!Array.isArray(order)) return;
    if (!table.__defaultOrder) return;
    const same = JSON.stringify(order) === JSON.stringify(table.__defaultOrder);
    if (same) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, JSON.stringify(order));
    }
    if (table.__columnResetBtn) {
      table.__columnResetBtn.classList.toggle('d-none', same);
    }
  }

  function restoreOrder(table) {
    const key = storageKey(table);
    try {
      const raw = localStorage.getItem(key);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return null;
      return parsed;
    } catch (error) {
      return null;
    }
  }

  function resetOrder(table) {
    if (!table.__defaultOrder) return;
    table.__columnOrder = table.__defaultOrder.slice();
    applyColumnOrder(table, table.__columnOrder);
    persistOrder(table);
  }

  function attachResetButton(table) {
    if (table.__columnResetBtn) return;
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn btn-link btn-sm table-reset-order d-none';
    btn.textContent = 'Вернуть порядок по умолчанию';
    btn.addEventListener('click', () => resetOrder(table));
    table.__columnResetBtn = btn;
    const container = table.closest('[data-column-reorder-container]') || table.parentElement;
    if (container) {
      const wrapper = document.createElement('div');
      wrapper.className = 'd-flex justify-content-end mt-2 table-reset-wrapper';
      wrapper.appendChild(btn);
      container.appendChild(wrapper);
    } else {
      table.insertAdjacentElement('afterend', btn);
    }
  }

  function markDraggable(cell) {
    if (!cell) return;
    cell.setAttribute('draggable', 'true');
    cell.dataset.columnDraggable = 'true';
  }

  function setupTable(table) {
    const headerRow = table.tHead && table.tHead.rows && table.tHead.rows[0];
    if (!headerRow) return;
    const headerCells = Array.from(headerRow.cells);
    if (!headerCells.length) return;

    table.__defaultOrder = headerCells.map((_, index) => index);
    table.__columnOrder = table.__defaultOrder.slice();

    headerCells.forEach((cell) => {
      if (!cell.dataset.columnFixed) {
        markDraggable(cell);
      }
    });

    attachResetButton(table);

    const savedOrder = restoreOrder(table);
    table.__applyColumnOrder = () => reapplyStoredOrder(table);
    table.addEventListener('table:reapply-column-order', table.__applyColumnOrder);

    if (savedOrder) {
      table.__columnOrder = coerceOrder(savedOrder, headerCells.length);
      reapplyStoredOrder(table);
    }
    persistOrder(table);

    let dragIndex = null;

    headerRow.addEventListener('dragstart', (event) => {
      const cell = event.target.closest('th,td');
      if (!cell || cell.dataset.columnFixed || cell.dataset.columnDraggable !== 'true') {
        event.preventDefault();
        return;
      }
      dragIndex = Array.from(headerRow.cells).indexOf(cell);
      if (dragIndex === -1) {
        event.preventDefault();
        return;
      }
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', String(dragIndex));
      cell.classList.add('dragging');
    });

    headerRow.addEventListener('dragover', (event) => {
      if (dragIndex === null) return;
      event.preventDefault();
    });

    headerRow.addEventListener('drop', (event) => {
      if (dragIndex === null) return;
      const targetCell = event.target.closest('th,td');
      if (!targetCell || targetCell.dataset.columnFixed) {
        return;
      }
      event.preventDefault();
      const currentCells = Array.from(headerRow.cells);
      const targetIndex = currentCells.indexOf(targetCell);
      if (targetIndex === -1 || targetIndex === dragIndex) {
        return;
      }
      const order = table.__columnOrder.slice();
      const [moved] = order.splice(dragIndex, 1);
      order.splice(targetIndex, 0, moved);
      table.__columnOrder = order;
      reapplyStoredOrder(table);
      persistOrder(table);
      dragIndex = targetIndex;
    });

    headerRow.addEventListener('dragend', () => {
      const dragging = headerRow.querySelector('.dragging');
      if (dragging) dragging.classList.remove('dragging');
      dragIndex = null;
    });
  }

  tables.forEach(setupTable);

  window.applyStoredColumnOrder = function applyStoredColumnOrder(target) {
    const normalizeCollection = (value) => {
      if (!value) return tables;
      if (typeof value === 'string') {
        return Array.from(document.querySelectorAll(value));
      }
      if (typeof NodeList !== 'undefined' && value instanceof NodeList) {
        return Array.from(value);
      }
      if (Array.isArray(value)) {
        return value;
      }
      if (typeof Element !== 'undefined' && value instanceof Element) {
        return [value];
      }
      return tables;
    };

    const list = normalizeCollection(target);
    list.forEach((table) => {
      if (table && typeof table.__applyColumnOrder === 'function') {
        table.__applyColumnOrder();
      }
    });
  };

})();
