import { html, toHtml, $ } from '../util.js';
import { api, tokens } from '../api.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { telLink, toast, errorText } from '../ui.js';
import { quietLocation, getLocation } from '../geo.js';

export const chrome = 'full';
export const title = 'nav.catchers';

export default async function catcher(el, ctx) {
  let loc = await quietLocation();
  const c = await api(`/api/catchers/${ctx.params[0]}${loc ? `?lat=${loc.lat}&lng=${loc.lng}` : ''}`);
  let phone = '';
  try { if (tokens.get('user')) phone = (await api('/api/profile', { auth: 'user' })).phone || ''; } catch { /* guest */ }

  el.innerHTML = toHtml(html`
    <div class="crumbs" data-reveal><a href="#/catchers">${icon('shieldUser', 15)} ${t('nav.catchers')}</a><span>›</span><span>${c.name}</span></div>
    <div class="split">
      <section class="card" data-reveal>
        <span class="chip chip-outline">${c.district}</span>
        <h1 class="flow-title" style="margin-top:12px">${c.name}</h1>
        <p class="muted" style="margin-top:6px">${c.phone}${c.distanceKm != null ? html` · ${t('hosp.kmAway', { n: c.distanceKm })}` : ''}</p>
        <div class="row-wrap" style="margin-top:16px"><a class="btn btn-soft" href="${telLink(c.phone)}">${icon('phone', 16)} ${t('catch.callDirect')}</a></div>
        <div class="warn-box" style="margin-top:20px">${icon('warn', 20)}<span>${t('catch.notMedical', { n: ctx.config.emergencyNumber })}</span></div>
      </section>
      <form class="card stack" id="f" data-reveal="right" novalidate>
        <h3 style="font-size:20px">${t('catch.formTitle')}</h3>
        <div class="field"><label for="phone">${t('catch.yourPhone')}</label><input class="input" id="phone" type="tel" inputmode="tel" required value="${phone}" placeholder="98765 43210" autocomplete="tel"></div>
        <div class="field"><label for="notes">${t('catch.notes')}</label><textarea class="textarea" id="notes" maxlength="500" placeholder="${t('catch.notesHint')}"></textarea></div>
        <div class="row between" id="locrow" style="flex-wrap:wrap"></div>
        <p class="error-text hidden" id="err"></p>
        <button class="btn btn-lg btn-block" type="submit">${icon('send', 18)} ${t('catch.send')}</button>
      </form>
    </div>`);

  const locrow = $('#locrow', el);
  const drawLoc = () => { locrow.innerHTML = toHtml(loc
    ? html`<span class="chip chip-green">${icon('check', 14)} ${t('catch.locAttached')}</span>`
    : html`<span class="chip chip-amber">${icon('warn', 14)} ${t('catch.locNeeded')}</span><button class="btn btn-sm btn-soft" type="button" data-act="locate">${icon('locate', 15)} ${t('hosp.useLocation')}</button>`); };
  drawLoc();

  el.addEventListener('click', async (e) => {
    if (!e.target.closest('[data-act="locate"]')) return;
    try { loc = await getLocation(); drawLoc(); } catch { toast(t('err.LOCATION_OFF'), 'err'); }
  });

  $('#f', el).addEventListener('submit', async (e) => {
    e.preventDefault();
    const err = $('#err', el); const btn = e.target.querySelector('button[type=submit]');
    const showErr = (m) => { err.textContent = m; err.classList.remove('hidden'); };
    err.classList.add('hidden');
    if (!loc) { showErr(t('catch.locNeeded')); return; }
    const p = $('#phone', el).value.trim();
    if (!p) { showErr(t('err.INVALID_PHONE')); return; }
    btn.disabled = true;
    try {
      const r = await api('/api/rescues', { method: 'POST', body: { catcherId: c.id, lat: loc.lat, lng: loc.lng, notes: $('#notes', el).value.trim() || undefined, phone: p } });
      ctx.navigate(`/rescues/${r.id}`);
    } catch (ex) { showErr(errorText(ex)); btn.disabled = false; }
  });
}
