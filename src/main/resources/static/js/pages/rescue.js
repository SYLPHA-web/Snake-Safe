import { html, toHtml, $ } from '../util.js';
import { api } from '../api.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { telLink } from '../ui.js';

export const chrome = 'full';
export const title = 'nav.catchers';

const STEPS = ['REQUESTED', 'ACCEPTED', 'DONE'];

export default async function rescue(el, ctx) {
  const id = ctx.params[0];
  const draw = (r) => {
    const idx = STEPS.indexOf(r.status);
    el.innerHTML = toHtml(html`
      <div class="crumbs"><a href="#/catchers">${icon('shieldUser', 15)} ${t('nav.catchers')}</a><span>›</span><span>${t('rescue.title')}</span></div>
      <div class="flow-wrap"><section class="card">
        <div class="big-state"><div class="ico ${r.status === 'DONE' ? 'ico-green' : 'ico-blue'}">${icon(r.status === 'DONE' ? 'check' : 'clock', 42)}</div>
          <h2>${t(`rescue.${r.status}`)}</h2><p class="muted">${t('rescue.sub', { name: r.catcherName || '' })}</p></div>
        <div class="how-steps" style="grid-template-columns:repeat(3,1fr);margin-top:24px">${STEPS.map((s, i) => html`<div class="how-step ${i <= idx ? 'on' : ''}"><div class="n">${i + 1}</div><h4>${t(`rescue.${s}.short`)}</h4></div>`)}</div>
        ${r.catcherPhone ? html`<div class="stack" style="margin-top:22px"><a class="btn btn-lg btn-block" href="${telLink(r.catcherPhone)}">${icon('phone', 18)} ${t('catch.callDirect')}</a></div>` : ''}
        <div class="warn-box" style="margin-top:18px">${icon('warn', 20)}<span>${t('catch.notMedical', { n: ctx.config.emergencyNumber })} <a href="${telLink(ctx.config.emergencyNumber)}" class="bold" style="text-decoration:underline">${t('common.call108')}</a></span></div>
      </section></div>`);
  };
  draw(await api(`/api/rescues/${id}`));
  const timer = setInterval(async () => {
    if (!ctx.alive()) return;
    try { const r = await api(`/api/rescues/${id}`); if (!$('.flow-wrap', el)) return; draw(r); } catch { /* keep last view */ }
  }, 10000);
  ctx.onCleanup(() => clearInterval(timer));
}
