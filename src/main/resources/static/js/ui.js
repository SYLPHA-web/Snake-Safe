// Shared UI pieces: toast, modal, stock labels, snake art, hospital map, error block.
import { html, raw, toHtml, esc, $ } from './util.js';
import { icon } from './icons.js';
import { t, getLang } from './i18n.js';

export function toast(msg, kind = '') {
  const box = $('#toasts');
  if (!box) return;
  const el = document.createElement('div');
  el.className = `toast ${kind}`;
  el.textContent = msg;
  el.setAttribute('role', 'status');
  box.append(el);
  setTimeout(() => el.remove(), 3600);
}

/** Opens a modal. content is html``; returns { el, close }. */
export function openModal(content, { onClose } = {}) {
  const back = document.createElement('div');
  back.className = 'modal-back';
  back.innerHTML = `<div class="modal" role="dialog" aria-modal="true">${toHtml(content)}</div>`;
  const prev = document.activeElement;
  const close = () => {
    document.removeEventListener('keydown', onKey);
    back.remove();
    if (prev && prev.focus) prev.focus();
    if (onClose) onClose();
  };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  back.addEventListener('mousedown', (e) => { if (e.target === back) close(); });
  back.addEventListener('click', (e) => { if (e.target.closest('[data-close]')) close(); });
  document.body.append(back);
  const first = back.querySelector('input,select,textarea,button');
  if (first) first.focus();
  return { el: back.firstElementChild, close };
}

export function confirmDialog(title, text, okLabel) {
  return new Promise((resolve) => {
    let done = false;
    const m = openModal(html`
      <h3>${title}</h3>
      <p class="muted">${text}</p>
      <div class="actions">
        <button class="btn btn-soft" data-close>${t('common.cancel')}</button>
        <button class="btn btn-red" data-ok>${okLabel || t('common.ok')}</button>
      </div>`, { onClose: () => { if (!done) resolve(false); } });
    m.el.querySelector('[data-ok]').addEventListener('click', () => { done = true; m.close(); resolve(true); });
  });
}

// ---------- stock ----------
export function ago(minutes) {
  if (minutes == null) return '';
  if (minutes < 60) return t('time.min', { n: Math.max(1, Math.round(minutes)) });
  if (minutes < 1440) return t('time.hour', { n: Math.round(minutes / 60) });
  return t('time.day', { n: Math.round(minutes / 1440) });
}

export function stockChip(stock) {
  if (stock.status === 'VERIFIED') return html`<span class="chip chip-green">${icon('check', 14)} ${t('stock.verified', { ago: ago(stock.minutesAgo) })}</span>`;
  if (stock.status === 'UNVERIFIED') return html`<span class="chip chip-amber">${icon('clock', 14)} ${t('stock.unverified', { ago: ago(stock.minutesAgo) })}</span>`;
  return html`<span class="chip chip-red">${icon('xcircle', 14)} ${t('stock.none')}</span>`;
}

export function stockText(stock) {
  if (stock.status === 'VERIFIED') return t('stock.verified', { ago: ago(stock.minutesAgo) });
  if (stock.status === 'UNVERIFIED') return t('stock.unverified', { ago: ago(stock.minutesAgo) });
  return t('stock.none');
}

export const shortName = (n) => String(n || '').replace(/^Sample Hospital\s*/i, '');

export function telLink(num) { return `tel:${String(num).replace(/[^\d+]/g, '')}`; }

export function errorText(e) {
  if (!e) return '';
  const key = `err.${e.code}`;
  const msg = t(key);
  return msg === key ? (e.message || t('err.ERROR')) : msg;
}

export function errorBlock(e, retry = true) {
  const offline = e && e.code === 'OFFLINE';
  return html`<div class="card empty" data-reveal>
    ${icon(offline ? 'warn' : 'info', 40)}
    <h3>${offline ? t('err.offlineTitle') : t('err.title')}</h3>
    <p>${errorText(e)}</p>
    <div class="row-wrap" style="justify-content:center;margin-top:18px">
      ${retry ? html`<button class="btn btn-soft" data-act="retry">${icon('refresh', 16)} ${t('common.retry')}</button>` : ''}
      <a class="btn btn-red" href="tel:108">${icon('phone', 16)} ${t('common.call108')}</a>
    </div>
  </div>`;
}

// ---------- snake art (used until a real photo loads) ----------
const PALETTES = {
  'Naja naja': ['#7a5a2e', '#d3b16d', '#2d1f0e'],
  'Ophiophagus hannah': ['#47532a', '#a3b06f', '#1c2410'],
  'Bungarus caeruleus': ['#10151d', '#e8edf3', '#06080b'],
  'Daboia russelii': ['#8a6330', '#e1c283', '#3a260f'],
  'Echis carinatus': ['#a07a45', '#ecd3a0', '#4a3315'],
  'Trimeresurus malabaricus': ['#3d7a3a', '#c7dd6a', '#16300f'],
  'Ptyas mucosa': ['#6b5a3a', '#bda878', '#2b2216'],
  'Python molurus': ['#7c6540', '#d2b985', '#2e2312'],
  'Fowlea piscator': ['#59623a', '#b5bb85', '#1f2412'],
  'Lycodon aulicus': ['#5a4a3a', '#e3d6c3', '#1d1812'],
  'Eryx johnii': ['#8a6a48', '#d9bd96', '#33261a'],
  'Ahaetulla nasuta': ['#2f8a3a', '#b9ec6d', '#0e3012'],
};

function hash(s) { let h = 7; for (const c of String(s)) h = (h * 31 + c.charCodeAt(0)) >>> 0; return h; }

export function snakeArt(sci = '') {
  const pal = PALETTES[sci] || (() => { const h = hash(sci) % 360; return [`hsl(${h} 35% 34%)`, `hsl(${h} 45% 70%)`, `hsl(${h} 40% 14%)`]; })();
  const [base, accent, dark] = pal;
  const pts = [];
  for (let i = 0; i <= 150; i++) {
    const th = (i / 150) * 5.4 * Math.PI;
    const r = 6 + 3.5 * th;
    pts.push([100 + r * Math.cos(th) * 1.3, 72 + r * Math.sin(th) * 0.92]);
  }
  const d = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ');
  const head = pts[pts.length - 1];
  const id = `g${hash(sci)}`;
  return raw(`<svg viewBox="0 0 200 144" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
    <defs><radialGradient id="${id}" cx="50%" cy="40%" r="80%"><stop offset="0" stop-color="${accent}" stop-opacity=".55"/><stop offset="1" stop-color="${dark}"/></radialGradient></defs>
    <rect width="200" height="144" fill="url(#${id})"/>
    <path d="${d}" fill="none" stroke="${dark}" stroke-width="15" stroke-linecap="round" stroke-linejoin="round" opacity=".5" transform="translate(2 3)"/>
    <path d="${d}" fill="none" stroke="${base}" stroke-width="13" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="${d}" fill="none" stroke="${accent}" stroke-width="13" stroke-linecap="butt" stroke-dasharray="3 12" opacity=".75"/>
    <path d="${d}" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" opacity=".14" transform="translate(-3 -3)"/>
    <ellipse cx="${head[0].toFixed(1)}" cy="${head[1].toFixed(1)}" rx="10" ry="8" fill="${base}"/>
    <circle cx="${(head[0] + 3).toFixed(1)}" cy="${(head[1] - 2.5).toFixed(1)}" r="1.8" fill="#fff"/><circle cx="${(head[0] + 3.4).toFixed(1)}" cy="${(head[1] - 2.5).toFixed(1)}" r=".8" fill="#000"/>
  </svg>`);
}

/** Snake picture: drawn art first, real photo fades in over it when the file exists. */
export function snakeImg(s, { badge = true } = {}) {
  return html`<div class="snake-img">
    ${snakeArt(s.scientificName)}
    ${s.imageUrl ? html`<img src="${s.imageUrl}" alt="${getLang() === 'kn' ? s.nameKn : s.nameEn}" loading="lazy" data-fade>` : ''}
    ${badge ? html`<span class="badge ${s.venomous ? 'badge-red' : 'badge-green'}">${s.venomous ? t('snake.venomous') : t('snake.nonVenomous')}</span>` : ''}
  </div>`;
}

// reveal images that load (error events do not bubble, so use capture)
document.addEventListener('load', (e) => { if (e.target.matches && e.target.matches('img[data-fade]')) e.target.classList.add('loaded'); }, true);

// ---------- map ----------
export function mapSvg(hospitals, user, { w = 640, h = 320 } = {}) {
  const pts = hospitals.map((x) => [x.lat, x.lng]);
  if (user) pts.push([user.lat, user.lng]);
  if (!pts.length) return html`<div class="map"></div>`;
  const lats = pts.map((p) => p[0]); const lngs = pts.map((p) => p[1]);
  let [la0, la1, ln0, ln1] = [Math.min(...lats), Math.max(...lats), Math.min(...lngs), Math.max(...lngs)];
  if (la1 - la0 < 0.12) { la0 -= 0.06; la1 += 0.06; }
  if (ln1 - ln0 < 0.12) { ln0 -= 0.06; ln1 += 0.06; }
  const cos = Math.cos((((la0 + la1) / 2) * Math.PI) / 180);
  const rx = (ln1 - ln0) * cos; const ry = la1 - la0; const m = 46;
  const sc = Math.min((w - 2 * m) / rx, (h - 2 * m) / ry);
  const ox = (w - rx * sc) / 2; const oy = (h - ry * sc) / 2;
  const X = (lng) => ox + (lng - ln0) * cos * sc;
  const Y = (lat) => oy + (la1 - lat) * sc;
  const col = { VERIFIED: '#0a7a3c', UNVERIFIED: '#c07a00', NONE: '#b5001a' };

  const pins = hospitals.map((x) => {
    const cx = X(x.lng).toFixed(1); const cy = Y(x.lat).toFixed(1); const c = col[x.stock.status];
    return `<a href="#/hospitals/${x.id}" class="pin" aria-label="${esc(x.name)}">
      ${x.stock.status === 'VERIFIED' ? `<circle class="pin-ring" cx="${cx}" cy="${cy}" r="9" fill="${c}" opacity=".35"/>` : ''}
      <circle cx="${cx}" cy="${cy}" r="9" fill="${c}" stroke="#fff" stroke-width="3"/>
      <circle cx="${cx}" cy="${cy}" r="3" fill="#fff"/>
      <text class="m-label" x="${cx}" y="${(+cy - 15).toFixed(1)}" text-anchor="middle">${esc(shortName(x.name))}</text>
    </a>`;
  }).join('');
  const me = user ? `<g><circle cx="${X(user.lng).toFixed(1)}" cy="${Y(user.lat).toFixed(1)}" r="16" fill="#1f6feb" opacity=".18"/><circle cx="${X(user.lng).toFixed(1)}" cy="${Y(user.lat).toFixed(1)}" r="7" fill="#1f6feb" stroke="#fff" stroke-width="3"/></g>` : '';
  const grid = Array.from({ length: 9 }, (_, i) => `<path d="M${(i + 1) * w / 10} 0V${h}" />`).join('') + Array.from({ length: 4 }, (_, i) => `<path d="M0 ${(i + 1) * h / 5}H${w}" />`).join('');
  return raw(`<div class="map"><svg viewBox="0 0 ${w} ${h}" role="img" aria-label="${esc(t('map.label'))}">
    <rect class="m-bg" width="${w}" height="${h}"/>
    <g class="m-grid">${grid}</g>
    <path d="M-10 ${h * .72} C ${w * .2} ${h * .55}, ${w * .35} ${h * .9}, ${w * .55} ${h * .62} S ${w * .85} ${h * .3}, ${w + 10} ${h * .4}" class="m-road" stroke-width="9"/>
    <path d="M${w * .18} -10 C ${w * .25} ${h * .3}, ${w * .12} ${h * .6}, ${w * .3} ${h + 10}" class="m-road" stroke-width="6"/>
    <path d="M-10 ${h * .2} C ${w * .3} ${h * .1}, ${w * .5} ${h * .35}, ${w + 10} ${h * .12}" class="m-river" stroke-width="12"/>
    ${me}${pins}
  </svg></div>`);
}

export function pageHead(titleKey, subKey, extra = '') {
  return html`<div data-reveal>
    <h1 class="flow-title" style="font-size:34px">${t(titleKey)}</h1>
    ${subKey ? html`<p class="muted" style="margin-top:8px;max-width:680px">${t(subKey)}</p>` : ''}
    ${extra}
  </div>`;
}

export const KARNATAKA_DISTRICTS = ['Bagalkot', 'Ballari', 'Belagavi', 'Bengaluru Rural', 'Bengaluru Urban', 'Bidar', 'Chamarajanagar', 'Chikkaballapur', 'Chikkamagaluru', 'Chitradurga', 'Dakshina Kannada', 'Davanagere', 'Dharwad', 'Gadag', 'Hassan', 'Haveri', 'Kalaburagi', 'Kodagu', 'Kolar', 'Koppal', 'Mandya', 'Mysuru', 'Raichur', 'Ramanagara', 'Shivamogga', 'Tumakuru', 'Udupi', 'Uttara Kannada', 'Vijayanagara', 'Vijayapura', 'Yadgir'];

export function districtOptions(selected = '') {
  return html`<option value="">${t('common.allDistricts')}</option>${KARNATAKA_DISTRICTS.map((d) => html`<option value="${d}" ${d === selected ? raw('selected') : ''}>${d}</option>`)}`;
}
