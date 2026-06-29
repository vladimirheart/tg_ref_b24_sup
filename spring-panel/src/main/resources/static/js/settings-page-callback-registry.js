(function () {
  if (window.SettingsPageCallbackRegistry) {
    return;
  }

  const callbacks = new Map();

  function normalizeName(name) {
    return typeof name === 'string' ? name.trim() : '';
  }

  function register(name, callback) {
    const normalizedName = normalizeName(name);
    if (!normalizedName || typeof callback !== 'function') {
      return null;
    }
    callbacks.set(normalizedName, callback);
    return callback;
  }

  function registerMany(entries) {
    if (!entries || typeof entries !== 'object') {
      return;
    }
    Object.entries(entries).forEach(([name, callback]) => {
      register(name, callback);
    });
  }

  function resolve(name) {
    const normalizedName = normalizeName(name);
    if (!normalizedName) {
      return null;
    }
    return callbacks.get(normalizedName) || null;
  }

  function unregister(name) {
    const normalizedName = normalizeName(name);
    if (!normalizedName) {
      return false;
    }
    return callbacks.delete(normalizedName);
  }

  window.SettingsPageCallbackRegistry = Object.freeze({
    register,
    registerMany,
    resolve,
    unregister,
  });
}());
