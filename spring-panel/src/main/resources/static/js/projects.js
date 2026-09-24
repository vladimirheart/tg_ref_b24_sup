(function () {
  const grid = document.getElementById('projectsGrid');
  if (!grid) return;

  const totalEl = document.getElementById('projectsTotal');
  const activeEl = document.getElementById('projectsActive');
  const pausedEl = document.getElementById('projectsPaused');
  const includeArchived = document.getElementById('includeArchivedProjects');
  const createBtn = document.getElementById('createProjectBtn');
  const modalEl = document.getElementById('projectModal');
  const form = document.getElementById('projectForm');
  const saveBtn = document.getElementById('saveProjectBtn');
  const archiveBtn = document.getElementById('archiveProjectBtn');
  const meta = document.getElementById('projectModalMeta');
  let items = [];

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';

  function requestOptions(options = {}) {
    const merged = { credentials: 'same-origin', ...options };
    const method = String(merged.method || 'GET').toUpperCase();
    if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method) || !csrfToken) return merged;
    const headers = new Headers(merged.headers || {});
    headers.set(csrfHeader, csrfToken);
    return { ...merged, headers };
  }

  const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[ch]));
  const labels = { active: 'Активный', paused: 'Пауза', done: 'Завершён', archived: 'Архив' };
  const modal = () => bootstrap.Modal.getOrCreateInstance(modalEl);

  async function request(url, options = {}) {
    const response = await fetch(url, requestOptions(options));
    if (response.status === 401) {
      window.location.href = '/login';
      throw new Error('Unauthorized');
    }
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      throw new Error(data.message || data.error || `HTTP ${response.status}`);
    }
    return response.json();
  }

  function render() {
    totalEl.textContent = items.length;
    activeEl.textContent = items.filter((item) => item.status === 'active').length;
    pausedEl.textContent = items.filter((item) => item.status === 'paused').length;

    if (!items.length) {
      grid.innerHTML = '<div class="projects-empty-state"><strong>Проектов пока нет.</strong><span>Создайте первый проект и добавьте в него существующие задачи.</span></div>';
      return;
    }

    grid.innerHTML = items.map((project) => `
      <article class="project-card" data-project-id="${esc(project.id)}">
        <div class="project-card-head">
          <span class="project-code">${esc(project.project_key || `PRJ_${project.id}`)}</span>
          <span class="project-status" data-project-status="${esc(project.status)}">${esc(labels[project.status] || project.status || '—')}</span>
        </div>
        <div class="project-card-main">
          <h2>${esc(project.name || 'Без названия')}</h2>
          <p>${esc(project.description || 'Без описания')}</p>
        </div>
        <div class="project-card-meta">
          <span><i class="bi bi-person"></i>${esc(project.lead_identity || 'Руководитель не назначен')}</span>
          <span><i class="bi bi-check2-square"></i>${esc(project.task_count || 0)} задач</span>
        </div>
        <div class="project-card-actions">
          <a class="btn btn-sm btn-outline-primary" href="/tasks?project=${encodeURIComponent(project.id)}">Открыть задачи</a>
          <button class="btn btn-sm btn-outline-secondary" type="button" data-project-edit="${esc(project.id)}" aria-label="Редактировать проект" title="Редактировать"><i class="bi bi-pencil"></i></button>
        </div>
      </article>`).join('');
  }

  async function load() {
    const data = await request(`/api/projects?include_archived=${includeArchived.checked ? 'true' : 'false'}`);
    items = data.items || [];
    render();
  }

  function openProject(project) {
    form.reset();
    form.elements.id.value = project?.id || '';
    form.elements.name.value = project?.name || '';
    form.elements.project_key.value = project?.project_key || '';
    form.elements.status.value = project?.status === 'archived' ? 'done' : (project?.status || 'active');
    form.elements.lead_identity.value = project?.lead_identity || '';
    form.elements.description.value = project?.description || '';
    archiveBtn.hidden = !project?.id || project?.status === 'archived';
    meta.textContent = project?.project_key || 'Новый проект';
    modal().show();
  }

  createBtn.addEventListener('click', () => openProject(null));
  includeArchived.addEventListener('change', () => load().catch(showLoadError));
  grid.addEventListener('click', (event) => {
    const button = event.target.closest('[data-project-edit]');
    if (!button) return;
    openProject(items.find((item) => String(item.id) === String(button.dataset.projectEdit)) || null);
  });

  saveBtn.addEventListener('click', async () => {
    if (!form.reportValidity()) return;
    const id = String(form.elements.id.value || '').trim();
    const payload = {
      name: form.elements.name.value.trim(),
      project_key: form.elements.project_key.value.trim() || null,
      status: form.elements.status.value,
      lead_identity: form.elements.lead_identity.value.trim() || null,
      description: form.elements.description.value.trim() || null,
    };

    try {
      await request(id ? `/api/projects/${id}` : '/api/projects', {
        method: id ? 'PATCH' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      modal().hide();
      await load();
    } catch (error) {
      showAppModalMessage({ title: 'Не удалось сохранить проект', message: error.message, variant: 'danger' });
    }
  });

  archiveBtn.addEventListener('click', async () => {
    const id = String(form.elements.id.value || '').trim();
    if (!id) return;

    const confirmed = await showConfirmActionModal({
      title: 'Архивировать проект',
      message: 'Проект исчезнет из активного списка. Связанные задачи сохранятся.',
      confirmText: 'Архивировать',
      confirmVariant: 'warning',
    });
    if (!confirmed) return;

    try {
      await request(`/api/projects/${id}`, { method: 'DELETE' });
      modal().hide();
      await load();
    } catch (error) {
      showAppModalMessage({ title: 'Не удалось архивировать проект', message: error.message, variant: 'danger' });
    }
  });

  function showLoadError(error) {
    grid.innerHTML = `<div class="projects-empty-state"><strong>Не удалось загрузить проекты.</strong><span>${esc(error.message)}</span></div>`;
  }

  load().catch(showLoadError);
})();
