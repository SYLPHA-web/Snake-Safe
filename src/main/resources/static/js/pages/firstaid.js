import { html, toHtml } from '../util.js';
import { api } from '../api.js';
import { icon } from '../icons.js';
import { t, pick } from '../i18n.js';
import { telLink } from '../ui.js';

export const chrome = 'full';
export const title = 'nav.firstAid';

export default async function firstAid(el, ctx) {
  const d = await api('/api/first-aid');
  const cfg = ctx.config;
  el.innerHTML = toHtml(html`
    <section class="hero" style="grid-template-columns:1fr" data-reveal="zoom">
      <div>
        <span class="chip chip-red">${icon('kit', 13)} ${t('aid.chip')}</span>
        <h1 style="font-size:34px">${pick(d.title)}</h1>
        <div class="safety-note">${icon('warn', 20)}<p><b>${t('home.safetyLabel')}</b>${pick(d.reminder)} ${t('aid.intro')}</p></div>
        <div class="hero-actions">
          <a class="btn btn-red btn-lg" href="#/emergency">${icon('sos', 18)} ${t('home.startShort2')}</a>
          <a class="btn btn-soft btn-lg" href="${telLink(d.numbers.emergency)}">${icon('phone', 18)} ${t('home.call', { n: d.numbers.emergency })}</a>
        </div>
      </div>
    </section>
    <div class="status-strip" style="margin-top:18px" data-reveal>${icon('download', 14)} ${t('aid.offline')}</div>

    <section class="section">
      <div class="section-head" data-reveal><div><div class="eyebrow">${t('aid.stepsEyebrow')}</div><h2>${t('aid.stepsTitle')}</h2></div></div>
      <div class="stack" data-stagger="left">
        ${d.steps.map((s, i) => html`<article class="step-card"><div class="n">${i + 1}</div><div><h3>${pick(s).title}</h3><p>${pick(s).text}</p></div></article>`)}
      </div>
    </section>

    <section class="section grid-2" data-stagger>
      <div class="dont-box" style="display:block"><div class="row" style="gap:10px;margin-bottom:10px"><span class="ico" style="width:36px;height:36px;border-radius:50%;background:var(--red);color:#fff;display:grid;place-items:center">${icon('x', 18)}</span><h4 style="margin:0">${t('aid.dont')}</h4></div>
        <ul class="stack" style="gap:8px">${d.avoid.map((a) => html`<li style="display:flex;gap:10px;font-size:14px">${icon('xcircle', 18)}<span>${pick(a)}</span></li>`)}</ul></div>
      <div class="banner-green" style="display:block"><div class="eyebrow">${t('home.helpEyebrow')}</div><h3>${t('home.helpTitle')}</h3><p style="margin-bottom:16px">${t('home.helpText')}</p>
        <div class="row-wrap"><a class="btn btn-white" href="${telLink(d.numbers.poisonHelpline)}">${icon('phone', 16)} ${d.numbers.poisonHelpline}</a><a class="btn btn-mint" href="#/hospitals">${icon('hospital', 16)} ${t('nav.hospitals')}</a></div></div>
    </section>`);
}
