(function () {
  if (window.PassportDetailNetworkRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const getPassport = typeof options.getPassport === 'function' ? options.getPassport : () => ({});
    const coreRuntime = options.coreRuntime && typeof options.coreRuntime === 'object' ? options.coreRuntime : null;
    if (!coreRuntime) {
      throw new Error('PassportDetailNetworkRuntime requires coreRuntime');
    }
    const { text, escapeHtml, first, renderProperties } = coreRuntime;

    function resolvePassport() {
      const value = getPassport();
      return value && typeof value === 'object' ? value : {};
    }

    function renderNetwork() {
        const passport = resolvePassport();
        renderProperties('passportInternalNetwork', [
            { label: 'Внутренняя сеть', value: passport.network },
            { label: 'Туннель', value: passport.network_tunnel },
            { label: 'Сервер iiko', value: passport.it_iiko_server },
            { label: 'Тип удалённого доступа', value: passport.it_connection_type },
            { label: 'ID подключения', value: passport.it_connection_id }
        ]);
        renderProperties('passportProviderNetwork', [
            { label: 'Провайдер', value: passport.network_provider },
            { label: 'Номер договора', value: passport.network_contract_number },
            { label: 'ID ресторана', value: passport.network_restaurant_id },
            { label: 'ЮЛ провайдера', value: passport.network_legal_entity },
            { label: 'Телефон ТП', value: passport.network_support_phone },
            { label: 'Скорость', value: passport.network_speed }
        ]);

        const params = text(passport.network_connection_params);
        const details = document.getElementById('passportNetworkTechnicalDetails');
        if (params) {
            details.classList.remove('d-none');
            document.getElementById('passportNetworkParams').textContent = params;
        }

        const files = Array.isArray(passport.network_files) ? passport.network_files : [];
        const fileSection = document.getElementById('passportNetworkFilesSection');
        const fileList = document.getElementById('passportNetworkFiles');
        if (files.length) {
            fileSection.classList.remove('d-none');
            fileList.innerHTML = files.map((file) => {
                const name = first(file && file.name, file && file.original_name, file && file.filename, 'Файл');
                const url = first(file && file.url, file && file.download_url, '');
                return url
                    ? `<a class="passport-file" href="${escapeHtml(url)}" target="_blank" rel="noopener">${escapeHtml(name)}</a>`
                    : `<span class="passport-file">${escapeHtml(name)}</span>`;
            }).join('');
        }
    }


    return Object.freeze({
      renderNetwork,
    });
  }

  function mount(options = {}) {
    return createRuntime(options);
  }

  window.PassportDetailNetworkRuntime = Object.freeze({
    mount,
  });
}());
