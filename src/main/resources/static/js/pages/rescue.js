import { html, toHtml, $ } from '../util.js';
import { api, tokens } from '../api.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { telLink, confirmDialog, toast, errorText } from '../ui.js';

export const chrome = 'full';
export const title = 'nav.catchers';

const STEPS = ['REQUESTED', 'ACCEPTED', 'DONE'];

export default async function rescue(el, ctx) {
  const id = ctx.params[0];
  let timer;

  const draw = (r) => {
    const isCancelled = r.status === 'CANCELLED';
    const isDeclined = r.status === 'DECLINED';
    const isRequested = r.status === 'REQUESTED';
    const idx = STEPS.indexOf(r.status);

    let stateIcon = 'clock';
    let stateColor = 'ico-blue';
    if (r.status === 'DONE') {
      stateIcon = 'check';
      stateColor = 'ico-green';
    } else if (isCancelled || isDeclined) {
      stateIcon = 'xcircle';
      stateColor = 'ico-red';
    }

    const subtitle = isCancelled ? t('rescue.cancelledSub')
      : isDeclined ? t('rescue.declinedSub')
      : t('rescue.sub', { name: r.catcherName || '' });

    el.innerHTML = toHtml(html`
      <div class="crumbs"><a href="#/catchers">${icon('shieldUser', 15)} ${t('nav.catchers')}</a><span>›</span><span>${t('rescue.title')}</span></div>
      <div class="flow-wrap"><section class="card">
        <div class="big-state">
          <div class="ico ${stateColor}">${icon(stateIcon, 42)}</div>
          <h2>${t(`rescue.${r.status}`)}</h2>
          <p class="muted">${subtitle}</p>
        </div>
        ${isCancelled || isDeclined ? html`
          <div class="how-steps" style="grid-template-columns:repeat(2,1fr);margin-top:24px">
            <div class="how-step on"><div class="n">1</div><h4>${t('rescue.REQUESTED.short')}</h4></div>
            <div class="how-step on"><div class="n" style="background:var(--red);color:#fff;box-shadow:0 0 0 3px var(--red-soft),var(--shadow)">${icon('x', 22)}</div><h4>${t(`rescue.${r.status}.short`)}</h4></div>
          </div>
        ` : html`
          <div class="how-steps" style="grid-template-columns:repeat(3,1fr);margin-top:24px">
            ${STEPS.map((s, i) => html`<div class="how-step ${i <= idx ? 'on' : ''}"><div class="n">${i + 1}</div><h4>${t(`rescue.${s}.short`)}</h4></div>`)}
          </div>
        `}
        <div class="stack" style="margin-top:22px">
          ${isCancelled ? html`
            <a class="btn btn-lg btn-block" href="#/catchers">${icon('shieldUser', 18)} ${t('rescue.backToCatchers')}</a>
            <a class="btn btn-soft btn-lg btn-block" href="#/">${icon('home', 18)} ${t('common.home')}</a>
          ` : isDeclined ? html`
            <a class="btn btn-lg btn-block" href="#/catchers">${icon('shieldUser', 18)} ${t('rescue.chooseAnother')}</a>
            <a class="btn btn-soft btn-lg btn-block" href="#/">${icon('home', 18)} ${t('common.home')}</a>
          ` : html`
            ${r.catcherPhone ? html`<a class="btn btn-lg btn-block" href="${telLink(r.catcherPhone)}">${icon('phone', 18)} ${t('catch.callDirect')}</a>` : ''}
            ${isRequested ? html`<button class="btn btn-soft btn-block" type="button" data-act="cancel" data-id="${r.id}" style="margin-top:8px">${icon('xcircle', 18)} ${t('rescue.cancel')}</button>` : ''}
          `}
        </div>
        <div class="warn-box" style="margin-top:18px">
          ${icon('warn', 20)}
          <span>${t('catch.notMedical', { n: ctx.config.emergencyNumber })} <a href="${telLink(ctx.config.emergencyNumber)}" class="bold" style="text-decoration:underline">${t('common.call108')}</a></span>
        </div>
      </section></div>`);
  };

  const startPolling = () => {
    if (timer) clearInterval(timer);
    timer = setInterval(async () => {
      if (!ctx.alive()) return;
      try {
        const r = await api(`/api/rescues/${encodeURIComponent(id)}`, { auth: tokens.get('user') ? 'user' : undefined });
        if (!$('.flow-wrap', el)) return;
        draw(r);
        if (r.status === 'CANCELLED' || r.status === 'DECLINED' || r.status === 'DONE') {
          clearInterval(timer);
        }
      } catch { /* keep last view */ }
    }, 10000);
  };

  let initial;
  try {
    initial = await api(`/api/rescues/${encodeURIComponent(id)}`, { auth: tokens.get('user') ? 'user' : undefined });
  } catch (err) {
    el.innerHTML = toHtml(html`
      <div class="crumbs"><a href="#/catchers">${icon('shieldUser', 15)} ${t('nav.catchers')}</a><span>›</span><span>${t('rescue.title')}</span></div>
      <div class="flow-wrap"><section class="card">
        <div class="big-state">
          <div class="ico ico-red">${icon('xcircle', 42)}</div>
          <h2>${t('err.RESCUE_NOT_FOUND')}</h2>
          <p class="muted">${errorText(err)}</p>
        </div>
        <div class="stack" style="margin-top:22px">
          <a class="btn btn-lg btn-block" href="#/catchers">${icon('shieldUser', 18)} ${t('rescue.backToCatchers')}</a>
          <a class="btn btn-soft btn-lg btn-block" href="#/">${icon('home', 18)} ${t('common.home')}</a>
        </div>
      </section></div>`);
    return;
  }

  draw(initial);
  if (initial.status !== 'CANCELLED' && initial.status !== 'DECLINED' && initial.status !== 'DONE') {
    startPolling();
  }
  ctx.onCleanup(() => { if (timer) clearInterval(timer); });

  el.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-act="cancel"]');
    if (!btn) return;
    const rescueId = btn.dataset.id || id;
    if (!rescueId) return;
    const ok = await confirmDialog(t('rescue.cancelTitle'), t('rescue.cancelAsk'), t('rescue.cancelYes'));
    if (!ok) return;
    btn.disabled = true;
    try {
      const updated = await api(`/api/rescues/${encodeURIComponent(rescueId)}/cancel`, {
        method: 'POST',
        auth: tokens.get('user') ? 'user' : undefined,
      });
      if (timer) clearInterval(timer);
      draw(updated);
      toast(t('rescue.CANCELLED'));
    } catch (ex) {
      btn.disabled = false;
      toast(errorText(ex), 'err');
    }
  });
}
