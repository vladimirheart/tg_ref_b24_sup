(function () {
  if (window.SettingsRuntimeAccess) {
    return;
  }

  function normalizeRuntimeName(runtimeName) {
    return typeof runtimeName === 'string' ? runtimeName.trim() : '';
  }

  const pageDataSectionCache = new Map();
  const pageDataSectionPending = new Map();

  function normalizePageSectionName(sectionName) {
    return typeof sectionName === 'string' ? sectionName.trim().toLowerCase() : '';
  }

  function resolveRuntimeApi(runtimeName) {
    const normalizedName = normalizeRuntimeName(runtimeName);
    if (!normalizedName) {
      return null;
    }
    const runtimeApi = globalThis[normalizedName];
    const apiType = typeof runtimeApi;
    if ((apiType !== 'object' && apiType !== 'function') || !runtimeApi) {
      return null;
    }
    return runtimeApi;
  }

  function resolveRuntimeMethod(runtimeName, methodName) {
    const runtimeApi = resolveRuntimeApi(runtimeName);
    const normalizedMethodName = typeof methodName === 'string' ? methodName.trim() : '';
    if (!runtimeApi || !normalizedMethodName) {
      return null;
    }
    const method = runtimeApi[normalizedMethodName];
    return typeof method === 'function' ? method : null;
  }

  function invokeRuntimeMethod(runtimeName, methodName, ...args) {
    const method = resolveRuntimeMethod(runtimeName, methodName);
    if (typeof method !== 'function') {
      return undefined;
    }
    return method(...args);
  }

  function mountRuntime(runtimeName, ...args) {
    const result = invokeRuntimeMethod(runtimeName, 'mount', ...args);
    return result == null ? null : result;
  }

  function resolvePageConfigRuntime() {
    return resolveRuntimeApi('SettingsPageConfigRuntime');
  }

  function buildPageConfig(rawConfig) {
    const runtimeApi = resolvePageConfigRuntime();
    if (!runtimeApi || typeof runtimeApi.build !== 'function') {
      return {};
    }
    return runtimeApi.build(rawConfig) || {};
  }

  function getCachedPageDataSection(sectionName) {
    const normalizedSectionName = normalizePageSectionName(sectionName);
    if (!normalizedSectionName || !pageDataSectionCache.has(normalizedSectionName)) {
      return null;
    }
    return pageDataSectionCache.get(normalizedSectionName);
  }

  function primePageDataSection(sectionName, data) {
    const normalizedSectionName = normalizePageSectionName(sectionName);
    if (!normalizedSectionName) {
      return null;
    }
    const normalizedData = data && typeof data === 'object' && !Array.isArray(data) ? data : {};
    pageDataSectionCache.set(normalizedSectionName, normalizedData);
    return normalizedData;
  }

  function fetchPageDataSection(sectionName, options = {}) {
    const normalizedSectionName = normalizePageSectionName(sectionName);
    if (!normalizedSectionName) {
      return Promise.resolve({});
    }

    const force = Boolean(options && options.force);
    if (!force && pageDataSectionCache.has(normalizedSectionName)) {
      return Promise.resolve(pageDataSectionCache.get(normalizedSectionName));
    }
    if (!force && pageDataSectionPending.has(normalizedSectionName)) {
      return pageDataSectionPending.get(normalizedSectionName);
    }

    const request = fetch(`/api/settings/page-data/${encodeURIComponent(normalizedSectionName)}`, {
      credentials: 'same-origin',
    })
      .then(async (response) => {
        const payload = await response.json().catch(() => ({}));
        if (!response.ok || payload.success === false) {
          throw new Error(payload.error || `HTTP ${response.status}`);
        }
        return primePageDataSection(normalizedSectionName, payload.data);
      })
      .finally(() => {
        pageDataSectionPending.delete(normalizedSectionName);
      });

    pageDataSectionPending.set(normalizedSectionName, request);
    return request;
  }

  window.SettingsRuntimeAccess = Object.freeze({
    buildPageConfig,
    fetchPageDataSection,
    getCachedPageDataSection,
    invokeRuntimeMethod,
    mountRuntime,
    primePageDataSection,
    resolvePageConfigRuntime,
    resolveRuntimeApi,
    resolveRuntimeMethod,
  });
}());
