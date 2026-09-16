(function () {
  if (window.PassportDetailPageRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const equipmentCatalog = Array.isArray(options.equipmentCatalog) ? options.equipmentCatalog : [];
    const initialEditMode = options.initialEditMode === true;
    const statusesRaw = Array.isArray(options.statusesRaw) ? options.statusesRaw : [];
    const parameterValuesRaw = options.parameterValuesRaw && typeof options.parameterValuesRaw === 'object' ? options.parameterValuesRaw : {};

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const idMatch = window.location.pathname.match(/object-passports\/(\d+)/);
    const passportId = idMatch ? Number(idMatch[1]) : null;

    const loading = document.getElementById('passportWorkspaceLoading');
    const workspace = document.getElementById('passportWorkspace');
    const errorBox = document.getElementById('passportWorkspaceError');
    const equipmentSearch = document.getElementById('passportEquipmentSearch');

    let passport = {};
    let cases = [];
    let tasks = [];
    let incidents = [];

    const passportDetailCoreRuntime = window.PassportDetailCoreRuntime?.mount({
        equipmentCatalog
    });
    if (!passportDetailCoreRuntime) {
        throw new Error('PassportDetailCoreRuntime is unavailable');
    }
    const {
        DAY_LABELS,
        text,
        escapeHtml,
        normalizeKey,
        first,
        statusTone,
        setStatus,
        renderProperties,
        parseCatalogMedia,
        parseLinks,
        isEquipmentArchived,
        catalogKey,
        findCatalogItem,
    } = passportDetailCoreRuntime;
    const passportDetailHeaderRuntime = window.PassportDetailHeaderRuntime?.mount({
        getPassport: () => passport,
        getPassportId: () => passportId,
        coreRuntime: passportDetailCoreRuntime,
    });
    if (!passportDetailHeaderRuntime) {
        throw new Error('PassportDetailHeaderRuntime is unavailable');
    }
    const { renderCover, renderHeader } = passportDetailHeaderRuntime;
    const passportDetailOverviewRuntime = window.PassportDetailOverviewRuntime?.mount({
        getPassport: () => passport,
        coreRuntime: passportDetailCoreRuntime,
    });
    if (!passportDetailOverviewRuntime) {
        throw new Error('PassportDetailOverviewRuntime is unavailable');
    }
    const { renderOverview } = passportDetailOverviewRuntime;
    const passportDetailNetworkRuntime = window.PassportDetailNetworkRuntime?.mount({
        getPassport: () => passport,
        coreRuntime: passportDetailCoreRuntime,
    });
    if (!passportDetailNetworkRuntime) {
        throw new Error('PassportDetailNetworkRuntime is unavailable');
    }
    const { renderNetwork } = passportDetailNetworkRuntime;
    const passportDetailEquipmentRuntime = window.PassportDetailEquipmentRuntime?.mount({
        getPassport: () => passport,
        coreRuntime: passportDetailCoreRuntime,
    });
    if (!passportDetailEquipmentRuntime) {
        throw new Error('PassportDetailEquipmentRuntime is unavailable');
    }
    const { renderEquipment } = passportDetailEquipmentRuntime;
    const passportDetailActivityRuntime = window.PassportDetailActivityRuntime?.mount({
        getCases: () => cases,
        getTasks: () => tasks,
        getIncidents: () => incidents,
        coreRuntime: passportDetailCoreRuntime,
    });
    if (!passportDetailActivityRuntime) {
        throw new Error('PassportDetailActivityRuntime is unavailable');
    }
    const { renderActivity } = passportDetailActivityRuntime;
    const passportDetailMediaRuntime = window.PassportDetailMediaRuntime?.mount({
        getPassport: () => passport,
        coreRuntime: passportDetailCoreRuntime,
    });
    if (!passportDetailMediaRuntime) {
        throw new Error('PassportDetailMediaRuntime is unavailable');
    }
    const { renderPhotos, openPhotoViewer, closePhotoViewer, movePhotoViewer } = passportDetailMediaRuntime;
    const passportDetailEditorRuntime = window.PassportDetailEditorRuntime?.mount({
        getPassport: () => passport,
        setPassport: (value) => { passport = value && typeof value === 'object' ? value : {}; },
        getPassportId: () => passportId,
        equipmentCatalog,
        statusesRaw,
        parameterValuesRaw,
        csrfToken,
        coreRuntime: passportDetailCoreRuntime,
        refreshWorkspace: () => {
            renderHeader();
            renderOverview();
            renderNetwork();
            renderEquipment();
            renderActivity();
            renderPhotos();
            updateCounts();
        },
        refreshMedia: () => {
            renderCover();
            renderPhotos();
            updateCounts();
        },
    });
    if (!passportDetailEditorRuntime) {
        throw new Error('PassportDetailEditorRuntime is unavailable');
    }
    const { openEditor, closeEditor, bindEvents: bindEditorEvents } = passportDetailEditorRuntime;
    function bindR4Events() {
        bindEditorEvents();
        document.getElementById('passportPhotoGrid')?.addEventListener('click', (event) => {
            const tile = event.target.closest('[data-passport-photo-index]');
            if (tile) openPhotoViewer(tile.dataset.passportPhotoIndex);
        });
        document.getElementById('passportWorkspaceCover')?.addEventListener('click', () => {
            const cover = document.getElementById('passportWorkspaceCover');
            if (cover && cover.dataset.passportPhotoIndex != null) openPhotoViewer(cover.dataset.passportPhotoIndex);
        });
        document.querySelectorAll('[data-passport-photo-close]').forEach((button) => button.addEventListener('click', closePhotoViewer));
        document.querySelector('[data-passport-photo-prev]')?.addEventListener('click', () => movePhotoViewer(-1));
        document.querySelector('[data-passport-photo-next]')?.addEventListener('click', () => movePhotoViewer(1));
        document.addEventListener('keydown', (event) => {
            const viewer = document.getElementById('passportPhotoViewer');
            if (viewer && !viewer.hidden) {
                if (event.key === 'Escape') closePhotoViewer();
                if (event.key === 'ArrowLeft') movePhotoViewer(-1);
                if (event.key === 'ArrowRight') movePhotoViewer(1);
                return;
            }
            const layer = document.getElementById('passportEditLayer');
            if (layer && !layer.hidden && event.key === 'Escape') closeEditor();
        });
    }

    function updateCounts() {
        const equipmentCount = Array.isArray(passport.equipment)
            ? passport.equipment.filter((item) => !isEquipmentArchived(item)).length
            : 0;
        const photosCount = Array.isArray(passport.photos) ? passport.photos.length : 0;
        const values = {
            passportEquipmentCount: equipmentCount,
            passportEquipmentTabCount: equipmentCount,
            passportCasesCount: cases.length,
            passportCasesTabCount: cases.length,
            passportTasksCount: tasks.length,
            passportTasksTabCount: tasks.length,
            passportPhotosCount: photosCount,
            passportPhotosTabCount: photosCount
        };
        Object.entries(values).forEach(([id, value]) => {
            const node = document.getElementById(id);
            if (node) node.textContent = String(value);
        });
    }

    function activateTab(name) {
        document.querySelectorAll('[data-passport-tab]').forEach((button) => {
            button.classList.toggle('is-active', button.dataset.passportTab === name);
        });
        document.querySelectorAll('[data-passport-panel]').forEach((panel) => {
            panel.classList.toggle('is-active', panel.dataset.passportPanel === name);
        });
        if (name === 'equipment' && equipmentSearch) {
            window.requestAnimationFrame(() => equipmentSearch.focus({ preventScroll: true }));
        }
    }

    function bindTabs() {
        document.addEventListener('click', (event) => {
            const tab = event.target.closest('[data-passport-tab]');
            if (tab) {
                activateTab(tab.dataset.passportTab);
                return;
            }
            const shortcut = event.target.closest('[data-passport-tab-target]');
            if (shortcut) activateTab(shortcut.dataset.passportTabTarget);
        });
        if (equipmentSearch) equipmentSearch.addEventListener('input', renderEquipment);
    }

    async function loadJson(url, fallback) {
        try {
            const response = await fetch(url, { headers: { Accept: 'application/json' } });
            if (!response.ok) return fallback;
            return await response.json();
        } catch (error) {
            return fallback;
        }
    }

    async function load() {
        if (!Number.isFinite(passportId) || passportId <= 0) {
            throw new Error('Не удалось определить ID паспорта объекта.');
        }

        const [passportResponse, casesResponse, tasksResponse, incidentsResponse] = await Promise.all([
            loadJson(`/api/object_passports/${passportId}`, null),
            loadJson(`/api/object_passports/${passportId}/cases`, { items: [] }),
            loadJson(`/api/object_passports/${passportId}/tasks`, { items: [] }),
            loadJson(`/api/object_passports/${passportId}/incidents`, { items: [] })
        ]);

        if (!passportResponse || passportResponse.success === false || !passportResponse.passport) {
            throw new Error('Паспорт объекта не найден или недоступен.');
        }

        passport = passportResponse.passport || {};
        cases = Array.isArray(casesResponse && casesResponse.items) ? casesResponse.items : (Array.isArray(passport.cases) ? passport.cases : []);
        tasks = Array.isArray(tasksResponse && tasksResponse.items) ? tasksResponse.items : (Array.isArray(passport.tasks) ? passport.tasks : []);
        incidents = Array.isArray(incidentsResponse && incidentsResponse.items) ? incidentsResponse.items : [];

        renderHeader();
        renderOverview();
        renderNetwork();
        renderEquipment();
        renderActivity();
        renderPhotos();
        updateCounts();
        loading.classList.add('d-none');
        workspace.classList.remove('d-none');
        if (initialEditMode) openEditor('main');
    }

    function boot() {
        bindTabs();
        bindR4Events();
        load().catch((error) => {
            loading.classList.add('d-none');
            errorBox.textContent = error && error.message ? error.message : String(error);
            errorBox.classList.remove('d-none');
        });
    }

    return Object.freeze({
      boot,
    });
  }

  function mount(options = {}) {
    return createRuntime(options);
  }

  window.PassportDetailPageRuntime = Object.freeze({
    mount,
  });
}());
