import { html, toHtml, debounce, $ } from '../util.js';
import { api } from '../api.js';
import { icon } from '../icons.js';
import { t, nameOf, getLang } from '../i18n.js';
import { snakeImg, districtOptions, errorBlock } from '../ui.js';

export const chrome = 'full';
export const title = 'nav.snakes';

export default async function snakes(el, ctx) {
  const f = { search: '', venomous: '', district: '' };
  el.innerHTML = toHtml(html`
    <div data-reveal>
      <div class="eyebrow">${t('snakes.eyebrow')}</div>
      <h1 class="flow-title" style="font-size:36px;margin-top:6px">${t('snakes.title')}</h1>
      <p class="muted" style="margin-top:8px;max-width:640px">${t('snakes.sub')}</p>
    </div>
    <div class="filter-bar" style="margin-top:20px" data-reveal>
      <div class="filter-row">
        <div class="input-icon">${icon('search', 18)}<input class="input" id="q" type="search" placeholder="${t('snakes.search')}" aria-label="${t('snakes.search')}"></div>
        <div class="seg" role="group" aria-label="${t('snakes.filter')}">
          <button data-v="" class="on">${t('snakes.all')}</button><button data-v="true">${t('snake.venomous')}</button><button data-v="false">${t('snake.nonVenomous')}</button>
        </div>
        <select class="select select-pill" id="district" aria-label="${t('common.allDistricts')}">${districtOptions()}</select>
      </div>
    </div>
    <div class="warn-box" style="margin-top:16px" data-reveal>${icon('warn', 20)}<span>${t('common.reminder')} ${t('snakes.anySnake')}</span></div>
    <div class="grid-4" id="grid" style="margin-top:20px"></div>`);

  const grid = $('#grid', el);
  let seq = 0;
  async function load() {
    const my = ++seq;
    const qs = new URLSearchParams();
    if (f.search) qs.set('search', f.search);
    if (f.venomous) qs.set('venomous', f.venomous);
    if (f.district) qs.set('district', f.district);
    let list;
    try { list = await api(`/api/snakes${qs.size ? `?${qs}` : ''}`); } catch (e) { if (my === seq) grid.innerHTML = toHtml(html`<div style="grid-column:1/-1">${errorBlock(e)}</div>`); return; }
    if (my !== seq || !ctx.alive()) return;
    grid.innerHTML = list.length ? toHtml(list.map((s) => html`
      <a class="snake-card" href="#/snakes/${s.id}">
        ${snakeImg(s)}
        <div class="meta"><h4>${nameOf(s)}</h4><div class="sci">${s.scientificName}</div><div class="kn">${getLang() === 'kn' ? s.nameEn : s.nameKn}</div><p class="d">${s.description}</p></div>
      </a>`))
      : toHtml(html`<div class="card empty" style="grid-column:1/-1">${icon('search', 40)}<h3>${t('snakes.emptyTitle')}</h3><p>${t('snakes.emptyText')}</p></div>`);
    ctx.motion(grid);
  }
  await load();

  $('#q', el).addEventListener('input', debounce((e) => { f.search = e.target.value.trim(); load(); }, 250));
  $('#district', el).addEventListener('change', (e) => { f.district = e.target.value; load(); });
  el.querySelector('.seg').addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    f.venomous = b.dataset.v;
    el.querySelectorAll('.seg button').forEach((x) => x.classList.toggle('on', x === b));
    load();
  });
}
