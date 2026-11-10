(function (global) {
    'use strict';

    const { escapeHtml, formatCurrency, formatCompactCurrency } = global.FlowApi;
    const tooltip = document.getElementById('chartTooltip');

    function showTooltip(clientX, clientY, html) {
        if (!tooltip) {
            return;
        }
        tooltip.innerHTML = html;
        tooltip.classList.remove('hidden');
        const pad = 12;
        const rect = tooltip.getBoundingClientRect();
        let left = clientX + pad;
        let top = clientY - rect.height - pad;
        if (left + rect.width > window.innerWidth - 8) {
            left = clientX - rect.width - pad;
        }
        if (top < 8) {
            top = clientY + pad;
        }
        tooltip.style.left = `${left}px`;
        tooltip.style.top = `${top}px`;
    }

    function hideTooltip() {
        if (tooltip) {
            tooltip.classList.add('hidden');
        }
    }

    function prepareCanvas(canvas) {
        const rect = canvas.parentElement.getBoundingClientRect();
        const width = Math.max(100, Math.floor(rect.width));
        const height = Math.max(100, Math.floor(rect.height));
        const dpr = window.devicePixelRatio || 1;
        canvas.width = width * dpr;
        canvas.height = height * dpr;
        canvas.style.width = `${width}px`;
        canvas.style.height = `${height}px`;
        const ctx = canvas.getContext('2d');
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, width, height);
        return { ctx, width, height };
    }

    /**
     * Renders an interactive category expense donut chart + clickable/hoverable legend.
     */
    function renderDonutChart(canvas, legendEl, emptyEl, items) {
        if (!canvas) {
            return;
        }
        const validItems = (items || []).filter((it) => Number(it.totalAmount) > 0);
        if (validItems.length === 0) {
            emptyEl.classList.remove('hidden');
            legendEl.innerHTML = '';
            const { ctx, width, height } = prepareCanvas(canvas);
            ctx.clearRect(0, 0, width, height);
            return;
        }

        emptyEl.classList.add('hidden');
        const total = validItems.reduce((sum, it) => sum + Number(it.totalAmount), 0);
        let hoveredIdx = -1;

        function draw() {
            const { ctx, width, height } = prepareCanvas(canvas);
            const cx = width / 2;
            const cy = height / 2;
            const outerR = Math.min(width, height) * 0.44;
            const innerR = outerR * 0.6;

            let startAngle = -Math.PI / 2;
            validItems.forEach((item, idx) => {
                const val = Number(item.totalAmount);
                const sliceAngle = (val / total) * Math.PI * 2;
                const endAngle = startAngle + sliceAngle;
                const isHover = idx === hoveredIdx;
                const r = isHover ? outerR + 5 : outerR;

                ctx.beginPath();
                ctx.arc(cx, cy, r, startAngle, endAngle);
                ctx.arc(cx, cy, innerR, endAngle, startAngle, true);
                ctx.closePath();
                ctx.fillStyle = item.color || '#64748B';
                ctx.fill();

                if (validItems.length > 1) {
                    ctx.lineWidth = 2;
                    ctx.strokeStyle = '#ffffff';
                    ctx.stroke();
                }

                item._startAngle = startAngle;
                item._endAngle = endAngle;
                startAngle = endAngle;
            });

            // Center readout
            const active = hoveredIdx >= 0 ? validItems[hoveredIdx] : null;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillStyle = '#64748b';
            ctx.font = '600 11px Inter, system-ui, sans-serif';
            ctx.fillText(active ? active.categoryName : 'Total Expenses', cx, cy - 11);

            ctx.fillStyle = '#0f172a';
            ctx.font = '700 15px Inter, system-ui, sans-serif';
            const centerAmt = active ? formatCompactCurrency(active.totalAmount) : formatCompactCurrency(total);
            ctx.fillText(centerAmt, cx, cy + 9);
        }

        function renderLegend() {
            legendEl.innerHTML = validItems
                .map((item, idx) => `
                    <div class="legend-item${idx === hoveredIdx ? ' highlighted' : ''}" data-idx="${idx}">
                        <div class="legend-left">
                            <span class="legend-swatch" style="background:${escapeHtml(item.color || '#64748B')}"></span>
                            <span class="legend-name" title="${escapeHtml(item.categoryName)}">${escapeHtml(item.categoryName)}</span>
                        </div>
                        <div class="legend-right">
                            <span class="legend-amt">${escapeHtml(formatCurrency(item.totalAmount))}</span>
                            <span class="legend-pct">${Number(item.percentage).toFixed(1)}%</span>
                        </div>
                    </div>
                `)
                .join('');

            legendEl.querySelectorAll('.legend-item').forEach((row) => {
                row.addEventListener('mouseenter', () => {
                    hoveredIdx = Number(row.dataset.idx);
                    draw();
                });
                row.addEventListener('mouseleave', () => {
                    hoveredIdx = -1;
                    draw();
                });
            });
        }

        canvas.onmousemove = (e) => {
            const rect = canvas.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            const cx = rect.width / 2;
            const cy = rect.height / 2;
            const outerR = Math.min(rect.width, rect.height) * 0.44 + 6;
            const innerR = Math.min(rect.width, rect.height) * 0.44 * 0.6;
            const dx = x - cx;
            const dy = y - cy;
            const dist = Math.hypot(dx, dy);

            let found = -1;
            if (dist >= innerR && dist <= outerR) {
                let angle = Math.atan2(dy, dx);
                if (angle < -Math.PI / 2) {
                    angle += Math.PI * 2;
                }
                for (let i = 0; i < validItems.length; i++) {
                    if (angle >= validItems[i]._startAngle && angle <= validItems[i]._endAngle) {
                        found = i;
                        break;
                    }
                }
            }

            if (found !== hoveredIdx) {
                hoveredIdx = found;
                draw();
                renderLegend();
            }

            if (found >= 0) {
                const it = validItems[found];
                showTooltip(
                    e.clientX,
                    e.clientY,
                    `<strong>${escapeHtml(it.categoryName)}</strong><br>${escapeHtml(formatCurrency(it.totalAmount))} (${Number(it.percentage).toFixed(1)}%)`
                );
            } else {
                hideTooltip();
            }
        };

        canvas.onmouseleave = () => {
            hoveredIdx = -1;
            hideTooltip();
            draw();
            renderLegend();
        };

        draw();
        renderLegend();
    }

    /**
     * Renders a grouped bar chart for Monthly Income vs Expenses.
     */
    function renderMonthlyBarChart(canvas, emptyEl, months) {
        if (!canvas) {
            return;
        }
        const data = months || [];
        const hasActivity = data.some((m) => Number(m.income) > 0 || Number(m.expense) > 0);
        if (emptyEl) {
            emptyEl.classList.toggle('hidden', hasActivity);
        }
        if (!hasActivity && emptyEl) {
            const { ctx, width, height } = prepareCanvas(canvas);
            ctx.clearRect(0, 0, width, height);
            return;
        }

        let hoveredMonth = -1;

        function computeNiceMax(maxVal) {
            if (maxVal <= 0) {
                return 100;
            }
            const exp = Math.floor(Math.log10(maxVal));
            const base = Math.pow(10, exp);
            const frac = maxVal / base;
            let nice = 10;
            if (frac <= 1.2) nice = 1.2;
            else if (frac <= 1.5) nice = 1.5;
            else if (frac <= 2) nice = 2;
            else if (frac <= 3) nice = 3;
            else if (frac <= 5) nice = 5;
            else if (frac <= 8) nice = 8;
            return nice * base;
        }

        function draw() {
            const { ctx, width, height } = prepareCanvas(canvas);
            if (width <= 10 || height <= 10) {
                return;
            }
            const padLeft = 56;
            const padRight = 14;
            const padTop = 26;
            const padBottom = 30;
            const plotW = Math.max(40, width - padLeft - padRight);
            const plotH = Math.max(40, height - padTop - padBottom);

            const maxRaw = Math.max(
                10,
                ...data.map((m) => Math.max(Number(m.income || 0), Number(m.expense || 0)))
            );
            const yMax = computeNiceMax(maxRaw);

            // Top Legend
            ctx.font = '600 11px Inter, system-ui, sans-serif';
            ctx.textBaseline = 'middle';
            ctx.textAlign = 'left';
            ctx.fillStyle = '#10b981';
            ctx.fillRect(width - 148, 8, 10, 10);
            ctx.fillStyle = '#475569';
            ctx.fillText('Income', width - 134, 13);

            ctx.fillStyle = '#f43f5e';
            ctx.fillRect(width - 76, 8, 10, 10);
            ctx.fillStyle = '#475569';
            ctx.fillText('Expense', width - 62, 13);

            // Gridlines & Y ticks
            const steps = 4;
            ctx.font = '500 11px Inter, system-ui, sans-serif';
            ctx.textAlign = 'right';
            ctx.textBaseline = 'middle';

            for (let i = 0; i <= steps; i++) {
                const ratio = i / steps;
                const y = padTop + plotH - ratio * plotH;
                const val = yMax * ratio;

                ctx.beginPath();
                ctx.moveTo(padLeft, y);
                ctx.lineTo(width - padRight, y);
                ctx.strokeStyle = i === 0 ? '#cbd5e1' : '#e2e8f0';
                ctx.lineWidth = 1;
                ctx.stroke();

                ctx.fillStyle = '#64748b';
                ctx.fillText(formatCompactCurrency(val), padLeft - 8, y);
            }

            const slotW = plotW / Math.max(1, data.length);
            const barW = Math.max(4, Math.min(16, slotW * 0.3));
            const gap = Math.max(2, barW * 0.25);

            data.forEach((m, idx) => {
                const slotX = padLeft + idx * slotW;
                if (idx === hoveredMonth) {
                    ctx.fillStyle = 'rgba(13, 148, 136, 0.06)';
                    ctx.fillRect(slotX + 2, padTop, slotW - 4, plotH);
                }

                const center = slotX + slotW / 2;
                const incVal = Number(m.income || 0);
                const expVal = Number(m.expense || 0);
                const incH = (incVal / yMax) * plotH;
                const expH = (expVal / yMax) * plotH;

                drawRoundedTopBar(ctx, center - barW - gap / 2, padTop + plotH - incH, barW, incH, '#10b981');
                drawRoundedTopBar(ctx, center + gap / 2, padTop + plotH - expH, barW, expH, '#f43f5e');

                ctx.fillStyle = idx === hoveredMonth ? '#0f172a' : '#64748b';
                ctx.font = idx === hoveredMonth ? '700 11px Inter, system-ui, sans-serif' : '500 11px Inter, system-ui, sans-serif';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'top';
                ctx.fillText(m.monthLabel || String(m.month), center, padTop + plotH + 8);
            });
        }

        function drawRoundedTopBar(ctx, x, y, w, h, color) {
            if (h <= 0.5) {
                return;
            }
            const r = Math.min(4, w / 2, h);
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.moveTo(x, y + h);
            ctx.lineTo(x, y + r);
            ctx.quadraticCurveTo(x, y, x + r, y);
            ctx.lineTo(x + w - r, y);
            ctx.quadraticCurveTo(x + w, y, x + w, y + r);
            ctx.lineTo(x + w, y + h);
            ctx.closePath();
            ctx.fill();
        }

        canvas.onmousemove = (e) => {
            const rect = canvas.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const padLeft = 56;
            const padRight = 14;
            const plotW = Math.max(40, rect.width - padLeft - padRight);
            if (x < padLeft || x > rect.width - padRight || data.length === 0) {
                if (hoveredMonth !== -1) {
                    hoveredMonth = -1;
                    draw();
                }
                hideTooltip();
                return;
            }
            const idx = Math.min(data.length - 1, Math.max(0, Math.floor(((x - padLeft) / plotW) * data.length)));
            if (idx !== hoveredMonth) {
                hoveredMonth = idx;
                draw();
            }
            const m = data[idx];
            showTooltip(
                e.clientX,
                e.clientY,
                `<strong>${escapeHtml(m.monthLabel)} ${escapeHtml(m.year)}</strong><br>`
                + `Income: ${escapeHtml(formatCurrency(m.income))}<br>`
                + `Expense: ${escapeHtml(formatCurrency(m.expense))}<br>`
                + `Net: ${escapeHtml(formatCurrency(m.net))}`
            );
        };

        canvas.onmouseleave = () => {
            hoveredMonth = -1;
            hideTooltip();
            draw();
        };

        draw();
    }

    global.FlowCharts = {
        renderDonutChart,
        renderMonthlyBarChart
    };
})(window);

