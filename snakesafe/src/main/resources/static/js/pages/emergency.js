import { html, toHtml, $, session } from '../util.js';
import { api, tokens } from '../api.js';
import { icon } from '../icons.js';
import { t, getLang, pick } from '../i18n.js';
import { setFlowStep } from '../nav.js';
import { stockChip, stockText, mapSvg, telLink, toast, errorText, confirmDialog } from '../ui.js';
import { getLocation } from '../geo.js';

export const chrome = 'flow';
export const title = 'nav.emergency';

const SS_ALERT = 'ss-alert';
const RANK = { PENDING_CANCEL: 0, WAITING: 1, CONFIRMED: 2, DECLINED: 2, NO_REPLY: 2, CANCELLED: 2, ARRIVED: 3 };
const STEP_OF = { who: 1, locating: 2, best: 3, none: 3, nolocation: 3, error: 3, countdown: 4, waiting: 4, confirmed: 4, declined: 4, noreply: 4, cancelled: 4, arrived: 4 };
const STATE_OF = { PENDING_CANCEL: 'countdown', WAITING: 'waiting', CONFIRMED: 'confirmed', DECLINED: 'declined', NO_REPLY: 'noreply', CANCELLED: 'cancelled', ARRIVED: 'arrived' };
const RING_LEN = 2 * Math.PI * 44;

export default async function emergency(el, ctx) {
  const cfg = ctx.config;
  const S = {
    step: 'who',
    who: { type: 'SELF', name: '', age: '' },
    loc: null, best: null, hospital: null, others: [], all: [], alert: null, aid: null,
    presetId: ctx.query.get('hospital'), showOthers: false, errMsg: '', locErr: '',
    cdEnd: 0, cdTotal: cfg.cancelWindowSeconds, sending: false,
  };
  let ws = null; let poll = null; let tick = null;

  const stopLive = () => { if (ws) { ws.onclose = null; try { ws.close(); } catch { /* closed */ } ws = null; } clearInterval(poll); poll = null; };
  ctx.onCleanup(() => { stopLive(); clearInterval(tick); document.removeEventListener('click', onExit, true); });
  ctx.onLang = () => draw(false);

  // Leaving the screen while an alert is live: ask first (it keeps running either way)
  function onExit(e) {
    const a = e.target.closest && e.target.closest('[data-exit-flow]');
    if (!a || !(S.step === 'countdown' || S.step === 'waiting')) return;
    e.preventDefault();
    confirmDialog(t('flow.leaveTitle'), t('flow.leaveText'), t('flow.leave')).then((ok) => { if (ok) ctx.navigate('/'); });
  }
  document.addEventListener('click', onExit, true);

  api('/api/first-aid').then((d) => { S.aid = d; if (['best', 'confirmed', 'waiting'].includes(S.step)) draw(false); }).catch(() => {});

  // ------------------------------------------------------------ data steps
  async function locate() {
    S.step = 'locating'; draw();
    try { S.loc = await getLocation(); } catch (e) {
      S.locErr = e.code; S.step = 'nolocation';
      try { S.all = await api('/api/hospitals'); } catch { S.all = []; }
      draw(); return;
    }
    try {
      const best = await api(`/api/emergency/best-hospital?lat=${S.loc.lat}&lng=${S.loc.lng}`);
      S.best = best;
      if (S.presetId) {
        const preset = await api(`/api/hospitals/${S.presetId}?lat=${S.loc.lat}&lng=${S.loc.lng}`);
        S.hospital = preset;
        S.others = [...(best.found ? [best.hospital] : []), ...best.others].filter((h) => h.id !== preset.id);
        S.step = preset.stock.status === 'NONE' ? 'none' : 'best';
      } else if (best.found) {
        S.hospital = best.hospital; S.others = best.others; S.step = 'best';
      } else {
        S.hospital = null; S.others = best.others; S.step = 'none';
      }
    } catch (e) { S.errMsg = errorText(e); S.step = 'error'; }
    draw();
  }

  async function send(hospitalId) {
    if (S.sending) return;
    S.sending = true;
    const lat = S.loc?.lat ?? S.alert?.lat; const lng = S.loc?.lng ?? S.alert?.lng;
    try {
      const body = { patientType: S.who.type, lat, lng, hospitalId };
      if (S.who.name) body.patientName = S.who.name;
      if (S.who.age !== '' && !Number.isNaN(+S.who.age)) body.patientAge = +S.who.age;
      const a = await api('/api/alerts', { method: 'POST', body, auth: tokens.get('user') ? 'user' : undefined });
      session.set(SS_ALERT, a.id);
      history.replaceState(null, '', `#/emergency/alert/${a.id}`);
      S.alert = null; // fresh alert: accept its first status
      applyAlert(a);
      connect(a.id);
    } catch (e) { toast(errorText(e), 'err'); if (e.code === 'NO_STOCK') locate(); } finally { S.sending = false; }
  }

  function applyAlert(a) {
    const prev = S.alert;
    if (prev && prev.id === a.id && RANK[a.status] < RANK[prev.status]) return; // stale update
    S.alert = a; S.hospital = a.hospital;
    S.loc = S.loc || { lat: a.lat, lng: a.lng };
    S.who = { type: a.patientType, name: a.patientName || '', age: a.patientAge ?? '' };
    const changed = !prev || prev.id !== a.id || prev.status !== a.status;
    S.step = STATE_OF[a.status];
    if (a.status === 'PENDING_CANCEL' && changed) { S.cdTotal = Math.max(1, cfg.cancelWindowSeconds); S.cdEnd = performance.now() + a.cancelSecondsLeft * 1000; }
    if (['CANCELLED', 'ARRIVED', 'DECLINED', 'NO_REPLY'].includes(a.status)) { session.del(SS_ALERT); stopLive(); }
    if (changed) draw(); else drawLive();
  }

  function connect(id) {
    stopLive();
    try {
      ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/alerts/${id}`);
      ws.onmessage = (ev) => { try { const m = JSON.parse(ev.data); if (m.type === 'alert' && ctx.alive()) applyAlert(m.alert); } catch { /* ignore bad frame */ } };
      ws.onclose = () => { ws = null; };
    } catch { ws = null; }
    poll = setInterval(async () => {
      if (!ctx.alive()) return;
      try { applyAlert(await api(`/api/alerts/${id}`)); } catch (e) { if (e.status === 404) { session.del(SS_ALERT); stopLive(); } }
    }, 3000);
  }

  async function act(path) {
    try { applyAlert(await api(`/api/alerts/${S.alert.id}/${path}`, { method: 'POST' })); } catch (e) { toast(errorText(e), 'err'); }
  }

  // ------------------------------------------------------------ views
  const aidSteps = () => {
    const ids = ['limb-still', 'remove-tight', 'call-108', 'note-time'];
    const steps = S.aid?.steps?.filter((s) => ids.includes(s.id)) || [];
    return steps.length ? html`<ul class="num-list">${steps.map((s, i) => html`<li><i>${i + 1}</i><div><b>${pick(s).title}</b><div class="muted">${pick(s).text}</div></div></li>`)}</ul>`
      : html`<ul class="num-list"><li><i>1</i><div>${t('flow.aid1')}</div></li><li><i>2</i><div>${t('flow.aid2')}</div></li><li><i>3</i><div>${t('flow.aid3')}</div></li></ul>`;
  };

  const callRow = () => html`<a class="btn btn-red btn-block" href="${telLink(cfg.emergencyNumber)}">${icon('phone', 18)} ${t('flow.call', { n: cfg.emergencyNumber })}</a>`;

  function otherItem(h, label) {
    return html`<div class="alt-item">
      <div class="info"><h4>${h.name}</h4><div class="muted xs">${h.distanceKm != null ? `${t('hosp.kmAway', { n: h.distanceKm })} · ${t('hosp.eta', { n: h.etaMinutes })}` : h.district}</div><div style="margin-top:6px">${stockChip(h.stock)}</div></div>
      <div class="row-wrap">
        <a class="btn btn-sm btn-soft" href="${telLink(h.phone)}">${icon('phone', 14)}</a>
        <button class="btn btn-sm" data-act="${label}" data-id="${h.id}">${label === 'pick' ? t('flow.choose') : t('flow.alertThis')}</button>
      </div></div>`;
  }

  function viewWho() {
    return html`<div class="flow-wrap">
      <div data-reveal><span class="chip chip-red">${icon('sos', 13)} ${t('flow.chip')}</span>
        <h1 class="flow-title" style="margin-top:12px">${t('flow.whoTitle')}</h1><p class="muted" style="margin-top:6px">${t('flow.whoSub')}</p></div>
      <div class="who-grid" data-stagger>
        ${[['SELF', 'user', 'flow.self', 'flow.selfSub'], ['OTHER', 'users', 'flow.other', 'flow.otherSub'], ['CHILD', 'child', 'flow.child', 'flow.childSub']].map(([v, ic, k, sk]) => html`
          <button class="who-opt ${S.who.type === v ? 'on' : ''}" data-act="who" data-v="${v}" aria-pressed="${String(S.who.type === v)}"><span class="ico">${icon(ic, 28)}</span><span>${t(k)}<br><small>${t(sk)}</small></span></button>`)}
      </div>
      <div class="card" style="margin-top:18px" data-reveal>
        <div class="eyebrow" style="margin-bottom:10px">${t('flow.details')}</div>
        <div class="grid-2" style="gap:14px">
          <div class="field"><label for="pname">${t('flow.name')}</label><input class="input" id="pname" maxlength="100" value="${S.who.name}" autocomplete="off"></div>
          <div class="field"><label for="page">${t('flow.age')}</label><input class="input" id="page" type="number" min="0" max="120" inputmode="numeric" value="${S.who.age}"></div>
        </div>
      </div>
      <div class="stack" style="margin-top:20px" data-reveal>
        <button class="btn btn-red btn-lg btn-block" data-act="go">${t('flow.findHospital')} ${icon('arrow', 18)}</button>
        <a class="btn btn-soft btn-lg btn-block" href="${telLink(cfg.emergencyNumber)}">${icon('phone', 18)} ${t('flow.call', { n: cfg.emergencyNumber })}</a>
        <p class="center muted small">${t('common.reminder')}</p>
      </div></div>`;
  }

  const viewLocating = () => html`<div class="flow-wrap"><div class="card locate">
    <div class="pulse">${icon('locate', 40)}</div><h2>${t('flow.locating')}</h2><p class="muted">${t('flow.locatingSub')}</p>
    <a class="btn btn-soft" href="${telLink(cfg.emergencyNumber)}">${icon('phone', 16)} ${t('flow.call', { n: cfg.emergencyNumber })}</a></div></div>`;

  function viewBest() {
    const h = S.hospital; const s = h.stock;
    const alts = S.others.slice(0, 3);
    return html`<div class="flow-wrap wide">
      <div class="banner-green" style="padding:14px 20px;margin-bottom:20px" data-reveal>${icon('shield', 22)}<b style="font-size:13px;letter-spacing:.05em;text-transform:uppercase">${S.presetId || S.chosen ? t('flow.chosenBanner') : t('flow.bestBanner')}</b></div>
      <div class="split">
        <div class="stack">
          <section class="card" data-reveal>
            <div class="row-wrap"><span class="chip chip-dark">${S.presetId || S.chosen ? t('flow.yourChoice') : t('flow.nearestVerified')}</span>${stockChip(s)}</div>
            <h1 class="flow-title" style="margin-top:14px">${h.name}</h1>
            <p class="muted row" style="gap:6px;margin-top:6px">${icon('pin', 15)} ${h.address}</p>
            <div class="tiles" style="margin-top:18px">
              <div class="tile"><small>${t('flow.tileDistance')}</small><b>${h.etaMinutes != null ? t('hosp.eta', { n: h.etaMinutes }) : '–'}</b><span>${h.distanceKm != null ? t('hosp.kmAway', { n: h.distanceKm }) : ''}</span></div>
              <div class="tile"><small>${t('flow.tileStock')}</small><b>${t('hosp.vials', { n: s.vials })}</b><span>${stockText(s)}</span></div>
              <div class="tile"><small>${t('flow.tilePhone')}</small><b style="font-size:17px;padding-top:4px">${h.phone}</b><span>${t('flow.tilePhoneSub')}</span></div>
            </div>
            <div class="card-flat" style="margin-top:16px;display:flex;gap:12px;font-size:13.5px">${icon('info', 20)}<p>${s.status === 'UNVERIFIED' ? t('flow.unverifiedNote') + ' ' : ''}${t('flow.sendNote', { s: cfg.cancelWindowSeconds })}</p></div>
            <button class="btn btn-lg btn-block" style="margin-top:16px" data-act="send" data-id="${h.id}" ${S.sending ? 'disabled' : ''}>${icon('send', 18)} ${t('flow.send')}</button>
            <div class="grid-2" style="margin-top:10px;gap:10px">
              <a class="btn btn-soft" href="${h.mapsUrl}" target="_blank" rel="noopener">${icon('nav', 16)} ${t('common.directions')}</a>
              <a class="btn btn-soft" href="${telLink(h.phone)}">${icon('phone', 16)} ${t('flow.callHospital')}</a>
            </div>
            ${S.others.length ? html`<button class="btn-link" style="margin-top:12px" data-act="others">${icon('list', 16)} ${t('flow.another', { n: S.others.length })}</button>` : ''}
            ${S.showOthers ? html`<div class="tel-list" style="margin-top:12px">${S.others.map((o) => otherItem(o, 'pick'))}</div>` : ''}
          </section>
          <section class="map-card" data-reveal>${mapSvg([h, ...alts], S.loc, { w: 640, h: 260 })}</section>
        </div>
        <div class="stack">
          <section class="card" data-reveal="right"><div class="row" style="gap:10px;margin-bottom:14px"><span class="chip chip-red" style="width:34px;height:34px;padding:0;justify-content:center">${icon('kit', 18)}</span><h3 style="font-size:18px">${t('flow.whileTravel')}</h3></div>${aidSteps()}</section>
          ${alts.length ? html`<section class="card" data-reveal="right"><h3 style="font-size:18px;margin-bottom:12px">${t('flow.altTitle')}</h3><div class="tel-list">${alts.map((o) => otherItem(o, 'pick'))}</div></section>` : ''}
          <div class="banner-green" data-reveal="right" style="padding:20px"><div class="txt"><h3 style="font-size:16px">${t('home.helpTitle')}</h3><p>${t('home.helpText')}</p></div><a class="btn btn-white btn-sm" href="${telLink(cfg.poisonHelpline)}">${cfg.poisonHelpline}</a></div>
        </div>
      </div></div>`;
  }

  const viewNone = () => html`<div class="flow-wrap">
    <div class="card" data-reveal><div class="big-state"><div class="ico ico-amber">${icon('warn', 40)}</div><h2>${S.hospital ? t('flow.noStockTitle') : t('flow.noVerifiedTitle')}</h2>
      <p class="muted" style="max-width:480px">${S.hospital ? t('flow.noStockText') : t('flow.noVerifiedText', { n: cfg.emergencyNumber })}</p></div>
      <div class="stack" style="margin-top:18px">${callRow()}</div></div>
    <h3 style="margin:26px 0 12px" data-reveal>${t('flow.otherOptions')}</h3>
    <div class="tel-list" data-stagger>${S.others.length ? S.others.map((o) => otherItem(o, 'pick')) : html`<div class="card empty">${t('flow.noOthers')}</div>`}</div></div>`;

  const viewNoLoc = () => html`<div class="flow-wrap">
    <div class="card" data-reveal><div class="big-state"><div class="ico ico-amber">${icon('locate', 40)}</div><h2>${t('flow.locOffTitle')}</h2>
      <p class="muted" style="max-width:480px">${S.locErr === 'denied' ? t('flow.locDenied') : t('flow.locOffText')}</p></div>
      <div class="stack" style="margin-top:18px"><button class="btn btn-lg btn-block" data-act="retryLoc">${icon('refresh', 18)} ${t('flow.tryAgain')}</button>${callRow()}</div></div>
    <h3 style="margin:26px 0 12px" data-reveal>${t('flow.callAhead')}</h3>
    <div class="tel-list" data-stagger>${S.all.filter((h) => h.stock.status !== 'NONE').map((h) => html`<div class="alt-item"><div class="info"><h4>${h.name}</h4><div class="muted xs">${h.district}</div><div style="margin-top:6px">${stockChip(h.stock)}</div></div>
      <div class="row-wrap"><a class="btn btn-sm" href="${telLink(h.phone)}">${icon('phone', 14)} ${t('common.call')}</a><a class="btn btn-sm btn-soft" href="${h.mapsUrl}" target="_blank" rel="noopener">${icon('nav', 14)}</a></div></div>`)}</div></div>`;

  const viewError = () => html`<div class="flow-wrap"><div class="card"><div class="big-state"><div class="ico ico-red">${icon('warn', 40)}</div><h2>${t('err.title')}</h2><p class="muted">${S.errMsg}</p></div>
    <div class="stack" style="margin-top:18px"><button class="btn btn-lg btn-block" data-act="retryLoc">${icon('refresh', 18)} ${t('flow.tryAgain')}</button>${callRow()}</div></div></div>`;

  const hospitalStrip = () => { const h = S.hospital; return html`<div class="card-flat row between" style="flex-wrap:wrap;margin-top:18px"><div><b>${h.name}</b><div class="muted xs">${h.address}</div></div>
    <div class="row-wrap"><a class="btn btn-sm btn-soft" href="${telLink(h.phone)}">${icon('phone', 14)} ${t('flow.callHospital')}</a><a class="btn btn-sm btn-soft" href="${h.mapsUrl}" target="_blank" rel="noopener">${icon('nav', 14)} ${t('common.directions')}</a></div></div>`; };

  function viewCountdown() {
    const a = S.alert;
    return html`<div class="flow-wrap"><div class="card" data-reveal>
      <div class="big-state">
        <div class="countdown"><svg viewBox="0 0 100 100"><circle class="track" cx="50" cy="50" r="44"/><circle class="bar" id="cd-ring" cx="50" cy="50" r="44" stroke-dasharray="${RING_LEN}" stroke-dashoffset="0"/></svg>
          <div class="mid"><b id="cd-num">${a.cancelSecondsLeft}</b><small>${t('flow.seconds')}</small></div></div>
        <h2>${t('flow.cdTitle', { name: a.hospital.name })}</h2><p class="muted" style="max-width:440px">${t('flow.cdText')}</p>
      </div>
      <div class="stack" style="margin-top:20px"><button class="btn btn-soft btn-lg btn-block" data-act="cancel">${icon('x', 18)} ${t('flow.cancel')}</button>${callRow()}</div></div></div>`;
  }

  function viewWaiting() {
    return html`<div class="flow-wrap"><div class="card" data-reveal>
      <div class="big-state"><div class="pulse">${icon('bell', 38)}</div><h2>${t('alert.waiting')}</h2>
        <p class="muted">${t('flow.waitSub', { name: S.hospital.name })}</p><span class="chip chip-amber" id="dl" style="height:32px;font-size:13px"></span></div>
      ${hospitalStrip()}
      <div class="stack" style="margin-top:18px">${callRow()}<button class="btn btn-ghost btn-block" data-act="cancelWait">${icon('x', 16)} ${t('flow.cancelAlert')}</button></div></div></div>`;
  }

  function viewConfirmed() {
    const a = S.alert; const h = S.hospital;
    return html`<div class="flow-wrap wide"><div class="split"><div class="stack">
      <section class="card" data-reveal>
        <div class="big-state"><div class="ico ico-green">${icon('check', 46)}</div><h2>${t('alert.confirmed', { time: a.confirmedAtLabel })}</h2>
          <p class="muted">${t('flow.confirmedSub', { name: h.name })}</p></div>
        <div class="tiles" style="margin-top:18px"><div class="tile"><small>${t('flow.tileDistance')}</small><b>${h.etaMinutes != null ? t('hosp.eta', { n: h.etaMinutes }) : '–'}</b><span>${h.distanceKm != null ? t('hosp.kmAway', { n: h.distanceKm }) : ''}</span></div>
          <div class="tile"><small>${t('flow.tileStock')}</small><b>${t('hosp.vials', { n: h.stock.vials })}</b><span>${stockText(h.stock)}</span></div>
          <div class="tile"><small>${t('flow.tilePhone')}</small><b style="font-size:17px;padding-top:4px">${h.phone}</b><span>${t('flow.tilePhoneSub')}</span></div></div>
        <div class="stack" style="margin-top:18px">
          <a class="btn btn-lg btn-block" href="${telLink(h.phone)}">${icon('phone', 18)} ${t('flow.callHospital')}</a>
          <a class="btn btn-soft btn-lg btn-block" href="${h.mapsUrl}" target="_blank" rel="noopener">${icon('nav', 18)} ${t('flow.openDirections')}</a>
          <button class="btn btn-mint btn-lg btn-block" data-act="arrived">${icon('check', 18)} ${t('flow.reached')}</button>
        </div></section></div>
      <div class="stack"><section class="card" data-reveal="right"><h3 style="font-size:18px;margin-bottom:12px">${t('flow.whileTravel')}</h3>${aidSteps()}</section>${callRow()}
        <section class="map-card">${mapSvg([h], S.loc, { w: 520, h: 240 })}</section></div></div></div>`;
  }

  function viewTryOthers(kind) {
    const a = S.alert; const alts = a.alternatives || [];
    const declined = kind === 'declined';
    return html`<div class="flow-wrap">
      <div class="card" data-reveal><div class="big-state"><div class="ico ${declined ? 'ico-red' : 'ico-amber'}">${icon(declined ? 'xcircle' : 'clock', 42)}</div>
        <h2>${declined ? t('alert.declined') : t('alert.noReply')}</h2>
        ${declined && a.declineReason ? html`<span class="chip chip-outline">${a.declineReason}</span>` : ''}
        <p class="muted" style="max-width:460px">${t('flow.tryOthersText', { n: cfg.emergencyNumber })}</p></div>
        <div class="stack" style="margin-top:18px">${callRow()}</div></div>
      <h3 style="margin:26px 0 12px" data-reveal>${t('flow.otherOptions')}</h3>
      <div class="tel-list" data-stagger>${alts.length ? alts.map((o) => otherItem(o, 'resend')) : html`<div class="card empty">${t('flow.noOthers')}</div>`}</div></div>`;
  }

  const viewCancelled = () => html`<div class="flow-wrap"><div class="card" data-reveal><div class="big-state"><div class="ico ico-blue">${icon('xcircle', 42)}</div><h2>${t('alert.cancelled')}</h2><p class="muted">${t('flow.cancelledSub')}</p></div>
    <div class="stack" style="margin-top:18px"><button class="btn btn-lg btn-block" data-act="restart">${icon('refresh', 18)} ${t('flow.startAgain')}</button>${callRow()}<a class="btn btn-soft btn-lg btn-block" href="#/">${icon('home', 18)} ${t('common.home')}</a></div></div></div>`;

  const viewArrived = () => html`<div class="flow-wrap"><div class="card" data-reveal><div class="big-state"><div class="ico ico-green">${icon('check', 46)}</div><h2>${t('alert.arrived')}</h2><p class="muted">${t('flow.arrivedSub')}</p></div>
    <div class="stack" style="margin-top:18px"><a class="btn btn-lg btn-block" href="#/">${icon('home', 18)} ${t('common.home')}</a><a class="btn btn-soft btn-lg btn-block" href="#/first-aid">${icon('kit', 18)} ${t('nav.firstAid')}</a></div></div></div>`;

  const VIEWS = { who: viewWho, locating: viewLocating, best: viewBest, none: viewNone, nolocation: viewNoLoc, error: viewError, countdown: viewCountdown, waiting: viewWaiting, confirmed: viewConfirmed, declined: () => viewTryOthers('declined'), noreply: () => viewTryOthers('noreply'), cancelled: viewCancelled, arrived: viewArrived };

  function draw(animate = true) {
    const scroll = window.scrollY;
    el.innerHTML = toHtml(VIEWS[S.step]());
    setFlowStep(STEP_OF[S.step]);
    if (animate) { window.scrollTo({ top: 0, behavior: 'instant' }); ctx.motion(el); } else window.scrollTo({ top: scroll, behavior: 'instant' });
    drawLive();
  }

  // numbers that change every second without redrawing the whole screen
  function drawLive() {
    if (S.step === 'countdown' && S.alert) {
      const left = Math.max(0, Math.ceil((S.cdEnd - performance.now()) / 1000));
      const n = $('#cd-num', el); const r = $('#cd-ring', el);
      if (n) n.textContent = left;
      if (r) r.style.strokeDashoffset = RING_LEN * (1 - left / S.cdTotal);
    }
    if (S.step === 'waiting' && S.alert?.replyDeadline) {
      const dl = $('#dl', el);
      const ms = Math.max(0, Date.parse(S.alert.replyDeadline) - Date.now());
      if (dl) dl.textContent = t('flow.replyIn', { time: `${Math.floor(ms / 60000)}:${String(Math.floor(ms / 1000) % 60).padStart(2, '0')}` });
    }
  }
  tick = setInterval(drawLive, 250);

  // ------------------------------------------------------------ events
  el.addEventListener('click', async (e) => {
    const b = e.target.closest('[data-act]');
    if (!b) return;
    const act0 = b.dataset.act;
    if (act0 === 'who') {
      S.who.type = b.dataset.v;
      el.querySelectorAll('.who-opt').forEach((x) => { const on = x === b; x.classList.toggle('on', on); x.setAttribute('aria-pressed', on); });
    } else if (act0 === 'go') {
      S.who.name = ($('#pname', el)?.value || '').trim(); S.who.age = ($('#page', el)?.value || '').trim();
      if (S.who.age !== '' && (Number.isNaN(+S.who.age) || +S.who.age < 0 || +S.who.age > 120)) { toast(t('flow.ageBad'), 'err'); return; }
      locate();
    } else if (act0 === 'retryLoc') locate();
    else if (act0 === 'others') { S.showOthers = !S.showOthers; draw(false); }
    else if (act0 === 'pick') {
      const h = [...S.others, S.hospital].find((x) => x && x.id === +b.dataset.id);
      if (!h) return;
      S.others = [...S.others, S.hospital].filter((x) => x && x.id !== h.id);
      S.hospital = h; S.chosen = true; S.showOthers = false; S.step = 'best'; draw();
    } else if (act0 === 'send') send(+b.dataset.id);
    else if (act0 === 'resend') { S.chosen = true; send(+b.dataset.id); }
    else if (act0 === 'cancel') act('cancel');
    else if (act0 === 'cancelWait') { if (await confirmDialog(t('flow.cancelAlert'), t('flow.cancelAsk'), t('flow.cancelYes'))) act('cancel'); }
    else if (act0 === 'arrived') act('arrived');
    else if (act0 === 'restart') { S.alert = null; S.hospital = null; S.step = 'who'; history.replaceState(null, '', '#/emergency'); draw(); }
  });

  // ------------------------------------------------------------ start
  const resumeId = ctx.params[0] || null;
  if (resumeId) {
    try {
      const a = await api(`/api/alerts/${resumeId}`);
      applyAlert(a);
      if (!['CANCELLED', 'ARRIVED', 'DECLINED', 'NO_REPLY'].includes(a.status)) { session.set(SS_ALERT, a.id); connect(a.id); }
      return;
    } catch { session.del(SS_ALERT); history.replaceState(null, '', '#/emergency'); }
  }
  draw();
}
