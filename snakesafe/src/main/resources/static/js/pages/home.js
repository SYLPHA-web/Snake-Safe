import { html, toHtml } from '../util.js';
import { api } from '../api.js';
import { icon } from '../icons.js';
import { t, tOther } from '../i18n.js';
import { snakeImg, telLink } from '../ui.js';

export const chrome = 'full';
export const title = 'nav.home';

const FEATURED = ['Naja naja', 'Daboia russelii', 'Bungarus caeruleus', 'Echis carinatus'];

export default async function home(el, ctx) {
  const cfg = ctx.config;
  const [hs, sn] = await Promise.allSettled([api('/api/hospitals'), api('/api/snakes')]);
  const hospitals = hs.status === 'fulfilled' ? hs.value : [];
  const snakes = sn.status === 'fulfilled' ? sn.value : [];

  const verified = hospitals.filter((h) => h.stock.status === 'VERIFIED');
  const vials = verified.reduce((a, h) => a + h.stock.vials, 0);
  const freshest = hospitals.length ? Math.min(...hospitals.map((h) => h.stock.minutesAgo ?? 9e9)) : null;
  const frac = hospitals.length ? verified.length / hospitals.length : 0;

  let featured = FEATURED.map((s) => snakes.find((x) => x.scientificName === s)).filter(Boolean);
  if (featured.length < 4) featured = snakes.filter((s) => s.venomous).slice(0, 4);

  const tools = [
    ['camera', 'tool.identify', '/identify', 'tool.identify.btn'],
    ['hospital', 'tool.hospitals', '/hospitals', 'tool.hospitals.btn'],
    ['shieldUser', 'tool.catchers', '/catchers', 'tool.catchers.btn'],
    ['kit', 'tool.firstAid', '/first-aid', 'tool.firstAid.btn'],
  ];

  el.innerHTML = toHtml(html`
    <div class="status-strip" data-reveal>
      <span><span class="dot"></span>${t('home.live')}</span><span class="sep">|</span>
      ${hospitals.length ? html`
        <span>${icon('hospital', 14)} ${t('home.stripHospitals', { n: verified.length })}</span><span class="sep">•</span>
        <span>${icon('drop', 14)} ${t('home.stripVials', { n: vials })}</span><span class="sep">•</span>
        <span>${icon('clock', 14)} ${t('home.stripUpdated', { ago: freshest < 60 ? t('time.min', { n: Math.max(1, freshest) }) : t('time.hour', { n: Math.round(freshest / 60) }) })}</span>`
        : html`<span>${t('home.noData')}</span>`}
    </div>

    <section class="hero" data-reveal="zoom">
      <div>
        <span class="chip chip-red">${icon('sos', 13)} ${t('home.chip')}</span>
        <h1>${t('home.h1')}</h1>
        <p class="kn-line">${tOther('home.h1')}</p>
        <div class="safety-note">
          ${icon('shield', 20)}
          <p><b>${t('home.safetyLabel')}</b>${t('home.safety')}</p>
        </div>
        <div class="hero-actions">
          <a class="btn btn-red btn-lg" href="#/emergency">${icon('sos', 18)} ${t('home.start')} <span style="opacity:.85">(${tOther('home.startShort')})</span></a>
          <a class="btn btn-soft btn-lg" href="${telLink(cfg.emergencyNumber)}">${icon('phone', 18)} ${t('home.call', { n: cfg.emergencyNumber })}</a>
        </div>
      </div>
      <div class="ring-card" data-reveal="right" data-delay="0.15">
        <div class="ring">
          <svg viewBox="0 0 100 100" aria-hidden="true"><circle class="track" cx="50" cy="50" r="42"/><circle class="bar" cx="50" cy="50" r="42" data-ring="${frac}"/></svg>
          <div class="mid"><b data-count="${verified.length}">${verified.length}</b><small>${t('home.ringUnit')}</small></div>
        </div>
        <h4>${t('home.ringTitle')}</h4>
        <p>${t('home.ringText', { h: cfg.verifiedHours })}</p>
      </div>
    </section>

    <section class="section">
      <div class="section-head" data-reveal>
        <div><div class="eyebrow">${t('home.toolkitEyebrow')}</div><h2>${t('home.toolkitTitle')}</h2></div>
      </div>
      <div class="grid-2" data-stagger>
        ${tools.map(([ic, key, href, btn]) => html`
          <article class="tool-card">
            <div class="ico">${icon(ic, 24)}</div>
            <span class="tag chip ${key === 'tool.hospitals' ? 'chip-green' : ''}">${t(`${key}.tag`)}</span>
            <h3>${t(`${key}.title`)} <span class="muted" style="font-weight:600">(${tOther(`${key}.title`)})</span></h3>
            <p>${t(`${key}.text`)}</p>
            <div class="foot">
              <a class="btn btn-sm" href="#${href}">${t(btn)} ${icon('arrow', 15)}</a>
              <small>${key === 'tool.hospitals' && hospitals.length ? t('tool.hospitals.foot', { n: hospitals.length }) : t(`${key}.foot`)}</small>
            </div>
          </article>`)}
      </div>
    </section>

    <section class="section">
      <div class="section-head" data-reveal>
        <div><div class="eyebrow">${t('how.eyebrow')}</div><h2>${t('how.title')}</h2></div>
      </div>
      <div class="how" data-scrub-steps>
        <div class="how-line"><span></span></div>
        <div class="how-steps">
          ${[1, 2, 3, 4].map((n) => html`<div class="how-step"><div class="n">${n}</div><h4>${t(`how.${n}.t`)}</h4><p>${t(`how.${n}.d`, { s: cfg.cancelWindowSeconds })}</p></div>`)}
        </div>
      </div>
    </section>

    ${featured.length ? html`
    <section class="section card" style="padding:30px" data-reveal>
      <div class="chip chip-red">${icon('warn', 13)} ${t('home.snakesChip')}</div>
      <div class="section-head" style="margin-top:12px">
        <div><h2 style="margin:0">${t('home.snakesTitle')}</h2><p class="muted small" style="margin-top:6px;max-width:560px">${t('home.snakesText')}</p></div>
        <a class="btn btn-soft" href="#/snakes">${t('home.allSnakes')} ${icon('arrow', 15)}</a>
      </div>
      <div class="grid-4" data-stagger>
        ${featured.map((s) => html`
          <a class="snake-card" href="#/snakes/${s.id}">
            ${snakeImg(s)}
            <div class="meta">
              <h4>${s.nameEn}</h4>
              <div class="sci">${s.scientificName}</div>
              <div class="kn">${s.nameKn}</div>
              <p class="d">${s.description}</p>
            </div>
          </a>`)}
      </div>
    </section>` : ''}

    <section class="section" data-reveal>
      <div class="banner-green">
        <div class="ico">${icon('phone', 26)}</div>
        <div class="txt">
          <div class="eyebrow">${t('home.helpEyebrow')}</div>
          <h3>${t('home.helpTitle')}</h3>
          <p>${t('home.helpText')}</p>
        </div>
        <div class="row-wrap">
          <a class="btn btn-white" href="${telLink(cfg.poisonHelpline)}">${icon('phone', 16)} ${cfg.poisonHelpline}</a>
          <a class="btn btn-mint" href="#/first-aid">${icon('book', 16)} ${t('home.helpSteps')}</a>
        </div>
      </div>
    </section>`);
}
