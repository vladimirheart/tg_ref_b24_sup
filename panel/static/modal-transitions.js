// panel/static/modal-transitions.js
// Универсальные обработчики для плавного открытия и закрытия Bootstrap-модалок
(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const handleShow = (event) => {
    const modal = event.target;
    if (!modal || !modal.classList || !modal.classList.contains('modal') || !modal.classList.contains('fade')) {
      return;
    }
    modal.classList.remove('modal-closing');
  };

  const handleHide = (event) => {
    const modal = event.target;
    if (!modal || !modal.classList || !modal.classList.contains('modal') || !modal.classList.contains('fade')) {
      return;
    }
    modal.classList.add('modal-closing');
  };

  const handleHidden = (event) => {
    const modal = event.target;
    if (!modal || !modal.classList || !modal.classList.contains('modal') || !modal.classList.contains('fade')) {
      return;
    }
    modal.classList.remove('modal-closing');
  };

  document.addEventListener('show.bs.modal', handleShow);
  document.addEventListener('hide.bs.modal', handleHide);
  document.addEventListener('hidden.bs.modal', handleHidden);
})();
