import { html, toHtml, $ } from '../util.js';
import { api } from '../api.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { districtOptions, telLink, toast, errorBlock } from '../ui.js';
import { quietLocation, getLocation } from '../geo.js';

export const chrome = 'full';
export const title = 'nav.catchers';

export default async function catchers(el, ctx) {
  let loc = await quietLocation();
  let district = '';
  el.innerHTML = toHtml(html`
    <div data-reveal>
      <div class="eyebrow">${t('catch.eyebrow')}</div>
      <h1 class="flow-title" style="font-size:36px;margin-top:6px">${t('catch.title')}</h1>
      <p class="muted" style="margin-top:8px;max-width:640px">${t('catch.sub')}</p>
    </div>
    <div class="warn-box" style="margin-top:18px" data-reveal>${icon('warn', 20)}<span>${t('catch.notMedical', { n: ctx.config.emergencyNumber })}</span></div>
    <div class="filter-bar" style="margin-top:18px" data-reveal><div class="filter-row">
      <select class="select select-pill" id="district" aria-label="${t('common.allDistricts')}">${districtOptions()}</select>
      <span id="locbox"></span>
    </div></div>
    <div class="grid-3" id="list" style="margin-top:20px"></div>`);

  const list = $('#list', el);
  async function load() {
    const qs = new URLSearchParams();
    if (district) qs.set('district', district);
    if (loc) { qs.set('lat', loc.lat); qs.set('lng', loc.lng); }
    let items;
    try { items = await api(`/api/catchers${qs.size ? `?${qs}` : ''}`); } catch (e) { list.innerHTML = toHtml(html`<div style="grid-column:1/-1">${errorBlock(e)}</div>`); return; }
    if (!ctx.alive()) return;
    $('#locbox', el).innerHTML = toHtml(loc
      ? html`<span class="chip chip-green">${icon('locate', 14)} ${t('catch.nearest')}</span>`
      : html`<button class="btn btn-sm btn-soft" data-act="locate">${icon('locate', 15)} ${t('hosp.useLocation')}</button>`);
    list.innerHTML = items.length ? toHtml(items.map((c) => html`
      <article class="h-card">
        <div class="row between"><span class="chip chip-outline">${c.district}</span>${c.distanceKm != null ? html`<span class="chip chip-green">${icon('pin', 13)} ${t('hosp.kmAway', { n: c.distanceKm })}</span>` : ''}</div>
        <div class="row" style="gap:14px"><span class="chip chip-dark" style="width:48px;height:48px;padding:0;justify-content:center;border-radius:15px">${icon('shieldUser', 24)}</span><div><h3>${c.name}</h3><div class="muted small">${c.phone}</div></div></div>
        <div class="row-wrap" style="margin-top:auto">
          <a class="btn btn-soft btn-sm" href="${telLink(c.phone)}">${icon('phone', 14)} ${t('common.call')}</a>
          <a class="btn btn-sm" href="#/catchers/${c.id}">${t('catch.request')} ${icon('arrow', 14)}</a>
        </div>
      </article>`))
      : toHtml(html`<div class="card empty" style="grid-column:1/-1">${icon('shieldUser', 40)}<h3>${t('catch.emptyTitle')}</h3><p>${t('catch.emptyText')}</p></div>`);
    ctx.motion(list);
  }
  await load();

  $('#district', el).addEventListener('change', (e) => { district = e.target.value; load(); });
  el.addEventListener('click', async (e) => {
    if (!e.target.closest('[data-act="locate"]')) return;
    try { loc = await getLocation(); load(); } catch { toast(t('err.LOCATION_OFF'), 'err'); }
  });
}
