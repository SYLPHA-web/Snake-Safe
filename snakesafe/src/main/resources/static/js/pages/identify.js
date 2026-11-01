import { html, toHtml, $ } from '../util.js';
import { api } from '../api.js';
import { icon } from '../icons.js';
import { t, nameOf } from '../i18n.js';
import { telLink, toast, errorText, snakeImg } from '../ui.js';

export const chrome = 'full';
export const title = 'nav.identify';

const MAX_SIDE = 1600;

/** Phone photos can be HEIC/WebP/huge: redraw to a JPEG under the 5 MB limit. */
async function toJpeg(file) {
  const bmp = await createImageBitmap(file);
  const scale = Math.min(1, MAX_SIDE / Math.max(bmp.width, bmp.height));
  const c = document.createElement('canvas');
  c.width = Math.round(bmp.width * scale); c.height = Math.round(bmp.height * scale);
  c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
  return new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('encode'))), 'image/jpeg', 0.86));
}

export default async function identify(el, ctx) {
  const cfg = ctx.config;
  let common = [];
  try { common = (await api('/api/snakes')).slice(0, 3); } catch { /* optional */ }
  let blobUrl = null;
  ctx.onCleanup(() => { if (blobUrl) URL.revokeObjectURL(blobUrl); });

  el.innerHTML = toHtml(html`
    <div class="banner-red" data-reveal>
      <div class="ico">${icon('sos', 24)}</div>
      <div class="txt"><h3>${t('id.bannerTitle')}</h3><p>${t('id.bannerText')}</p></div>
      <div class="row-wrap"><a class="btn btn-white" style="color:var(--red)" href="#/emergency">${icon('sos', 16)} ${t('home.startShort2')}</a><a class="btn btn-soft" href="${telLink(cfg.emergencyNumber)}">${icon('phone', 16)} ${t('snake.dial', { n: cfg.emergencyNumber })}</a></div>
    </div>

    <div class="split" style="margin-top:22px">
      <section class="card" data-reveal>
        <div class="row between" style="margin-bottom:14px"><div><div class="eyebrow">${t('id.eyebrow')}</div><h2 style="font-size:25px;margin-top:4px">${t('id.title')}</h2></div><span class="chip chip-green"><i class="dot"></i>${t('id.ready')}</span></div>
        <div id="panel"></div>
      </section>
      <div class="stack">
        <section class="card" data-reveal="right"><h3 style="font-size:18px;margin-bottom:12px">${icon('shield', 18)} ${t('id.safetyTitle')}</h3>
          <ul class="num-list">${[1, 2, 3, 4].map((n) => html`<li><i>${n}</i><div>${t(`id.safe${n}`)}</div></li>`)}</ul></section>
        <section class="card" data-reveal="right"><h3 style="font-size:18px;margin-bottom:12px">${t('id.commonTitle')}</h3>
          <div class="stack" style="gap:10px">${common.map((s) => html`<a class="mini-snake" href="#/snakes/${s.id}">${snakeImg(s, { badge: false })}<div class="grow"><b>${nameOf(s)}</b><div class="muted xs italic">${s.scientificName}</div></div><span class="chip ${s.venomous ? 'chip-red' : 'chip-green'}">${s.venomous ? t('snake.venomous') : t('snake.nonVenomous')}</span></a>`)}
            <a class="btn-link" href="#/snakes">${t('id.browse')} ${icon('arrow', 15)}</a></div></section>
      </div>
    </div>`);

  const panel = $('#panel', el);

  const idle = () => html`
    <div class="dropzone" id="drop">
      <div class="cam">${icon('camera', 26)}</div>
      <h3 style="font-size:19px">${t('id.drop')}</h3>
      <p class="muted small" style="max-width:380px">${t('id.dropSub')}</p>
      <div class="row-wrap" style="justify-content:center;margin-top:6px">
        <label class="btn" tabindex="0">${icon('camera', 16)} ${t('id.camera')}<input type="file" accept="image/*" capture="environment" class="sr-only" data-file></label>
        <label class="btn btn-soft" tabindex="0">${icon('upload', 16)} ${t('id.upload')}<input type="file" accept="image/jpeg,image/png,image/webp,image/*" class="sr-only" data-file></label>
      </div>
    </div>
    <div class="card-flat row" style="margin-top:14px;gap:12px">${icon('info', 20)}<span class="small">${t('id.distance')}</span></div>`;

  const busy = () => html`<div class="locate" style="padding:30px 10px"><img class="preview-img" src="${blobUrl}" alt="" style="max-height:240px"><div class="spinner"></div><b>${t('id.analysing')}</b></div>`;

  function result(r) {
    const vcls = r.venomous === 'YES' ? 'chip-red' : r.venomous === 'NO' ? 'chip-green' : 'chip-amber';
    return html`
      <img class="preview-img" src="${blobUrl}" alt="" style="max-height:230px">
      <div class="row between" style="margin:16px 0 4px;flex-wrap:wrap"><h3 style="font-size:19px">${t('id.results')}</h3><span class="chip ${vcls}" style="height:30px;font-size:12.5px">${t(`id.v.${r.venomous}`)}</span></div>
      ${r.mode === 'STUB' ? html`<p class="chip chip-outline" style="margin:6px 0">${t('id.demo')}</p>` : ''}
      ${r.guesses.length ? r.guesses.map((g) => html`<div class="guess"><div class="row between"><div><b>${g.nameEn}</b>${g.nameKn ? html` <span class="muted small">/ ${g.nameKn}</span>` : ''}<div class="muted xs italic">${g.scientificName}</div></div>
        <span class="chip ${g.venomous === 'YES' ? 'chip-red' : g.venomous === 'NO' ? 'chip-green' : 'chip-amber'}">${t(`id.v.${g.venomous}`)}</span></div>
        <div class="row" style="gap:10px"><div class="conf" style="flex:1"><span style="width:${Math.round(g.confidence * 100)}%"></span></div><b class="small">${Math.round(g.confidence * 100)}%</b></div>
        ${g.snakeId ? html`<a class="btn-link" href="#/snakes/${g.snakeId}">${t('id.readMore')} ${icon('arrow', 14)}</a>` : ''}</div>`)
        : html`<p class="muted" style="margin:12px 0">${t('id.noGuess')}</p>`}
      <div class="warn-box" style="margin-top:14px">${icon('warn', 20)}<span>${t('id.disclaimer')} ${t('common.reminder')}</span></div>
      <div class="row-wrap" style="margin-top:14px"><a class="btn btn-red" href="${telLink(r.emergencyNumber)}">${icon('phone', 16)} ${t('snake.dial', { n: r.emergencyNumber })}</a><a class="btn btn-soft" href="#/emergency">${icon('sos', 16)} ${t('home.startShort2')}</a><button class="btn btn-ghost" data-act="again">${icon('refresh', 16)} ${t('id.another')}</button></div>`;
  }

  const show = (h) => { panel.innerHTML = toHtml(h); ctx.motion(panel); };
  show(idle());

  async function handle(file) {
    if (!file) return;
    if (blobUrl) URL.revokeObjectURL(blobUrl);
    blobUrl = URL.createObjectURL(file);
    show(busy());
    try {
      let blob = file;
      const okType = file.type === 'image/jpeg' || file.type === 'image/png';
      if (!okType || file.size > 4.5 * 1024 * 1024) blob = await toJpeg(file);
      const fd = new FormData();
      fd.append('file', blob, blob.type === 'image/png' ? 'snake.png' : 'snake.jpg');
      const keep = $('#keep', el)?.checked;
      if (keep) fd.append('keep', 'true');
      const r = await api('/api/identify', { method: 'POST', body: fd });
      if (!ctx.alive()) return;
      show(result(r));
    } catch (e) {
      toast(e.code === undefined ? t('id.unreadable') : errorText(e), 'err');
      show(idle());
    }
  }

  el.addEventListener('change', (e) => { if (e.target.matches('[data-file]')) handle(e.target.files[0]); });
  el.addEventListener('click', (e) => { if (e.target.closest('[data-act="again"]')) show(idle()); });
  el.addEventListener('keydown', (e) => { if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('label.btn')) { e.preventDefault(); e.target.click(); } });
  ['dragenter', 'dragover'].forEach((ev) => el.addEventListener(ev, (e) => { const d = e.target.closest('#drop'); if (d) { e.preventDefault(); d.classList.add('drag'); } }));
  el.addEventListener('dragleave', (e) => { const d = e.target.closest('#drop'); if (d) d.classList.remove('drag'); });
  el.addEventListener('drop', (e) => { const d = e.target.closest('#drop'); if (d) { e.preventDefault(); d.classList.remove('drag'); handle(e.dataTransfer.files[0]); } });
}
