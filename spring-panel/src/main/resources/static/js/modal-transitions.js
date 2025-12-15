// panel/static/modal-transitions.js
// Универсальные обработчики для плавного открытия и закрытия Bootstrap-модалок
(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const STACK_BASE_Z_INDEX = 20000;
  const STACK_STEP = 20;

  const isAnimatedModal = (modal) =>
    Boolean(modal && modal.classList && modal.classList.contains('modal') && modal.classList.contains('fade'));

  const adjustModalStacking = (modal) => {
    if (!isAnimatedModal(modal)) {
      return;
    }
    const openModals = document.querySelectorAll('.modal.show').length;
    const zIndex = STACK_BASE_Z_INDEX + (openModals + 1) * STACK_STEP;
    modal.style.zIndex = zIndex;
    const updateBackdrop = () => {
      const backdrops = document.querySelectorAll('.modal-backdrop');
      if (!backdrops.length) {
        return;
      }
      const backdrop = backdrops[backdrops.length - 1];
      if (backdrop) {
        backdrop.style.zIndex = zIndex - 1;
      }
    };
    if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
      window.requestAnimationFrame(updateBackdrop);
    } else {
      setTimeout(updateBackdrop, 0);
    }
  };

  const handleShow = (event) => {
    const modal = event.target;
    if (!isAnimatedModal(modal)) {
      return;
    }
    modal.classList.remove('modal-closing');
    adjustModalStacking(modal);
  };

  const handleHide = (event) => {
    const modal = event.target;
    if (!isAnimatedModal(modal)) {
      return;
    }
    modal.classList.add('modal-closing');
  };

  const handleHidden = (event) => {
    const modal = event.target;
    if (!isAnimatedModal(modal)) {
      return;
    }
    modal.classList.remove('modal-closing');
    if (modal.style) {
      modal.style.removeProperty('z-index');
    }
  };

  document.addEventListener('show.bs.modal', handleShow);
  document.addEventListener('hide.bs.modal', handleHide);
  document.addEventListener('hidden.bs.modal', handleHidden);
})();