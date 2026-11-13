(function () {
    'use strict';

    const { apiRequest } = window.FlowApi;

    const tabLogin = document.getElementById('tabLogin');
    const tabRegister = document.getElementById('tabRegister');
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    const authAlert = document.getElementById('authAlert');
    const dbStatusNotice = document.getElementById('dbStatusNotice');

    function showAlert(message, type = 'danger') {
        authAlert.textContent = message;
        authAlert.className = `alert alert-${type}`;
        authAlert.classList.remove('hidden');
    }

    function clearAlert() {
        authAlert.classList.add('hidden');
        authAlert.textContent = '';
    }

    function switchTab(mode) {
        clearAlert();
        const isRegister = mode === 'register';
        tabLogin.classList.toggle('active', !isRegister);
        tabRegister.classList.toggle('active', isRegister);
        tabLogin.setAttribute('aria-selected', String(!isRegister));
        tabRegister.setAttribute('aria-selected', String(isRegister));
        loginForm.classList.toggle('hidden', isRegister);
        registerForm.classList.toggle('hidden', !isRegister);

        if (isRegister) {
            document.getElementById('regName').focus();
        } else {
            document.getElementById('loginEmail').focus();
        }
    }

    tabLogin.addEventListener('click', () => switchTab('login'));
    tabRegister.addEventListener('click', () => switchTab('register'));
    document.getElementById('goToRegisterBtn').addEventListener('click', () => switchTab('register'));
    document.getElementById('goToLoginBtn').addEventListener('click', () => switchTab('login'));

    document.querySelectorAll('.password-toggle').forEach((btn) => {
        btn.addEventListener('click', () => {
            const target = document.getElementById(btn.dataset.target);
            if (!target) {
                return;
            }
            const show = target.type === 'password';
            target.type = show ? 'text' : 'password';
            btn.textContent = show ? 'Hide' : 'Show';
        });
    });

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        clearAlert();
        const email = document.getElementById('loginEmail').value.trim();
        const password = document.getElementById('loginPassword').value;
        const submitBtn = document.getElementById('loginSubmitBtn');

        if (!email || !password) {
            showAlert('Please enter your email address and password.');
            return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = 'Signing in...';
        try {
            await apiRequest('api/auth/login', {
                method: 'POST',
                body: { email, password },
                skipAuthRedirect: true
            });
            window.location.href = 'dashboard.html';
        } catch (err) {
            showAlert(err.message || 'Unable to sign in.');
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Sign In';
        }
    });

    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        clearAlert();
        const fullName = document.getElementById('regName').value.trim();
        const email = document.getElementById('regEmail').value.trim();
        const password = document.getElementById('regPassword').value;
        const confirmPassword = document.getElementById('regConfirmPassword').value;
        const submitBtn = document.getElementById('registerSubmitBtn');

        if (fullName.length < 2) {
            showAlert('Full name must be at least 2 characters.');
            return;
        }
        if (!email) {
            showAlert('Please enter a valid email address.');
            return;
        }
        if (password.length < 6) {
            showAlert('Password must be at least 6 characters.');
            return;
        }
        if (password !== confirmPassword) {
            showAlert('Passwords do not match.');
            return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = 'Creating account...';
        try {
            await apiRequest('api/auth/register', {
                method: 'POST',
                body: { fullName, email, password, confirmPassword },
                skipAuthRedirect: true
            });
            window.location.href = 'dashboard.html';
        } catch (err) {
            showAlert(err.message || 'Unable to create account.');
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Create Account';
        }
    });

    async function initAuthPage() {
        if (window.location.hash === '#register') {
            switchTab('register');
        }
        const params = new URLSearchParams(window.location.search);
        if (params.get('loggedOut') === '1') {
            showAlert('You have been signed out safely.', 'success');
        }

        try {
            const me = await apiRequest('api/auth/me', { skipAuthRedirect: true });
            if (me && me.authenticated) {
                window.location.replace('dashboard.html');
                return;
            }
        } catch (_) {
            // Expected when user is not logged in
        }

        try {
            const health = await fetch('api/health', { credentials: 'same-origin' });
            const info = await health.json();
            if (!info.database) {
                dbStatusNotice.textContent = 'Database not connected yet. Import database.sql in MySQL and check src/main/resources/db.properties.';
                dbStatusNotice.classList.remove('hidden');
            }
        } catch (_) {
            // Ignore transient health check failure
        }
    }

    initAuthPage();
})();

