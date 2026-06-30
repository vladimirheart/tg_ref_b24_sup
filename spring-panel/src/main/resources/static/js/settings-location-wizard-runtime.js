(function () {
  if (window.SettingsLocationWizardRuntime) {
    return;
  }

  const TYPE_OPTIONS = ['Корпоративная сеть', 'Партнёры-франчайзи'];

  function uniqueSorted(values) {
    return Array.from(
      new Set(
        (values || [])
          .map((value) => (typeof value === 'string' ? value.trim() : ''))
          .filter((value) => value),
      ),
    ).sort((left, right) => left.localeCompare(right));
  }

  function createRuntime(options = {}) {
    const notify = typeof options.showPopup === 'function'
      ? options.showPopup
      : (message) => console.log(message);
    let locationWizardModalEl = null;
    let locationWizardInitialised = false;
    let locationWizardStep = 0;
    const locationWizardState = {
      business: '',
      type: '',
      country: '',
      partner_type: '',
      city: '',
      department: '',
    };

    let wizardSteps = [];
    let wizardSummary;
    let wizardBusinessSelect;
    let wizardTypeSelect;
    let wizardCountrySelect;
    let wizardPartnerTypeSelect;
    let wizardCitySelect;
    let wizardDepartmentSelect;
    let wizardPrevBtn;
    let wizardNextBtn;
    let wizardFinishBtn;

    function getParameterData() {
      return typeof options.getParameterData === 'function' ? options.getParameterData() : {};
    }

    function getLocationsState() {
      return typeof options.getLocationsState === 'function' ? options.getLocationsState() : {};
    }

    function getCityOptionsFallback() {
      return typeof options.getCityOptionsFallback === 'function' ? options.getCityOptionsFallback() : [];
    }

    function getBusinessOptions() {
      const parameterData = getParameterData();
      const locationsState = getLocationsState();
      const optionsList = uniqueSorted((parameterData.business || []).map((item) => item.value));
      if (optionsList.length) {
        return optionsList;
      }
      return uniqueSorted(Object.keys((locationsState && locationsState.tree) || {}));
    }

    function getCityOptions() {
      const parameterData = getParameterData();
      const locationsState = getLocationsState();
      const optionsList = uniqueSorted((parameterData.city || []).map((item) => item.value));
      if (optionsList.length) {
        return optionsList;
      }
      const collected = [];
      Object.values((locationsState && locationsState.tree) || {}).forEach((types) => {
        Object.values(types || {}).forEach((cities) => {
          Object.keys(cities || {}).forEach((city) => {
            collected.push(city);
          });
        });
      });
      const fallback = uniqueSorted(collected);
      if (fallback.length) {
        return fallback;
      }
      const cityOptions = getCityOptionsFallback();
      return Array.isArray(cityOptions) ? cityOptions : [];
    }

    function getCountryOptions() {
      const parameterData = getParameterData();
      return uniqueSorted((parameterData.country || []).map((item) => item.value));
    }

    function getPartnerTypeOptions() {
      const parameterData = getParameterData();
      return uniqueSorted((parameterData.partner_type || []).map((item) => item.value));
    }

    function getDepartmentOptions() {
      const parameterData = getParameterData();
      const locationsState = getLocationsState();
      const optionsList = uniqueSorted((parameterData.department || []).map((item) => item.value));
      if (optionsList.length) {
        return optionsList;
      }
      const collected = [];
      Object.values((locationsState && locationsState.tree) || {}).forEach((types) => {
        Object.values(types || {}).forEach((cities) => {
          Object.values(cities || {}).forEach((departments) => {
            if (Array.isArray(departments)) {
              departments.forEach((department) => collected.push(department));
            }
          });
        });
      });
      return uniqueSorted(collected);
    }

    function clearInvalid(selectEl) {
      if (selectEl instanceof HTMLElement) {
        selectEl.classList.remove('is-invalid');
      }
    }

    function markInvalid(selectEl) {
      if (selectEl instanceof HTMLElement) {
        selectEl.classList.add('is-invalid');
        selectEl.focus();
      }
    }

    function fillSelect(selectEl, values, placeholder, selectedValue) {
      if (!(selectEl instanceof HTMLSelectElement)) {
        return;
      }
      selectEl.innerHTML = '';
      const placeholderOption = document.createElement('option');
      placeholderOption.value = '';
      placeholderOption.textContent = placeholder;
      placeholderOption.disabled = true;
      if (!selectedValue) {
        placeholderOption.selected = true;
      }
      selectEl.appendChild(placeholderOption);
      values.forEach((value) => {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = value;
        if (value === selectedValue) {
          option.selected = true;
        }
        selectEl.appendChild(option);
      });
      if (selectedValue && !values.includes(selectedValue)) {
        placeholderOption.selected = true;
        selectEl.value = '';
      }
      clearInvalid(selectEl);
    }

    function populateWizardOptions() {
      fillSelect(wizardBusinessSelect, getBusinessOptions(), 'Выберите бизнес', locationWizardState.business);
      fillSelect(wizardTypeSelect, TYPE_OPTIONS.slice(), 'Выберите тип структуры', locationWizardState.type);
      fillSelect(wizardCountrySelect, getCountryOptions(), 'Выберите страну', locationWizardState.country);
      fillSelect(wizardPartnerTypeSelect, getPartnerTypeOptions(), 'Выберите тип партнёра', locationWizardState.partner_type);
      fillSelect(wizardCitySelect, getCityOptions(), 'Выберите город', locationWizardState.city);
      fillSelect(wizardDepartmentSelect, getDepartmentOptions(), 'Выберите локацию', locationWizardState.department);
    }

    function resetLocationWizardState() {
      locationWizardState.business = '';
      locationWizardState.type = '';
      locationWizardState.country = '';
      locationWizardState.partner_type = '';
      locationWizardState.city = '';
      locationWizardState.department = '';
      locationWizardStep = 0;
    }

    function updateWizardSummary() {
      if (!(wizardSummary instanceof HTMLElement)) {
        return;
      }
      const entries = [
        ['Бизнес', locationWizardState.business || '—'],
        ['Тип структуры', locationWizardState.type || '—'],
        ['Страна', locationWizardState.country || '—'],
        ['Тип партнёра', locationWizardState.partner_type || '—'],
        ['Город', locationWizardState.city || '—'],
        ['Локация', locationWizardState.department || '—'],
      ];
      wizardSummary.innerHTML = `
        <ul class="mb-0 small">
          ${entries.map(([label, value]) => `<li><strong>${label}:</strong> ${value}</li>`).join('')}
        </ul>
      `;
    }

    function showLocationWizardStep(step) {
      locationWizardStep = step;
      if (wizardSteps.length) {
        wizardSteps.forEach((stepEl, index) => {
          stepEl.classList.toggle('d-none', index !== step);
        });
      }
      if (wizardPrevBtn instanceof HTMLButtonElement) {
        wizardPrevBtn.disabled = step === 0;
        wizardPrevBtn.classList.toggle('disabled', step === 0);
      }
      if (wizardNextBtn instanceof HTMLButtonElement) {
        wizardNextBtn.classList.toggle('d-none', step === wizardSteps.length - 1);
      }
      if (wizardFinishBtn instanceof HTMLButtonElement) {
        wizardFinishBtn.classList.toggle('d-none', step !== wizardSteps.length - 1);
      }
      updateWizardSummary();
    }

    function handleLocationWizardPrev() {
      if (locationWizardStep > 0) {
        showLocationWizardStep(locationWizardStep - 1);
      }
    }

    function handleLocationWizardNext() {
      if (locationWizardStep === 0) {
        if (!locationWizardState.business) {
          markInvalid(wizardBusinessSelect);
          return;
        }
      } else if (locationWizardStep === 1) {
        if (!locationWizardState.type) {
          markInvalid(wizardTypeSelect);
          return;
        }
      } else if (locationWizardStep === 2) {
        if (!locationWizardState.country) {
          markInvalid(wizardCountrySelect);
          return;
        }
      } else if (locationWizardStep === 3) {
        if (!locationWizardState.partner_type) {
          markInvalid(wizardPartnerTypeSelect);
          return;
        }
      } else if (locationWizardStep === 4) {
        if (!locationWizardState.city) {
          markInvalid(wizardCitySelect);
          return;
        }
      }
      showLocationWizardStep(Math.min(locationWizardStep + 1, wizardSteps.length - 1));
    }

    function addLocationEntryFromWizard(entry) {
      const business = String(entry.business || '').trim();
      const type = String(entry.type || '').trim();
      const country = String(entry.country || '').trim();
      const partnerType = String(entry.partner_type || '').trim();
      const city = String(entry.city || '').trim();
      const department = String(entry.department || '').trim();
      if (!business || !type || !country || !partnerType || !city || !department) {
        return false;
      }

      const locationsState = getLocationsState();
      if (!locationsState.tree[business]) {
        locationsState.tree[business] = {};
        options.setStatus('business', [business], options.defaultLocationStatus);
      }
      if (!locationsState.tree[business][type]) {
        locationsState.tree[business][type] = {};
        options.setStatus('type', [business, type], options.defaultLocationStatus);
      }
      if (!Array.isArray(locationsState.tree[business][type][city])) {
        locationsState.tree[business][type][city] = [];
        options.setStatus('city', [business, type, city], options.defaultLocationStatus);
      }

      const targetList = locationsState.tree[business][type][city];
      if (targetList.includes(department)) {
        return false;
      }

      targetList.push(department);
      locationsState.tree[business][type][city] = options.sortArrayAlphabetically(targetList);
      options.writeNodeMeta('city_meta', options.makeCityMetaKey(business, type, city), {
        country,
        partner_type: partnerType,
      });
      options.writeNodeMeta('location_meta', options.makeLocationMetaKey(business, type, city, department), {
        country,
        partner_type: partnerType,
      });
      options.setStatus('location', [business, type, city, department], options.defaultLocationStatus);
      options.buildLocationsTree();

      return true;
    }

    function handleLocationWizardFinish() {
      if (!locationWizardState.country) {
        markInvalid(wizardCountrySelect);
        return;
      }
      if (!locationWizardState.partner_type) {
        markInvalid(wizardPartnerTypeSelect);
        return;
      }
      if (!locationWizardState.department) {
        markInvalid(wizardDepartmentSelect);
        return;
      }
      if (!addLocationEntryFromWizard(locationWizardState)) {
        notify('Такая запись уже существует в выбранной структуре.');
        return;
      }
      if (locationWizardModalEl && typeof options.requestSettingsModalClose === 'function') {
        options.requestSettingsModalClose(wizardFinishBtn);
      }
    }

    function initLocationWizard() {
      if (locationWizardInitialised) {
        return;
      }
      const wizardModal = document.getElementById('locationWizardModal');
      if (!(wizardModal instanceof HTMLElement)) {
        return;
      }
      locationWizardModalEl = wizardModal;
      wizardBusinessSelect = document.getElementById('wizardBusinessSelect');
      wizardTypeSelect = document.getElementById('wizardTypeSelect');
      wizardCountrySelect = document.getElementById('wizardCountrySelect');
      wizardPartnerTypeSelect = document.getElementById('wizardPartnerTypeSelect');
      wizardCitySelect = document.getElementById('wizardCitySelect');
      wizardDepartmentSelect = document.getElementById('wizardDepartmentSelect');
      wizardPrevBtn = document.getElementById('wizardPrevStep');
      wizardNextBtn = document.getElementById('wizardNextStep');
      wizardFinishBtn = document.getElementById('wizardFinish');
      wizardSummary = document.getElementById('wizardSummary');
      wizardSteps = Array.from(document.querySelectorAll('[data-location-wizard-step]'));

      wizardPrevBtn?.addEventListener('click', handleLocationWizardPrev);
      wizardNextBtn?.addEventListener('click', handleLocationWizardNext);
      wizardFinishBtn?.addEventListener('click', handleLocationWizardFinish);

      if (wizardBusinessSelect instanceof HTMLSelectElement) {
        wizardBusinessSelect.addEventListener('change', (event) => {
          locationWizardState.business = String(event.target.value || '').trim();
          locationWizardState.type = '';
          locationWizardState.country = '';
          locationWizardState.partner_type = '';
          locationWizardState.city = '';
          locationWizardState.department = '';
          if (wizardTypeSelect instanceof HTMLSelectElement) wizardTypeSelect.value = '';
          if (wizardCountrySelect instanceof HTMLSelectElement) wizardCountrySelect.value = '';
          if (wizardPartnerTypeSelect instanceof HTMLSelectElement) wizardPartnerTypeSelect.value = '';
          if (wizardCitySelect instanceof HTMLSelectElement) wizardCitySelect.value = '';
          if (wizardDepartmentSelect instanceof HTMLSelectElement) wizardDepartmentSelect.value = '';
          clearInvalid(wizardBusinessSelect);
          clearInvalid(wizardTypeSelect);
          clearInvalid(wizardCountrySelect);
          clearInvalid(wizardPartnerTypeSelect);
          clearInvalid(wizardCitySelect);
          clearInvalid(wizardDepartmentSelect);
          updateWizardSummary();
        });
      }

      if (wizardTypeSelect instanceof HTMLSelectElement) {
        wizardTypeSelect.addEventListener('change', (event) => {
          locationWizardState.type = String(event.target.value || '').trim();
          locationWizardState.country = '';
          locationWizardState.partner_type = '';
          locationWizardState.city = '';
          locationWizardState.department = '';
          if (wizardCountrySelect instanceof HTMLSelectElement) wizardCountrySelect.value = '';
          if (wizardPartnerTypeSelect instanceof HTMLSelectElement) wizardPartnerTypeSelect.value = '';
          if (wizardCitySelect instanceof HTMLSelectElement) wizardCitySelect.value = '';
          if (wizardDepartmentSelect instanceof HTMLSelectElement) wizardDepartmentSelect.value = '';
          clearInvalid(wizardTypeSelect);
          clearInvalid(wizardCountrySelect);
          clearInvalid(wizardPartnerTypeSelect);
          clearInvalid(wizardCitySelect);
          clearInvalid(wizardDepartmentSelect);
          updateWizardSummary();
        });
      }

      if (wizardCountrySelect instanceof HTMLSelectElement) {
        wizardCountrySelect.addEventListener('change', (event) => {
          locationWizardState.country = String(event.target.value || '').trim();
          clearInvalid(wizardCountrySelect);
          updateWizardSummary();
        });
      }

      if (wizardPartnerTypeSelect instanceof HTMLSelectElement) {
        wizardPartnerTypeSelect.addEventListener('change', (event) => {
          locationWizardState.partner_type = String(event.target.value || '').trim();
          clearInvalid(wizardPartnerTypeSelect);
          updateWizardSummary();
        });
      }

      if (wizardCitySelect instanceof HTMLSelectElement) {
        wizardCitySelect.addEventListener('change', (event) => {
          locationWizardState.city = String(event.target.value || '').trim();
          locationWizardState.department = '';
          if (wizardDepartmentSelect instanceof HTMLSelectElement) wizardDepartmentSelect.value = '';
          clearInvalid(wizardCitySelect);
          clearInvalid(wizardDepartmentSelect);
          updateWizardSummary();
        });
      }

      if (wizardDepartmentSelect instanceof HTMLSelectElement) {
        wizardDepartmentSelect.addEventListener('change', (event) => {
          locationWizardState.department = String(event.target.value || '').trim();
          clearInvalid(wizardDepartmentSelect);
          updateWizardSummary();
        });
      }

      populateWizardOptions();
      showLocationWizardStep(0);
      locationWizardInitialised = true;
    }

    function resetLocationWizardSettingsModal() {
      if (!locationWizardInitialised) {
        return;
      }
      resetLocationWizardState();
      populateWizardOptions();
      showLocationWizardStep(0);
    }

    async function prepareLocationWizardSettingsTrigger() {
      initLocationWizard();
      if (!locationWizardModalEl) {
        return false;
      }
      if (!options.isParametersLoaded()) {
        await options.loadParameters();
        populateWizardOptions();
      }
      resetLocationWizardState();
      populateWizardOptions();
      showLocationWizardStep(0);
      updateWizardSummary();
      return true;
    }

    return {
      initLocationWizard,
      resetLocationWizardSettingsModal,
      prepareLocationWizardSettingsTrigger,
    };
  }

  function mount(options = {}) {
    if (window.__settingsLocationWizardRuntime) {
      return window.__settingsLocationWizardRuntime;
    }

    const runtime = createRuntime(options);
    window.SettingsPageCallbackRegistry?.registerMany({
      initLocationWizard: runtime.initLocationWizard,
      resetLocationWizardSettingsModal: runtime.resetLocationWizardSettingsModal,
      prepareLocationWizardSettingsTrigger: runtime.prepareLocationWizardSettingsTrigger,
    });
    window.__settingsLocationWizardRuntime = runtime;
    return runtime;
  }

  window.SettingsLocationWizardRuntime = Object.freeze({
    mount,
    initLocationWizard(...args) {
      return window.__settingsLocationWizardRuntime?.initLocationWizard?.(...args);
    },
  });
}());
