(function () {
  function refreshTokenValues() {
    const styles = getComputedStyle(document.documentElement);

    document
      .querySelectorAll('[data-ui-token]')
      .forEach((element) => {
        const token = element.dataset.uiToken;

        if (!token) {
          return;
        }

        element.textContent =
          styles.getPropertyValue(token).trim() || '—';
      });
  }

  function refreshControls() {
    const runtime = window.ThemeRuntime;

    if (!runtime) {
      return;
    }

    const theme = runtime.getTheme();
    const palette = runtime.getPalette();

    document
      .querySelectorAll('[data-ui-kit-theme]')
      .forEach((button) => {
        const active =
          button.dataset.uiKitTheme === theme;

        button.classList.toggle('is-active', active);
        button.setAttribute(
          'aria-pressed',
          active ? 'true' : 'false'
        );
      });

    document
      .querySelectorAll('[data-ui-kit-palette]')
      .forEach((button) => {
        const active =
          button.dataset.uiKitPalette === palette;

        button.classList.toggle('is-active', active);
        button.setAttribute(
          'aria-pressed',
          active ? 'true' : 'false'
        );
      });
  }

  function refresh() {
    requestAnimationFrame(() => {
      refreshControls();
      refreshTokenValues();
    });
  }

  document
    .querySelectorAll('[data-ui-kit-theme]')
    .forEach((button) => {
      button.addEventListener('click', () => {
        const theme = button.dataset.uiKitTheme;

        if (
          theme &&
          window.ThemeRuntime
        ) {
          window.ThemeRuntime.setTheme(theme);
          refresh();
        }
      });
    });

  document
    .querySelectorAll('[data-ui-kit-palette]')
    .forEach((button) => {
      button.addEventListener('click', () => {
        const palette =
          button.dataset.uiKitPalette;

        if (
          palette &&
          window.ThemeRuntime
        ) {
          window.ThemeRuntime.setPalette(palette);
          refresh();
        }
      });
    });

  document.addEventListener(
    'theme:change',
    refresh
  );

  document.addEventListener(
    'theme:palette-change',
    refresh
  );

  refresh();
})();