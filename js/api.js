(function (global) {
    'use strict';

    const CURRENCY_CONFIG = {
        USD: { locale: 'en-US', currency: 'USD' },
        INR: { locale: 'en-IN', currency: 'INR' },
        EUR: { locale: 'de-DE', currency: 'EUR' },
        GBP: { locale: 'en-GB', currency: 'GBP' }
    };

    const STORAGE_KEY = 'flowledger_currency';

    function getCurrency() {
        const saved = localStorage.getItem(STORAGE_KEY);
        return CURRENCY_CONFIG[saved] ? saved : 'USD';
    }

    function setCurrency(code) {
        if (CURRENCY_CONFIG[code]) {
            localStorage.setItem(STORAGE_KEY, code);
        }
    }

    function formatCurrency(amount) {
        const num = Number(amount || 0);
        const code = getCurrency();
        const cfg = CURRENCY_CONFIG[code] || CURRENCY_CONFIG.USD;
        return new Intl.NumberFormat(cfg.locale, {
            style: 'currency',
            currency: cfg.currency,
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(num);
    }

    function formatCompactCurrency(amount) {
        const num = Number(amount || 0);
        const code = getCurrency();
        const cfg = CURRENCY_CONFIG[code] || CURRENCY_CONFIG.USD;
        return new Intl.NumberFormat(cfg.locale, {
            style: 'currency',
            currency: cfg.currency,
            notation: Math.abs(num) >= 10000 ? 'compact' : 'standard',
            maximumFractionDigits: 1
        }).format(num);
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function todayIso() {
        const d = new Date();
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
    }

    function formatDate(isoDate) {
        if (!isoDate) {
            return '';
        }
        const parts = String(isoDate).split('-');
        if (parts.length !== 3) {
            return isoDate;
        }
        const dt = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
        return dt.toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    }

    async function apiRequest(url, options = {}) {
        const { method = 'GET', body = null, skipAuthRedirect = false } = options;
        const fetchOptions = {
            method,
            credentials: 'same-origin',
            headers: {}
        };
        if (body !== null && body !== undefined) {
            fetchOptions.headers['Content-Type'] = 'application/json';
            fetchOptions.body = JSON.stringify(body);
        }

        const resp = await fetch(url, fetchOptions);
        let data = {};
        try {
            const text = await resp.text();
            data = text ? JSON.parse(text) : {};
        } catch (err) {
            data = { success: false, error: 'Unexpected response from server.' };
        }

        if (resp.status === 401 && !skipAuthRedirect) {
            window.location.href = 'index.html';
            throw new Error(data.error || 'Authentication required');
        }

        if (!resp.ok) {
            const error = new Error(data.error || `Request failed (${resp.status})`);
            error.status = resp.status;
            error.data = data;
            throw error;
        }

        return data;
    }

    global.FlowApi = {
        apiRequest,
        escapeHtml,
        formatCurrency,
        formatCompactCurrency,
        formatDate,
        getCurrency,
        setCurrency,
        todayIso
    };
})(window);

