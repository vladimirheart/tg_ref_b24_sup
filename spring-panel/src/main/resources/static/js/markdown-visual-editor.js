(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }
  if (window.__iguanaMarkdownVisualEditorInitialized) {
    return;
  }
  window.__iguanaMarkdownVisualEditorInitialized = true;

  const BLOCK_SELECTOR = 'p,h1,h2,h3,h4,h5,h6,blockquote,pre,li,div';
  const TOGGLE_COMMANDS = new Set(['bold', 'italic', 'strikeThrough', 'insertUnorderedList', 'insertOrderedList']);

  function normalizeText(value) {
    return String(value || '').replace(/\u00a0/g, ' ');
  }

  function escapeInlineText(value) {
    return normalizeText(value)
      .replace(/\\/g, '\\\\')
      .replace(/([*_~\[\]])/g, '\\$1');
  }

  function compactBlankLines(value) {
    return String(value || '')
      .replace(/[ \t]+\n/g, '\n')
      .replace(/\n{3,}/g, '\n\n');
  }

  function inlineCode(value) {
    const text = normalizeText(value);
    const fence = text.includes('`') ? '``' : '`';
    return `${fence}${text}${fence}`;
  }

  function serializeChildren(node) {
    return Array.from(node.childNodes || []).map(serializeNode).join('');
  }

  function serializeListItemContent(item) {
    return Array.from(item.childNodes || [])
      .filter((child) => !(child.nodeType === Node.ELEMENT_NODE && /^(UL|OL)$/.test(child.tagName)))
      .map(serializeNode)
      .join('')
      .replace(/\n+/g, ' ')
      .replace(/\s{2,}/g, ' ')
      .trim();
  }

  function serializeList(list, depth) {
    const ordered = list.tagName === 'OL';
    let index = Number.parseInt(list.getAttribute('start') || '1', 10);
    const indent = '  '.repeat(depth || 0);
    const lines = [];

    Array.from(list.children || []).forEach((item) => {
      if (item.tagName !== 'LI') {
        return;
      }
      const checkbox = item.querySelector(':scope > input[type="checkbox"]');
      const marker = ordered ? `${index}.` : '-';
      let body = serializeListItemContent(item);
      if (checkbox) {
        body = `[${checkbox.checked ? 'x' : ' '}] ${body}`.trimEnd();
      }
      lines.push(`${indent}${marker} ${body}`.trimEnd());

      Array.from(item.children || []).forEach((child) => {
        if (/^(UL|OL)$/.test(child.tagName)) {
          lines.push(serializeList(child, (depth || 0) + 1).trimEnd());
        }
      });
      index += 1;
    });

    return `${lines.filter(Boolean).join('\n')}\n\n`;
  }

  function serializeTable(table) {
    const rows = Array.from(table.querySelectorAll('tr'));
    if (!rows.length) {
      return '';
    }
    const matrix = rows.map((row) => Array.from(row.children).map((cell) =>
      compactBlankLines(serializeChildren(cell))
        .replace(/\n/g, ' ')
        .replace(/\|/g, '\\|')
        .trim()
    ));
    const width = Math.max(...matrix.map((row) => row.length));
    const normalized = matrix.map((row) => Array.from({ length: width }, (_, i) => row[i] || ''));
    const header = normalized[0];
    const body = normalized.slice(1);
    const render = (row) => `| ${row.join(' | ')} |`;
    return [
      render(header),
      render(header.map(() => '---')),
      ...body.map(render),
      '',
      '',
    ].join('\n');
  }

  function calloutColor(element) {
    const match = Array.from(element.classList || [])
      .map((className) => className.match(/^knowledge-callout--(.+)$/))
      .find(Boolean);
    return match ? match[1] : 'default';
  }

  function serializeCallout(element) {
    const icon = normalizeText(element.querySelector('.knowledge-callout__icon')?.textContent || 'ℹ').trim() || 'ℹ';
    const bodyElement = element.querySelector('.knowledge-callout__body');
    const body = bodyElement ? compactBlankLines(serializeChildren(bodyElement)).trim() : '';
    const color = calloutColor(element).replace(/"/g, '');
    const safeIcon = icon.replace(/"/g, '&quot;');
    const content = body ? `\n${body}\n` : '\n';
    return `<callout color="${color}" icon="${safeIcon}">${content}</callout>\n\n`;
  }

  function serializeNode(node) {
    if (!node) {
      return '';
    }
    if (node.nodeType === Node.TEXT_NODE) {
      return escapeInlineText(node.textContent || '');
    }
    if (node.nodeType !== Node.ELEMENT_NODE) {
      return '';
    }

    const tag = node.tagName;
    if (node.classList.contains('knowledge-toc')) {
      return '<table_of_contents />\n\n';
    }
    if (node.classList.contains('knowledge-empty-block')) {
      return '<empty-block />\n\n';
    }
    if (node.classList.contains('knowledge-callout')) {
      return serializeCallout(node);
    }

    switch (tag) {
      case 'BR':
        return '  \n';
      case 'P':
        return `${serializeChildren(node).trim()}\n\n`;
      case 'H1':
      case 'H2':
      case 'H3':
      case 'H4':
      case 'H5':
      case 'H6': {
        const level = Number.parseInt(tag.substring(1), 10);
        return `${'#'.repeat(level)} ${serializeChildren(node).trim()}\n\n`;
      }
      case 'STRONG':
      case 'B':
        return `**${serializeChildren(node)}**`;
      case 'EM':
      case 'I':
        return `*${serializeChildren(node)}*`;
      case 'DEL':
      case 'S':
      case 'STRIKE':
        return `~~${serializeChildren(node)}~~`;
      case 'CODE':
        if (node.parentElement && node.parentElement.tagName === 'PRE') {
          return normalizeText(node.textContent || '');
        }
        return inlineCode(node.textContent || '');
      case 'PRE': {
        const code = node.querySelector('code');
        const raw = normalizeText((code || node).textContent || '').replace(/\n$/, '');
        const languageClass = Array.from(code?.classList || []).find((className) => className.startsWith('language-'));
        const language = languageClass ? languageClass.substring('language-'.length) : '';
        return `\`\`\`${language}\n${raw}\n\`\`\`\n\n`;
      }
      case 'A': {
        const href = node.getAttribute('href') || '';
        const label = serializeChildren(node).trim() || escapeInlineText(href);
        return href ? `[${label}](${href})` : label;
      }
      case 'IMG': {
        const alt = escapeInlineText(node.getAttribute('alt') || '');
        const src = node.getAttribute('src') || '';
        const title = node.getAttribute('title');
        return `![${alt}](${src}${title ? ` "${title.replace(/"/g, '\\"')}"` : ''})`;
      }
      case 'UL':
      case 'OL':
        return serializeList(node, 0);
      case 'BLOCKQUOTE': {
        const body = compactBlankLines(serializeChildren(node)).trim();
        return `${body.split('\n').map((line) => `> ${line}`.trimEnd()).join('\n')}\n\n`;
      }
      case 'TABLE':
        return serializeTable(node);
      case 'HR':
        return '---\n\n';
      case 'INPUT':
        return '';
      case 'DIV':
      case 'SECTION':
      case 'ARTICLE':
      case 'NAV': {
        const childText = serializeChildren(node);
        return `${childText}${/\n\s*$/.test(childText) ? '' : '\n'}`;
      }
      default:
        return serializeChildren(node);
    }
  }

  function serializeMarkdown(surface) {
    const markdown = compactBlankLines(serializeChildren(surface)).trim();
    return markdown ? `${markdown}\n` : '';
  }

  function isSelectionInside(surface, selection) {
    if (!selection || !selection.rangeCount) {
      return false;
    }
    const range = selection.getRangeAt(0);
    const container = range.commonAncestorContainer.nodeType === Node.ELEMENT_NODE
      ? range.commonAncestorContainer
      : range.commonAncestorContainer.parentElement;
    return Boolean(container && surface.contains(container));
  }

  function closestElement(node) {
    return node && node.nodeType === Node.ELEMENT_NODE ? node : node?.parentElement || null;
  }

  function currentBlock(surface, selection) {
    if (!selection || !selection.rangeCount || !isSelectionInside(surface, selection)) {
      return null;
    }
    const node = closestElement(selection.anchorNode);
    if (!node) {
      return null;
    }
    const block = node.closest(BLOCK_SELECTOR);
    return block && surface.contains(block) ? block : surface;
  }

  function currentBlockType(surface, selection) {
    const block = currentBlock(surface, selection);
    if (!block || block === surface) {
      return 'p';
    }
    if (block.tagName === 'LI') {
      const list = block.closest('ul,ol');
      return list?.tagName === 'OL' ? 'ol' : 'ul';
    }
    const tag = block.tagName.toLowerCase();
    if (['h1', 'h2', 'h3', 'blockquote', 'pre'].includes(tag)) {
      return tag;
    }
    return 'p';
  }

  function selectionLink(surface, selection) {
    if (!selection || !selection.rangeCount || !isSelectionInside(surface, selection)) {
      return null;
    }
    const node = closestElement(selection.anchorNode);
    const link = node?.closest('a');
    return link && surface.contains(link) ? link : null;
  }

  function setButtonActive(button, active) {
    button.classList.toggle('is-active', Boolean(active));
    if (button.hasAttribute('aria-pressed')) {
      button.setAttribute('aria-pressed', active ? 'true' : 'false');
    }
  }

  function queryCommandStateSafe(command) {
    try {
      return Boolean(document.queryCommandState(command));
    } catch (error) {
      return false;
    }
  }

  function queryCommandEnabledSafe(command) {
    try {
      return Boolean(document.queryCommandEnabled(command));
    } catch (error) {
      return true;
    }
  }

  function syncFormattingState(root, surface) {
    const selection = window.getSelection();
    if (!isSelectionInside(surface, selection)) {
      return;
    }
    const blockType = currentBlockType(surface, selection);
    const linkActive = Boolean(selectionLink(surface, selection));

    root.querySelectorAll('[data-editor-block-select]').forEach((select) => {
      if (select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === blockType)) {
        select.value = blockType;
      }
    });

    root.querySelectorAll('[data-editor-command]').forEach((button) => {
      if (!(button instanceof HTMLButtonElement)) {
        return;
      }
      const command = button.dataset.editorCommand || '';
      let active = false;
      if (TOGGLE_COMMANDS.has(command)) {
        active = queryCommandStateSafe(command);
      } else if (command === 'formatBlock') {
        active = (button.dataset.editorValue || '').toLowerCase() === blockType;
      } else if (command === 'createLink') {
        active = linkActive;
      }
      setButtonActive(button, active);
      if (command === 'undo' || command === 'redo') {
        button.disabled = !queryCommandEnabledSafe(command);
      }
    });
  }

  function restoreRange(surface, savedRange) {
    if (!savedRange || !surface.contains(closestElement(savedRange.commonAncestorContainer))) {
      return false;
    }
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(savedRange.cloneRange());
    return true;
  }

  function commandValue(button) {
    const value = button.dataset.editorValue;
    return value || null;
  }

  function normalizeFormatBlockValue(value) {
    return String(value || '').toLowerCase().replace(/[<>]/g, '');
  }

  function applyBlockType(surface, type) {
    const selection = window.getSelection();
    const current = currentBlockType(surface, selection);
    if (type === current) {
      return;
    }
    if (type === 'ul') {
      if (current === 'ol') {
        document.execCommand('insertOrderedList', false, null);
      }
      if (!queryCommandStateSafe('insertUnorderedList')) {
        document.execCommand('insertUnorderedList', false, null);
      }
      return;
    }
    if (type === 'ol') {
      if (current === 'ul') {
        document.execCommand('insertUnorderedList', false, null);
      }
      if (!queryCommandStateSafe('insertOrderedList')) {
        document.execCommand('insertOrderedList', false, null);
      }
      return;
    }
    if (current === 'ul') {
      document.execCommand('insertUnorderedList', false, null);
    } else if (current === 'ol') {
      document.execCommand('insertOrderedList', false, null);
    }
    document.execCommand('formatBlock', false, type || 'p');
  }

  function runCommand(surface, button, savedRange) {
    const command = button.dataset.editorCommand;
    if (!command) {
      return false;
    }

    surface.focus({ preventScroll: true });
    restoreRange(surface, savedRange);
    if (command === 'createLink') {
      const existing = selectionLink(surface, window.getSelection());
      const url = window.prompt('Адрес ссылки', existing?.getAttribute('href') || 'https://');
      if (!url) {
        return false;
      }
      document.execCommand('createLink', false, url);
      return true;
    }
    if (command === 'formatBlock') {
      applyBlockType(surface, normalizeFormatBlockValue(commandValue(button)));
      return true;
    }
    document.execCommand(command, false, commandValue(button));
    return true;
  }

  function caretRect(range, fallback) {
    if (range) {
      const rect = range.getBoundingClientRect();
      if (rect && (rect.width || rect.height || rect.top || rect.left)) {
        return rect;
      }
    }
    return fallback?.getBoundingClientRect() || new DOMRect(0, 0, 0, 0);
  }

  function positionFloating(element, anchorRect, preferAbove) {
    element.hidden = false;
    element.style.visibility = 'hidden';
    element.style.left = '0px';
    element.style.top = '0px';
    const box = element.getBoundingClientRect();
    const gap = 8;
    const viewportPadding = 8;
    let left = anchorRect.left + (anchorRect.width / 2) - (box.width / 2);
    left = Math.max(viewportPadding, Math.min(left, window.innerWidth - box.width - viewportPadding));
    let top = preferAbove ? anchorRect.top - box.height - gap : anchorRect.bottom + gap;
    if (top < viewportPadding) {
      top = anchorRect.bottom + gap;
    }
    if (top + box.height > window.innerHeight - viewportPadding) {
      top = Math.max(viewportPadding, anchorRect.top - box.height - gap);
    }
    element.style.left = `${Math.round(left)}px`;
    element.style.top = `${Math.round(top)}px`;
    element.style.visibility = '';
  }

  function textOffsetWithin(root, node, offset) {
    const range = document.createRange();
    range.selectNodeContents(root);
    try {
      range.setEnd(node, offset);
    } catch (error) {
      return null;
    }
    return normalizeText(range.toString()).length;
  }

  function pointAtTextOffset(root, offset) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let remaining = Math.max(0, offset);
    let node = walker.nextNode();
    while (node) {
      const length = normalizeText(node.textContent || '').length;
      if (remaining <= length) {
        return { node, offset: remaining };
      }
      remaining -= length;
      node = walker.nextNode();
    }
    return { node: root, offset: root.childNodes.length };
  }

  function slashContext(surface) {
    const selection = window.getSelection();
    if (!selection || !selection.isCollapsed || !isSelectionInside(surface, selection)) {
      return null;
    }
    const block = currentBlock(surface, selection);
    if (!block || block === surface || block.classList.contains('knowledge-callout') || block.classList.contains('knowledge-toc')) {
      return null;
    }
    const offset = textOffsetWithin(block, selection.anchorNode, selection.anchorOffset);
    if (offset == null) {
      return null;
    }
    const before = normalizeText(block.textContent || '').slice(0, offset);
    const match = before.match(/^\s*\/([^\s/]*)$/u);
    if (!match) {
      return null;
    }
    return {
      block,
      query: (match[1] || '').toLowerCase(),
      slashStart: before.lastIndexOf('/'),
      caretOffset: offset,
      range: selection.getRangeAt(0).cloneRange(),
    };
  }

  function deleteSlashQuery(context) {
    if (!context) {
      return false;
    }
    const start = pointAtTextOffset(context.block, context.slashStart);
    const end = pointAtTextOffset(context.block, context.caretOffset);
    if (!start || !end) {
      return false;
    }
    const range = document.createRange();
    range.setStart(start.node, start.offset);
    range.setEnd(end.node, end.offset);
    range.deleteContents();
    range.collapse(true);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    return true;
  }

  function initializeEditor(root) {
    const form = root.closest('[data-markdown-editor-form]');
    const surface = root.querySelector('[data-markdown-surface]');
    const toolbar = root.querySelector('[data-markdown-toolbar]');
    const source = root.querySelector('[data-markdown-source]');
    const fields = root.querySelector('[data-editor-fields]');
    const cancel = root.querySelector('[data-editor-cancel]');
    const bubble = root.querySelector('[data-editor-bubble]');
    const slashMenu = root.querySelector('[data-editor-slash-menu]');
    const toggle = document.getElementById('knowledgeArticleEditToggle');

    if (!(form instanceof HTMLFormElement)
        || !(surface instanceof HTMLElement)
        || !(source instanceof HTMLTextAreaElement)
        || !(toolbar instanceof HTMLElement)
        || !(fields instanceof HTMLElement)
        || !(bubble instanceof HTMLElement)
        || !(slashMenu instanceof HTMLElement)) {
      return;
    }

    const initialHtml = surface.innerHTML;
    const initialSource = source.value;
    let editing = false;
    let contentDirty = false;
    let formDirty = false;
    let submitting = false;
    let savedRange = null;
    let slashState = null;
    let slashIndex = 0;
    let selectionFrame = 0;

    function hasUnsavedChanges() {
      return contentDirty || formDirty;
    }

    function hideBubble() {
      bubble.hidden = true;
    }

    function hideSlashMenu() {
      slashMenu.hidden = true;
      slashState = null;
      slashIndex = 0;
      slashMenu.querySelectorAll('[data-editor-slash-type]').forEach((option) => option.classList.remove('is-active'));
    }

    function syncToggle() {
      if (!toggle) {
        return;
      }
      toggle.setAttribute('aria-expanded', editing ? 'true' : 'false');
      toggle.setAttribute('aria-label', editing ? 'Закрыть редактирование' : 'Редактировать статью');
      toggle.setAttribute('title', editing ? 'Закрыть редактирование' : 'Редактировать статью');
      const icon = toggle.querySelector('i');
      if (icon) {
        icon.className = editing ? 'bi bi-x-lg' : 'bi bi-pencil';
      }
    }

    function setEditing(next) {
      editing = Boolean(next);
      root.classList.toggle('is-editing', editing);
      toolbar.hidden = !editing;
      fields.hidden = !editing;
      surface.contentEditable = editing ? 'true' : 'false';
      surface.setAttribute('aria-multiline', editing ? 'true' : 'false');
      surface.setAttribute('role', editing ? 'textbox' : 'document');
      syncToggle();
      if (!editing) {
        savedRange = null;
        hideBubble();
        hideSlashMenu();
      }
      if (editing) {
        window.requestAnimationFrame(() => {
          surface.focus({ preventScroll: true });
          updateSelectionUi();
        });
      }
    }

    function resetEditor() {
      form.reset();
      surface.innerHTML = initialHtml;
      source.value = initialSource;
      contentDirty = false;
      formDirty = false;
      submitting = false;
    }

    function cancelEditing() {
      const isNew = root.dataset.editorAutostart === 'true';
      if (hasUnsavedChanges() && !window.confirm('Отменить несохранённые изменения?')) {
        return;
      }
      if (isNew) {
        window.location.assign('/knowledge-base');
        return;
      }
      resetEditor();
      setEditing(false);
    }

    function visibleSlashOptions() {
      return Array.from(slashMenu.querySelectorAll('[data-editor-slash-type]')).filter((option) => !option.hidden);
    }

    function syncSlashActiveOption() {
      const options = visibleSlashOptions();
      if (!options.length) {
        return;
      }
      slashIndex = Math.max(0, Math.min(slashIndex, options.length - 1));
      options.forEach((option, index) => {
        option.classList.toggle('is-active', index === slashIndex);
        option.setAttribute('aria-selected', index === slashIndex ? 'true' : 'false');
      });
      options[slashIndex]?.scrollIntoView({ block: 'nearest' });
    }

    function openSlashMenu(context) {
      if (!context || !editing) {
        hideSlashMenu();
        return;
      }
      slashState = context;
      const query = context.query;
      slashMenu.querySelectorAll('[data-editor-slash-type]').forEach((option) => {
        const haystack = `${option.dataset.editorSlashKeywords || ''} ${option.textContent || ''}`.toLowerCase();
        option.hidden = Boolean(query) && !haystack.includes(query);
      });
      const options = visibleSlashOptions();
      if (!options.length) {
        hideSlashMenu();
        return;
      }
      slashIndex = 0;
      syncSlashActiveOption();
      const anchor = caretRect(context.range, context.block);
      positionFloating(slashMenu, anchor, false);
    }

    function executeSlashOption(option) {
      if (!(option instanceof HTMLButtonElement) || !slashState) {
        return;
      }
      restoreRange(surface, slashState.range);
      deleteSlashQuery(slashState);
      const type = option.dataset.editorSlashType || 'p';
      if (type === 'hr') {
        document.execCommand('insertHorizontalRule', false, null);
      } else {
        applyBlockType(surface, type);
      }
      contentDirty = true;
      hideSlashMenu();
      updateSelectionUi();
    }

    function updateSelectionUi() {
      if (!editing) {
        return;
      }
      const selection = window.getSelection();
      if (!isSelectionInside(surface, selection)) {
        hideBubble();
        hideSlashMenu();
        return;
      }
      if (selection.rangeCount) {
        savedRange = selection.getRangeAt(0).cloneRange();
      }
      syncFormattingState(root, surface);

      if (!selection.isCollapsed) {
        hideSlashMenu();
        const rect = caretRect(selection.getRangeAt(0), currentBlock(surface, selection));
        positionFloating(bubble, rect, true);
        return;
      }
      hideBubble();
      const context = slashContext(surface);
      if (context) {
        openSlashMenu(context);
      } else {
        hideSlashMenu();
      }
    }

    function scheduleSelectionUi() {
      if (selectionFrame) {
        cancelAnimationFrame(selectionFrame);
      }
      selectionFrame = requestAnimationFrame(() => {
        selectionFrame = 0;
        updateSelectionUi();
      });
    }

    root.querySelectorAll('[data-editor-command]').forEach((button) => {
      button.addEventListener('mousedown', (event) => {
        event.preventDefault();
      });
      button.addEventListener('click', () => {
        const changed = runCommand(surface, button, savedRange);
        if (changed) {
          contentDirty = true;
        }
        scheduleSelectionUi();
      });
    });

    root.querySelectorAll('[data-editor-block-select]').forEach((select) => {
      select.addEventListener('mousedown', () => {
        const selection = window.getSelection();
        if (selection && selection.rangeCount && isSelectionInside(surface, selection)) {
          savedRange = selection.getRangeAt(0).cloneRange();
        }
      });
      select.addEventListener('change', () => {
        surface.focus({ preventScroll: true });
        restoreRange(surface, savedRange);
        applyBlockType(surface, select.value);
        contentDirty = true;
        scheduleSelectionUi();
      });
    });

    slashMenu.querySelectorAll('[data-editor-slash-type]').forEach((option) => {
      option.addEventListener('mousedown', (event) => event.preventDefault());
      option.addEventListener('click', () => executeSlashOption(option));
    });

    surface.addEventListener('input', () => {
      contentDirty = true;
      scheduleSelectionUi();
    });
    surface.addEventListener('keyup', scheduleSelectionUi);
    surface.addEventListener('mouseup', scheduleSelectionUi);
    surface.addEventListener('scroll', () => {
      hideBubble();
      hideSlashMenu();
    });

    surface.addEventListener('keydown', (event) => {
      if (!slashMenu.hidden) {
        const options = visibleSlashOptions();
        if (event.key === 'ArrowDown') {
          event.preventDefault();
          slashIndex = (slashIndex + 1) % options.length;
          syncSlashActiveOption();
          return;
        }
        if (event.key === 'ArrowUp') {
          event.preventDefault();
          slashIndex = (slashIndex - 1 + options.length) % options.length;
          syncSlashActiveOption();
          return;
        }
        if (event.key === 'Enter') {
          event.preventDefault();
          executeSlashOption(options[slashIndex]);
          return;
        }
        if (event.key === 'Escape') {
          event.preventDefault();
          hideSlashMenu();
          return;
        }
      }
      if ((event.ctrlKey || event.metaKey) && ['b', 'i'].includes(event.key.toLowerCase())) {
        requestAnimationFrame(scheduleSelectionUi);
      }
    });

    document.addEventListener('selectionchange', scheduleSelectionUi);
    window.addEventListener('resize', () => {
      hideBubble();
      hideSlashMenu();
    });
    window.addEventListener('scroll', () => {
      if (!bubble.hidden) {
        scheduleSelectionUi();
      }
      if (!slashMenu.hidden) {
        scheduleSelectionUi();
      }
    }, true);

    fields.addEventListener('input', () => {
      formDirty = true;
    });
    fields.addEventListener('change', () => {
      formDirty = true;
    });

    function prepareSubmit() {
      if (contentDirty) {
        source.value = serializeMarkdown(surface);
      }
      submitting = true;
    }

    form.addEventListener('submit', prepareSubmit);

    form.addEventListener('keydown', (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
        event.preventDefault();
        if (typeof form.requestSubmit === 'function') {
          form.requestSubmit();
        } else {
          prepareSubmit();
          form.submit();
        }
      }
    });

    if (toggle) {
      toggle.addEventListener('click', () => {
        if (editing) {
          cancelEditing();
        } else {
          setEditing(true);
        }
      });
    }

    cancel?.addEventListener('click', cancelEditing);

    window.addEventListener('beforeunload', (event) => {
      if (submitting || !editing || !hasUnsavedChanges()) {
        return;
      }
      event.preventDefault();
      event.returnValue = '';
    });

    if (root.dataset.editorAutostart === 'true') {
      setEditing(true);
    } else {
      setEditing(false);
    }
  }

  function initMarkdownVisualEditors() {
    document.querySelectorAll('[data-markdown-editor]').forEach(initializeEditor);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initMarkdownVisualEditors, { once: true });
  } else {
    initMarkdownVisualEditors();
  }
})();
