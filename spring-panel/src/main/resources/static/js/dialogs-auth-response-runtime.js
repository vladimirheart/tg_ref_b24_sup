(function () {
  if (window.DialogsAuthResponseRuntime) return;

  let redirectIssued = false;

  function isLoginRedirect(response) {
    if (!response?.redirected || !response.url) return false;
    try {
      const target = new URL(response.url, window.location.href);
      return target.origin === window.location.origin && target.pathname === '/login';
    } catch (_error) {
      return false;
    }
  }

  function ensureApiSession(response) {
    if (response?.status !== 401 && !isLoginRedirect(response)) return response;

    if (!redirectIssued) {
      redirectIssued = true;
      window.location.assign('/login');
    }
    const error = new Error('Сессия истекла. Войдите повторно.');
    error.code = 'session_expired';
    throw error;
  }

  window.DialogsAuthResponseRuntime = { ensureApiSession };
})();
