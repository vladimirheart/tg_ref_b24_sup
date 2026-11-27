/* photo controller. The host owns authoritative form/account state; no copied permissions or identity. */
(function () {
  'use strict';
  window.AuthUserPhoto = Object.freeze({
    create(context, helpers) {
      return new class AuthUserPhotoController {
        constructor() { this.context = context; }

      createUserAvatar(user) {
        const container = document.createElement('div');
        container.className = 'auth-user-avatar';
        container.dataset.employeeId = String(user.id);
        container.dataset.employeePreserveContent = 'true';
        const displayName = (user?.full_name || user?.username || '').trim();
        const photoUrl = String(user?.photo || '').trim();
        if (photoUrl) {
          container.classList.add('auth-user-avatar--image');
          const img = document.createElement('img');
          img.src = photoUrl;
          img.alt = displayName ? `Фото ${displayName}` : 'Фото пользователя';
          img.loading = 'lazy';
          container.appendChild(img);
        } else {
          container.classList.add('auth-user-avatar--placeholder');
          const initials = document.createElement('span');
          initials.className = 'auth-user-avatar__initials';
          initials.textContent = this.context.getUserInitials(displayName || '');
          container.appendChild(initials);
        }
        return container;
      }

      resetPhotoUploader(value = '', canEdit = true) {
        if (this.context.photoUploadState?.tempUrl && this.context.photoUploadState.tempUrl.startsWith('blob:')) {
          URL.revokeObjectURL(this.context.photoUploadState.tempUrl);
        }
        const cleanValue = String(value || '').trim();
        this.context.photoUploadState = { value: cleanValue, previewUrl: cleanValue, tempUrl: '' };
        if (this.context.elements.userPhotoInput) {
          this.context.elements.userPhotoInput.value = cleanValue;
          this.context.elements.userPhotoInput.disabled = !canEdit;
        }
        if (this.context.elements.userPhotoFileInput) {
          this.context.elements.userPhotoFileInput.value = '';
          this.context.elements.userPhotoFileInput.disabled = !canEdit;
        }
        this.applyPhotoPreview(cleanValue, { temporary: false });
        this.setPhotoStatus('');
        this.setPhotoUploading(false);
        this.setPhotoUploadDisabled(!canEdit);
      }

      setPhotoValue(value) {
        const cleanValue = String(value || '').trim();
        this.context.photoUploadState.value = cleanValue;
        this.context.photoUploadState.previewUrl = cleanValue;
        if (this.context.elements.userPhotoInput) {
          this.context.elements.userPhotoInput.value = cleanValue;
        }
        this.applyPhotoPreview(cleanValue, { temporary: false });
      }

      applyPhotoPreview(url, { temporary = false } = {}) {
        const nextUrl = String(url || '').trim();
        if (temporary) {
          if (this.context.photoUploadState.tempUrl && this.context.photoUploadState.tempUrl.startsWith('blob:') && this.context.photoUploadState.tempUrl !== nextUrl) {
            URL.revokeObjectURL(this.context.photoUploadState.tempUrl);
          }
          this.context.photoUploadState.tempUrl = nextUrl;
        } else {
          if (this.context.photoUploadState.tempUrl && this.context.photoUploadState.tempUrl.startsWith('blob:')) {
            URL.revokeObjectURL(this.context.photoUploadState.tempUrl);
          }
          this.context.photoUploadState.tempUrl = '';
          this.context.photoUploadState.previewUrl = nextUrl;
        }
        const effectiveUrl = (this.context.photoUploadState.tempUrl || this.context.photoUploadState.previewUrl || '').trim();
        const hasPreview = Boolean(effectiveUrl);
        if (this.context.elements.userPhotoPreviewImage) {
          if (hasPreview) {
            this.context.elements.userPhotoPreviewImage.src = effectiveUrl;
            this.context.elements.userPhotoPreviewImage.alt = 'Фото пользователя';
          } else {
            this.context.elements.userPhotoPreviewImage.removeAttribute('src');
            this.context.elements.userPhotoPreviewImage.alt = 'Фото пользователя';
          }
        }
        if (this.context.elements.userPhotoPreview) {
          this.context.elements.userPhotoPreview.classList.toggle('d-none', !hasPreview);
        }
        if (this.context.elements.userPhotoPlaceholder) {
          this.context.elements.userPhotoPlaceholder.classList.toggle('d-none', hasPreview);
        }
        if (this.context.elements.userPhotoDropzone) {
          this.context.elements.userPhotoDropzone.classList.toggle('has-photo', hasPreview);
          this.context.elements.userPhotoDropzone.classList.remove('is-dragover');
        }
        if (this.context.elements.userPhotoViewerDownload && !hasPreview) {
          this.context.elements.userPhotoViewerDownload.href = '#';
          this.context.elements.userPhotoViewerDownload.classList.add('disabled');
          this.context.elements.userPhotoViewerDownload.removeAttribute('download');
        }
        this.updatePhotoControls();
      }

      setPhotoStatus(message, variant = 'muted') {
        const status = this.context.elements.userPhotoStatus;
        if (!status) {
          return;
        }
        const text = String(message || '').trim();
        status.textContent = text;
        status.classList.remove('text-danger', 'text-success', 'text-muted');
        if (!text) {
          status.classList.add('text-muted');
          return;
        }
        const variantClass = variant === 'danger' ? 'text-danger' : variant === 'success' ? 'text-success' : 'text-muted';
        status.classList.add(variantClass);
      }

      setPhotoUploadDisabled(disabled) {
        this.context.photoUploadDisabled = Boolean(disabled);
        const dropzone = this.context.elements.userPhotoDropzone;
        if (dropzone) {
          dropzone.classList.toggle('is-disabled', this.context.photoUploadDisabled);
          if (this.context.photoUploadDisabled) {
            dropzone.classList.remove('is-dragover');
          }
        }
        if (this.context.elements.userPhotoTrigger) {
          this.context.elements.userPhotoTrigger.disabled = this.context.photoUploadDisabled || this.context.photoUploadUploading;
        }
        if (this.context.elements.userPhotoChangeButton) {
          this.context.elements.userPhotoChangeButton.disabled = this.context.photoUploadDisabled || this.context.photoUploadUploading;
        }
        if (this.context.elements.userPhotoRemoveButton) {
          this.context.elements.userPhotoRemoveButton.disabled = this.context.photoUploadDisabled || !this.context.photoUploadState.value;
        }
        if (this.context.elements.userPhotoFileInput) {
          this.context.elements.userPhotoFileInput.disabled = this.context.photoUploadDisabled || this.context.photoUploadUploading;
        }
        this.updatePhotoControls();
      }

      setPhotoUploading(isUploading) {
        this.context.photoUploadUploading = Boolean(isUploading);
        const dropzone = this.context.elements.userPhotoDropzone;
        if (dropzone) {
          dropzone.classList.toggle('is-uploading', this.context.photoUploadUploading);
        }
        if (this.context.elements.userPhotoTrigger) {
          this.context.elements.userPhotoTrigger.disabled = this.context.photoUploadDisabled || this.context.photoUploadUploading;
        }
        if (this.context.elements.userPhotoChangeButton) {
          this.context.elements.userPhotoChangeButton.disabled = this.context.photoUploadDisabled || this.context.photoUploadUploading;
        }
        if (this.context.elements.userPhotoRemoveButton) {
          this.context.elements.userPhotoRemoveButton.disabled = this.context.photoUploadDisabled || (!this.context.photoUploadState.value && !this.context.photoUploadState.tempUrl) || this.context.photoUploadUploading;
        }
        if (this.context.elements.userPhotoUploadingIndicator) {
          this.context.elements.userPhotoUploadingIndicator.classList.toggle('d-none', !this.context.photoUploadUploading);
        }
        if (this.context.elements.userPhotoFileInput) {
          this.context.elements.userPhotoFileInput.disabled = this.context.photoUploadDisabled || this.context.photoUploadUploading;
        }
        this.updatePhotoControls();
      }

      updatePhotoControls() {
        if (this.context.elements.userPhotoRemoveButton) {
          const hasValue = Boolean((this.context.photoUploadState.value || this.context.photoUploadState.tempUrl || '').trim());
          this.context.elements.userPhotoRemoveButton.disabled = this.context.photoUploadDisabled || !hasValue || this.context.photoUploadUploading;
        }
      }

      isPhotoInteractionLocked() {
        return this.context.photoUploadDisabled || this.context.photoUploadUploading;
      }

      handlePhotoDropzoneClick(event) {
        if (event) {
          event.preventDefault();
          event.stopPropagation();
        }
        if (event?.target?.closest('[data-auth-user-photo-remove]')) {
          return;
        }
        if (event?.target?.closest('[data-auth-user-photo-preview-trigger]')) {
          return;
        }
        if (this.isPhotoInteractionLocked()) {
          return;
        }
        const dropzone = this.context.elements.userPhotoDropzone;
        const hasPhoto = dropzone ? dropzone.classList.contains('has-photo') : false;
        const clickedTrigger = event?.target?.closest('[data-auth-user-photo-trigger]');
        const clickedChange = event?.target?.closest('[data-auth-user-photo-change]');
        if (hasPhoto && !clickedTrigger && !clickedChange) {
          return;
        }
        if (this.context.elements.userPhotoFileInput) {
          this.context.elements.userPhotoFileInput.click();
        }
      }

      handlePhotoFileChange(event) {
        const files = event?.target?.files;
        if (!files || !files.length) {
          return;
        }
        this.processPhotoFile(files[0]);
      }

      handlePhotoDrop(event) {
        event.preventDefault();
        event.stopPropagation();
        if (this.context.elements.userPhotoDropzone) {
          this.context.elements.userPhotoDropzone.classList.remove('is-dragover');
        }
        if (this.isPhotoInteractionLocked()) {
          return;
        }
        let file = null;
        const items = event.dataTransfer?.items;
        if (items && items.length) {
          for (const item of items) {
            if (item.kind === 'file') {
              file = item.getAsFile();
              break;
            }
          }
        }
        if (!file) {
          const files = event.dataTransfer?.files;
          if (files && files.length) {
            file = files[0];
          }
        }
        if (file) {
          this.processPhotoFile(file);
        }
      }

      handlePhotoDragOver(event) {
        event.preventDefault();
        event.stopPropagation();
        if (!this.context.elements.userPhotoDropzone) {
          return;
        }
        if (this.isPhotoInteractionLocked()) {
          if (event.dataTransfer) {
            event.dataTransfer.dropEffect = 'none';
          }
          return;
        }
        this.context.elements.userPhotoDropzone.classList.add('is-dragover');
        if (event.dataTransfer) {
          event.dataTransfer.dropEffect = 'copy';
        }
      }

      handlePhotoDragLeave(event) {
        event.preventDefault();
        event.stopPropagation();
        if (this.context.elements.userPhotoDropzone) {
          this.context.elements.userPhotoDropzone.classList.remove('is-dragover');
        }
      }

      handlePhotoRemove(event) {
        if (event) {
          event.preventDefault();
          event.stopPropagation();
        }
        if (this.isPhotoInteractionLocked()) {
          return;
        }
        const hadValue = Boolean(this.context.photoUploadState.value || this.context.photoUploadState.previewUrl);
        this.setPhotoValue('');
        this.setPhotoStatus(hadValue ? 'Фото удалено.' : '');
      }

      processPhotoFile(file) {
        if (!file || this.isPhotoInteractionLocked()) {
          return;
        }
        const errorMessage = this.validatePhotoFile(file);
        if (errorMessage) {
          this.setPhotoStatus(errorMessage, 'danger');
          return;
        }
        const previousValue = this.context.photoUploadState.value;
        const previousPreview = this.context.photoUploadState.previewUrl;
        const objectUrl = URL.createObjectURL(file);
        this.applyPhotoPreview(objectUrl, { temporary: true });
        this.setPhotoStatus('Загружаем файл…', 'muted');
        this.setPhotoUploading(true);
        this.uploadUserPhoto(file)
          .then((data) => {
            const url = String(data?.url || data?.photo || data?.path || '').trim();
            if (!url) {
              throw new Error('Сервер не вернул ссылку на фото.');
            }
            this.setPhotoValue(url);
            this.persistUploadedPhotoState(url);
            this.setPhotoStatus('Фото обновлено.', 'success');
          })
          .catch((error) => {
            this.applyPhotoPreview('', { temporary: true });
            this.applyPhotoPreview(previousPreview, { temporary: false });
            this.context.photoUploadState.value = previousValue;
            this.context.photoUploadState.previewUrl = previousPreview;
            if (this.context.elements.userPhotoInput) {
              this.context.elements.userPhotoInput.value = previousValue;
            }
            this.setPhotoStatus(error.message || String(error), 'danger');
          })
          .finally(() => {
            this.setPhotoUploading(false);
            if (this.context.elements.userPhotoFileInput) {
              this.context.elements.userPhotoFileInput.value = '';
            }
          });
      }

      validatePhotoFile(file) {
        const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
        const maxSize = 5 * 1024 * 1024;
        if (file.size > maxSize) {
          return 'Размер файла не должен превышать 5 МБ.';
        }
        if (file.type && !allowedTypes.includes(file.type)) {
          return 'Поддерживаются изображения в форматах PNG, JPG, GIF или WebP.';
        }
        const extension = (file.name || '').split('.').pop()?.toLowerCase();
        if (!extension) {
          return 'Выберите изображение в поддерживаемом формате.';
        }
        const allowedExtensions = ['jpg', 'jpeg', 'png', 'gif', 'webp'];
        if (!allowedExtensions.includes(extension)) {
          return 'Поддерживаются изображения в форматах PNG, JPG, GIF или WebP.';
        }
        return '';
      }

      uploadUserPhoto(file) {
        if (!this.context.photoUploadEndpoint) {
          return Promise.reject(new Error('Загрузка фотографий недоступна.'));
        }
        const formData = new FormData();
        formData.append('photo', file);
        const userId = this.context.modalState?.mode === 'edit' ? this.context.modalState?.userId : null;
        if (userId != null && userId !== '') {
          formData.append('userId', String(userId));
        }
        return fetch(this.context.photoUploadEndpoint, {
          method: 'POST',
          credentials: 'same-origin',
          body: formData,
        })
          .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
          .then(({ ok, data }) => {
            if (!ok || data.success === false) {
              throw new Error(data?.error || 'Не удалось загрузить фото');
            }
            return data;
          });
      }

      persistUploadedPhotoState(url) {
        const normalizedUrl = String(url || '').trim();
        if (!normalizedUrl) {
          return;
        }
        const userId = this.context.modalState?.mode === 'edit' ? this.context.modalState?.userId : null;
        if (userId != null) {
          this.context.applyUserUpdate({ id: userId, photo: normalizedUrl });
        }
        if (this.context.modalState?.original) {
          this.context.modalState.original.photo = normalizedUrl;
        }
        if (userId != null && String(this.context.state.currentUserId) === String(userId)) {
          this.syncSidebarAvatar(normalizedUrl);
        }
      }

      syncSidebarAvatar(url) {
        const avatar = document.querySelector('[data-sidebar-user-avatar]');
        if (!(avatar instanceof HTMLElement)) {
          return;
        }
        let avatarImage = avatar.querySelector('[data-sidebar-user-avatar-img]');
        const normalizedUrl = String(url || '').trim();
        if (!normalizedUrl) {
          avatar.classList.remove('has-image', 'is-loaded');
          if (avatarImage) {
            avatarImage.remove();
          }
          return;
        }
        if (!(avatarImage instanceof HTMLImageElement)) {
          avatarImage = document.createElement('img');
          avatarImage.alt = 'Аватар';
          avatarImage.loading = 'lazy';
          avatarImage.decoding = 'async';
          avatarImage.dataset.sidebarUserAvatarImg = '';
          avatar.appendChild(avatarImage);
        }
        avatar.classList.add('has-image');
        avatar.classList.remove('is-loaded');
        avatarImage.onload = () => avatar.classList.add('is-loaded');
        avatarImage.onerror = () => avatar.classList.remove('is-loaded');
        avatarImage.src = normalizedUrl;
      }

      handlePhotoPreviewClick(event) {
        const trigger = event.target.closest('[data-auth-user-photo-preview-trigger]');
        if (!trigger) {
          return;
        }
        event.preventDefault();
        event.stopPropagation();
        this.openPhotoViewer();
      }

      handlePhotoPreviewKeydown(event) {
        if (event.key !== 'Enter' && event.key !== ' ') {
          return;
        }
        event.preventDefault();
        event.stopPropagation();
        this.openPhotoViewer();
      }

      openPhotoViewer() {
        const url = (this.context.photoUploadState?.tempUrl || this.context.photoUploadState?.previewUrl || '').trim();
        if (!url || !this.context.elements.userPhotoViewerImage) {
          return;
        }
        const username =
          (this.context.modalState?.mode === 'edit' && this.context.modalState?.original?.username) ||
          (this.context.elements.userUsernameInput ? this.context.elements.userUsernameInput.value : '') ||
          '';
        const fullName =
          (this.context.modalState?.mode === 'edit' && this.context.modalState?.original?.full_name) ||
          (this.context.elements.userFullNameInput ? this.context.elements.userFullNameInput.value : '') ||
          '';
        const displayName = fullName || username;
        const altText = displayName ? `Фото пользователя ${displayName}` : 'Фото пользователя';
        this.context.elements.userPhotoViewerImage.src = url;
        this.context.elements.userPhotoViewerImage.alt = altText;
        if (this.context.elements.userPhotoViewerDownload) {
          const sanitizedName = (displayName || 'user-photo').replace(/[^\w\-]+/g, '_');
          this.context.elements.userPhotoViewerDownload.href = url;
          this.context.elements.userPhotoViewerDownload.setAttribute('download', sanitizedName || 'user-photo');
          this.context.elements.userPhotoViewerDownload.classList.remove('disabled');
        }
        if (this.context.photoViewerModalInstance) {
          this.context.photoViewerModalInstance.show();
        } else if (this.context.elements.userPhotoViewerModal) {
          this.context.elements.userPhotoViewerModal.classList.add('show');
          this.context.elements.userPhotoViewerModal.style.display = 'block';
          this.context.elements.userPhotoViewerModal.removeAttribute('aria-hidden');
        }
      }
      }();
    }
  });
})();
