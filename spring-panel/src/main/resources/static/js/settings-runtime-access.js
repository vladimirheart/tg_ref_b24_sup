(function () {
  if (window.SettingsRuntimeAccess) {
    return;
  }

  function normalizeRuntimeName(runtimeName) {
    return typeof runtimeName === 'string' ? runtimeName.trim() : '';
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

  window.SettingsRuntimeAccess = Object.freeze({
    buildPageConfig,
    invokeRuntimeMethod,
    mountRuntime,
    resolvePageConfigRuntime,
    resolveRuntimeApi,
    resolveRuntimeMethod,
  });
}());
