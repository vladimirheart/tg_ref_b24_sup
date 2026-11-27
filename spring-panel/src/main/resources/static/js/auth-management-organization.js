/* organization controller. The host owns authoritative form/account state; no copied permissions or identity. */
(function () {
  'use strict';
  window.AuthOrganization = Object.freeze({
    create(context, helpers) {
      return new class AuthOrganizationController {
        constructor() { this.context = context; }

      normalizeOrgStructure(structure) {
        const nodes = [];
        const source = Array.isArray(structure?.nodes)
          ? structure.nodes
          : Array.isArray(structure)
          ? structure
          : [];
        if (!Array.isArray(source)) {
          return { nodes };
        }
        const seenIds = new Set();
        source.forEach((entry) => {
          if (!entry || typeof entry !== 'object') {
            return;
          }
          const rawId = String(entry.id || '').trim();
          if (!rawId || seenIds.has(rawId)) {
            return;
          }
          seenIds.add(rawId);
          const name = String(entry.name || '').trim() || 'Новая ветка';
          const parentIdRaw = entry.parent_id != null ? String(entry.parent_id).trim() : '';
          const parentId = parentIdRaw || null;
          const members = Array.isArray(entry.members)
            ? Array.from(
                new Set(
                  entry.members
                    .map((value) => {
                      const num = Number(value);
                      return Number.isInteger(num) ? num : null;
                    })
                    .filter((value) => Number.isInteger(value)),
                ),
              ).sort((a, b) => a - b)
            : [];
          nodes.push({ id: rawId, name, parent_id: parentId, members });
        });
        return { nodes };
      }

      generateOrgNodeId() {
        if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
          return crypto.randomUUID();
        }
        return `node-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
      }

      buildOrgTree() {
        const nodes = this.context.state.orgStructure?.nodes || [];
        const map = new Map();
        nodes.forEach((node) => {
          if (!node || !node.id) {
            return;
          }
          map.set(node.id, {
            id: node.id,
            name: node.name || 'Новая ветка',
            parent_id: node.parent_id || null,
            members: Array.isArray(node.members) ? node.members.slice() : [],
            children: [],
          });
        });

        const roots = [];
        map.forEach((node) => {
          let parentId = node.parent_id && map.has(node.parent_id) ? node.parent_id : null;
          const ancestors = new Set([node.id]);
          let ancestor = parentId;
          while (ancestor) {
            if (ancestors.has(ancestor)) { parentId = null; break; }
            ancestors.add(ancestor);
            ancestor = map.get(ancestor)?.parent_id;
          }
          if (parentId) {
            map.get(parentId).children.push(node);
          } else {
            roots.push(node);
          }
        });

        const collator = new Intl.Collator('ru', { sensitivity: 'base' });
        const sortNodes = (list) => {
          list.sort((a, b) => collator.compare(a.name, b.name));
          list.forEach((child) => sortNodes(child.children));
        };
        sortNodes(roots);
        return roots;
      }

      renderOrgStructure() {
        const container = this.context.elements.orgTree;
        if (!container) {
          return;
        }
        container.innerHTML = '';
        const tree = this.buildOrgTree();
        const hasNodes = tree.length > 0;
        if (this.context.elements.orgEmpty) {
          this.context.elements.orgEmpty.classList.toggle('d-none', hasNodes);
        }
        if (!hasNodes) {
          return;
        }
        tree.forEach((node) => {
          container.appendChild(this.createOrgNodeElement(node));
        });
        this.context.updateDepartmentOptionsFromOrgStructure();
      }

      createOrgNodeElement(node) {
        const item = document.createElement('li');
        item.className = 'org-tree__item';
        item.dataset.nodeId = node.id;

        const card = document.createElement('div');
        card.className = 'org-node';

        const header = document.createElement('div');
        header.className = 'org-node__header';

        const info = document.createElement('div');
        info.className = 'org-node__info';
        const title = document.createElement('div');
        title.className = 'org-node__title';
        title.textContent = node.name || 'Новая ветка';
        info.appendChild(title);

        const members = this.getOrgNodeMembers(node);
        const meta = document.createElement('div');
        meta.className = 'org-node__meta';
        meta.textContent = members.length
          ? `Участников: ${members.length}`
          : 'Участники не назначены';
        info.appendChild(meta);

        if (members.length) {
          const membersContainer = document.createElement('div');
          membersContainer.className = 'org-node__members';
          members.forEach((user) => {
            const badge = document.createElement('span');
            badge.className = 'org-node__badge';
            badge.textContent = user.full_name || user.username || `ID ${user.id}`;
            badge.dataset.employeeId = String(user.id);
            badge.dataset.employeeCompact = 'true';
            badge.title = this.isOrgDepartmentMember(node, user) ? 'Отдел указан в карточке сотрудника' : 'Назначен в состав ветки';
            membersContainer.appendChild(badge);
          });
          info.appendChild(membersContainer);
        }

        header.appendChild(info);

        const actions = document.createElement('div');
        actions.className = 'org-node__actions d-flex flex-wrap gap-2';

        const membersBtn = document.createElement('button');
        membersBtn.type = 'button';
        membersBtn.className = 'btn btn-outline-secondary btn-sm';
        membersBtn.dataset.orgAction = 'members';
        membersBtn.dataset.settingsRead = '';
        membersBtn.dataset.nodeId = node.id;
        membersBtn.textContent = 'Состав';
        actions.appendChild(membersBtn);

        const childBtn = document.createElement('button');
        childBtn.type = 'button';
        childBtn.className = 'btn btn-outline-primary btn-sm';
        childBtn.dataset.orgAction = 'add-child';
        childBtn.dataset.nodeId = node.id;
        childBtn.textContent = '+ Ветка';
        actions.appendChild(childBtn);

        const renameBtn = document.createElement('button');
        renameBtn.type = 'button';
        renameBtn.className = 'btn btn-outline-secondary btn-sm';
        renameBtn.dataset.orgAction = 'rename';
        renameBtn.dataset.nodeId = node.id;
        renameBtn.textContent = 'Переименовать';
        actions.appendChild(renameBtn);

        const deleteBtn = document.createElement('button');
        deleteBtn.type = 'button';
        deleteBtn.className = 'btn btn-outline-danger btn-sm';
        deleteBtn.dataset.orgAction = 'delete';
        deleteBtn.dataset.nodeId = node.id;
        deleteBtn.textContent = 'Удалить';
        actions.appendChild(deleteBtn);

        header.appendChild(actions);
        card.appendChild(header);
        item.appendChild(card);

        if (Array.isArray(node.children) && node.children.length) {
          const toggle = document.createElement('button');
          toggle.type = 'button'; toggle.className = 'org-node__toggle';
          toggle.dataset.orgAction = 'toggle'; toggle.dataset.nodeId = node.id;
          toggle.dataset.settingsRead = '';
          toggle.setAttribute('aria-label', `Подветки: ${node.name}`);
          toggle.setAttribute('aria-expanded', String(!this.context.collapsedOrgNodes.has(node.id)));
          const icon = document.createElement('i'); icon.className = 'bi bi-chevron-down'; icon.setAttribute('aria-hidden', 'true');
          toggle.append(icon); header.prepend(toggle);
          const childrenList = document.createElement('ul');
          childrenList.className = 'org-tree__children list-unstyled';
          childrenList.id = `org-children-${node.id}`;
          toggle.setAttribute('aria-controls', childrenList.id);
          childrenList.hidden = this.context.collapsedOrgNodes.has(node.id);
          node.children.forEach((child) => {
            childrenList.appendChild(this.createOrgNodeElement(child));
          });
          item.appendChild(childrenList);
        }

        return item;
      }

      getOrgDepartmentPaths() {
        const paths = new Map();
        const visit = (nodes, prefix = []) => nodes.forEach(node => {
          const parts = [...prefix, node.name];
          paths.set(node.id, parts.join(' / '));
          visit(node.children, parts);
        });
        visit(this.buildOrgTree());
        return paths;
      }

      isOrgDepartmentMember(node, user, paths = this.getOrgDepartmentPaths()) {
        const normalize = value => String(value || '').split('/').map(part => part.trim().toLocaleLowerCase('ru')).join(' / ');
        const department = normalize(user?.department);
        if (!department) return false;
        const exact = [...paths].filter(([, path]) => normalize(path) === department);
        if (exact.length) return exact.length === 1 && exact[0][0] === node.id;
        // Legacy cards sometimes contain only the leaf name. Resolve only unique names.
        const names = (this.context.state.orgStructure?.nodes || []).filter(item => normalize(item.name) === department);
        return names.length === 1 && names[0].id === node.id;
      }

      getOrgNodeMembers(node) {
        const ids = new Set((node?.members || []).map(Number));
        const paths = this.getOrgDepartmentPaths();
        const users = (this.context.state.users || []).filter(user => ids.has(Number(user.id)) || this.isOrgDepartmentMember(node, user, paths));
        const known = new Set(users.map(user => Number(user.id)));
        ids.forEach(id => { if (!known.has(id)) users.push({ id, username: `ID ${id} (недоступен)` }); });
        return users.sort((a, b) => new Intl.Collator('ru', { sensitivity: 'base' }).compare(a.full_name || a.username || '', b.full_name || b.username || ''));
      }

      markOrgStructureDirty() {
        this.context.orgStructureDirty = true;
        this.updateOrgStructureControls();
      }

      updateOrgStructureControls() {
        const saveBtn = this.context.elements.orgSaveButton;
        if (!saveBtn) {
          return;
        }
        if (this.context.orgStructureSaving) {
          saveBtn.disabled = true;
          saveBtn.classList.add('disabled');
          saveBtn.textContent = 'Сохранение...';
          return;
        }
        saveBtn.disabled = !this.context.orgStructureDirty;
        saveBtn.classList.toggle('disabled', saveBtn.disabled);
        if (this.context.orgSaveButtonDefaultText) {
          saveBtn.textContent = this.context.orgSaveButtonDefaultText;
        }
      }

      handleOrgSectionClick(event) {
        const actionEl = event.target.closest('[data-org-action]');
        if (!actionEl || !this.context.elements.orgSection?.contains(actionEl)) {
          return;
        }
        const action = actionEl.dataset.orgAction;
        const nodeId = actionEl.dataset.nodeId || null;
        if (action === 'toggle') {
          event.preventDefault();
          if (this.context.collapsedOrgNodes.has(nodeId)) this.context.collapsedOrgNodes.delete(nodeId);
          else this.context.collapsedOrgNodes.add(nodeId);
          const list = document.getElementById(`org-children-${nodeId}`);
          if (list) list.hidden = this.context.collapsedOrgNodes.has(nodeId);
          actionEl.setAttribute('aria-expanded', String(!this.context.collapsedOrgNodes.has(nodeId)));
          return;
        }
        switch (action) {
          case 'add-root':
            event.preventDefault();
            this.addOrgNode(null);
            break;
          case 'add-child':
            event.preventDefault();
            if (nodeId) {
              this.addOrgNode(nodeId);
            }
            break;
          case 'rename':
            event.preventDefault();
            if (nodeId) {
              this.renameOrgNode(nodeId);
            }
            break;
          case 'delete':
            event.preventDefault();
            if (nodeId) {
              this.removeOrgNode(nodeId);
            }
            break;
          case 'members':
            event.preventDefault();
            if (nodeId) {
              this.openOrgMembersModal(nodeId);
            }
            break;
          default:
            break;
        }
      }

      addOrgNode(parentId) {
        if (!Array.isArray(this.context.state.orgStructure?.nodes)) {
          this.context.state.orgStructure = { nodes: [] };
        }
        const nodes = this.context.state.orgStructure.nodes;
        const newNode = {
          id: this.generateOrgNodeId(),
          name: parentId ? 'Новая ветка' : 'Новый отдел',
          parent_id: parentId || null,
          members: [],
        };
        nodes.push(newNode);
        this.markOrgStructureDirty();
        this.renderOrgStructure();
      }

      findOrgNode(nodeId) {
        if (!nodeId) {
          return null;
        }
        const nodes = this.context.state.orgStructure?.nodes || [];
        return nodes.find((node) => node?.id === nodeId) || null;
      }

      collectOrgNodeDescendants(nodeId) {
        const nodes = this.context.state.orgStructure?.nodes || [];
        const toRemove = new Set();
        const stack = [nodeId];
        while (stack.length) {
          const current = stack.pop();
          if (!current || toRemove.has(current)) {
            continue;
          }
          toRemove.add(current);
          nodes.forEach((node) => {
            if (node?.parent_id === current) {
              stack.push(node.id);
            }
          });
        }
        return toRemove;
      }

      async removeOrgNode(nodeId) {
          const node = this.findOrgNode(nodeId);
          if (!node) {
            return;
          }
          const toRemove = this.collectOrgNodeDescendants(nodeId);
          const total = toRemove.size;
          const rest = total - 1;
          let suffix = 'дочерних веток';
          if (rest === 1) {
            suffix = 'дочернюю ветку';
          } else if (rest >= 2 && rest <= 4) {
            suffix = 'дочерние ветки';
          }
          const message =
            rest > 0
              ? `Удалить ветку "${node.name}" и ${rest} ${suffix}?`
              : `Удалить ветку "${node.name}"?`;
          const confirmed = await showConfirmActionModal({
            title: 'Удаление ветки',
            message,
            confirmText: 'Удалить',
            confirmVariant: 'danger',
            icon: '🗑️',
          });
          if (!confirmed) {
            return;
          }
          this.context.state.orgStructure.nodes = (this.context.state.orgStructure.nodes || []).filter(
            (entry) => !toRemove.has(entry.id),
          );
          this.markOrgStructureDirty();
          this.renderOrgStructure();
        }

      async renameOrgNode(nodeId) {
          const node = this.findOrgNode(nodeId);
          if (!node) {
            return;
          }
          const nextName = await showPromptModal({
            title: 'Переименовать ветку',
            message: 'Введите новое название для ветки.',
            confirmText: 'Сохранить',
            cancelText: 'Отмена',
            icon: '✏️',
            defaultValue: node.name || '',
          });
          if (nextName === null) {
            return;
          }
          const trimmed = nextName.trim();
          if (!trimmed || trimmed === node.name) {
            return;
          }
          node.name = trimmed;
        this.markOrgStructureDirty();
        this.renderOrgStructure();
      }

      openOrgMembersModal(nodeId) {
        const node = this.findOrgNode(nodeId);
        if (!node || !this.context.elements.orgMembersModal) {
          return;
        }
        this.context.orgMembersModalState = { nodeId };
        if (this.context.elements.orgMembersTitle) {
          this.context.elements.orgMembersTitle.textContent = `Состав ветки «${node.name}»`;
        }
        if (this.context.elements.orgMembersDescription) {
          this.context.elements.orgMembersDescription.textContent =
            'Сотрудники с этим отделом в карточке включены автоматически. Остальных можно назначить вручную. Чтобы изменить отдел сотрудника, откройте его карточку.';
        }
        this.populateOrgMembersModal(node);
        if (this.context.orgMembersModalInstance) {
          this.context.orgMembersModalInstance.show();
        } else {
          this.context.elements.orgMembersModal.classList.add('show');
          this.context.elements.orgMembersModal.style.display = 'block';
        }
      }

      closeOrgMembersModal() {
        if (this.context.orgMembersModalInstance) {
          this.context.orgMembersModalInstance.hide();
        } else if (this.context.elements.orgMembersModal) {
          this.context.elements.orgMembersModal.classList.remove('show');
          this.context.elements.orgMembersModal.style.display = 'none';
        }
        this.context.orgMembersModalState = { nodeId: null };
      }

      populateOrgMembersModal(node) {
        const list = this.context.elements.orgMembersList;
        if (!list) return;
        list.replaceChildren();
        const membersSet = new Set((node.members || []).map(Number));
        const paths = this.getOrgDepartmentPaths();
        const users = [...(this.context.state.users || [])];
        const known = new Set(users.map(user => Number(user.id)));
        membersSet.forEach(id => { if (!known.has(id)) users.push({ id, username: `ID ${id} (недоступен)` }); });
        const collator = new Intl.Collator('ru', { sensitivity: 'base' });
        users.sort((a, b) => collator.compare(a.full_name || a.username || '', b.full_name || b.username || ''));
        const roster = this.context.elements.orgMembersRoster;
        const renderRoster = (memberIds) => {
          roster?.replaceChildren();
          const current = this.getOrgNodeMembers({ ...node, members: memberIds });
          current.forEach(user => {
            const row = document.createElement('div'); row.className = 'org-member';
            const icon = document.createElement('i'); icon.className = 'bi bi-person'; icon.setAttribute('aria-hidden', 'true');
            const info = document.createElement('span'); info.className = 'org-member__info';
            const name = document.createElement('span'); name.className = 'org-member__name'; name.textContent = user.full_name || user.username;
            name.dataset.employeeId = String(user.id); name.dataset.employeeCompact = 'true';
            const meta = document.createElement('span'); meta.className = 'org-member__meta'; meta.textContent = [user.full_name ? user.username : '', user.department].filter(Boolean).join(' · ');
            info.append(name, meta);
            const source = document.createElement('span'); source.className = 'org-member__source';
            source.textContent = this.isOrgDepartmentMember(node, user, paths) ? 'Отдел в карточке' : 'Вручную';
            row.append(icon, info, source); roster?.append(row);
          });
          this.context.elements.orgMembersEmpty?.classList.toggle('d-none', current.length > 0);
          if (this.context.elements.orgMembersCount) this.context.elements.orgMembersCount.textContent = `В составе: ${current.length}`;
        };
        renderRoster(node.members || []);
        const select = document.createElement('select');
        select.id = 'orgMembersSelect'; select.multiple = true; select.name = 'memberIds'; select.className = 'form-select';
        select.dataset.employeePicker = 'true'; select.setAttribute('aria-label', 'Назначить участников вручную');
        users.forEach(user => {
          const id = Number(user?.id);
          if (!Number.isInteger(id) || id <= 0) return;
          if (this.isOrgDepartmentMember(node, user, paths)) {
            if (membersSet.has(id)) {
              const retained = document.createElement('input'); retained.type = 'hidden'; retained.name = 'memberIds'; retained.value = String(id); list.append(retained);
            }
            return;
          }
          const label = [user.full_name || user.username || `ID ${id}`, user.full_name ? user.username : '', user.department, user.role].filter(Boolean).join(' · ');
          const option = new Option(label, String(id)); option.selected = membersSet.has(id); select.add(option);
        });
        select.addEventListener('change', () => renderRoster([...select.selectedOptions].map(option => Number(option.value))));
        list.append(select);
        window.EmployeePicker?.refresh();
      }

      handleOrgMembersSubmit(event) {
        event.preventDefault();
        const nodeId = this.context.orgMembersModalState?.nodeId;
        const node = nodeId ? this.findOrgNode(nodeId) : null;
        if (!node) {
          this.closeOrgMembersModal();
          return;
        }
        const form = event.currentTarget;
        const formData = new FormData(form);
        const selected = formData
          .getAll('memberIds')
          .map((value) => Number(value))
          .filter((value) => Number.isInteger(value));
        selected.sort((a, b) => a - b);
        node.members = Array.from(new Set(selected));
        this.markOrgStructureDirty();
        this.renderOrgStructure();
        this.closeOrgMembersModal();
        this.context.setMessage('Состав ветки обновлён. Нажмите «Сохранить оргструктуру», чтобы сохранить изменения.', 'info');
      }

      handleOrgSaveClick(event) {
        event.preventDefault();
        this.saveOrgStructure();
      }

      saveOrgStructure() {
        if (this.context.orgStructureSaving) {
          return;
        }
        if (!this.context.orgStructureDirty) {
          this.context.setMessage('Нет изменений для сохранения.', 'info');
          return;
        }
        this.context.orgStructureSaving = true;
        this.updateOrgStructureControls();
        const nodes = Array.isArray(this.context.state.orgStructure?.nodes)
          ? this.context.state.orgStructure.nodes
          : [];
        const payload = {
          org_structure: {
            nodes: nodes.map((node) => ({
              id: node.id,
              name: node.name,
              parent_id: node.parent_id || null,
              members: Array.isArray(node.members) ? node.members.slice() : [],
            })),
          },
        };

        fetch(this.context.orgStructureEndpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'same-origin',
          body: JSON.stringify(payload),
        })
          .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
          .then(({ ok, data }) => {
            if (!ok || data.success === false) {
              throw new Error(data.error || 'Не удалось сохранить оргструктуру');
            }
            this.context.state.orgStructure = this.normalizeOrgStructure(data.org_structure);
            this.context.orgStructureDirty = false;
            this.context.setMessage('Оргструктура сохранена.', 'success');
            this.renderOrgStructure();
          })
          .catch((error) => {
            this.context.setMessage(error.message || String(error));
          })
          .finally(() => {
            this.context.orgStructureSaving = false;
            this.updateOrgStructureControls();
          });
      }
      }();
    }
  });
})();
