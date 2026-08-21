$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$baseScss = Join-Path $repoRoot 'spring-panel\src\main\resources\scss\style\_base.scss'
$uiHead = Join-Path $repoRoot 'spring-panel\src\main\resources\templates\fragments\ui-head.html'
$resizeJs = Join-Path $repoRoot 'spring-panel\src\main\resources\static\js\modal-resize.js'

function Read-Utf8Text([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "File not found: $Path"
    }
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Has-Utf8Bom([string]$Path) {
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    return $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
}

function Write-Utf8Text([string]$Path, [string]$Text, [bool]$WithBom) {
    $encoding = New-Object System.Text.UTF8Encoding($WithBom)
    [System.IO.File]::WriteAllText($Path, $Text, $encoding)
}

$baseText = Read-Utf8Text $baseScss
$uiHeadText = Read-Utf8Text $uiHead
$baseBom = Has-Utf8Bom $baseScss
$uiHeadBom = Has-Utf8Bom $uiHead

$cssMarker = '/* Resizable Bootstrap modals */'
$cssBlock = @'

/* Resizable Bootstrap modals */

.modal-dialog.modal-resizable {
  position: relative;
}

.modal-dialog.modal-user-sized {
  width: var(--iguana-modal-user-width) !important;
  max-width: calc(100vw - 1.5rem) !important;
  height: var(--iguana-modal-user-height) !important;
  max-height: calc(100vh - 1.5rem) !important;
}

.modal-dialog.modal-user-sized > .modal-content {
  width: 100%;
  height: 100%;
  min-height: 0;
  max-height: 100%;
}

.modal-dialog.modal-user-sized > .modal-content > form {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.modal-dialog.modal-user-sized .modal-body,
.modal-dialog.modal-user-sized > .modal-content > form > .modal-body {
  min-height: 0;
  overflow: auto;
}

.modal-resize-handle {
  position: absolute;
  right: 0.28rem;
  bottom: 0.28rem;
  z-index: 6;

  width: 1.15rem;
  height: 1.15rem;
  padding: 0;

  border: 0;
  border-radius: 0.25rem;
  background: transparent;
  color: var(--color-text-muted);

  cursor: nwse-resize;
  pointer-events: auto;
  opacity: 0.42;
}

.modal-resize-handle::before,
.modal-resize-handle::after {
  content: '';
  position: absolute;
  right: 0.15rem;
  bottom: 0.15rem;

  border-right: 1px solid currentColor;
  border-bottom: 1px solid currentColor;
}

.modal-resize-handle::before {
  width: 0.48rem;
  height: 0.48rem;
}

.modal-resize-handle::after {
  width: 0.78rem;
  height: 0.78rem;
  opacity: 0.58;
}

.modal-resize-handle:hover,
.modal-resize-handle:focus-visible {
  color: var(--primary);
  opacity: 0.9;
}

.modal-resize-handle:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--primary) 42%, transparent);
  outline-offset: 2px;
}

body.modal-resize-active,
body.modal-resize-active * {
  cursor: nwse-resize !important;
  user-select: none !important;
}

@media (max-width: 767.98px) {
  .modal-resize-handle {
    display: none !important;
  }
}
'@

if (-not $baseText.Contains($cssMarker)) {
    $anchor = '/* Bootstrap close button'
    $anchorIndex = $baseText.IndexOf($anchor)
    if ($anchorIndex -lt 0) {
        throw 'SCSS anchor not found. No files were changed.'
    }

    $nextSection = $baseText.IndexOf('.card,', $anchorIndex)
    if ($nextSection -lt 0) {
        throw 'SCSS insertion point not found. No files were changed.'
    }

    $baseText = $baseText.Insert($nextSection, $cssBlock + "`r`n")
}

$scriptInclude = '    <script th:src="@{/js/modal-resize.js}" defer></script>'
if (-not $uiHeadText.Contains('/js/modal-resize.js')) {
    $uiAnchor = '    <script th:src="@{/js/ui-config.js}"></script>'
    if (-not $uiHeadText.Contains($uiAnchor)) {
        throw 'ui-head anchor not found. No files were changed.'
    }
    $uiHeadText = $uiHeadText.Replace($uiAnchor, $uiAnchor + "`r`n" + $scriptInclude)
}

$jsContent = @'
(function () {
  if (window.__iguanaModalResizeInitialized) {
    return;
  }
  window.__iguanaModalResizeInitialized = true;

  const STORAGE_PREFIX = 'iguana:modal-size:v1:';
  const MIN_WIDTH = 320;
  const MIN_HEIGHT = 220;
  const VIEWPORT_GAP = 24;
  const DESKTOP_QUERY = '(min-width: 768px)';
  const desktopMedia = window.matchMedia ? window.matchMedia(DESKTOP_QUERY) : null;
  const initialized = new WeakSet();

  function isDesktop() {
    return !desktopMedia || desktopMedia.matches;
  }

  function storageKey(modal) {
    if (!modal || !modal.id) {
      return '';
    }
    return `${STORAGE_PREFIX}${encodeURIComponent(window.location.pathname)}:${modal.id}`;
  }

  function readStoredSize(modal) {
    const key = storageKey(modal);
    if (!key) {
      return null;
    }
    try {
      const raw = window.localStorage.getItem(key);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw);
      const width = Number(parsed && parsed.width);
      const height = Number(parsed && parsed.height);
      if (!Number.isFinite(width) || !Number.isFinite(height)) {
        return null;
      }
      return { width, height };
    } catch (error) {
      return null;
    }
  }

  function writeStoredSize(modal, size) {
    const key = storageKey(modal);
    if (!key || !size) {
      return;
    }
    try {
      window.localStorage.setItem(key, JSON.stringify({
        width: Math.round(size.width),
        height: Math.round(size.height),
      }));
    } catch (error) {
      // Local storage can be unavailable in privacy-restricted contexts.
    }
  }

  function removeStoredSize(modal) {
    const key = storageKey(modal);
    if (!key) {
      return;
    }
    try {
      window.localStorage.removeItem(key);
    } catch (error) {
      // Ignore storage failures and still restore the default geometry.
    }
  }

  function clampSize(width, height) {
    const maxWidth = Math.max(MIN_WIDTH, window.innerWidth - VIEWPORT_GAP);
    const maxHeight = Math.max(MIN_HEIGHT, window.innerHeight - VIEWPORT_GAP);
    return {
      width: Math.min(maxWidth, Math.max(MIN_WIDTH, Math.round(width))),
      height: Math.min(maxHeight, Math.max(MIN_HEIGHT, Math.round(height))),
    };
  }

  function applySize(dialog, size) {
    if (!dialog || !size || !isDesktop()) {
      return;
    }
    const clamped = clampSize(size.width, size.height);
    dialog.style.setProperty('--iguana-modal-user-width', `${clamped.width}px`);
    dialog.style.setProperty('--iguana-modal-user-height', `${clamped.height}px`);
    dialog.classList.add('modal-user-sized');
  }

  function clearAppliedSize(dialog) {
    if (!dialog) {
      return;
    }
    dialog.classList.remove('modal-user-sized');
    dialog.style.removeProperty('--iguana-modal-user-width');
    dialog.style.removeProperty('--iguana-modal-user-height');
  }

  function restoreSize(modal, dialog) {
    if (!isDesktop()) {
      clearAppliedSize(dialog);
      return;
    }
    const stored = readStoredSize(modal);
    if (stored) {
      applySize(dialog, stored);
    } else {
      clearAppliedSize(dialog);
    }
  }

  function resetSize(modal, dialog) {
    removeStoredSize(modal);
    clearAppliedSize(dialog);
  }

  function shouldSkip(modal, dialog) {
    if (!modal || !dialog) {
      return true;
    }
    if (modal.dataset.modalResize === 'off') {
      return true;
    }
    if (modal.matches('.settings-sheet-modal, .settings-child-modal--sheet')) {
      return true;
    }
    if (dialog.classList.contains('modal-fullscreen')) {
      return true;
    }
    return false;
  }

  function resizeByKeyboard(event, modal, dialog) {
    if (!isDesktop()) {
      return;
    }
    const keys = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'];
    if (!keys.includes(event.key)) {
      return;
    }
    event.preventDefault();

    const rect = dialog.getBoundingClientRect();
    const step = event.shiftKey ? 48 : 24;
    let width = rect.width;
    let height = rect.height;

    if (event.key === 'ArrowLeft') width -= step;
    if (event.key === 'ArrowRight') width += step;
    if (event.key === 'ArrowUp') height -= step;
    if (event.key === 'ArrowDown') height += step;

    const next = clampSize(width, height);
    applySize(dialog, next);
    writeStoredSize(modal, next);
  }

  function attachResize(modal) {
    if (!(modal instanceof HTMLElement) || initialized.has(modal)) {
      return;
    }
    const dialog = modal.querySelector(':scope > .modal-dialog');
    if (!(dialog instanceof HTMLElement) || shouldSkip(modal, dialog)) {
      initialized.add(modal);
      return;
    }

    initialized.add(modal);
    dialog.classList.add('modal-resizable');

    const handle = document.createElement('button');
    handle.type = 'button';
    handle.className = 'modal-resize-handle';
    handle.setAttribute('aria-label', '\u0418\u0437\u043c\u0435\u043d\u0438\u0442\u044c \u0440\u0430\u0437\u043c\u0435\u0440 \u043e\u043a\u043d\u0430');
    handle.title = '\u041f\u043e\u0442\u044f\u043d\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u0438\u0437\u043c\u0435\u043d\u0438\u0442\u044c \u0440\u0430\u0437\u043c\u0435\u0440. \u0414\u0432\u043e\u0439\u043d\u043e\u0439 \u043a\u043b\u0438\u043a \u2014 \u0441\u0431\u0440\u043e\u0441.';
    dialog.appendChild(handle);

    restoreSize(modal, dialog);

    modal.addEventListener('show.bs.modal', () => restoreSize(modal, dialog));

    handle.addEventListener('dblclick', (event) => {
      event.preventDefault();
      event.stopPropagation();
      resetSize(modal, dialog);
    });

    handle.addEventListener('keydown', (event) => resizeByKeyboard(event, modal, dialog));

    handle.addEventListener('pointerdown', (event) => {
      if (!isDesktop() || event.button !== 0) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();

      const rect = dialog.getBoundingClientRect();
      const startX = event.clientX;
      const startY = event.clientY;
      const startWidth = rect.width;
      const startHeight = rect.height;
      let latest = clampSize(startWidth, startHeight);

      applySize(dialog, latest);
      document.body.classList.add('modal-resize-active');
      handle.setPointerCapture(event.pointerId);

      const onMove = (moveEvent) => {
        if (moveEvent.pointerId !== event.pointerId) {
          return;
        }
        const deltaX = moveEvent.clientX - startX;
        const deltaY = moveEvent.clientY - startY;

        // Bootstrap dialogs stay horizontally centered, so width grows on both sides.
        latest = clampSize(startWidth + (deltaX * 2), startHeight + deltaY);
        applySize(dialog, latest);
      };

      const finish = (finishEvent) => {
        if (finishEvent.pointerId !== event.pointerId) {
          return;
        }
        handle.removeEventListener('pointermove', onMove);
        handle.removeEventListener('pointerup', finish);
        handle.removeEventListener('pointercancel', finish);
        document.body.classList.remove('modal-resize-active');
        if (handle.hasPointerCapture(event.pointerId)) {
          handle.releasePointerCapture(event.pointerId);
        }
        writeStoredSize(modal, latest);
      };

      handle.addEventListener('pointermove', onMove);
      handle.addEventListener('pointerup', finish);
      handle.addEventListener('pointercancel', finish);
    });
  }

  function scan(root) {
    if (!root) {
      return;
    }
    if (root instanceof HTMLElement && root.matches('.modal')) {
      attachResize(root);
    }
    if (root.querySelectorAll) {
      root.querySelectorAll('.modal').forEach(attachResize);
    }
  }

  function refreshViewportSizes() {
    document.querySelectorAll('.modal').forEach((modal) => {
      const dialog = modal.querySelector(':scope > .modal-dialog');
      if (!(dialog instanceof HTMLElement) || shouldSkip(modal, dialog)) {
        return;
      }
      restoreSize(modal, dialog);
    });
  }

  function init() {
    scan(document);

    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
          if (node instanceof HTMLElement) {
            scan(node);
          }
        });
      });
    });
    observer.observe(document.body, { childList: true, subtree: true });

    let resizeFrame = 0;
    window.addEventListener('resize', () => {
      if (resizeFrame) {
        window.cancelAnimationFrame(resizeFrame);
      }
      resizeFrame = window.requestAnimationFrame(() => {
        resizeFrame = 0;
        refreshViewportSizes();
      });
    });

    if (desktopMedia && typeof desktopMedia.addEventListener === 'function') {
      desktopMedia.addEventListener('change', refreshViewportSizes);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
'@

if (Test-Path -LiteralPath $resizeJs) {
    $existingJs = Read-Utf8Text $resizeJs
    if (-not $existingJs.Contains('__iguanaModalResizeInitialized')) {
        throw 'modal-resize.js already exists but is not the Iguana resize runtime. No files were changed.'
    }
}

# All validations passed; now write changes.
Write-Utf8Text $baseScss $baseText $baseBom
Write-Utf8Text $uiHead $uiHeadText $uiHeadBom
Write-Utf8Text $resizeJs $jsContent $false

Write-Host '[1/3] Updated global modal resize styles.'
Write-Host '[2/3] Added modal resize runtime to ui-head.'
Write-Host '[3/3] Wrote static/js/modal-resize.js.'
Write-Host ''
Write-Host 'Build required because SCSS changed:'
Write-Host '  cd spring-panel'
Write-Host '  .\mvnw.cmd -q generate-resources'
Write-Host '  .\mvnw.cmd -q -DskipTests compile'
Write-Host ''
Write-Host 'Resize: drag bottom-right handle.'
Write-Host 'Reset to default: double-click the handle.'
