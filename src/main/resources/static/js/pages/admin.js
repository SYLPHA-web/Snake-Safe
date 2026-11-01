import { html, toHtml, $, hhmm } from '../util.js';
import { api, tokens, download } from '../api.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { openModal, confirmDialog, toast, errorText } from '../ui.js';

export const chrome = 'bare';
export const title = 'admin.title';

const A = { auth: 'admin' };
const time = (v) => (v ? hhmm(v) : '–');

// Each tab says how to list, which columns to show and how to create / edit / remove rows.
const TABS = {
  hospitals: {
    list: '/api/admin/hospitals', create: '/api/admin/hospitals', update: (r) => `/api/admin/hospitals/${r.id}`, remove: (r) => `/api/admin/hospitals/${r.id}`, removeLabel: 'admin.hide',
    cols: [['name', (r) => r.name], ['district', (r) => r.district], ['phone', (r) => r.phone], ['vials', (r) => `${r.stock.vials} · ${r.stock.status}`]],
    fields: [['name', 'text', 1], ['district', 'text', 1], ['address', 'text', 1], ['phone', 'text', 1], ['lat', 'number', 1], ['lng', 'number', 1], ['vials', 'number'], ['active', 'checkbox']],
    values: (r) => ({ ...r, vials: r.stock.vials, active: true }),
  },
  staff: {
    list: '/api/admin/staff', create: '/api/admin/staff', update: (r) => `/api/admin/staff/${r.id}`, remove: (r) => `/api/admin/staff/${r.id}`, removeLabel: 'admin.deactivate',
    cols: [['username', (r) => r.username], ['name', (r) => r.name], ['hospital', (r, S) => S.hosp[r.hospitalId] || r.hospitalId], ['active', (r) => (r.active ? '✓' : '✗')]],
    fields: [['username', 'text', 1], ['password', 'password'], ['name', 'text', 1], ['hospitalId', 'hospital', 1], ['active', 'checkbox']],
    values: (r) => r,
  },
  snakes: {
    list: '/api/admin/snakes', create: '/api/admin/snakes', update: (r) => `/api/admin/snakes/${r.id}`, remove: (r) => `/api/admin/snakes/${r.id}`, removeLabel: 'common.delete',
    cols: [['nameEn', (r) => r.nameEn], ['nameKn', (r) => r.nameKn], ['scientificName', (r) => r.scientificName], ['venomous', (r) => (r.venomous ? '✓' : '✗')]],
    fields: [['nameEn', 'text', 1], ['nameKn', 'text', 1], ['scientificName', 'text', 1], ['venomous', 'checkbox'], ['description', 'textarea'], ['habitat', 'textarea'], ['districts', 'list'], ['imageUrl', 'text']],
    values: (r) => ({ ...r, districts: (r.districts || []).join(', ') }),
  },
  catchers: {
    list: '/api/admin/catchers', create: '/api/admin/catchers', update: (r) => `/api/admin/catchers/${r.id}`, remove: (r) => `/api/admin/catchers/${r.id}`, removeLabel: 'admin.hide',
    cols: [['name', (r) => r.name], ['phone', (r) => r.phone], ['district', (r) => r.district], ['active', (r) => (r.active ? '✓' : '✗')]],
    fields: [['name', 'text', 1], ['phone', 'text', 1], ['district', 'text', 1], ['lat', 'number', 1], ['lng', 'number', 1], ['active', 'checkbox']],
    values: (r) => r,
  },
  alerts: {
    list: '/api/admin/alerts',
    cols: [['status', (r) => r.status], ['patientType', (r) => r.patientType], ['hospital', (r) => r.hospitalName], ['created', (r) => time(r.createdAt)], ['sent', (r) => time(r.sentAt)], ['confirmed', (r) => time(r.confirmedAt)], ['arrived', (r) => time(r.arrivedAt)]],
  },
  rescues: {
    list: '/api/admin/rescues',
    cols: [['catcher', (r) => r.catcherName], ['notes', (r) => r.notes || '–'], ['created', (r) => time(r.createdAt)], ['status', (r) => r.status]],
    rowExtra: (r) => html`<select class="select select-pill" data-rescue="${r.id}">${['REQUESTED', 'ACCEPTED', 'DONE'].map((s) => html`<option ${s === r.status ? 'selected' : ''}>${s}</option>`)}</select>`,
  },
  photos: {
    list: '/api/admin/snake-photos',
    cols: [['species', (r) => r.species], ['source', (r) => r.source], ['verifiedBy', (r) => r.verifiedBy || '–'], ['created', (r) => time(r.createdAt)]],
    rowExtra: (r) => html`<button class="btn btn-sm btn-soft" data-act="verify" data-id="${r.id}">${icon('check', 14)} ${t('admin.verify')}</button>`,
    toolbar: html`<button class="btn btn-sm" data-act="export">${icon('download', 15)} ${t('admin.export')}</button>`,
  },
};

export default async function admin(el, ctx) {
  if (!tokens.get('admin')) return loginView(el, ctx);
  const S = { tab: 'hospitals', rows: [], hosp: {} };
  ctx.onLang = () => draw();

  async function load() {
    try {
      if (S.tab === 'staff') { const hs = await api('/api/admin/hospitals', A); S.hosp = Object.fromEntries(hs.map((h) => [h.id, h.name])); S.hospList = hs; }
      S.rows = await api(TABS[S.tab].list, A);
    } catch (e) { if (e.status === 401) { ctx.rerender(); return; } toast(errorText(e), 'err'); S.rows = []; }
    draw();
  }

  function draw() {
    const cfg = TABS[S.tab];
    el.innerHTML = toHtml(html`
      <header class="staff-head"><div class="container in">
        <span class="brand-mark">${icon('shield', 24)}</span><div><b style="font-size:18px;color:var(--green)">${t('admin.title')}</b><div class="muted xs">${ctx.config.name}</div></div>
        <div class="top-actions"><button class="lang-toggle" data-lang-toggle type="button"><span class="${t('lang.code') === 'en' ? 'on' : ''}">EN</span><span class="${t('lang.code') === 'kn' ? 'on' : ''}">ಕನ್ನಡ</span></button>
          <button class="btn btn-soft btn-sm" data-act="logout">${icon('logout', 15)} ${t('staff.logout')}</button></div></div></header>
      <div class="container page" style="padding-bottom:50px">
        <div class="tabs" role="tablist">${Object.keys(TABS).map((k) => html`<button class="btn btn-sm ${k === S.tab ? '' : 'btn-soft'}" role="tab" aria-selected="${String(k === S.tab)}" data-tab="${k}">${t(`admin.tab.${k}`)}</button>`)}</div>
        <div class="row between" style="margin:18px 0 12px;flex-wrap:wrap"><h2 style="font-size:22px">${t(`admin.tab.${S.tab}`)} <span class="muted small">(${S.rows.length})</span></h2>
          <div class="row-wrap">${cfg.toolbar || ''}${cfg.create ? html`<button class="btn btn-sm" data-act="add">${icon('plus', 15)} ${t('common.add')}</button>` : ''}</div></div>
        <div class="table-wrap"><table class="t"><thead><tr>${cfg.cols.map(([k]) => html`<th>${t(`admin.col.${k}`)}</th>`)}<th></th></tr></thead>
          <tbody>${S.rows.length ? S.rows.map((r, i) => html`<tr>${cfg.cols.map(([, f]) => html`<td>${f(r, S)}</td>`)}<td><div class="row-wrap" style="justify-content:flex-end;flex-wrap:nowrap">
            ${cfg.rowExtra ? cfg.rowExtra(r) : ''}${cfg.update ? html`<button class="icon-btn" data-act="edit" data-i="${i}" aria-label="${t('common.edit')}">${icon('edit', 15)}</button>` : ''}${cfg.remove ? html`<button class="icon-btn danger" data-act="del" data-i="${i}" aria-label="${t(cfg.removeLabel)}" title="${t(cfg.removeLabel)}">${icon('trash', 15)}</button>` : ''}</div></td></tr>`)
            : html`<tr><td colspan="${cfg.cols.length + 1}" class="center muted" style="padding:30px">${t('admin.empty')}</td></tr>`}</tbody></table></div>
      </div>`);
  }

  function field([k, type, req], v) {
    const label = `${t(`admin.f.${k}`)}${req ? ' *' : ''}`;
    const val = v == null ? '' : v;
    if (type === 'checkbox') return html`<label class="switch"><input type="checkbox" name="${k}" ${val === true || val === '' && k === 'active' ? 'checked' : ''}><span class="track"></span>${label}</label>`;
    if (type === 'textarea') return html`<div class="field"><label>${label}</label><textarea class="textarea" name="${k}" maxlength="2000">${val}</textarea></div>`;
    if (type === 'hospital') return html`<div class="field"><label>${label}</label><select class="select" name="${k}" required>${(S.hospList || []).map((h) => html`<option value="${h.id}" ${h.id === val ? 'selected' : ''}>${h.name}</option>`)}</select></div>`;
    return html`<div class="field"><label>${label}</label><input class="input" name="${k}" type="${type === 'number' ? 'number' : type === 'password' ? 'password' : 'text'}" ${type === 'number' ? 'step="any"' : ''} ${req ? 'required' : ''} value="${val}" autocomplete="off"></div>`;
  }

  function form(row) {
    const cfg = TABS[S.tab];
    const vals = row ? cfg.values(row) : { active: true };
    const m = openModal(html`<h3>${row ? t('common.edit') : t('common.add')}</h3><form class="stack" style="gap:12px" novalidate>${cfg.fields.map((f) => field(f, vals[f[0]]))}
      <p class="error-text hidden" data-err></p><div class="actions"><button type="button" class="btn btn-soft" data-close>${t('common.cancel')}</button><button class="btn" type="submit">${t('common.save')}</button></div></form>`);
    m.el.querySelector('form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target); const body = {}; const er = m.el.querySelector('[data-err]');
      for (const [k, type, req] of cfg.fields) {
        const raw = fd.get(k);
        if (type === 'checkbox') body[k] = e.target.elements[k].checked;
        else if (type === 'number') { if (raw === '' || raw == null) { if (req) { er.textContent = t('common.required'); er.classList.remove('hidden'); return; } } else body[k] = +raw; }
        else if (type === 'hospital') body[k] = +raw;
        else if (type === 'list') body[k] = String(raw || '').split(',').map((s) => s.trim()).filter(Boolean);
        else { const s = String(raw ?? '').trim(); if (req && !s) { er.textContent = t('common.required'); er.classList.remove('hidden'); return; } if (s || row) body[k] = s || undefined; }
      }
      try { await api(row ? cfg.update(row) : cfg.create, { method: row ? 'PUT' : 'POST', body, ...A }); m.close(); toast(t('common.saved'), 'ok'); load(); } catch (ex) { er.textContent = errorText(ex); er.classList.remove('hidden'); }
    });
  }

  el.addEventListener('click', async (e) => {
    const tab = e.target.closest('[data-tab]');
    if (tab) { S.tab = tab.dataset.tab; load(); return; }
    const b = e.target.closest('[data-act]'); if (!b) return;
    const cfg = TABS[S.tab]; const a = b.dataset.act;
    if (a === 'logout') { tokens.clear('admin'); ctx.rerender(); }
    else if (a === 'add') form(null);
    else if (a === 'edit') form(S.rows[+b.dataset.i]);
    else if (a === 'del') {
      const r = S.rows[+b.dataset.i];
      if (!(await confirmDialog(t(cfg.removeLabel), t('admin.confirmRemove'), t(cfg.removeLabel)))) return;
      try { await api(cfg.remove(r), { method: 'DELETE', ...A }); toast(t('common.saved'), 'ok'); load(); } catch (ex) { toast(errorText(ex), 'err'); }
    } else if (a === 'verify') { try { await api(`/api/admin/snake-photos/${b.dataset.id}/verify`, { method: 'PUT', body: {}, ...A }); toast(t('common.saved'), 'ok'); load(); } catch (ex) { toast(errorText(ex), 'err'); } }
    else if (a === 'export') { try { await download('/api/admin/snake-photos/export', 'snake-photos.csv', 'admin'); } catch (ex) { toast(errorText(ex), 'err'); } }
  });
  el.addEventListener('change', async (e) => {
    const sel = e.target.closest('[data-rescue]'); if (!sel) return;
    try { await api(`/api/admin/rescues/${sel.dataset.rescue}/status`, { method: 'PUT', body: { status: sel.value }, ...A }); toast(t('common.saved'), 'ok'); } catch (ex) { toast(errorText(ex), 'err'); load(); }
  });
  await load();
}

function loginView(el, ctx) {
  el.innerHTML = toHtml(html`
    <div style="min-height:80vh;display:grid;place-items:center;padding:20px 0"><div style="width:100%">
      <div class="auth-card" style="margin-top:0"><div class="mark">${icon('shield', 30)}</div><h1>${t('admin.signIn')}</h1><p class="sub">${t('admin.signInSub')}</p>
        <form id="f" class="stack" style="gap:14px" novalidate>
          <div class="field"><label for="u">${t('staff.username')}</label><div class="input-icon">${icon('user', 18)}<input class="input" id="u" autocomplete="username" required></div></div>
          <div class="field"><label for="p">${t('staff.password')}</label><div class="input-icon">${icon('shield', 18)}<input class="input" id="p" type="password" autocomplete="current-password" required></div></div>
          <p class="error-text hidden" id="err"></p><button class="btn btn-lg btn-block" type="submit">${t('staff.signInBtn')} ${icon('arrow', 18)}</button></form></div>
      <p class="center small" style="margin-top:16px"><a class="btn-link" href="#/">${icon('back', 14)} ${t('common.home')}</a></p></div></div>`);
  $('#f', el).addEventListener('submit', async (e) => {
    e.preventDefault();
    const err = $('#err', el); const b = e.target.querySelector('button'); b.disabled = true; err.classList.add('hidden');
    try { const r = await api('/api/admin/login', { method: 'POST', body: { username: $('#u', el).value.trim(), password: $('#p', el).value } }); tokens.set('admin', r.token); ctx.rerender(); } catch (ex) { err.textContent = errorText(ex); err.classList.remove('hidden'); b.disabled = false; }
  });
}
