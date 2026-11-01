import { html, toHtml } from '../util.js';
import { api } from '../api.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { stockChip, mapSvg, telLink } from '../ui.js';
import { quietLocation } from '../geo.js';

export const chrome = 'full';
export const title = 'nav.hospitals';

export default async function hospital(el, ctx) {
  const loc = await quietLocation();
  const h = await api(`/api/hospitals/${ctx.params[0]}${loc ? `?lat=${loc.lat}&lng=${loc.lng}` : ''}`);
  const s = h.stock;
  const pct = Math.min(1, s.vials / 30);
  const canAlert = s.status !== 'NONE';

  el.innerHTML = toHtml(html`
    <div class="crumbs" data-reveal><a href="#/hospitals">${icon('hospital', 15)} ${t('nav.hospitals')}</a><span>›</span><span>${h.name}</span></div>

    <section class="card" data-reveal>
      <div class="d-head">
        <div>
          <div class="d-badges">${stockChip(s)}<span class="chip chip-outline">${h.district}</span></div>
          <h1>${h.name}</h1>
          <p class="muted row" style="margin-top:8px;gap:6px">${icon('pin', 16)} ${h.address}</p>
          ${h.distanceKm != null ? html`<div class="chip chip-green" style="margin-top:14px;height:32px;font-size:12.5px">${icon('nav', 14)} ${t('hosp.minsAway', { n: h.etaMinutes })} (${t('hosp.kmAway', { n: h.distanceKm })})</div>` : ''}
        </div>
        <a class="phone-box" href="${telLink(h.phone)}"><span class="ico">${icon('phone', 20)}</span><span><small>${t('hosp.directLine')}</small><b>${h.phone}</b></span></a>
      </div>
    </section>

    <div class="split" style="margin-top:22px">
      <div class="stack">
        <section class="card" data-reveal>
          <div class="row between"><div class="row" style="gap:12px"><span class="chip chip-dark" style="width:40px;height:40px;padding:0;justify-content:center;border-radius:12px">${icon('drop', 20)}</span><div><h3 style="font-size:18px">${t('hosp.stockTitle')}</h3><div class="muted xs">${t('hosp.stockSub')}</div></div></div>${stockChip(s)}</div>
          <div class="vial-big" style="margin-top:18px">
            <div class="num"><span data-count="${s.vials}">${s.vials}</span><small>${t('hosp.vialsInStock')}</small></div>
            <div class="bar"><span data-bar="${pct}" style="width:100%"></span></div>
            <div class="row between xs muted" style="margin-top:8px"><span>0</span><span>${t('hosp.barNote')}</span></div>
          </div>
          <p class="help" style="margin-top:14px">${s.status === 'VERIFIED' ? t('hosp.verifiedNote') : s.status === 'UNVERIFIED' ? t('hosp.unverifiedNote') : t('hosp.noneNote')}</p>
        </section>

        <section class="grid-2" data-stagger>
          <div class="do-box"><div class="ico">${icon('tick', 18)}</div><div><h4>${t('aid.do')}</h4><p>${t('hosp.doText')}</p></div></div>
          <div class="dont-box"><div class="ico">${icon('x', 18)}</div><div><h4>${t('aid.dont')}</h4><p>${t('hosp.dontText')}</p></div></div>
        </section>
      </div>

      <div class="stack">
        <section class="card" data-reveal="right">
          <div class="row between"><h3 style="font-size:19px">${t('hosp.goTitle')}</h3></div>
          <p class="muted small" style="margin:8px 0 16px">${canAlert ? t('hosp.goText', { name: h.name }) : t('hosp.goNone')}</p>
          ${canAlert ? html`<a class="btn btn-red btn-lg btn-block" href="#/emergency?hospital=${h.id}">${icon('sos', 18)} ${t('hosp.startHere')}</a>` : html`<a class="btn btn-red btn-lg btn-block" href="#/emergency">${icon('sos', 18)} ${t('hosp.findOther')}</a>`}
          <div class="grid-2" style="margin-top:12px;gap:10px">
            <a class="btn btn-soft" href="${h.mapsUrl}" target="_blank" rel="noopener">${icon('nav', 16)} ${t('common.directions')}</a>
            <a class="btn btn-soft" href="${telLink(h.phone)}">${icon('phone', 16)} ${t('hosp.directCall')}</a>
          </div>
          <div style="margin-top:16px">${mapSvg([h], loc, { w: 560, h: 240 })}</div>
        </section>
        <div class="warn-box" data-reveal>${icon('warn', 20)}<span>${t('common.reminder')} ${t('hosp.callAhead', { n: ctx.config.emergencyNumber })}</span></div>
      </div>
    </div>`);
}
