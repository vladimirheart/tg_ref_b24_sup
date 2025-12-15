(function () {
  if (window.__autoCopyEnhancerInitialized) {
    return;
  }
  window.__autoCopyEnhancerInitialized = true;

  const PATTERN = String.raw`(?:https?:\/\/[^\s"'<>]+|www\.[^\s"'<>]+|(?:25[0-5]|2[0-4]\d|1?\d{1,2})(?:\.(?:25[0-5]|2[0-4]\d|1?\d{1,2})){3})`;
  const detectionRegex = new RegExp(PATTERN, 'i');
  const trailingPunctuationRegex = /[)\]\}.,;!?'"”]+$/;

  const isSkippableTag = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEXTAREA', 'OPTION', 'BUTTON']);

  const editableSelector = [
    'input',
    'textarea',
    '[contenteditable]',
    '.toastui-editor-defaultUI',
    '[data-copy-editor-ignore]',
  ].join(',');

  function isInsideEditable(node) {
    if (!node) {
      return false;
    }

    if (node.nodeType === Node.TEXT_NODE) {
      node = node.parentElement;
    }

    if (!node || node.nodeType !== Node.ELEMENT_NODE) {
      return false;
    }

    if (node.matches(editableSelector)) {
      return true;
    }

    if (node.isContentEditable) {
      return true;
    }

    if (typeof node.closest === 'function' && node.closest(editableSelector)) {
      return true;
    }

    return false;
  }

  function createCopyButton(copyValue) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'copy-icon-btn';
    button.setAttribute('aria-label', 'Скопировать значение');
    button.setAttribute('title', 'Скопировать');
    button.dataset.copyValue = copyValue;
    button.setAttribute('data-copy-state', 'idle');
    button.innerHTML =
      '<svg aria-hidden="true" focusable="false" viewBox="0 0 16 16" class="copy-icon" xmlns="http://www.w3.org/2000/svg">' +
      '<path d="M10 1H2a1 1 0 0 0-1 1v11h1V2h8V1Zm3 3H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V5a1 1 0 0 0-1-1Zm0 11H5V5h8v10Z" fill="currentColor"/>' +
      '</svg>';
    button.addEventListener('click', handleCopyClick);
    return button;
  }

  async function copyToClipboard(value) {
    if (!value) {
      return false;
    }

    if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
      try {
        await navigator.clipboard.writeText(value);
        return true;
      } catch (error) {
        // fallback below
      }
    }

    const helper = document.createElement('textarea');
    helper.value = value;
    helper.setAttribute('readonly', '');
    helper.style.position = 'fixed';
    helper.style.opacity = '0';
    helper.style.pointerEvents = 'none';
    helper.style.top = '0';
    helper.style.left = '0';
    document.body.appendChild(helper);
    try {
      helper.focus({ preventScroll: true });
    } catch (error) {
      helper.focus();
    }
    helper.select();

    let success = false;
    try {
      success = document.execCommand('copy');
    } catch (error) {
      success = false;
    }

    document.body.removeChild(helper);
    return success;
  }

  function resetCopyState(button) {
    button.classList.remove('copied', 'copy-error');
    button.setAttribute('data-copy-state', 'idle');
    button.setAttribute('title', 'Скопировать');
  }

  function markCopyResult(button, ok) {
    button.setAttribute('data-copy-state', ok ? 'copied' : 'error');
    button.classList.toggle('copied', ok);
    button.classList.toggle('copy-error', !ok);
    button.setAttribute('title', ok ? 'Скопировано' : 'Не удалось скопировать');

    setTimeout(() => {
      if (!document.body.contains(button)) {
        return;
      }
      resetCopyState(button);
    }, 2000);
  }

  async function handleCopyClick(event) {
    const button = event.currentTarget;
    if (!button || button.dataset.copyState === 'pending') {
      return;
    }

    const value = button.dataset.copyValue || '';
    button.setAttribute('data-copy-state', 'pending');
    const ok = await copyToClipboard(value);
    markCopyResult(button, ok);
  }

  function createWrapper(tagName, displayMode) {
    const wrapper = document.createElement(tagName);
    wrapper.classList.add('copyable-inline-container');
    if (displayMode === 'block') {
      wrapper.classList.add('copyable-block-container');
    }
    wrapper.dataset.copyEnhanced = 'true';
    return wrapper;
  }

  function enhanceAnchor(anchor) {
    if (!anchor || anchor.dataset.copyEnhanced === 'true') {
      return;
    }
    if (anchor.closest('[data-copy-ignore], .copyable-ignore')) {
      return;
    }
    if (isInsideEditable(anchor)) {
      return;
    }
    const href = anchor.getAttribute('href') || '';
    if (!href || !detectionRegex.test(href)) {
      return;
    }

    const computedDisplay = window.getComputedStyle(anchor).display;
    const needsBlock = computedDisplay === 'block' || computedDisplay === 'flex' || computedDisplay === 'grid';
    const wrapper = createWrapper(needsBlock ? 'div' : 'span', needsBlock ? 'block' : 'inline');
    anchor.dataset.copyEnhanced = 'true';

    if (anchor.parentNode) {
      anchor.parentNode.insertBefore(wrapper, anchor);
    }
    wrapper.appendChild(anchor);
    const button = createCopyButton(href);
    wrapper.appendChild(button);
  }

  function enhanceTextNode(node) {
    if (!node || node.nodeType !== Node.TEXT_NODE) {
      return;
    }
    const parent = node.parentElement;
    if (!parent) {
      return;
    }

    if (parent.closest('[data-copy-ignore], .copyable-ignore')) {
      return;
    }
    if (isInsideEditable(parent)) {
      return;
    }
    if (parent.closest('[data-copy-enhanced="true"]')) {
      return;
    }
    if (isSkippableTag.has(parent.tagName)) {
      return;
    }

    const text = node.textContent || '';
    if (!detectionRegex.test(text)) {
      return;
    }

    const matches = [...text.matchAll(new RegExp(PATTERN, 'gi'))];
    if (!matches.length) {
      return;
    }

    const fragment = document.createDocumentFragment();
    let lastIndex = 0;

    matches.forEach((match) => {
      const rawMatch = match[0];
      const start = match.index || 0;
      const end = start + rawMatch.length;

      if (start > lastIndex) {
        fragment.appendChild(document.createTextNode(text.slice(lastIndex, start)));
      }

      let sanitized = rawMatch;
      let trailing = '';
      const punctuationMatch = rawMatch.match(trailingPunctuationRegex);
      if (punctuationMatch) {
        sanitized = rawMatch.slice(0, rawMatch.length - punctuationMatch[0].length);
        trailing = punctuationMatch[0];
      }

      const trimmedValue = sanitized.trim();
      if (trimmedValue) {
        const wrapper = createWrapper('span', 'inline');
        wrapper.appendChild(document.createTextNode(sanitized));
        const button = createCopyButton(trimmedValue);
        wrapper.appendChild(button);
        fragment.appendChild(wrapper);
      } else {
        fragment.appendChild(document.createTextNode(rawMatch));
      }

      if (trailing) {
        fragment.appendChild(document.createTextNode(trailing));
      }

      lastIndex = end;
    });

    if (lastIndex < text.length) {
      fragment.appendChild(document.createTextNode(text.slice(lastIndex)));
    }

    parent.replaceChild(fragment, node);
  }

  function scanRoot(root) {
    if (!root) {
      return;
    }

    if (root.nodeType === Node.TEXT_NODE) {
      enhanceTextNode(root);
      return;
    }

    if (root.nodeType !== Node.ELEMENT_NODE) {
      return;
    }

    if (root.matches('[data-copy-ignore], .copyable-ignore')) {
      return;
    }

    if (isInsideEditable(root)) {
      return;
    }

    if (isSkippableTag.has(root.tagName)) {
      return;
    }

    if (root.matches('a[href]')) {
      enhanceAnchor(root);
    }

    root.querySelectorAll('a[href]').forEach((anchor) => enhanceAnchor(anchor));

    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        if (!node.parentElement) {
          return NodeFilter.FILTER_REJECT;
        }
        if (node.parentElement.closest('[data-copy-ignore], .copyable-ignore')) {
          return NodeFilter.FILTER_REJECT;
        }
        if (isInsideEditable(node.parentElement)) {
          return NodeFilter.FILTER_REJECT;
        }
        if (node.parentElement.closest('[data-copy-enhanced="true"]')) {
          return NodeFilter.FILTER_REJECT;
        }
        if (isSkippableTag.has(node.parentElement.tagName)) {
          return NodeFilter.FILTER_REJECT;
        }
        const value = node.textContent;
        if (!value || !value.trim()) {
          return NodeFilter.FILTER_SKIP;
        }
        return detectionRegex.test(value) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP;
      },
    });

    const nodes = [];
    while (walker.nextNode()) {
      nodes.push(walker.currentNode);
    }
    nodes.forEach((textNode) => enhanceTextNode(textNode));
  }

  function initAutoCopy() {
    if (!document.body) {
      return;
    }
    scanRoot(document.body);

    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
          if (node.nodeType === Node.TEXT_NODE) {
            enhanceTextNode(node);
          } else {
            scanRoot(node);
          }
        });
      });
    });

    observer.observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initAutoCopy, { once: true });
  } else {
    initAutoCopy();
  }
})();