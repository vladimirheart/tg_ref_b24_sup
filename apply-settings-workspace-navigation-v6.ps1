$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$settingsHtml = Join-Path $repoRoot 'spring-panel\src\main\resources\templates\settings\index.html'
$settingsScss = Join-Path $repoRoot 'spring-panel\src\main\resources\scss\settings.scss'
$workspaceJs = Join-Path $repoRoot 'spring-panel\src\main\resources\static\js\settings-workspace-layout.js'

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

Write-Host '[1/5] Reading migrated Settings sources...'

$html = Read-Utf8Text $settingsHtml
$scss = Read-Utf8Text $settingsScss
$htmlBom = Has-Utf8Bom $settingsHtml
$scssBom = Has-Utf8Bom $settingsScss

$marker = '/* Settings workspace navigation v6 */'
$scriptRef = '/js/settings-workspace-layout.js'

if (-not $html.Contains('/css/settings.css')) {
    throw 'settings.css is not linked. Apply the SCSS migration first. No files were changed.'
}

if ($html -match '(?is)<style\b') {
    throw 'Inline STYLE blocks are still present in settings/index.html. No files were changed.'
}

$requiredModalIds = @('channelsModal', 'categoriesModal', 'usersModal', 'locationsModal', 'panelDesignSettingsModal')
foreach ($modalId in $requiredModalIds) {
    if (-not $html.Contains('id="' + $modalId + '"')) {
        throw "Expected modal not found: $modalId. No files were changed."
    }
}

if (-not $scss.Contains('Settings Calm UI workspace pass v4')) {
    throw 'Expected v4 Settings styles were not found in settings.scss. No files were changed.'
}

if ($scss.Contains($marker)) {
    Write-Host '[2/5] Workspace navigation v6 is already present in settings.scss.'
} else {
    Write-Host '[2/5] Adding reusable workspace navigation styles...'

    $workspaceScss = @'

/* Settings workspace navigation v6 */

/*
 * Workspace primitive
 * -------------------
 * Large Settings modals use the same information architecture as the
 * panel-design workspace: quiet navigation on the left, task content on
 * the right. Bootstrap tab behavior is unchanged; JS only annotates the
 * existing tab host with reusable classes.
 */

.settings-workspace-body {
  padding: 0 !important;
  overflow: hidden !important;
}

.settings-workspace-body:not(.settings-workspace-host) {
  display: flex;
  flex-direction: column;
}

.settings-workspace-body > .settings-modal-lead {
  flex: 0 0 auto;
  margin: 0;
  padding: 0.7rem 1rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--surface-card);
}

.settings-workspace-host {
  display: grid;
  grid-template-columns: 15rem minmax(0, 1fr);
  grid-auto-rows: auto;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--surface-card);
}

.settings-workspace-body:not(.settings-workspace-host) > .settings-workspace-host {
  flex: 1 1 auto;
}

.settings-workspace-host > .settings-workspace-wide {
  grid-column: 1 / -1;
  min-width: 0;
}

.settings-workspace-nav.settings-menu-tabs {
  grid-column: 1;
  align-self: stretch;
  align-content: flex-start;
  display: flex;
  flex-direction: column;
  flex-wrap: nowrap;
  gap: 0.25rem;
  min-width: 0;
  min-height: 0;
  margin: 0 !important;
  padding: 0.75rem !important;
  overflow-x: hidden;
  overflow-y: auto;
  border: 0 !important;
  border-right: 1px solid var(--color-border) !important;
  border-radius: 0 !important;
  background: var(--surface-raised) !important;
  backdrop-filter: none;
}

.settings-workspace-nav.settings-menu-tabs .nav-item {
  width: 100%;
  flex: 0 0 auto;
}

.settings-workspace-nav.settings-menu-tabs .nav-link {
  width: 100%;
  min-height: 2.65rem;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0.5rem;
  padding: 0.68rem 0.72rem;
  border: 0 !important;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-muted);
  text-align: left;
  font-size: 0.86rem;
  font-weight: 650;
  line-height: 1.25;
  white-space: normal;
  box-shadow: none;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.settings-workspace-nav.settings-menu-tabs .nav-link:hover,
.settings-workspace-nav.settings-menu-tabs .nav-link:focus-visible {
  background: var(--surface-interactive);
  color: var(--color-text);
  box-shadow: none;
}

.settings-workspace-nav.settings-menu-tabs .nav-link.active {
  background: var(--surface-selected);
  color: var(--primary);
  box-shadow: none;
}

.settings-workspace-nav.settings-menu-tabs .nav-link i {
  width: 1.05rem;
  flex: 0 0 1.05rem;
  color: currentColor;
  text-align: center;
  opacity: 0.9;
}

.settings-workspace-content.settings-tab-content {
  grid-column: 2;
  align-self: stretch;
  min-width: 0;
  min-height: 0;
  margin: 0 !important;
  padding: 1rem 1.1rem 1.25rem;
  overflow: auto;
  overscroll-behavior: contain;
  background: var(--surface-card);
}

.settings-workspace-content.settings-tab-content > .tab-pane {
  min-width: 0;
  padding: 0 !important;
  border: 0 !important;
  background: transparent !important;
}

.settings-workspace-content.settings-tab-content > .tab-pane > :first-child {
  margin-top: 0 !important;
}

/* Keep runtime/status messages readable when a workspace host owns them. */
.settings-workspace-host > .settings-workspace-wide.alert {
  margin: 0.75rem 1rem 0;
}

/* Users has its tabs inside the auth-management runtime root. */
#usersModal .settings-workspace-host {
  height: 100%;
}

/* Location actions belong to the content plane, not to the navigation rail. */
#locationsModal .settings-workspace-content > .tab-pane > .d-flex.flex-wrap.gap-2:first-child {
  padding-bottom: 0.75rem;
  margin-bottom: 0.9rem !important;
  border-bottom: 1px solid var(--color-border);
}

/* Dialogue tabs can be numerous; keep the rail dense enough for one screen. */
#categoriesModal .settings-workspace-nav.settings-menu-tabs .nav-link {
  min-height: 2.45rem;
  padding-top: 0.58rem;
  padding-bottom: 0.58rem;
}

/* Channels benefits from slightly more room for operational labels. */
#channelsModal .settings-workspace-nav.settings-menu-tabs {
  width: auto;
}

@media (max-width: 767.98px) {
  .settings-workspace-body {
    overflow-y: auto !important;
  }

  .settings-workspace-body:not(.settings-workspace-host) {
    display: block;
  }

  .settings-workspace-host {
    display: flex;
    flex-direction: column;
    overflow: visible;
  }

  .settings-workspace-host > .settings-workspace-wide,
  .settings-workspace-nav.settings-menu-tabs,
  .settings-workspace-content.settings-tab-content {
    grid-column: auto !important;
    grid-row: auto !important;
  }

  .settings-workspace-nav.settings-menu-tabs {
    position: sticky;
    top: 0;
    z-index: 2;
    flex-direction: row;
    gap: 0.25rem;
    padding: 0.55rem 0.65rem !important;
    overflow-x: auto;
    overflow-y: hidden;
    border-right: 0 !important;
    border-bottom: 1px solid var(--color-border) !important;
  }

  .settings-workspace-nav.settings-menu-tabs .nav-item {
    width: auto;
  }

  .settings-workspace-nav.settings-menu-tabs .nav-link {
    width: auto;
    min-width: max-content;
    min-height: 2.4rem;
    white-space: nowrap;
  }

  .settings-workspace-content.settings-tab-content {
    overflow: visible;
    padding: 0.9rem 0.9rem 1.15rem;
  }
}
'@

    $scss = $scss.TrimEnd() + "`r`n" + $workspaceScss.TrimStart() + "`r`n"
}

Write-Host '[3/5] Preparing Settings workspace runtime...'

$jsContent = @'
(() => {
  const TARGET_MODAL_IDS = [
    'channelsModal',
    'categoriesModal',
    'usersModal',
    'locationsModal',
  ];

  function enhanceWorkspace(modal) {
    if (!(modal instanceof HTMLElement) || modal.dataset.settingsWorkspaceReady === 'true') {
      return;
    }

    const body = modal.querySelector(':scope > .modal-dialog > .modal-content > .modal-body');
    if (!(body instanceof HTMLElement)) {
      return;
    }

    const nav = body.querySelector('.settings-menu-tabs');
    const content = body.querySelector('.settings-tab-content');
    if (!(nav instanceof HTMLElement) || !(content instanceof HTMLElement)) {
      return;
    }

    const host = nav.parentElement;
    if (!(host instanceof HTMLElement) || host !== content.parentElement || !body.contains(host)) {
      modal.dataset.settingsWorkspaceReady = 'unsupported';
      return;
    }

    modal.classList.add('settings-workspace-modal');
    body.classList.add('settings-workspace-body');
    host.classList.add('settings-workspace-host');
    nav.classList.add('settings-workspace-nav');
    content.classList.add('settings-workspace-content');

    const wideChildren = Array.from(host.children).filter(
      (child) => child !== nav && child !== content,
    );

    wideChildren.forEach((child, index) => {
      if (!(child instanceof HTMLElement)) {
        return;
      }
      child.classList.add('settings-workspace-wide');
      child.style.gridRow = String(index + 1);
    });

    const contentRow = String(wideChildren.length + 1);
    nav.style.gridRow = contentRow;
    content.style.gridRow = contentRow;

    modal.dataset.settingsWorkspaceReady = 'true';
  }

  function init() {
    TARGET_MODAL_IDS.forEach((id) => {
      enhanceWorkspace(document.getElementById(id));
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
'@

if (Test-Path -LiteralPath $workspaceJs) {
    $existingJs = Read-Utf8Text $workspaceJs
    if ($existingJs.Trim() -ne $jsContent.Trim()) {
        throw 'settings-workspace-layout.js already exists with different content. No files were changed.'
    }
}

Write-Host '[4/5] Linking runtime and validating output...'

if (-not $html.Contains($scriptRef)) {
    $headClose = $html.IndexOf('</head>', [System.StringComparison]::OrdinalIgnoreCase)
    if ($headClose -lt 0) {
        throw '</head> was not found. No files were changed.'
    }

    $scriptTag = '  <script src="/js/settings-workspace-layout.js" defer></script>'
    $html = $html.Insert($headClose, $scriptTag + "`r`n")
}

if (-not $scss.Contains($marker)) {
    throw 'Internal validation failed: v6 SCSS marker is missing. No files were changed.'
}
if (-not $html.Contains($scriptRef)) {
    throw 'Internal validation failed: workspace runtime link is missing. No files were changed.'
}

# All transformations are complete. Write only after validation.
Write-Utf8Text $settingsScss $scss $scssBom
Write-Utf8Text $settingsHtml $html $htmlBom
Write-Utf8Text $workspaceJs ($jsContent.Trim() + "`r`n") $false

Write-Host '[5/5] Done.'
Write-Host 'Changed:'
Write-Host '  spring-panel/src/main/resources/scss/settings.scss'
Write-Host '  spring-panel/src/main/resources/templates/settings/index.html'
Write-Host '  spring-panel/src/main/resources/static/js/settings-workspace-layout.js'
Write-Host 'Run Maven generate-resources, restart the panel if needed, then Ctrl+F5.'
