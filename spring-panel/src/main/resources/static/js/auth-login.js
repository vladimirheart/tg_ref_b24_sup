(function () {
  const canvas = document.getElementById('authParticles');
  if (!canvas) return;

  const passwordInput = document.querySelector('[data-auth-password]');
  const passwordToggle = document.querySelector('[data-auth-password-toggle]');
  const forgotPasswordForm = document.querySelector('[data-forgot-password-form]');
  const forgotPasswordError = document.querySelector('[data-forgot-password-error]');
  const forgotPasswordSuccess = document.querySelector('[data-forgot-password-success]');
  const forgotPasswordSubmit = document.querySelector('[data-forgot-password-submit]');

  if (passwordInput && passwordToggle) {
    passwordToggle.addEventListener('click', function () {
      const isPassword = passwordInput.getAttribute('type') === 'password';
      passwordInput.setAttribute('type', isPassword ? 'text' : 'password');
      passwordToggle.textContent = isPassword ? 'Скрыть' : 'Показать';
    });
  }

  function getCsrfToken() {
    var tokenInput = document.querySelector('input[name=\"_csrf\"]');
    return tokenInput ? tokenInput.value : '';
  }

  function setForgotMessage(type, message) {
    if (forgotPasswordError) {
      forgotPasswordError.classList.add('d-none');
      forgotPasswordError.textContent = '';
    }
    if (forgotPasswordSuccess) {
      forgotPasswordSuccess.classList.add('d-none');
      forgotPasswordSuccess.textContent = '';
    }
    if (!message) return;
    if (type === 'error' && forgotPasswordError) {
      forgotPasswordError.textContent = message;
      forgotPasswordError.classList.remove('d-none');
    }
    if (type === 'success' && forgotPasswordSuccess) {
      forgotPasswordSuccess.textContent = message;
      forgotPasswordSuccess.classList.remove('d-none');
    }
  }

  if (forgotPasswordForm) {
    forgotPasswordForm.addEventListener('submit', function (event) {
      event.preventDefault();
      setForgotMessage('', '');
      var formData = new FormData(forgotPasswordForm);
      var username = String(formData.get('username') || '').trim();
      var comment = String(formData.get('comment') || '').trim();
      if (!username) {
        setForgotMessage('error', 'Введите логин.');
        return;
      }
      if (forgotPasswordSubmit) {
        forgotPasswordSubmit.disabled = true;
      }
      fetch('/api/password-reset-requests/public', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-TOKEN': getCsrfToken()
        },
        body: JSON.stringify({
          username: username,
          comment: comment
        })
      })
        .then(function (response) { return response.json(); })
        .then(function (data) {
          if (!data || data.success === false) {
            throw new Error((data && data.error) || 'Не удалось отправить заявку');
          }
          setForgotMessage('success', data.message || 'Заявка отправлена. Ожидайте ответа администратора.');
          forgotPasswordForm.reset();
        })
        .catch(function (error) {
          setForgotMessage('error', error.message || 'Не удалось отправить заявку');
        })
        .finally(function () {
          if (forgotPasswordSubmit) {
            forgotPasswordSubmit.disabled = false;
          }
        });
    });
  }

  const context = canvas.getContext('2d');
  if (!context) return;

  function resize() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
  }

  resize();
  window.addEventListener('resize', resize);

  const particleCount = Math.min(90, Math.floor((window.innerWidth * window.innerHeight) / 18000));
  const particles = Array.from({ length: particleCount }, function () {
    return {
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      s: Math.random() * 2 + 1,
      dx: (Math.random() - 0.5) * 0.35,
      dy: (Math.random() - 0.5) * 0.35,
    };
  });

  function frame() {
    context.clearRect(0, 0, canvas.width, canvas.height);
    for (let i = 0; i < particles.length; i += 1) {
      const p = particles[i];
      p.x += p.dx;
      p.y += p.dy;
      if (p.x < 0) p.x = canvas.width;
      if (p.x > canvas.width) p.x = 0;
      if (p.y < 0) p.y = canvas.height;
      if (p.y > canvas.height) p.y = 0;

      context.beginPath();
      context.arc(p.x, p.y, p.s, 0, Math.PI * 2);
      context.fillStyle = 'rgba(34, 197, 94, 0.16)';
      context.fill();
    }
    window.requestAnimationFrame(frame);
  }

  frame();
})();
