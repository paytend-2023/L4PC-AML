/**
 * AML Screening Portal — single-page app.
 * Communicates with backend REST APIs under /pc/aml/*.
 */
(function () {
    'use strict';

    const API = {
        alerts:    (params) => get('/pc/aml/alerts' + qs(params)),
        alert:     (id)     => get('/pc/aml/alerts/' + id),
        updateAlert: (id, body) => patch('/pc/aml/alerts/' + id, body),
        addDecision: (id, body) => post('/pc/aml/alerts/' + id + '/decisions', body),
        screenings: (params) => get('/pc/aml/screening' + qs(params)),
        screening:  (id)     => get('/pc/aml/screening/' + id),
        newScreening: (body) => post('/pc/aml/screening', body),
        dataSyncs: (provider) => get('/pc/aml/data-sync?provider=' + encodeURIComponent(provider)),
        session:   ()        => get('/pc/auth/session'),
    };

    // --- HTTP helpers ---
    async function get(url) {
        const r = await fetch(url);
        if (!r.ok) throw await extractError(r);
        return r.json();
    }
    async function post(url, body) {
        const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        if (!r.ok) throw await extractError(r);
        return r.json();
    }
    async function patch(url, body) {
        const r = await fetch(url, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        if (!r.ok) throw await extractError(r);
        return r.json();
    }
    async function extractError(r) {
        try { const j = await r.json(); return new Error(j.message || j.error || r.statusText); }
        catch { return new Error(r.statusText); }
    }
    function qs(params) {
        if (!params) return '';
        const p = new URLSearchParams();
        Object.entries(params).forEach(([k, v]) => { if (v) p.set(k, v); });
        const s = p.toString();
        return s ? '?' + s : '';
    }

    // --- State ---
    let currentPage = 'alerts';
    const content = document.getElementById('content');

    // --- Navigation ---
    document.querySelectorAll('#sidebar a[data-page]').forEach(a => {
        a.addEventListener('click', e => {
            e.preventDefault();
            navigateTo(a.dataset.page);
        });
    });

    function navigateTo(page, params) {
        currentPage = page;
        document.querySelectorAll('#sidebar a[data-page]').forEach(a =>
            a.classList.toggle('active', a.dataset.page === page));
        switch (page) {
            case 'alerts': renderAlerts(params); break;
            case 'alert-detail': renderAlertDetail(params); break;
            case 'screening': renderScreening(); break;
            case 'screening-detail': renderScreeningDetail(params); break;
            case 'datasync': renderDataSync(); break;
        }
    }

    // --- Session ---
    async function loadSession() {
        try {
            const s = await API.session();
            if (s.authenticated && s.user) {
                document.getElementById('user-info').textContent = s.user.name || s.user.email;
            }
        } catch { /* SSO not enabled locally */ }
    }

    // --- Alert List ---
    async function renderAlerts(params) {
        const status = (params && params.status) || '';
        content.innerHTML = `
            <div class="page-header"><h1>Alerts</h1></div>
            <div class="filters">
                <select id="alert-status-filter">
                    <option value="">All Statuses</option>
                    <option value="OPEN">Open</option>
                    <option value="UNDER_REVIEW">Under Review</option>
                    <option value="ESCALATED">Escalated</option>
                    <option value="APPROVED">Approved</option>
                    <option value="REJECTED">Rejected</option>
                </select>
                <input id="alert-customer-filter" type="text" placeholder="Customer ID..." style="width:200px">
                <button class="btn btn-outline btn-sm" id="alert-filter-btn">Filter</button>
            </div>
            <div class="card"><div class="loading">Loading alerts...</div></div>`;

        const filterSel = document.getElementById('alert-status-filter');
        const customerInput = document.getElementById('alert-customer-filter');
        if (status) filterSel.value = status;

        document.getElementById('alert-filter-btn').addEventListener('click', () => {
            loadAlertTable(filterSel.value, customerInput.value.trim());
        });
        filterSel.addEventListener('change', () => {
            loadAlertTable(filterSel.value, customerInput.value.trim());
        });

        await loadAlertTable(status, '');
    }

    async function loadAlertTable(status, customerId) {
        const card = content.querySelector('.card');
        card.innerHTML = '<div class="loading">Loading...</div>';
        try {
            const params = {};
            if (status) params.status = status;
            if (customerId) params.customer_id = customerId;
            const alerts = await API.alerts(params);
            if (alerts.length === 0) {
                card.innerHTML = '<div class="empty-state">No alerts found</div>';
                return;
            }
            card.innerHTML = `<table>
                <thead><tr>
                    <th>Alert ID</th><th>Customer</th><th>Type</th><th>Risk</th><th>Status</th><th>Assigned</th><th>Created</th>
                </tr></thead>
                <tbody>${alerts.map(a => `<tr data-id="${a.alert_id}">
                    <td>${shortId(a.alert_id)}</td>
                    <td>${a.customer_id || '-'}</td>
                    <td>${a.alert_type || '-'}</td>
                    <td>${riskBadge(a.risk_level)}</td>
                    <td>${statusBadge(a.status)}</td>
                    <td>${a.assigned_to || '-'}</td>
                    <td>${fmtTime(a.created_at)}</td>
                </tr>`).join('')}</tbody></table>`;
            card.querySelectorAll('tbody tr').forEach(tr => {
                tr.addEventListener('click', () => navigateTo('alert-detail', { id: tr.dataset.id }));
            });
        } catch (e) {
            card.innerHTML = `<div class="empty-state" style="color:var(--danger)">Error: ${e.message}</div>`;
        }
    }

    // --- Alert Detail ---
    async function renderAlertDetail(params) {
        content.innerHTML = '<div class="loading">Loading alert...</div>';
        try {
            const alert = await API.alert(params.id);
            const isClosed = alert.status === 'APPROVED' || alert.status === 'REJECTED';
            content.innerHTML = `
                <div class="page-header">
                    <h1>Alert ${shortId(alert.alert_id)}</h1>
                    <button class="btn btn-outline" id="back-btn">Back to list</button>
                </div>
                <div class="card">
                    <div class="detail-grid">
                        <div class="detail-item"><label>Alert ID</label><span>${alert.alert_id}</span></div>
                        <div class="detail-item"><label>Customer ID</label><span>${alert.customer_id || '-'}</span></div>
                        <div class="detail-item"><label>Screening ID</label><span class="link-screening" data-id="${alert.screening_id}" style="cursor:pointer;color:var(--primary)">${shortId(alert.screening_id)}</span></div>
                        <div class="detail-item"><label>Alert Type</label><span>${alert.alert_type || '-'}</span></div>
                        <div class="detail-item"><label>Risk Level</label><span>${riskBadge(alert.risk_level)}</span></div>
                        <div class="detail-item"><label>Status</label><span>${statusBadge(alert.status)}</span></div>
                        <div class="detail-item"><label>Assigned To</label><span>${alert.assigned_to || '-'}</span></div>
                        <div class="detail-item"><label>Created</label><span>${fmtTime(alert.created_at)}</span></div>
                        <div class="detail-item"><label>Updated</label><span>${fmtTime(alert.updated_at)}</span></div>
                    </div>
                    ${!isClosed ? `<div style="display:flex;gap:8px;margin-top:12px">
                        <button class="btn btn-outline btn-sm" id="assign-btn">Assign</button>
                        <button class="btn btn-primary btn-sm" id="decision-btn">Add Decision</button>
                    </div>` : ''}
                </div>

                <h2 style="font-size:16px;margin:20px 0 10px">Decision History</h2>
                ${alert.decisions && alert.decisions.length > 0 ? `
                <div class="card">
                    <div class="timeline">
                        ${alert.decisions.map(d => `<div class="timeline-item">
                            <div class="timeline-date">${fmtTime(d.decided_at)} &mdash; ${d.decided_by || 'System'}</div>
                            <div class="timeline-body">
                                <strong>${statusBadge(d.decision)}</strong>
                                ${d.decision_reason ? `<p style="margin-top:4px;font-size:13px;color:var(--gray-700)">${escHtml(d.decision_reason)}</p>` : ''}
                                ${d.approved_by ? `<p style="font-size:12px;color:var(--gray-500)">Approved by: ${escHtml(d.approved_by)}</p>` : ''}
                            </div>
                        </div>`).join('')}
                    </div>
                </div>` : '<div class="card"><div class="empty-state">No decisions yet</div></div>'}
            `;

            document.getElementById('back-btn').addEventListener('click', () => navigateTo('alerts'));
            content.querySelectorAll('.link-screening').forEach(el => {
                el.addEventListener('click', () => navigateTo('screening-detail', { id: el.dataset.id }));
            });

            if (!isClosed) {
                const assignBtn = document.getElementById('assign-btn');
                if (assignBtn) assignBtn.addEventListener('click', () => showAssignModal(alert));
                const decisionBtn = document.getElementById('decision-btn');
                if (decisionBtn) decisionBtn.addEventListener('click', () => showDecisionModal(alert));
            }
        } catch (e) {
            content.innerHTML = `<div class="empty-state" style="color:var(--danger)">Error: ${e.message}</div>`;
        }
    }

    function showAssignModal(alert) {
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.innerHTML = `<div class="modal">
            <h2>Assign Alert</h2>
            <div class="form-group">
                <label>Assign To</label>
                <input id="assign-to-input" type="text" value="${alert.assigned_to || ''}" placeholder="analyst@paytend.com">
            </div>
            <div class="form-group">
                <label>Status</label>
                <select id="assign-status-input">
                    <option value="">Keep current</option>
                    <option value="UNDER_REVIEW">Under Review</option>
                    <option value="ESCALATED">Escalated</option>
                </select>
            </div>
            <div class="modal-actions">
                <button class="btn btn-outline" id="assign-cancel">Cancel</button>
                <button class="btn btn-primary" id="assign-save">Save</button>
            </div>
        </div>`;
        document.body.appendChild(overlay);
        overlay.querySelector('#assign-cancel').addEventListener('click', () => overlay.remove());
        overlay.addEventListener('click', e => { if (e.target === overlay) overlay.remove(); });
        overlay.querySelector('#assign-save').addEventListener('click', async () => {
            const body = {};
            const assignTo = overlay.querySelector('#assign-to-input').value.trim();
            const status = overlay.querySelector('#assign-status-input').value;
            if (assignTo) body.assigned_to = assignTo;
            if (status) body.status = status;
            try {
                await API.updateAlert(alert.alert_id, body);
                overlay.remove();
                navigateTo('alert-detail', { id: alert.alert_id });
            } catch (e) { window.alert('Error: ' + e.message); }
        });
    }

    function showDecisionModal(alert) {
        const isHigh = alert.risk_level === 'HIGH';
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.innerHTML = `<div class="modal">
            <h2>Add Decision</h2>
            <div class="form-group">
                <label>Decision</label>
                <select id="dec-decision">
                    <option value="APPROVED">Approve (False Positive)</option>
                    <option value="REJECTED">Reject (True Match)</option>
                    <option value="ESCALATED">Escalate</option>
                </select>
            </div>
            <div class="form-group">
                <label>Reason</label>
                <textarea id="dec-reason" placeholder="Provide justification..."></textarea>
            </div>
            <div class="form-group">
                <label>Decided By</label>
                <input id="dec-by" type="text" placeholder="your.name@paytend.com">
            </div>
            ${isHigh ? `<div class="form-group">
                <label>Approved By (required for HIGH risk)</label>
                <input id="dec-approved-by" type="text" placeholder="supervisor@paytend.com">
            </div>` : ''}
            <div class="modal-actions">
                <button class="btn btn-outline" id="dec-cancel">Cancel</button>
                <button class="btn btn-primary" id="dec-save">Submit</button>
            </div>
        </div>`;
        document.body.appendChild(overlay);
        overlay.querySelector('#dec-cancel').addEventListener('click', () => overlay.remove());
        overlay.addEventListener('click', e => { if (e.target === overlay) overlay.remove(); });
        overlay.querySelector('#dec-save').addEventListener('click', async () => {
            const body = {
                decision: overlay.querySelector('#dec-decision').value,
                decision_reason: overlay.querySelector('#dec-reason').value.trim(),
                decided_by: overlay.querySelector('#dec-by').value.trim(),
            };
            if (isHigh) {
                body.approved_by = overlay.querySelector('#dec-approved-by').value.trim();
            }
            try {
                await API.addDecision(alert.alert_id, body);
                overlay.remove();
                navigateTo('alert-detail', { id: alert.alert_id });
            } catch (e) { window.alert('Error: ' + e.message); }
        });
    }

    // --- Screening ---
    async function renderScreening() {
        content.innerHTML = `
            <div class="page-header">
                <h1>Screening</h1>
                <button class="btn btn-primary" id="new-screening-btn">New Screening</button>
            </div>
            <div class="filters">
                <input id="scr-customer-input" type="text" placeholder="Customer ID..." style="width:200px">
                <button class="btn btn-outline btn-sm" id="scr-search-btn">Search</button>
            </div>
            <div class="card" id="scr-results"><div class="empty-state">Enter a customer ID to view screening history</div></div>`;

        document.getElementById('new-screening-btn').addEventListener('click', showNewScreeningModal);
        document.getElementById('scr-search-btn').addEventListener('click', () => {
            const cid = document.getElementById('scr-customer-input').value.trim();
            if (cid) loadScreeningTable(cid);
        });
        document.getElementById('scr-customer-input').addEventListener('keydown', e => {
            if (e.key === 'Enter') {
                const cid = e.target.value.trim();
                if (cid) loadScreeningTable(cid);
            }
        });
    }

    async function loadScreeningTable(customerId) {
        const card = document.getElementById('scr-results');
        card.innerHTML = '<div class="loading">Loading...</div>';
        try {
            const list = await API.screenings({ customer_id: customerId });
            if (list.length === 0) {
                card.innerHTML = '<div class="empty-state">No screenings found for this customer</div>';
                return;
            }
            card.innerHTML = `<table>
                <thead><tr><th>Screening ID</th><th>Type</th><th>Risk</th><th>Score</th><th>Matches</th><th>Status</th><th>Created</th></tr></thead>
                <tbody>${list.map(s => `<tr data-id="${s.screening_id}">
                    <td>${shortId(s.screening_id)}</td>
                    <td>${s.screening_type || '-'}</td>
                    <td>${riskBadge(s.risk_level)}</td>
                    <td>${s.risk_score}</td>
                    <td>${s.match_count}</td>
                    <td>${statusBadge(s.status)}</td>
                    <td>${fmtTime(s.created_at)}</td>
                </tr>`).join('')}</tbody></table>`;
            card.querySelectorAll('tbody tr').forEach(tr => {
                tr.addEventListener('click', () => navigateTo('screening-detail', { id: tr.dataset.id }));
            });
        } catch (e) {
            card.innerHTML = `<div class="empty-state" style="color:var(--danger)">Error: ${e.message}</div>`;
        }
    }

    function showNewScreeningModal() {
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.innerHTML = `<div class="modal">
            <h2>New Screening</h2>
            <div class="form-group"><label>Customer ID *</label><input id="ns-cid" type="text"></div>
            <div class="form-group"><label>Full Name *</label><input id="ns-name" type="text"></div>
            <div class="form-group"><label>Date of Birth</label><input id="ns-dob" type="text" placeholder="YYYY-MM-DD"></div>
            <div class="form-group"><label>Nationality (ISO 2)</label><input id="ns-nat" type="text" maxlength="2"></div>
            <div class="form-group"><label>ID Number</label><input id="ns-idnum" type="text"></div>
            <div class="form-group"><label>Screening Type</label>
                <select id="ns-type">
                    <option value="ONBOARDING">Onboarding</option>
                    <option value="PERIODIC">Periodic</option>
                    <option value="MANUAL">Manual</option>
                    <option value="TRANSACTION">Transaction</option>
                </select>
            </div>
            <div class="modal-actions">
                <button class="btn btn-outline" id="ns-cancel">Cancel</button>
                <button class="btn btn-primary" id="ns-submit">Submit</button>
            </div>
        </div>`;
        document.body.appendChild(overlay);
        overlay.querySelector('#ns-cancel').addEventListener('click', () => overlay.remove());
        overlay.addEventListener('click', e => { if (e.target === overlay) overlay.remove(); });
        overlay.querySelector('#ns-submit').addEventListener('click', async () => {
            const body = {
                customer_id: overlay.querySelector('#ns-cid').value.trim(),
                name: overlay.querySelector('#ns-name').value.trim(),
                date_of_birth: overlay.querySelector('#ns-dob').value.trim() || null,
                nationality: overlay.querySelector('#ns-nat').value.trim() || null,
                id_number: overlay.querySelector('#ns-idnum').value.trim() || null,
                screening_type: overlay.querySelector('#ns-type').value,
            };
            if (!body.customer_id || !body.name) { window.alert('Customer ID and Name are required'); return; }
            try {
                const result = await API.newScreening(body);
                overlay.remove();
                navigateTo('screening-detail', { id: result.screening_id });
            } catch (e) { window.alert('Error: ' + e.message); }
        });
    }

    // --- Screening Detail ---
    async function renderScreeningDetail(params) {
        content.innerHTML = '<div class="loading">Loading screening...</div>';
        try {
            const s = await API.screening(params.id);
            content.innerHTML = `
                <div class="page-header">
                    <h1>Screening ${shortId(s.screening_id)}</h1>
                    <button class="btn btn-outline" id="scr-back-btn">Back</button>
                </div>
                <div class="card">
                    <div class="detail-grid">
                        <div class="detail-item"><label>Screening ID</label><span>${s.screening_id}</span></div>
                        <div class="detail-item"><label>Customer ID</label><span>${s.customer_id}</span></div>
                        <div class="detail-item"><label>Type</label><span>${s.screening_type || '-'}</span></div>
                        <div class="detail-item"><label>Status</label><span>${statusBadge(s.status)}</span></div>
                        <div class="detail-item"><label>Risk Level</label><span>${riskBadge(s.risk_level)}</span></div>
                        <div class="detail-item"><label>Risk Score</label><span style="font-size:20px;font-weight:700">${s.risk_score}</span></div>
                        <div class="detail-item"><label>Match Count</label><span>${s.match_count}</span></div>
                        <div class="detail-item"><label>Created</label><span>${fmtTime(s.created_at)}</span></div>
                    </div>
                </div>

                <h2 style="font-size:16px;margin:20px 0 10px">Matches (${s.matches ? s.matches.length : 0})</h2>
                ${s.matches && s.matches.length > 0 ? s.matches.map(m => `
                    <div class="match-card">
                        <div class="match-header">
                            <div>
                                <strong>${escHtml(m.entity_name || '-')}</strong>
                                <span style="margin-left:8px;font-size:12px;color:var(--gray-500)">${m.provider || ''} / ${m.entity_type || ''}</span>
                            </div>
                            <div class="match-score" style="color:${m.match_score >= 90 ? 'var(--danger)' : m.match_score >= 70 ? 'var(--warning)' : 'var(--success)'}">${m.match_score}%</div>
                        </div>
                        <div style="font-size:13px;color:var(--gray-500)">
                            Match type: ${m.match_type || '-'} &middot; Category: ${m.category || '-'} &middot; Entity ID: ${m.entity_id || '-'}
                        </div>
                    </div>
                `).join('') : '<div class="card"><div class="empty-state">No matches found</div></div>'}
            `;
            document.getElementById('scr-back-btn').addEventListener('click', () => navigateTo('screening'));
        } catch (e) {
            content.innerHTML = `<div class="empty-state" style="color:var(--danger)">Error: ${e.message}</div>`;
        }
    }

    // --- Data Sync ---
    async function renderDataSync() {
        content.innerHTML = `
            <div class="page-header"><h1>Data Sync</h1></div>
            <div class="tabs">
                <button class="tab active" data-provider="DOW_JONES">Dow Jones</button>
                <button class="tab" data-provider="EU_SANCTIONS">EU Sanctions</button>
                <button class="tab" data-provider="UN_SANCTIONS">UN Sanctions</button>
            </div>
            <div class="card" id="sync-results"><div class="loading">Loading...</div></div>`;

        content.querySelectorAll('.tab').forEach(tab => {
            tab.addEventListener('click', () => {
                content.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                loadSyncTable(tab.dataset.provider);
            });
        });
        await loadSyncTable('DOW_JONES');
    }

    async function loadSyncTable(provider) {
        const card = document.getElementById('sync-results');
        card.innerHTML = '<div class="loading">Loading...</div>';
        try {
            const records = await API.dataSyncs(provider);
            if (records.length === 0) {
                card.innerHTML = '<div class="empty-state">No sync records found</div>';
                return;
            }
            card.innerHTML = `<table>
                <thead><tr><th>ID</th><th>Type</th><th>File</th><th>Status</th><th>Records</th><th>Started</th><th>Completed</th><th>Error</th></tr></thead>
                <tbody>${records.map(r => `<tr>
                    <td>${shortId(r.id)}</td>
                    <td>${r.syncType || '-'}</td>
                    <td>${r.fileName || '-'}</td>
                    <td>${statusBadge(r.status)}</td>
                    <td>${r.recordsProcessed != null ? r.recordsProcessed : '-'}</td>
                    <td>${fmtTime(r.startedAt)}</td>
                    <td>${fmtTime(r.completedAt)}</td>
                    <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${escAttr(r.errorMessage || '')}">${r.errorMessage || '-'}</td>
                </tr>`).join('')}</tbody></table>`;
        } catch (e) {
            card.innerHTML = `<div class="empty-state" style="color:var(--danger)">Error: ${e.message}</div>`;
        }
    }

    // --- Helpers ---
    function shortId(id) { return id ? id.substring(0, 12) + '...' : '-'; }
    function fmtTime(iso) {
        if (!iso) return '-';
        const d = new Date(iso);
        return d.toLocaleDateString('en-CA') + ' ' + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
    }
    function riskBadge(level) {
        if (!level) return '-';
        const cls = level === 'HIGH' ? 'high' : level === 'MEDIUM' ? 'medium' : 'low';
        return `<span class="badge badge-${cls}">${level}</span>`;
    }
    function statusBadge(status) {
        if (!status) return '-';
        return `<span class="badge badge-${status.toLowerCase()}">${status.replace('_', ' ')}</span>`;
    }
    function escHtml(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }
    function escAttr(s) { return s.replace(/"/g, '&quot;').replace(/</g, '&lt;'); }

    // --- Init ---
    loadSession();
    navigateTo('alerts');
})();
