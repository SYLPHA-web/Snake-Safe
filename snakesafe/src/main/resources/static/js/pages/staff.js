import { html, toHtml, $, hhmm } from '../util.js';
import { api, tokens } from '../api.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { stockChip, openModal, toast, errorText } from '../ui.js';

export const chrome = 'bare';
export const title = 'staff.title';

let audio = null;
function beep() {
  try {
    audio = audio || new (window.AudioContext || window.webkitAudioContext)();
    const o = audio.createOscillator(); const g = audio.createGain();
    o.connect(g); g.connect(audio.destination); o.frequency.value = 880; g.gain.setValueAtTime(0.18, audio.currentTime);
    g.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + 0.5); o.start(); o.stop(audio.currentTime + 0.5);
  } catch { /* sound is optional */ }
}

export default async function staff(el, ctx) {
  const A = { auth: 'staff' };
  if (!tokens.get('staff')) return loginView(el, ctx);

  let home;
  try { home = await api('/api/staff/home', A); } catch (e) { if (e.status === 401) return loginView(el, ctx); throw e; }
  const hospitalId = home.hospital.id;
  const S = { home, alerts: await api('/api/staff/alerts', A), fresh: new Set() };
  const noReplyMs = ctx.config.noReplyMinutes * 60000;
  let ws = null; let timer = null; let poll = null; let baseTitle = document.title; let seenIds = new Set(S.alerts.map((a) => a.id));
  ctx.onCleanup(() => { if (ws) { ws.onclose = null; ws.close(); } clearInterval(timer); clearInterval(poll); document.title = baseTitle; });
  ctx.onLang = () => draw();

  const active = () => S.alerts.filter((a) => a.status === 'WAITING' || a.status === 'CONFIRMED');
  const past = () => S.alerts.filter((a) => !['WAITING', 'CONFIRMED'].includes(a.status));

  function card(a) {
    const waiting = a.status === 'WAITING'; const ok = a.status === 'CONFIRMED';
    const who = [t(`flow.${a.patientType.toLowerCase()}`), a.patientName, a.patientAge != null ? t('profile.years', { n: a.patientAge }) : ''].filter(Boolean).join(' · ');
    return html`<article class="alert-card ${ok ? 'confirmed' : ''} ${waiting || ok ? '' : 'closed'} ${S.fresh.has(a.id) ? 'fresh' : ''}">
      <div class="row between" style="flex-wrap:wrap"><span class="chip ${waiting ? 'chip-amber' : ok ? 'chip-green' : ''}">${t(`staff.st.${a.status}`)}</span><span class="muted xs">${a.sentAt ? hhmm(a.sentAt) : ''}</span></div>
      <div><h3 style="font-size:20px">${who}</h3>
        <div class="muted small row" style="gap:6px;margin-top:6px">${icon('pin', 14)} ${a.distanceKm != null ? t('staff.away', { km: a.distanceKm, n: a.etaMinutes }) : ''}</div></div>
      ${waiting ? html`<div class="chip chip-amber" data-dl="${Date.parse(a.sentAt) + noReplyMs}" style="width:max-content"></div>
        <div class="row-wrap"><button class="btn" data-act="accept" data-id="${a.id}">${icon('check', 16)} ${t('staff.accept')}</button><button class="btn btn-danger-soft" data-act="decline" data-id="${a.id}">${icon('x', 16)} ${t('staff.decline')}</button></div>` : ''}
      ${ok ? html`<div class="row-wrap"><span class="chip chip-green" style="height:32px;font-size:13px">${icon('nav', 14)} ${t('staff.onTheWay')}</span><button class="btn btn-mint" data-act="arrived" data-id="${a.id}">${icon('check', 16)} ${t('staff.arrivedBtn')}</button></div>` : ''}
      ${a.declineReason ? html`<div class="muted small">${a.declineReason}</div>` : ''}
    </article>`;
  }

  function draw() {
    const h = S.home.hospital;
    el.innerHTML = toHtml(html`
      <header class="staff-head"><div class="container in">
        <span class="brand-mark">${icon('hospital', 24)}</span>
        <div><b style="font-size:18px;color:var(--green)">${h.name}</b><div class="muted xs">${t('staff.desk')}</div></div>
        <div class="top-actions"><span class="row small bold" style="gap:8px"><i class="dot"></i>${S.home.staffName}</span>
          <button class="lang-toggle" data-lang-toggle type="button"><span class="${t('lang.code') === 'en' ? 'on' : ''}">EN</span><span class="${t('lang.code') === 'kn' ? 'on' : ''}">ಕನ್ನಡ</span></button>
          <button class="btn btn-soft btn-sm" data-act="logout">${icon('logout', 15)} ${t('staff.logout')}</button></div></div></header>
      <div class="container page" style="padding-bottom:50px">
        <div class="split">
          <section class="card">
            <div class="row between" style="flex-wrap:wrap"><div class="row" style="gap:12px"><span class="chip chip-dark" style="width:44px;height:44px;padding:0;justify-content:center;border-radius:14px">${icon('drop', 22)}</span><div><h2 style="font-size:20px">${t('staff.stockTitle')}</h2><div class="muted xs">${t('staff.stockSub', { h: ctx.config.verifiedHours })}</div></div></div>${stockChip(h.stock)}</div>
            <div class="vial-big" style="margin-top:16px"><div class="num">${h.stock.vials}<small>${t('hosp.vialsInStock')}</small></div></div>
            <form id="sf" class="stock-edit" style="margin-top:16px" novalidate>
              <button class="icon-btn" type="button" data-act="dec" aria-label="-1">${icon('minus', 16)}</button>
              <input class="input" id="vials" type="number" min="0" max="10000" inputmode="numeric" value="${h.stock.vials}" aria-label="${t('staff.vials')}">
              <button class="icon-btn" type="button" data-act="inc" aria-label="+1">${icon('plus', 16)}</button>
              <button class="btn" type="submit">${icon('check', 16)} ${t('staff.update')}</button>
            </form>
            <p class="help" style="margin-top:10px">${t('staff.updateHelp')}</p>
          </section>
          <section>
            <div class="row between" style="margin-bottom:12px"><h2 style="font-size:22px">${icon('bell', 20)} ${t('staff.incoming')}</h2><span class="chip ${ws ? 'chip-green' : 'chip-amber'}">${ws ? t('staff.live') : t('staff.polling')}</span></div>
            <div class="stack" id="active">${active().length ? active().map(card) : html`<div class="card empty">${icon('bell', 40)}<h3>${t('staff.noneTitle')}</h3><p>${t('staff.noneText')}</p></div>`}</div>
            ${past().length ? html`<h3 style="margin:26px 0 12px;font-size:16px" class="muted">${t('staff.recent')}</h3><div class="stack">${past().slice(0, 8).map(card)}</div>` : ''}
          </section>
        </div>
      </div>`);
    tickDeadlines();
  }

  function tickDeadlines() {
    el.querySelectorAll('[data-dl]').forEach((n) => {
      const ms = Math.max(0, +n.dataset.dl - Date.now());
      n.textContent = t('staff.replyIn', { time: `${Math.floor(ms / 60000)}:${String(Math.floor(ms / 1000) % 60).padStart(2, '0')}` });
    });
  }
  timer = setInterval(tickDeadlines, 1000);

  const upsert = (a) => {
    const i = S.alerts.findIndex((x) => x.id === a.id);
    if (i >= 0) S.alerts[i] = a; else S.alerts.unshift(a);
    if (!seenIds.has(a.id) && a.status === 'WAITING') { seenIds.add(a.id); S.fresh.add(a.id); beep(); document.title = `(!) ${t('staff.newAlert')}`; setTimeout(() => { document.title = baseTitle; S.fresh.delete(a.id); }, 8000); }
  };

  function connect() {
    try {
      ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/hospital/${hospitalId}?token=${encodeURIComponent(tokens.get('staff'))}`);
      ws.onmessage = (ev) => {
        try {
          const m = JSON.parse(ev.data);
          if (m.type === 'snapshot') { S.alerts = m.alerts; seenIds = new Set(S.alerts.map((a) => a.id)); } else if (m.alert) upsert(m.alert);
          draw();
        } catch { /* ignore */ }
      };
      ws.onopen = () => draw();
      ws.onclose = () => { ws = null; };
    } catch { ws = null; }
  }
  connect();
  poll = setInterval(async () => {
    if (!ctx.alive()) return;
    try { const list = await api('/api/staff/alerts', A); list.forEach(upsert); S.alerts = list; S.home = await api('/api/staff/home', A); draw(); } catch (e) { if (e.status === 401) ctx.rerender(); }
  }, 5000);  // WebSocket pushes instantly; this also keeps the screen right if sockets are blocked (e.g. behind a proxy)

  async function run(path, body) {
    try { upsert(await api(path, { method: 'POST', body, ...A })); draw(); } catch (e) { toast(errorText(e), 'err'); const list = await api('/api/staff/alerts', A).catch(() => null); if (list) { S.alerts = list; draw(); } }
  }

  el.addEventListener('click', (e) => {
    const b = e.target.closest('[data-act]'); if (!b) return;
    const a = b.dataset.act; const id = b.dataset.id;
    if (a === 'logout') { tokens.clear('staff'); ctx.rerender(); }
    else if (a === 'inc' || a === 'dec') { const i = $('#vials', el); i.value = Math.max(0, (+i.value || 0) + (a === 'inc' ? 1 : -1)); }
    else if (a === 'accept') run(`/api/staff/alerts/${id}/accept`);
    else if (a === 'arrived') run(`/api/staff/alerts/${id}/arrived`);
    else if (a === 'decline') {
      const m = openModal(html`<h3>${t('staff.declineTitle')}</h3><div class="field"><label>${t('staff.reason')}</label><input class="input" id="why" maxlength="200" placeholder="${t('staff.reasonHint')}"></div>
        <div class="actions"><button class="btn btn-soft" data-close>${t('common.cancel')}</button><button class="btn btn-red" data-ok>${t('staff.decline')}</button></div>`);
      m.el.querySelector('[data-ok]').addEventListener('click', () => { const reason = m.el.querySelector('#why').value.trim(); m.close(); run(`/api/staff/alerts/${id}/decline`, { reason: reason || undefined }); });
    }
  });
  el.addEventListener('submit', async (e) => {
    if (e.target.id !== 'sf') return;
    e.preventDefault();
    const v = +$('#vials', el).value;
    if (!Number.isInteger(v) || v < 0 || v > 10000) { toast(t('staff.vialsBad'), 'err'); return; }
    try { await api('/api/staff/stock', { method: 'PUT', body: { vials: v }, ...A }); S.home = await api('/api/staff/home', A); toast(t('staff.updated'), 'ok'); draw(); } catch (ex) { toast(errorText(ex), 'err'); }
  });
  draw();
}

function loginView(el, ctx) {
  el.innerHTML = toHtml(html`
    <div style="min-height:80vh;display:grid;place-items:center;padding:20px 0">
      <div style="width:100%">
        <div class="auth-card" style="margin-top:0" data-reveal="zoom">
          <div class="mark">${icon('hospital', 30)}</div>
          <div class="center eyebrow" style="color:var(--green)">${t('staff.portal')}</div>
          <h1>${t('staff.signIn')}</h1><p class="sub">${t('staff.signInSub')}</p>
          <form id="f" class="stack" style="gap:14px" novalidate>
            <div class="field"><label for="u">${t('staff.username')}</label><div class="input-icon">${icon('user', 18)}<input class="input" id="u" autocomplete="username" required></div></div>
            <div class="field"><label for="p">${t('staff.password')}</label><div class="input-icon">${icon('shield', 18)}<input class="input" id="p" type="password" autocomplete="current-password" required></div></div>
            <p class="error-text hidden" id="err"></p>
            <button class="btn btn-lg btn-block" type="submit">${t('staff.signInBtn')} ${icon('arrow', 18)}</button>
          </form>
          ${ctx.config.otpMode === 'DEMO' ? html`<p class="center muted xs">${t('staff.demoHint')}</p>` : ''}
        </div>
        <p class="center small" style="margin-top:16px"><a class="btn-link" href="#/">${icon('back', 14)} ${t('common.home')}</a></p>
      </div></div>`);
  $('#f', el).addEventListener('submit', async (e) => {
    e.preventDefault();
    const err = $('#err', el); const b = e.target.querySelector('button'); b.disabled = true; err.classList.add('hidden');
    try {
      const r = await api('/api/staff/login', { method: 'POST', body: { username: $('#u', el).value.trim(), password: $('#p', el).value } });
      tokens.set('staff', r.token); ctx.rerender();
    } catch (ex) { err.textContent = errorText(ex); err.classList.remove('hidden'); b.disabled = false; }
  });
}
