import { html, toHtml } from '../util.js';
import { api } from '../api.js';
import { icon } from '../icons.js';
import { t, getLang } from '../i18n.js';
import { snakeImg, telLink } from '../ui.js';

export const chrome = 'full';
export const title = 'nav.snakes';

export default async function snake(el, ctx) {
  const s = await api(`/api/snakes/${ctx.params[0]}`);
  const kn = getLang() === 'kn';
  const primary = kn ? s.nameKn : s.nameEn;
  const secondary = kn ? s.nameEn : s.nameKn;
  const everywhere = s.districts.some((d) => d.toLowerCase() === 'statewide');

  el.innerHTML = toHtml(html`
    <div class="crumbs" data-reveal><a href="#/snakes">${icon('grid', 15)} ${t('nav.snakes')}</a><span>›</span><span>${primary}</span></div>
    <div class="snake-hero">
      <div data-reveal="left">
        ${snakeImg(s)}
        <div class="card-flat" style="margin-top:16px;display:grid;gap:10px">
          <div class="row" style="gap:10px">${icon('shield', 20)}<div><div class="eyebrow">${t('snake.status')}</div><b>${s.venomous ? t('snake.venomousFull') : t('snake.nonVenomousFull')}</b></div></div>
          <div class="row" style="gap:10px">${icon('pin', 20)}<div><div class="eyebrow">${t('snake.where')}</div><b>${everywhere ? t('snake.statewide') : s.districts.length ? t('snake.nDistricts', { n: s.districts.length }) : '–'}</b></div></div>
        </div>
      </div>
      <div>
        <div data-reveal>
          <h1>${primary} <span class="muted" style="font-weight:600;font-size:.6em">/ ${secondary}</span></h1>
          <div class="sci">${s.scientificName}</div>
        </div>
        <div class="callout-red" data-reveal>
          <div class="ico">${icon('sos', 26)}</div>
          <div class="txt"><h3>${t('snake.biteTitle')}</h3><p>${t('common.reminder')} ${t('snake.biteText')}</p></div>
          <div class="stack" style="gap:8px;min-width:170px"><a class="btn btn-red" href="#/emergency">${icon('sos', 16)} ${t('home.startShort2')}</a><a class="btn btn-white" href="${telLink(ctx.config.emergencyNumber)}">${icon('phone', 16)} ${t('snake.dial', { n: ctx.config.emergencyNumber })}</a></div>
        </div>
        <div class="grid-2" style="margin-top:20px" data-stagger>
          <div class="info-card"><h4>${icon('book', 18)} ${t('snake.about')}</h4><p>${s.description}</p></div>
          <div class="info-card"><h4>${icon('leaf', 18)} ${t('snake.habitat')}</h4><p>${s.habitat}</p></div>
          <div class="info-card" style="grid-column:1/-1"><h4>${icon('pin', 18)} ${t('snake.districts')}</h4>
            <div class="dist-chips">${everywhere ? html`<span class="chip chip-green">${t('snake.statewide')}</span>` : s.districts.map((d) => html`<span class="chip">${d}</span>`)}</div></div>
          <div class="info-card" style="grid-column:1/-1;background:var(--surface-low);box-shadow:none"><h4>${icon('warn', 18)} ${t('snake.lookalikeTitle')}</h4><p>${t('snake.lookalikeText')}</p></div>
        </div>
        <div class="row-wrap between" style="margin-top:24px" data-reveal>
          <a class="btn btn-ghost" href="#/snakes">${icon('back', 16)} ${t('snake.back')}</a>
          <a class="btn" href="#/catchers">${icon('shieldUser', 16)} ${t('snake.rescuer')}</a>
        </div>
      </div>
    </div>`);
}
