param(
    [string]$RepoRoot = (Get-Location).Path
)

$ErrorActionPreference = 'Stop'

function Read-Utf8PreservingBom([string]$Path) {
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $offset = if ($hasBom) { 3 } else { 0 }
    $text = [System.Text.Encoding]::UTF8.GetString($bytes, $offset, $bytes.Length - $offset)
    return [pscustomobject]@{ Text = $text; HasBom = $hasBom }
}

function Write-Utf8PreservingBom([string]$Path, [string]$Text, [bool]$HasBom) {
    $encoding = New-Object System.Text.UTF8Encoding($HasBom)
    [System.IO.File]::WriteAllText($Path, $Text, $encoding)
}

function Normalize-Newlines([string]$Text) {
    return $Text.Replace("`r`n", "`n")
}

function Replace-ExactOnce([string]$Text, [string]$Old, [string]$New, [string]$Label) {
    $first = $Text.IndexOf($Old, [System.StringComparison]::Ordinal)
    if ($first -lt 0) {
        throw "[$Label] Исходный блок не найден. Остановлено без частичного применения этого шага."
    }
    $second = $Text.IndexOf($Old, $first + $Old.Length, [System.StringComparison]::Ordinal)
    if ($second -ge 0) {
        throw "[$Label] Исходный блок найден более одного раза. Нужна ручная проверка."
    }
    return $Text.Substring(0, $first) + $New + $Text.Substring($first + $Old.Length)
}

$templatePath = Join-Path $RepoRoot 'spring-panel/src/main/resources/templates/settings/index.html'
$shellPath = Join-Path $RepoRoot 'spring-panel/src/main/resources/static/js/settings-page-shell.js'

if (-not (Test-Path $templatePath)) { throw "Не найден: $templatePath" }
if (-not (Test-Path $shellPath)) { throw "Не найден: $shellPath" }

$templateFile = Read-Utf8PreservingBom $templatePath
$templateNewline = if ($templateFile.Text.Contains("`r`n")) { "`r`n" } else { "`n" }
$template = Normalize-Newlines $templateFile.Text

$calmCss = @'

    /* Settings Calm UI pass: reduce visual noise and keep appearance settings in one workspace. */
    .settings-tiles .settings-tile__tags,
    .settings-tiles .settings-tile__footer {
      display: none;
    }

    .settings-surface .modal .modal-content {
      border-radius: var(--radius-lg);
      box-shadow: 0 16px 44px color-mix(in srgb, var(--shadow-color) 14%, transparent);
    }

    .settings-surface .modal .modal-header {
      padding: 0.9rem 1rem;
      backdrop-filter: none;
    }

    .settings-surface .modal .modal-body {
      padding: 1rem;
      background: var(--surface-card);
    }

    .settings-surface .modal .modal-footer {
      padding: 0.75rem 1rem;
    }

    .settings-surface .modal .btn-close {
      padding: 0.55rem;
      border: 0;
      background-color: transparent;
      box-shadow: none;
      opacity: 0.65;
    }

    .settings-surface .modal .btn-close:hover,
    .settings-surface .modal .btn-close:focus-visible {
      background-color: var(--surface-raised);
      box-shadow: none;
      opacity: 1;
    }

    .settings-modal-lead {
      display: block;
      margin: -0.15rem 0 0.9rem;
      padding: 0;
      border: 0;
      border-radius: 0;
      background: transparent;
      color: var(--color-text-muted);
      font-size: 0.84rem;
      line-height: 1.45;
      box-shadow: none;
    }

    .settings-modal-lead i {
      display: none;
    }

    #panelDesignSettingsModal .modal-body {
      padding: 0;
      overflow: hidden;
    }

    .settings-design-workspace {
      display: grid;
      grid-template-columns: 15rem minmax(0, 1fr);
      min-height: 0;
      height: 100%;
    }

    .settings-design-workspace__nav {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      padding: 0.75rem;
      border-right: 1px solid var(--color-border);
      background: var(--surface-raised);
    }

    .settings-design-nav-item {
      width: 100%;
      padding: 0.7rem 0.75rem;
      border: 0;
      border-radius: var(--radius-md);
      background: transparent;
      color: var(--color-text-muted);
      text-align: left;
      transition: background-color 0.16s ease, color 0.16s ease;
    }

    .settings-design-nav-item:hover {
      background: var(--surface-interactive);
      color: var(--color-text);
    }

    .settings-design-nav-item.active {
      background: var(--surface-selected);
      color: var(--primary);
    }

    .settings-design-nav-item__title {
      display: block;
      font-size: 0.9rem;
      font-weight: 700;
      line-height: 1.25;
    }

    .settings-design-nav-item__hint {
      display: block;
      margin-top: 0.2rem;
      color: var(--color-text-muted);
      font-size: 0.74rem;
      line-height: 1.3;
    }

    .settings-design-workspace__content {
      min-width: 0;
      min-height: 0;
      overflow-y: auto;
      padding: 1rem 1.1rem 1.25rem;
      background: var(--surface-card);
    }

    .settings-design-section__head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1rem;
      padding-bottom: 0.85rem;
      border-bottom: 1px solid var(--color-border);
    }

    .settings-design-section__title {
      margin: 0;
      color: var(--color-text);
      font-size: 1rem;
      font-weight: 700;
    }

    .settings-design-section__description {
      margin: 0.25rem 0 0;
      color: var(--color-text-muted);
      font-size: 0.82rem;
      line-height: 1.4;
    }

    .settings-choice-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 0.55rem;
    }

    .settings-choice-card {
      display: flex;
      align-items: flex-start;
      gap: 0.6rem;
      padding: 0.7rem 0.75rem;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      background: var(--surface-card);
      cursor: pointer;
    }

    .settings-choice-card:hover {
      background: var(--surface-interactive);
    }

    .settings-choice-card:has(.form-check-input:checked) {
      border-color: color-mix(in srgb, var(--primary) 38%, var(--color-border));
      background: var(--surface-selected);
    }

    .settings-choice-card .form-check-input {
      flex: 0 0 auto;
      margin-top: 0.15rem;
    }

    .settings-choice-card__title {
      display: block;
      color: var(--color-text);
      font-size: 0.86rem;
      font-weight: 700;
    }

    .settings-choice-card__hint {
      display: block;
      margin-top: 0.15rem;
      color: var(--color-text-muted);
      font-size: 0.74rem;
      line-height: 1.3;
    }

    @media (max-width: 767.98px) {
      #panelDesignSettingsModal .modal-body {
        overflow-y: auto;
      }

      .settings-design-workspace {
        grid-template-columns: 1fr;
        height: auto;
      }

      .settings-design-workspace__nav {
        position: sticky;
        top: 0;
        z-index: 1;
        flex-direction: row;
        overflow-x: auto;
        border-right: 0;
        border-bottom: 1px solid var(--color-border);
      }

      .settings-design-nav-item {
        min-width: 10rem;
      }

      .settings-design-workspace__content {
        overflow: visible;
      }

      .settings-choice-grid {
        grid-template-columns: 1fr;
      }
    }
'@
$calmCss = Normalize-Newlines $calmCss
if (-not $template.Contains('Settings Calm UI pass:')) {
    $styleClose = "  </style>"
    $styleIndex = $template.IndexOf($styleClose, [System.StringComparison]::Ordinal)
    if ($styleIndex -lt 0) { throw '[CSS] Не найден закрывающий </style>.' }
    $template = $template.Substring(0, $styleIndex) + $calmCss + "`n" + $template.Substring($styleIndex)
}

$oldStatusBusiness = @'
    <!-- Статусы клиентов -->
    <div class="modal fade settings-child-modal" id="statusesModal" tabindex="-1" aria-labelledby="statusesModalLabel" aria-hidden="true">
      <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="statusesModalLabel">Статусы клиентов</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Закрыть"></button>
          </div>
          <div class="modal-body">
            <div class="client-status-toolbar mb-3">
              <div class="d-flex align-items-center gap-2">
                <h6 class="mb-0">Список статусов</h6>
                <span class="text-muted small">Изменения можно вносить в режиме редактирования.</span>
              </div>
              <div class="d-flex gap-2">
                <button type="button" class="btn btn-outline-primary btn-sm" id="editStatusesBtn">
                  <i class="bi bi-pencil-square me-1"></i> Редактировать
                </button>
                <button type="button" class="btn btn-success btn-sm d-none" id="saveStatusesBtn">
                  <i class="bi bi-check-lg me-1"></i> Сохранить
                </button>
                <button type="button" class="btn btn-outline-secondary btn-sm d-none" id="cancelStatusesBtn">
                  Отмена
                </button>
              </div>
            </div>
            <div id="statusesList" class="list-group"></div>
            <div id="statusesEmptyPlaceholder" class="text-muted fst-italic small d-none">
              Пока нет статусов. Нажмите «Редактировать», чтобы добавить первый статус.
            </div>
            <button class="btn btn-sm btn-success mt-3 d-none" id="addStatusBtn" type="button">+ Добавить статус</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Цвета и иконки бизнесов -->
    <div class="modal fade settings-child-modal" id="businessStylesModal" tabindex="-1" aria-labelledby="businessStylesModalLabel" aria-hidden="true">
      <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="businessStylesModalLabel">Метки бизнесов</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Закрыть"></button>
          </div>
          <div class="modal-body">
            <p class="text-muted">
              Настройте цвет заливки, цвет текста и мини-иконку для столбца «Бизнес» на главной странице.
              Иконка отображается в левом верхнем углу ячейки и имеет размер 4×4 пикселя.
            </p>
            <div id="businessStylesEmpty" class="alert alert-light border d-none">
              Пока нет ни одной настройки. Добавьте бизнес, чтобы настроить отображение в таблице.
            </div>
            <div id="businessStylesContainer" class="d-flex flex-column gap-3"></div>
            <button type="button" class="btn btn-outline-primary btn-sm mt-3" id="addBusinessStyleBtn">+ Добавить бизнес</button>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Закрыть</button>
            <button type="button" class="btn btn-primary" id="saveBusinessStylesBtn">Сохранить изменения</button>
          </div>
        </div>
      </div>
    </div>

'@
$oldStatusBusiness = Normalize-Newlines $oldStatusBusiness
if ($template.Contains('id="statusesModal"') -or $template.Contains('id="businessStylesModal"')) {
    $template = Replace-ExactOnce $template $oldStatusBusiness '' 'Удаление дочерних modal статусов/бизнесов'
}

$oldPanel = @'
    <div class="modal fade settings-primary-modal" id="panelDesignSettingsModal" tabindex="-1" aria-labelledby="panelDesignSettingsModalLabel" aria-hidden="true">
      <div class="modal-dialog modal-xl modal-dialog-scrollable">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="panelDesignSettingsModalLabel">Настройки оформления панели</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Закрыть"></button>
          </div>
          <div class="modal-body">
            <div class="settings-modal-lead" role="note">
              <i class="bi bi-palette-fill" aria-hidden="true"></i>
              <div class="settings-modal-lead__text">Сначала выберите раздел оформления, затем внесите точечные изменения в тему, статусы и бизнес-метки.</div>
            </div>
            <div class="d-grid gap-2">
              <button type="button" class="btn btn-outline-primary text-start" data-panel-design-open-appearance>Оформление интерфейса</button>
              <button type="button" class="btn btn-outline-primary text-start" data-panel-design-open-statuses>Статусы клиентов</button>
              <button type="button" class="btn btn-outline-primary text-start" data-panel-design-open-business-styles>Метки бизнесов</button>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Закрыть</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Каналы (боты) -->
    <div class="modal fade settings-child-modal" id="appearanceModal" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Оформление интерфейса</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Закрыть"></button>
          </div>
          <div class="modal-body">
            <form data-theme-form>
              <div class="mb-3">
                <label class="form-label fw-semibold">Выберите тему</label>
                <div class="form-check">
                  <input class="form-check-input" type="radio" name="themeOption" id="themeOptionLight" value="light">
                  <label class="form-check-label" for="themeOptionLight">Светлая тема</label>
                </div>
                <div class="form-check">
                  <input class="form-check-input" type="radio" name="themeOption" id="themeOptionDark" value="dark">
                  <label class="form-check-label" for="themeOptionDark">Тёмная тема</label>
                </div>
                <div class="form-check">
                  <input class="form-check-input" type="radio" name="themeOption" id="themeOptionAuto" value="auto">
                  <label class="form-check-label" for="themeOptionAuto">Автоматически (как в системе)</label>
                </div>
              </div>
              <div class="mb-3">
                <label class="form-label fw-semibold">Стиль интерфейса</label>
                <div class="form-check">
                  <input class="form-check-input" type="radio" name="themePaletteOption" id="themePaletteNeo" value="neo">
                  <label class="form-check-label" for="themePaletteNeo">Штатная тема Iguana</label>
                </div>
                <div class="form-check">
                  <input class="form-check-input" type="radio" name="themePaletteOption" id="themePaletteCatppuccin" value="catppuccin">
                  <label class="form-check-label" for="themePaletteCatppuccin">Catppuccin</label>
                </div>
                <div class="form-check">
                  <input class="form-check-input" type="radio" name="themePaletteOption" id="themePaletteAmberMinimal" value="amber-minimal">
                  <label class="form-check-label" for="themePaletteAmberMinimal">Amber Minimal</label>
                </div>
              </div>
              <p class="text-muted small mb-0">Настройка сохраняется в вашем браузере и применяется ко всем страницам панели.</p>
            </form>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Закрыть</button>
          </div>
        </div>
      </div>
    </div>
'@
$oldPanel = Normalize-Newlines $oldPanel

$newPanel = @'
    <div class="modal fade settings-primary-modal" id="panelDesignSettingsModal" tabindex="-1" aria-labelledby="panelDesignSettingsModalLabel" aria-hidden="true">
      <div class="modal-dialog modal-xl modal-dialog-scrollable">
        <div class="modal-content">
          <div class="modal-header">
            <div>
              <h5 class="modal-title" id="panelDesignSettingsModalLabel">Настройки оформления панели</h5>
              <div class="small text-muted mt-1">Внешний вид интерфейса и визуальные справочники</div>
            </div>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Закрыть"></button>
          </div>
          <div class="modal-body settings-design-workspace">
            <div class="settings-design-workspace__nav" role="tablist" aria-label="Разделы оформления">
              <button type="button" class="settings-design-nav-item active" id="panelDesignAppearanceTab" data-bs-toggle="tab" data-bs-target="#panelDesignAppearancePane" role="tab" aria-controls="panelDesignAppearancePane" aria-selected="true">
                <span class="settings-design-nav-item__title">Оформление интерфейса</span>
                <span class="settings-design-nav-item__hint">Тема и визуальный стиль</span>
              </button>
              <button type="button" class="settings-design-nav-item" id="panelDesignStatusesTab" data-bs-toggle="tab" data-bs-target="#panelDesignStatusesPane" role="tab" aria-controls="panelDesignStatusesPane" aria-selected="false">
                <span class="settings-design-nav-item__title">Статусы клиентов</span>
                <span class="settings-design-nav-item__hint">Названия и цветовая семантика</span>
              </button>
              <button type="button" class="settings-design-nav-item" id="panelDesignBusinessStylesTab" data-bs-toggle="tab" data-bs-target="#panelDesignBusinessStylesPane" role="tab" aria-controls="panelDesignBusinessStylesPane" aria-selected="false">
                <span class="settings-design-nav-item__title">Метки бизнесов</span>
                <span class="settings-design-nav-item__hint">Цвета и мини-иконки</span>
              </button>
            </div>

            <div class="tab-content settings-design-workspace__content">
              <section class="tab-pane fade show active" id="panelDesignAppearancePane" role="tabpanel" aria-labelledby="panelDesignAppearanceTab" tabindex="0">
                <div class="settings-design-section__head">
                  <div>
                    <h6 class="settings-design-section__title">Оформление интерфейса</h6>
                    <p class="settings-design-section__description">Настройка сохраняется в браузере и применяется ко всем страницам панели.</p>
                  </div>
                </div>
                <form data-theme-form>
                  <div class="mb-4">
                    <div class="form-label fw-semibold mb-2">Тема</div>
                    <div class="settings-choice-grid">
                      <label class="settings-choice-card" for="themeOptionLight">
                        <input class="form-check-input" type="radio" name="themeOption" id="themeOptionLight" value="light">
                        <span><span class="settings-choice-card__title">Светлая</span><span class="settings-choice-card__hint">Светлая рабочая поверхность</span></span>
                      </label>
                      <label class="settings-choice-card" for="themeOptionDark">
                        <input class="form-check-input" type="radio" name="themeOption" id="themeOptionDark" value="dark">
                        <span><span class="settings-choice-card__title">Тёмная</span><span class="settings-choice-card__hint">Сниженная яркость интерфейса</span></span>
                      </label>
                      <label class="settings-choice-card" for="themeOptionAuto">
                        <input class="form-check-input" type="radio" name="themeOption" id="themeOptionAuto" value="auto">
                        <span><span class="settings-choice-card__title">Как в системе</span><span class="settings-choice-card__hint">Следовать настройкам устройства</span></span>
                      </label>
                    </div>
                  </div>
                  <div>
                    <div class="form-label fw-semibold mb-2">Стиль интерфейса</div>
                    <div class="settings-choice-grid">
                      <label class="settings-choice-card" for="themePaletteNeo">
                        <input class="form-check-input" type="radio" name="themePaletteOption" id="themePaletteNeo" value="neo">
                        <span><span class="settings-choice-card__title">Iguana</span><span class="settings-choice-card__hint">Штатная calm-tech палитра</span></span>
                      </label>
                      <label class="settings-choice-card" for="themePaletteCatppuccin">
                        <input class="form-check-input" type="radio" name="themePaletteOption" id="themePaletteCatppuccin" value="catppuccin">
                        <span><span class="settings-choice-card__title">Catppuccin</span><span class="settings-choice-card__hint">Мягкая контрастная палитра</span></span>
                      </label>
                      <label class="settings-choice-card" for="themePaletteAmberMinimal">
                        <input class="form-check-input" type="radio" name="themePaletteOption" id="themePaletteAmberMinimal" value="amber-minimal">
                        <span><span class="settings-choice-card__title">Amber Minimal</span><span class="settings-choice-card__hint">Минимальная тёплая палитра</span></span>
                      </label>
                    </div>
                  </div>
                </form>
              </section>

              <section class="tab-pane fade" id="panelDesignStatusesPane" role="tabpanel" aria-labelledby="panelDesignStatusesTab" tabindex="0">
                <div class="settings-design-section__head">
                  <div>
                    <h6 class="settings-design-section__title">Статусы клиентов</h6>
                    <p class="settings-design-section__description">Названия и цветовая семантика статусов клиентов.</p>
                  </div>
                  <div class="d-flex gap-2 flex-wrap justify-content-end">
                    <button type="button" class="btn btn-outline-primary btn-sm" id="editStatusesBtn"><i class="bi bi-pencil-square me-1"></i>Редактировать</button>
                    <button type="button" class="btn btn-primary btn-sm d-none" id="saveStatusesBtn"><i class="bi bi-check-lg me-1"></i>Сохранить</button>
                    <button type="button" class="btn btn-outline-secondary btn-sm d-none" id="cancelStatusesBtn">Отмена</button>
                  </div>
                </div>
                <div id="statusesList" class="list-group"></div>
                <div id="statusesEmptyPlaceholder" class="text-muted fst-italic small d-none">Пока нет статусов. Нажмите «Редактировать», чтобы добавить первый статус.</div>
                <button class="btn btn-sm btn-outline-primary mt-3 d-none" id="addStatusBtn" type="button">+ Добавить статус</button>
              </section>

              <section class="tab-pane fade" id="panelDesignBusinessStylesPane" role="tabpanel" aria-labelledby="panelDesignBusinessStylesTab" tabindex="0">
                <div class="settings-design-section__head">
                  <div>
                    <h6 class="settings-design-section__title">Метки бизнесов</h6>
                    <p class="settings-design-section__description">Цвет заливки, цвет текста и мини-иконка для столбца «Бизнес» на главной странице.</p>
                  </div>
                  <div class="d-flex gap-2 flex-wrap justify-content-end">
                    <button type="button" class="btn btn-outline-primary btn-sm" id="addBusinessStyleBtn">+ Добавить бизнес</button>
                    <button type="button" class="btn btn-primary btn-sm" id="saveBusinessStylesBtn">Сохранить</button>
                  </div>
                </div>
                <div id="businessStylesEmpty" class="alert alert-light border d-none">Пока нет ни одной настройки. Добавьте бизнес, чтобы настроить отображение в таблице.</div>
                <div id="businessStylesContainer" class="d-flex flex-column gap-3"></div>
              </section>
            </div>
          </div>
        </div>
      </div>
    </div>
'@
$newPanel = Normalize-Newlines $newPanel
if ($template.Contains('id="appearanceModal"')) {
    $template = Replace-ExactOnce $template $oldPanel $newPanel 'Workspace оформления'
}

# Sanity checks before writing template.
foreach ($legacyId in @('id="appearanceModal"', 'id="statusesModal"', 'id="businessStylesModal"')) {
    if ($template.Contains($legacyId)) { throw "После преобразования остался legacy modal: $legacyId" }
}
foreach ($requiredId in @('id="panelDesignAppearancePane"', 'id="panelDesignStatusesPane"', 'id="panelDesignBusinessStylesPane"', 'id="editStatusesBtn"', 'id="saveBusinessStylesBtn"')) {
    if (-not $template.Contains($requiredId)) { throw "После преобразования отсутствует обязательный элемент: $requiredId" }
}

$templateOut = if ($templateNewline -eq "`r`n") { $template.Replace("`n", "`r`n") } else { $template }
Write-Utf8PreservingBom $templatePath $templateOut $templateFile.HasBom

$shellFile = Read-Utf8PreservingBom $shellPath
$shellNewline = if ($shellFile.Text.Contains("`r`n")) { "`r`n" } else { "`n" }
$shell = Normalize-Newlines $shellFile.Text

$oldActions = @'
    Object.freeze({
      selector: '[data-panel-design-open-appearance]',
      openTarget: 'appearanceModal',
      hideTarget: 'self',
      callbackName: '',
    }),
    Object.freeze({
      selector: '[data-panel-design-open-statuses]',
      openTarget: 'statusesModal',
      hideTarget: 'self',
      callbackName: '',
    }),
    Object.freeze({
      selector: '[data-panel-design-open-business-styles]',
      openTarget: 'businessStylesModal',
      hideTarget: 'self',
      callbackName: '',
    }),
'@
$oldActions = Normalize-Newlines $oldActions
if ($shell.Contains("data-panel-design-open-appearance")) {
    $shell = Replace-ExactOnce $shell $oldActions '' 'Удаление legacy modal routing оформления'
}

foreach ($legacyToken in @('appearanceModal', 'statusesModal', 'businessStylesModal', 'data-panel-design-open-appearance', 'data-panel-design-open-statuses', 'data-panel-design-open-business-styles')) {
    if ($shell.Contains($legacyToken)) { throw "В settings-page-shell.js осталась legacy ссылка: $legacyToken" }
}

$shellOut = if ($shellNewline -eq "`r`n") { $shell.Replace("`n", "`r`n") } else { $shell }
Write-Utf8PreservingBom $shellPath $shellOut $shellFile.HasBom

Write-Host '[OK] Settings Calm UI pass применён.' -ForegroundColor Green
Write-Host 'Изменены:'
Write-Host "  $templatePath"
Write-Host "  $shellPath"
Write-Host ''
Write-Host 'Maven-сборка для этого прохода не требуется: SCSS/Java не менялись.' -ForegroundColor Cyan
Write-Host 'Перезапустите spring-panel при необходимости и сделайте hard refresh (Ctrl+F5).'
