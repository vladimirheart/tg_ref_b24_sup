(function () {
  function syncSubjects(form) {
    const type = form.querySelector('[data-access-type]');
    const subject = form.querySelector('[data-access-subject]');
    if (!type || !subject) return;
    Array.from(subject.options).forEach((option) => {
      option.hidden = option.dataset.subjectType !== type.value;
      option.disabled = option.hidden;
    });
    const current = subject.selectedOptions[0];
    if (!current || current.disabled) {
      const first = Array.from(subject.options).find((option) => !option.disabled);
      if (first) subject.value = first.value;
    }
  }

  document.querySelectorAll('[data-access-form]').forEach((form) => {
    form.querySelector('[data-access-type]')?.addEventListener('change', () => syncSubjects(form));
    syncSubjects(form);
  });
})();
