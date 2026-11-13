(function () {
    'use strict';

    const {
        apiRequest,
        escapeHtml,
        formatCurrency,
        formatDate,
        getCurrency,
        setCurrency,
        todayIso
    } = window.FlowApi;
    const { renderDonutChart, renderMonthlyBarChart } = window.FlowCharts;

    const state = {
        user: null,
        categories: [],
        transactions: [],
        dashboardData: null,
        monthlyReportData: null,
        activeView: 'overview',
        chartPeriod: 'all',
        selectedYear: new Date().getFullYear(),
        page: 1,
        size: 15,
        totalPages: 1,
        deleteTargetId: null,
        editingTxType: 'EXPENSE'
    };

    // ==================== DOM Elements ====================
    const sidebar = document.getElementById('sidebar');
    const sidebarBackdrop = document.getElementById('sidebarBackdrop');
    const currencySelect = document.getElementById('currencySelect');
    const pageTitle = document.getElementById('pageTitle');
    const pageSubtitle = document.getElementById('pageSubtitle');

    const filterSearch = document.getElementById('filterSearch');
    const filterType = document.getElementById('filterType');
    const filterCategory = document.getElementById('filterCategory');
    const filterFromDate = document.getElementById('filterFromDate');
    const filterToDate = document.getElementById('filterToDate');

    const txModal = document.getElementById('txModal');
    const txForm = document.getElementById('txForm');
    const txFormError = document.getElementById('txFormError');
    const deleteModal = document.getElementById('deleteModal');
    const catModal = document.getElementById('catModal');
    const catForm = document.getElementById('catForm');
    const catFormError = document.getElementById('catFormError');

    // ==================== Toast & Modals ====================
    function showToast(message, isError = false) {
        const container = document.getElementById('toastContainer');
        const toast = document.createElement('div');
        toast.className = `toast${isError ? ' toast-error' : ''}`;
        toast.textContent = message;
        container.appendChild(toast);
        setTimeout(() => {
            toast.remove();
        }, 3200);
    }

    function openModal(el) {
        el.classList.remove('hidden');
    }

    function closeModal(el) {
        el.classList.add('hidden');
    }

    document.querySelectorAll('[data-close]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const modal = document.getElementById(btn.dataset.close);
            if (modal) {
                closeModal(modal);
            }
        });
    });

    [txModal, deleteModal, catModal].forEach((backdrop) => {
        backdrop.addEventListener('click', (e) => {
            if (e.target === backdrop) {
                closeModal(backdrop);
            }
        });
    });

    // ==================== Navigation & Sidebar ====================
    function closeMobileSidebar() {
        sidebar.classList.remove('open');
        sidebarBackdrop.classList.add('hidden');
    }

    document.getElementById('openSidebarBtn').addEventListener('click', () => {
        sidebar.classList.add('open');
        sidebarBackdrop.classList.remove('hidden');
    });
    document.getElementById('closeSidebarBtn').addEventListener('click', closeMobileSidebar);
    sidebarBackdrop.addEventListener('click', closeMobileSidebar);

    function switchView(view) {
        state.activeView = view;
        document.querySelectorAll('.nav-item').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.view === view);
        });

        const overviewEl = document.getElementById('view-overview');
        const monthlyEl = document.getElementById('view-monthly');
        const categoriesEl = document.getElementById('view-categories');

        overviewEl.classList.add('hidden');
        overviewEl.classList.remove('transactions-focus');
        monthlyEl.classList.add('hidden');
        categoriesEl.classList.add('hidden');

        if (view === 'overview') {
            overviewEl.classList.remove('hidden');
            pageTitle.textContent = 'Financial Overview';
            pageSubtitle.textContent = 'Your income, expenses, and cashflow summary';
            requestAnimationFrame(renderOverviewCharts);
        } else if (view === 'transactions') {
            overviewEl.classList.remove('hidden');
            overviewEl.classList.add('transactions-focus');
            pageTitle.textContent = 'Transaction History';
            pageSubtitle.textContent = 'Search, filter, add, edit, and delete transactions';
        } else if (view === 'monthly') {
            monthlyEl.classList.remove('hidden');
            pageTitle.textContent = 'Monthly Summary';
            pageSubtitle.textContent = 'Monthly income, expenses, and savings performance';
            loadMonthlyReport().catch((err) => showToast(err.message || 'Failed to load monthly report.', true));
        } else if (view === 'categories') {
            categoriesEl.classList.remove('hidden');
            pageTitle.textContent = 'Categories';
            pageSubtitle.textContent = 'Manage built-in and custom income/expense categories';
            renderCategoriesManager();
        }

        closeMobileSidebar();
    }

    document.querySelectorAll('.nav-item').forEach((btn) => {
        btn.addEventListener('click', () => switchView(btn.dataset.view));
    });

    // ==================== Currency & Logout ====================
    currencySelect.value = getCurrency();
    currencySelect.addEventListener('change', () => {
        setCurrency(currencySelect.value);
        renderDashboardKpis();
        renderOverviewCharts();
        renderTransactionRows();
        if (state.monthlyReportData) {
            renderMonthlyReport();
        }
    });

    async function handleLogout() {
        try {
            await apiRequest('api/auth/logout', { method: 'POST', skipAuthRedirect: true });
        } catch (_) {
            // Even on transient error, proceed to login page
        }
        window.location.href = 'index.html?loggedOut=1';
    }

    document.getElementById('sidebarLogoutBtn').addEventListener('click', handleLogout);
    document.getElementById('topbarLogoutBtn').addEventListener('click', handleLogout);

    // ==================== Categories Data & UI ====================
    async function loadCategories() {
        const res = await apiRequest('api/categories');
        state.categories = res.categories || [];
        populateFilterCategorySelect();
        populateTxCategorySelect(state.editingTxType);
        if (state.activeView === 'categories') {
            renderCategoriesManager();
        }
    }

    function populateFilterCategorySelect() {
        const current = filterCategory.value;
        const selectedType = filterType.value;
        const list = selectedType
            ? state.categories.filter((c) => c.type === selectedType)
            : state.categories;

        filterCategory.innerHTML = '<option value="">All Categories</option>' + list
            .map((c) => `<option value="${c.id}">${escapeHtml(c.name)} (${c.type === 'INCOME' ? 'Income' : 'Expense'})</option>`)
            .join('');

        if (list.some((c) => String(c.id) === current)) {
            filterCategory.value = current;
        }
    }

    function populateTxCategorySelect(type, selectedId = null) {
        const select = document.getElementById('txCategory');
        const matching = state.categories.filter((c) => c.type === type);
        select.innerHTML = matching
            .map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`)
            .join('');
        if (selectedId && matching.some((c) => c.id === Number(selectedId))) {
            select.value = String(selectedId);
        }
    }

    function renderCategoriesManager() {
        const expEl = document.getElementById('expenseCategoriesList');
        const incEl = document.getElementById('incomeCategoriesList');

        const renderList = (type) => {
            const list = state.categories.filter((c) => c.type === type);
            if (list.length === 0) {
                return '<p class="muted-label">No categories found.</p>';
            }
            return list.map((c) => `
                <div class="category-card-row">
                    <div class="cat-badge">
                        <span class="cat-dot" style="background:${escapeHtml(c.color)}"></span>
                        <span>${escapeHtml(c.name)}</span>
                    </div>
                    <div class="row-actions">
                        ${c.systemDefault
                ? '<span class="category-tag-pill">Built-in</span>'
                : `<button type="button" class="action-link" data-edit-cat="${c.id}">Edit</button>
                   <button type="button" class="action-link action-link-danger" data-del-cat="${c.id}">Delete</button>`}
                    </div>
                </div>
            `).join('');
        };

        expEl.innerHTML = renderList('EXPENSE');
        incEl.innerHTML = renderList('INCOME');

        document.querySelectorAll('[data-edit-cat]').forEach((btn) => {
            btn.addEventListener('click', () => {
                const cat = state.categories.find((c) => c.id === Number(btn.dataset.editCat));
                if (cat) {
                    openCategoryModal(cat);
                }
            });
        });

        document.querySelectorAll('[data-del-cat]').forEach((btn) => {
            btn.addEventListener('click', async () => {
                const id = Number(btn.dataset.delCat);
                try {
                    await apiRequest(`api/categories/${id}`, { method: 'DELETE' });
                    showToast('Category deleted.');
                    await loadCategories();
                } catch (err) {
                    showToast(err.message || 'Unable to delete category.', true);
                }
            });
        });
    }

    function openCategoryModal(category = null) {
        catFormError.classList.add('hidden');
        document.getElementById('catId').value = category ? category.id : '';
        document.getElementById('catName').value = category ? category.name : '';
        document.getElementById('catType').value = category ? category.type : 'EXPENSE';
        document.getElementById('catTypeGroup').classList.toggle('hidden', Boolean(category));
        document.getElementById('catColor').value = category ? category.color : '#6366F1';
        document.getElementById('catModalTitle').textContent = category ? 'Edit Category' : 'New Category';
        openModal(catModal);
        document.getElementById('catName').focus();
    }

    document.getElementById('addCategoryBtn').addEventListener('click', () => openCategoryModal(null));

    catForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        catFormError.classList.add('hidden');
        const id = document.getElementById('catId').value;
        const name = document.getElementById('catName').value.trim();
        const type = document.getElementById('catType').value;
        const color = document.getElementById('catColor').value;

        if (name.length < 2) {
            catFormError.textContent = 'Category name must be at least 2 characters.';
            catFormError.classList.remove('hidden');
            return;
        }

        const btn = document.getElementById('catSaveBtn');
        btn.disabled = true;
        try {
            if (id) {
                await apiRequest(`api/categories/${id}`, {
                    method: 'PUT',
                    body: { name, color }
                });
                showToast('Category updated.');
            } else {
                await apiRequest('api/categories', {
                    method: 'POST',
                    body: { name, type, color }
                });
                showToast('Category added.');
            }
            closeModal(catModal);
            await loadCategories();
            await refreshAllData();
        } catch (err) {
            catFormError.textContent = err.message || 'Failed to save category.';
            catFormError.classList.remove('hidden');
        } finally {
            btn.disabled = false;
        }
    });

    // ==================== Dashboard KPIs & Charts ====================
    async function loadDashboard() {
        const url = `api/dashboard?year=${state.selectedYear}&period=${state.chartPeriod}`;
        const data = await apiRequest(url);
        state.dashboardData = data;
        populateYearSelects(data.availableYears || [state.selectedYear]);
        renderDashboardKpis();
        renderOverviewCharts();
    }

    function populateYearSelects(years) {
        const unique = Array.from(new Set(years)).sort((a, b) => b - a);
        const optionsHtml = unique.map((y) => `<option value="${y}">${y}</option>`).join('');
        const overviewSelect = document.getElementById('overviewYearSelect');
        const monthlySelect = document.getElementById('monthlyYearSelect');
        overviewSelect.innerHTML = optionsHtml;
        monthlySelect.innerHTML = optionsHtml;
        overviewSelect.value = String(state.selectedYear);
        monthlySelect.value = String(state.selectedYear);
    }

    function renderDashboardKpis() {
        if (!state.dashboardData) {
            return;
        }
        const s = state.dashboardData.summary;
        const totalIncome = Number(s.totalIncome || 0);
        const totalExpense = Number(s.totalExpense || 0);
        const balance = Number(s.balance || 0);
        const monthNet = Number(s.monthBalance || 0);

        const balanceEl = document.getElementById('kpiBalance');
        balanceEl.textContent = formatCurrency(balance);
        balanceEl.className = `kpi-value ${balance < 0 ? 'text-expense' : ''}`;

        const savingsRate = totalIncome > 0 ? ((totalIncome - totalExpense) / totalIncome) * 100 : 0;
        document.getElementById('savingsRateBadge').textContent = `${savingsRate.toFixed(1)}% saved`;
        document.getElementById('kpiBalanceFoot').textContent = `${s.transactionCount} total transactions`;

        document.getElementById('kpiIncome').textContent = formatCurrency(totalIncome);
        document.getElementById('kpiIncomeFoot').textContent = `${s.incomeCount} income entries`;

        document.getElementById('kpiExpense').textContent = formatCurrency(totalExpense);
        document.getElementById('kpiExpenseFoot').textContent = `${s.expenseCount} expense entries`;

        const monthNetEl = document.getElementById('kpiMonthNet');
        monthNetEl.textContent = formatCurrency(monthNet);
        monthNetEl.className = `kpi-value ${monthNet >= 0 ? 'text-income' : 'text-expense'}`;
        document.getElementById('kpiMonthFoot').textContent =
            `In ${formatCurrency(s.monthIncome)} · Out ${formatCurrency(s.monthExpense)}`;

        const nowLabel = new Date().toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
        document.getElementById('currentMonthLabel').textContent = nowLabel;
    }

    function renderOverviewCharts() {
        if (!state.dashboardData) {
            return;
        }
        renderDonutChart(
            document.getElementById('expenseDonutCanvas'),
            document.getElementById('donutLegend'),
            document.getElementById('donutEmptyState'),
            state.dashboardData.expenseBreakdown || []
        );
        renderMonthlyBarChart(
            document.getElementById('monthlyBarCanvas'),
            document.getElementById('barEmptyState'),
            state.dashboardData.monthlySummaries || []
        );
    }

    document.querySelectorAll('[data-chart-period]').forEach((btn) => {
        btn.addEventListener('click', () => {
            state.chartPeriod = btn.dataset.chartPeriod;
            document.querySelectorAll('[data-chart-period]').forEach((b) => {
                b.classList.toggle('active', b === btn);
            });
            loadDashboard().catch((err) => showToast(err.message || 'Failed to load dashboard.', true));
        });
    });

    document.getElementById('overviewYearSelect').addEventListener('change', (e) => {
        state.selectedYear = Number(e.target.value);
        loadDashboard().catch((err) => showToast(err.message || 'Failed to load dashboard.', true));
    });

    document.getElementById('pageSizeSelect').addEventListener('change', (e) => {
        state.size = Number(e.target.value) || 15;
        state.page = 1;
        loadTransactions().catch((err) => showToast(err.message || 'Failed to load transactions.', true));
    });

    // ==================== Transactions List, Search & Filters ====================
    async function loadTransactions() {
        const params = new URLSearchParams();
        params.set('page', String(state.page));
        params.set('size', String(state.size));

        const search = filterSearch.value.trim();
        const type = filterType.value;
        const categoryId = filterCategory.value;
        const fromDate = filterFromDate.value;
        const toDate = filterToDate.value;

        if (search) params.set('search', search);
        if (type) params.set('type', type);
        if (categoryId) params.set('categoryId', categoryId);
        if (fromDate) params.set('fromDate', fromDate);
        if (toDate) params.set('toDate', toDate);

        const data = await apiRequest(`api/transactions?${params.toString()}`);
        state.transactions = data.transactions || [];
        state.totalPages = data.totalPages || 1;
        state.txMeta = data;
        renderTransactionRows();
    }

    function renderTransactionRows() {
        const tbody = document.getElementById('txTableBody');
        const wrap = document.getElementById('txTableWrap');
        const empty = document.getElementById('txEmptyState');
        const pagination = document.getElementById('paginationBar');
        const strip = document.getElementById('filteredSummaryStrip');
        const meta = state.txMeta || { totalCount: 0, filteredIncome: 0, filteredExpense: 0, page: 1, totalPages: 1 };

        strip.textContent = `${meta.totalCount} match${meta.totalCount === 1 ? '' : 'es'} · `
            + `In ${formatCurrency(meta.filteredIncome)} · Out ${formatCurrency(meta.filteredExpense)}`;

        if (state.transactions.length === 0) {
            wrap.classList.add('hidden');
            pagination.classList.add('hidden');
            empty.classList.remove('hidden');
            tbody.innerHTML = '';
            return;
        }

        empty.classList.add('hidden');
        wrap.classList.remove('hidden');

        tbody.innerHTML = state.transactions.map((tx) => {
            const isIncome = tx.type === 'INCOME';
            const sign = isIncome ? '+' : '-';
            return `
                <tr>
                    <td>${escapeHtml(formatDate(tx.transactionDate))}</td>
                    <td>
                        <div class="tx-desc">${escapeHtml(tx.description)}</div>
                        ${tx.notes ? `<span class="tx-notes">${escapeHtml(tx.notes)}</span>` : ''}
                    </td>
                    <td>
                        <span class="cat-badge">
                            <span class="cat-dot" style="background:${escapeHtml(tx.categoryColor || '#64748B')}"></span>
                            <span>${escapeHtml(tx.categoryName)}</span>
                        </span>
                    </td>
                    <td>
                        <span class="type-pill ${isIncome ? 'pill-income' : 'pill-expense'}">
                            ${isIncome ? 'INCOME' : 'EXPENSE'}
                        </span>
                    </td>
                    <td class="text-right tx-amount ${isIncome ? 'text-income' : 'text-expense'}">
                        ${sign}${escapeHtml(formatCurrency(tx.amount))}
                    </td>
                    <td class="text-right">
                        <div class="row-actions">
                            <button type="button" class="action-link" data-edit-tx="${tx.id}">Edit</button>
                            <button type="button" class="action-link action-link-danger" data-del-tx="${tx.id}">Delete</button>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('[data-edit-tx]').forEach((btn) => {
            btn.addEventListener('click', () => {
                const tx = state.transactions.find((item) => item.id === Number(btn.dataset.editTx));
                if (tx) {
                    openTxModal(tx);
                }
            });
        });

        tbody.querySelectorAll('[data-del-tx]').forEach((btn) => {
            btn.addEventListener('click', () => {
                const tx = state.transactions.find((item) => item.id === Number(btn.dataset.delTx));
                if (tx) {
                    openDeleteModal(tx);
                }
            });
        });

        if (meta.totalPages > 1) {
            pagination.classList.remove('hidden');
            document.getElementById('paginationInfo').textContent =
                `Page ${meta.page} of ${meta.totalPages} (${meta.totalCount} total)`;
            document.getElementById('prevPageBtn').disabled = meta.page <= 1;
            document.getElementById('nextPageBtn').disabled = meta.page >= meta.totalPages;
        } else {
            pagination.classList.add('hidden');
        }
    }

    let searchTimer = null;
    filterSearch.addEventListener('input', () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
            state.page = 1;
            loadTransactions().catch((err) => showToast(err.message, true));
        }, 240);
    });

    filterType.addEventListener('change', () => {
        populateFilterCategorySelect();
        state.page = 1;
        loadTransactions().catch((err) => showToast(err.message, true));
    });

    [filterCategory, filterFromDate, filterToDate].forEach((el) => {
        el.addEventListener('change', () => {
            if (filterFromDate.value && filterToDate.value && filterFromDate.value > filterToDate.value) {
                showToast('From date cannot be later than To date.', true);
                return;
            }
            state.page = 1;
            loadTransactions().catch((err) => showToast(err.message, true));
        });
    });

    document.getElementById('clearFiltersBtn').addEventListener('click', () => {
        filterSearch.value = '';
        filterType.value = '';
        populateFilterCategorySelect();
        filterCategory.value = '';
        filterFromDate.value = '';
        filterToDate.value = '';
        document.querySelectorAll('[data-preset]').forEach((b) => b.classList.remove('active'));
        state.page = 1;
        loadTransactions().catch((err) => showToast(err.message, true));
    });

    document.querySelectorAll('[data-preset]').forEach((btn) => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('[data-preset]').forEach((b) => b.classList.toggle('active', b === btn));
            const preset = btn.dataset.preset;
            const now = new Date();
            const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

            if (preset === 'all') {
                filterFromDate.value = '';
                filterToDate.value = '';
            } else if (preset === 'thisMonth') {
                filterFromDate.value = fmt(new Date(now.getFullYear(), now.getMonth(), 1));
                filterToDate.value = fmt(new Date(now.getFullYear(), now.getMonth() + 1, 0));
            } else if (preset === 'last30') {
                const start = new Date(now);
                start.setDate(start.getDate() - 29);
                filterFromDate.value = fmt(start);
                filterToDate.value = fmt(now);
            } else if (preset === 'thisYear') {
                filterFromDate.value = `${now.getFullYear()}-01-01`;
                filterToDate.value = `${now.getFullYear()}-12-31`;
            }

            state.page = 1;
            loadTransactions().catch((err) => showToast(err.message, true));
        });
    });

    document.getElementById('prevPageBtn').addEventListener('click', () => {
        if (state.page > 1) {
            state.page--;
            loadTransactions().catch((err) => showToast(err.message, true));
        }
    });

    document.getElementById('nextPageBtn').addEventListener('click', () => {
        if (state.page < state.totalPages) {
            state.page++;
            loadTransactions().catch((err) => showToast(err.message, true));
        }
    });

    // ==================== Add / Edit / Delete Transactions ====================
    function setModalTxType(type, selectedCategoryId = null) {
        state.editingTxType = type;
        const expBtn = document.getElementById('typeExpenseBtn');
        const incBtn = document.getElementById('typeIncomeBtn');
        expBtn.className = `type-btn${type === 'EXPENSE' ? ' active-expense' : ''}`;
        incBtn.className = `type-btn${type === 'INCOME' ? ' active-income' : ''}`;
        populateTxCategorySelect(type, selectedCategoryId);
    }

    document.getElementById('typeExpenseBtn').addEventListener('click', () => setModalTxType('EXPENSE'));
    document.getElementById('typeIncomeBtn').addEventListener('click', () => setModalTxType('INCOME'));

    function openTxModal(tx = null) {
        txFormError.classList.add('hidden');
        document.getElementById('txId').value = tx ? tx.id : '';
        document.getElementById('txModalTitle').textContent = tx ? 'Edit Transaction' : 'Add Transaction';
        document.getElementById('txAmount').value = tx ? Number(tx.amount).toFixed(2) : '';
        document.getElementById('txDate').value = tx ? tx.transactionDate : todayIso();
        document.getElementById('txDescription').value = tx ? tx.description : '';
        document.getElementById('txNotes').value = tx ? (tx.notes || '') : '';
        setModalTxType(tx ? tx.type : 'EXPENSE', tx ? tx.categoryId : null);
        openModal(txModal);
        document.getElementById('txAmount').focus();
    }

    [document.getElementById('addTxBtn'), document.getElementById('overviewAddBtn'), document.getElementById('emptyAddTxBtn')]
        .forEach((btn) => btn.addEventListener('click', () => openTxModal(null)));

    txForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        txFormError.classList.add('hidden');

        const id = document.getElementById('txId').value;
        const type = state.editingTxType;
        const amount = Number(document.getElementById('txAmount').value);
        const categoryId = Number(document.getElementById('txCategory').value);
        const transactionDate = document.getElementById('txDate').value;
        const description = document.getElementById('txDescription').value.trim();
        const notes = document.getElementById('txNotes').value.trim();

        if (!Number.isFinite(amount) || amount <= 0) {
            txFormError.textContent = 'Please enter an amount greater than 0.';
            txFormError.classList.remove('hidden');
            return;
        }
        if (!categoryId) {
            txFormError.textContent = 'Please choose a category.';
            txFormError.classList.remove('hidden');
            return;
        }
        if (!transactionDate) {
            txFormError.textContent = 'Please select a date.';
            txFormError.classList.remove('hidden');
            return;
        }
        if (!description) {
            txFormError.textContent = 'Please enter a short description.';
            txFormError.classList.remove('hidden');
            return;
        }

        const saveBtn = document.getElementById('txSaveBtn');
        saveBtn.disabled = true;
        saveBtn.textContent = 'Saving...';

        const payload = { type, categoryId, amount, transactionDate, description, notes };
        try {
            if (id) {
                await apiRequest(`api/transactions/${id}`, { method: 'PUT', body: payload });
                showToast('Transaction updated.');
            } else {
                await apiRequest('api/transactions', { method: 'POST', body: payload });
                showToast('Transaction added.');
            }
            closeModal(txModal);
            await refreshAllData();
        } catch (err) {
            txFormError.textContent = err.message || 'Unable to save transaction.';
            txFormError.classList.remove('hidden');
        } finally {
            saveBtn.disabled = false;
            saveBtn.textContent = 'Save Transaction';
        }
    });

    function openDeleteModal(tx) {
        state.deleteTargetId = tx.id;
        document.getElementById('deleteTxLabel').textContent =
            `${tx.description} (${formatCurrency(tx.amount)})`;
        openModal(deleteModal);
    }

    document.getElementById('confirmDeleteBtn').addEventListener('click', async () => {
        if (!state.deleteTargetId) {
            return;
        }
        const btn = document.getElementById('confirmDeleteBtn');
        btn.disabled = true;
        try {
            await apiRequest(`api/transactions/${state.deleteTargetId}`, { method: 'DELETE' });
            closeModal(deleteModal);
            state.deleteTargetId = null;
            showToast('Transaction deleted.');
            await refreshAllData();
        } catch (err) {
            showToast(err.message || 'Failed to delete transaction.', true);
        } finally {
            btn.disabled = false;
        }
    });

    // ==================== Monthly Summary Report ====================
    async function loadMonthlyReport() {
        const data = await apiRequest(`api/reports/monthly?year=${state.selectedYear}`);
        state.monthlyReportData = data;
        populateYearSelects(data.availableYears || [state.selectedYear]);
        renderMonthlyReport();
    }

    function renderMonthlyReport() {
        const data = state.monthlyReportData;
        if (!data) {
            return;
        }
        const yInc = Number(data.yearlyIncome || 0);
        const yExp = Number(data.yearlyExpense || 0);
        const yNet = Number(data.yearlyNet || 0);
        const rate = yInc > 0 ? ((yInc - yExp) / yInc) * 100 : 0;

        document.getElementById('yearlyIncomeVal').textContent = formatCurrency(yInc);
        document.getElementById('yearlyExpenseVal').textContent = formatCurrency(yExp);
        const netEl = document.getElementById('yearlyNetVal');
        netEl.textContent = formatCurrency(yNet);
        netEl.className = yNet >= 0 ? 'text-income' : 'text-expense';
        document.getElementById('yearlyRateVal').textContent = `${rate.toFixed(1)}%`;

        requestAnimationFrame(() => {
            renderMonthlyBarChart(
                document.getElementById('monthlyReportBarCanvas'),
                null,
                data.months || []
            );
        });

        const tbody = document.getElementById('monthlySummaryBody');
        tbody.innerHTML = (data.months || []).map((m) => {
            const inc = Number(m.income || 0);
            const exp = Number(m.expense || 0);
            const net = Number(m.net || 0);
            const mRate = inc > 0 ? ((inc - exp) / inc) * 100 : 0;
            return `
                <tr>
                    <td><strong>${escapeHtml(m.monthLabel)} ${escapeHtml(m.year)}</strong></td>
                    <td class="text-right text-income">${escapeHtml(formatCurrency(inc))}</td>
                    <td class="text-right text-expense">${escapeHtml(formatCurrency(exp))}</td>
                    <td class="text-right tx-amount ${net >= 0 ? 'text-income' : 'text-expense'}">${escapeHtml(formatCurrency(net))}</td>
                    <td class="text-right">${inc > 0 ? `${mRate.toFixed(1)}%` : '—'}</td>
                    <td class="text-right">${m.transactionCount}</td>
                </tr>
            `;
        }).join('');
    }

    document.getElementById('monthlyYearSelect').addEventListener('change', (e) => {
        state.selectedYear = Number(e.target.value);
        loadMonthlyReport().catch((err) => showToast(err.message, true));
        loadDashboard().catch(() => {});
    });

    // ==================== CSV Export ====================
    document.getElementById('exportCsvBtn').addEventListener('click', () => {
        if (!state.transactions || state.transactions.length === 0) {
            showToast('No transactions to export.', true);
            return;
        }
        const safeCell = (val) => {
            let s = String(val ?? '');
            if (/^[=+\-@]/.test(s)) {
                s = "'" + s;
            }
            return `"${s.replace(/"/g, '""')}"`;
        };
        const headers = ['Date', 'Type', 'Category', 'Description', 'Amount', 'Notes'];
        const rows = state.transactions.map((t) => [
            t.transactionDate,
            t.type,
            t.categoryName,
            t.description,
            Number(t.amount).toFixed(2),
            t.notes || ''
        ].map(safeCell).join(','));

        const csv = [headers.join(','), ...rows].join('\r\n');
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `transactions-${todayIso()}.csv`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    });

    // ==================== Initialization & Refresh ====================
    async function refreshAllData() {
        const tasks = [loadDashboard(), loadTransactions()];
        if (state.activeView === 'monthly') {
            tasks.push(loadMonthlyReport());
        }
        await Promise.all(tasks);
    }

    let resizeTimer = null;
    window.addEventListener('resize', () => {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(() => {
            if (state.activeView === 'overview') {
                renderOverviewCharts();
            } else if (state.activeView === 'monthly' && state.monthlyReportData) {
                renderMonthlyBarChart(
                    document.getElementById('monthlyReportBarCanvas'),
                    null,
                    state.monthlyReportData.months || []
                );
            }
        }, 120);
    });

    async function initDashboard() {
        try {
            const me = await apiRequest('api/auth/me');
            state.user = me.user;
            const fullName = state.user.fullName || 'User';
            document.getElementById('sidebarUserName').textContent = fullName;
            document.getElementById('sidebarUserEmail').textContent = state.user.email || '';
            const initials = fullName.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
            document.getElementById('sidebarAvatar').textContent = initials || 'U';

            await loadCategories();
            await refreshAllData();
        } catch (err) {
            if (err.status !== 401) {
                showToast(err.message || 'Failed to load dashboard.', true);
            }
        }
    }

    initDashboard();
})();

