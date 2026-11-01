import { html, toHtml, debounce, $, session } from '../util.js';
import { api } from '../api.js';
import { icon } from '../icons.js';
import { t, tOther } from '../i18n.js';
import { stockChip, shortName, mapSvg, districtOptions, telLink, toast } from '../ui.js';
import { quietLocation, getLocation, lastLocation } from '../geo.js';

export const chrome = 'full';
export const title = 'nav.hospitals';

function level(v) { return v >= 20 ? 'level.plenty' : v >= 8 ? 'level.enough' : 'level.low'; }

export function hospitalCard(h) {
  const s = h.stock;
  const cls = s.status === 'NONE' ? 'none' : s.status === 'UNVERIFIED' ? 'amber' : '';
  return html`<article class="h-card">
    <div class="row between">
      ${stockChip(s)}
      <span class="chip chip-outline">${h.district}</span>
    </div>
    <div class="row between" style="align-items:flex-start">
      <div>
        <h3>${h.name}</h3>
        <div class="loc">${icon('pin', 14)} <span>${h.address}${h.distanceKm != null ? html` · <b>${t('hosp.kmAway', { n: h.distanceKm })}</b>` : ''}</span></div>
      </div>
      ${h.etaMinutes != null ? html`<span class="eta">${icon('clock', 16)} ${t('hosp.eta', { n: h.etaMinutes })}</span>` : ''}
    </div>
    <div class="stock-box ${cls}">
      <div class="ico">${icon(s.status === 'NONE' ? 'xcircle' : 'drop', 22)}</div>
      <div>
        <div class="big">${s.status === 'NONE' ? t('stock.outOfStock') : t('hosp.vials', { n: s.vials })}</div>
        <div class="sub">${s.status === 'VERIFIED' ? t('hosp.checkedByStaff') : s.status === 'UNVERIFIED' ? t('hosp.callFirst') : t('hosp.chooseOther')}</div>
      </div>
      ${s.status !== 'NONE' ? html`<span class="chip chip-dark end">${t(s.status === 'VERIFIED' ? level(s.vials) : 'level.reported')}</span>` : ''}
    </div>
    <div class="row-wrap">
      <a class="btn btn-sm btn-soft" href="${telLink(h.phone)}">${icon('phone', 14)} ${t('common.call')}</a>
      <a class="btn btn-sm btn-soft" href="${h.mapsUrl}" target="_blank" rel="noopener">${icon('nav', 14)} ${t('common.directions')}</a>
    </div>
    <a class="link-row" href="#/hospitals/${h.id}"><span>${t('hosp.viewDetails')}</span>${icon('arrow', 18)}</a>
  </article>`;
}

export default async function hospitals(el, ctx) {
  const cfg = ctx.config;
  let loc = await quietLocation();
  let all = await api(loc ? `/api/hospitals?lat=${loc.lat}&lng=${loc.lng}` : '/api/hospitals');
  const f = { q: '', verified: session.get('ss-vonly', '1') === '1', district: '' };

  const verifiedAll = all.filter((h) => h.stock.status === 'VERIFIED');
  const vials = verifiedAll.reduce((a, h) => a + h.stock.vials, 0);
  const frac = all.length ? verifiedAll.length / all.length : 0;
  const freshest = all.length ? Math.min(...all.map((h) => h.stock.minutesAgo ?? 9e9)) : 0;

  el.innerHTML = toHtml(html`
    <div class="status-strip" data-reveal><span><span class="dot"></span>${t('hosp.strip')}</span><span class="sep">•</span><span>${t('hosp.stripRule', { h: cfg.verifiedHours })}</span></div>
    <div style="margin-top:22px" data-reveal>
      <h1 class="flow-title" style="font-size:36px">${t('hosp.title')} <span class="muted" style="font-weight:600">/ ${tOther('hosp.title')}</span></h1>
      <p class="muted" style="margin-top:8px;max-width:640px">${t('hosp.sub')}</p>
      <div class="row-wrap" style="margin-top:16px">
        <div class="tile"><b data-count="${vials}">${vials}</b><span>${t('hosp.statVials')}</span></div>
        <div class="tile"><b>${verifiedAll.length} / ${all.length}</b><span>${t('hosp.statVerified')}</span></div>
      </div>
    </div>

    <div class="filter-bar" style="margin-top:22px" data-reveal>
      <div class="filter-row">
        <div class="input-icon">${icon('search', 18)}<input class="input" id="q" type="search" placeholder="${t('hosp.search')}" aria-label="${t('hosp.search')}"></div>
        <label class="switch"><input type="checkbox" id="vonly" ${f.verified ? 'checked' : ''}><span class="track"></span>${t('hosp.verifiedOnly')}</label>
        <select class="select select-pill" id="district" aria-label="${t('common.allDistricts')}">${districtOptions()}</select>
      </div>
      <div class="filter-row" id="locrow"></div>
    </div>

    <div class="grid-2" id="cards" style="margin-top:22px"></div>

    <div class="split" style="margin-top:26px">
      <div class="map-card" data-reveal>
        <div class="row between" style="margin-bottom:12px"><h3 style="font-size:17px">${t('hosp.mapTitle')}</h3><span class="chip chip-outline">${t('hosp.mapNote')}</span></div>
        <div id="map"></div>
        <div class="row-wrap xs muted" style="margin-top:10px">
          <span class="row" style="gap:6px"><i class="dot" style="background:#0a7a3c;animation:none"></i>${t('stock.legendVerified')}</span>
          <span class="row" style="gap:6px"><i class="dot" style="background:#c07a00;animation:none"></i>${t('stock.legendUnverified')}</span>
          <span class="row" style="gap:6px"><i class="dot" style="background:#b5001a;animation:none"></i>${t('stock.legendNone')}</span>
        </div>
      </div>
      <div class="fresh-card" data-reveal="right">
        <span class="chip">${icon('pulse', 14)} ${t('hosp.freshChip')}</span>
        <h3 style="font-size:20px">${t('hosp.freshTitle')}</h3>
        <p>${t('hosp.freshText', { h: cfg.verifiedHours })}</p>
        <div class="ring">
          <svg viewBox="0 0 100 100" aria-hidden="true"><circle class="track" cx="50" cy="50" r="42"/><circle class="bar" cx="50" cy="50" r="42" data-ring="${frac}"/></svg>
          <div class="mid"><b>${Math.round(frac * 100)}%</b><small>${t('hosp.freshUnit')}</small></div>
        </div>
        <div class="foot"><span>${t('hosp.freshLast')}</span><b>${all.length ? t('time.min', { n: Math.max(1, freshest) }) : '–'}</b></div>
      </div>
    </div>

    <section class="section" data-reveal>
      <div class="banner-red">
        <div class="ico">${icon('bell', 24)}</div>
        <div class="txt"><h3>${t('hosp.onWayTitle')}</h3><p>${t('hosp.onWayText')}</p></div>
        <div class="row-wrap"><a class="btn btn-white" href="${telLink(cfg.emergencyNumber)}" style="color:var(--red)">${icon('phone', 16)} ${t('common.call108')}</a><a class="btn btn-soft" href="#/first-aid">${icon('kit', 16)} ${t('nav.firstAid')}</a></div>
      </div>
    </section>`);

  const cards = $('#cards', el);
  const locrow = $('#locrow', el);
  const mapBox = $('#map', el);

  function renderLoc() {
    locrow.innerHTML = toHtml(loc
      ? html`<span class="chip chip-green">${icon('locate', 14)} ${t('hosp.locOn')}</span><span class="chip chip-outline">${icon('nav', 13)} ${t('hosp.sortedDistance')}</span>`
      : html`<button class="btn btn-sm btn-soft" data-act="locate">${icon('locate', 15)} ${t('hosp.useLocation')}</button><span class="chip chip-outline">${t('hosp.sortedName')}</span>`);
  }

  function renderList() {
    const q = f.q.trim().toLowerCase();
    const list = all.filter((h) => (!f.verified || h.stock.status === 'VERIFIED')
      && (!f.district || h.district === f.district)
      && (!q || `${h.name} ${h.district} ${h.address}`.toLowerCase().includes(q)));
    cards.innerHTML = list.length
      ? toHtml(list.map(hospitalCard))
      : toHtml(html`<div class="card empty" style="grid-column:1/-1">${icon('search', 40)}<h3>${t('hosp.emptyTitle')}</h3><p>${t('hosp.emptyText')}</p></div>`);
    mapBox.innerHTML = toHtml(mapSvg(list.length ? list : all, loc));
    ctx.motion(cards);
  }

  renderLoc(); renderList();

  $('#q', el).addEventListener('input', debounce((e) => { f.q = e.target.value; renderList(); }, 150));
  $('#vonly', el).addEventListener('change', (e) => { f.verified = e.target.checked; session.set('ss-vonly', f.verified ? '1' : '0'); renderList(); });
  $('#district', el).addEventListener('change', (e) => { f.district = e.target.value; renderList(); });
  el.addEventListener('click', async (e) => {
    if (!e.target.closest('[data-act="locate"]')) return;
    try {
      loc = await getLocation();
      all = await api(`/api/hospitals?lat=${loc.lat}&lng=${loc.lng}`);
      renderLoc(); renderList();
    } catch { toast(t('err.LOCATION_OFF'), 'err'); }
  });
}
