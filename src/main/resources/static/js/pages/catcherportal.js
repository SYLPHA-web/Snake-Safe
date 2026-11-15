import { html, toHtml, $, hhmm } from '../util.js';
import { api, tokens } from '../api.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { telLink, openModal, confirmDialog, toast, errorText } from '../ui.js';
import { themeSwitch } from '../nav.js';

export const chrome = 'bare';
export const title = 'cp.title';

let audio = null;
function beep() {
  try {
    audio = audio || new (window.AudioContext || window.webkitAudioContext)();
    const o = audio.createOscillator(); const g = audio.createGain();
    o.connect(g); g.connect(audio.destination); o.frequency.value = 880; g.gain.setValueAtTime(0.18, audio.currentTime);
    g.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + 0.5); o.start(); o.stop(audio.currentTime + 0.5);
  } catch { /* sound is optional */ }
}

export default async function catcherportal(el, ctx) {
  const A = { auth: 'catcher' };
  if (!tokens.get('catcher')) return loginView(el, ctx);

  let me;
  try {
    me = await api('/api/catcher/me', A);
  } catch (e) {
    if (e.status === 401 || e.status === 403) {
      tokens.clear('catcher');
      return loginView(el, ctx);
    }
    throw e;
  }

  const S = { me, rescues: [], tab: 'incoming', fresh: new Set() };
  try {
    S.rescues = await api('/api/catcher/rescues', A);
  } catch {
    S.rescues = [];
  }

  let timer = null;
  let baseTitle = document.title;
  let seenIds = new Set(S.rescues.map((r) => r.id));

  ctx.onCleanup(() => {
    if (timer) clearInterval(timer);
    document.title = baseTitle;
  });

  ctx.onLang = () => draw();

  const incoming = () => S.rescues.filter((r) => r.status === 'REQUESTED');
  const active = () => S.rescues.filter((r) => r.status === 'ACCEPTED');
  const history = () => S.rescues.filter((r) => r.status !== 'REQUESTED' && r.status !== 'ACCEPTED');

  function mapLink(lat, lng) {
    return `https://www.google.com/maps?q=${lat},${lng}`;
  }

  function card(r) {
    const isRequested = r.status === 'REQUESTED';
    const isAccepted = r.status === 'ACCEPTED';
    const isDone = r.status === 'DONE';
    const isDeclined = r.status === 'DECLINED';
    const isCancelled = r.status === 'CANCELLED';

    let chipClass = 'chip-blue';
    if (isAccepted) chipClass = 'chip-green';
    else if (isDone) chipClass = 'chip-green';
    else if (isDeclined || isCancelled) chipClass = 'chip-red';

    return html`
      <article class="alert-card ${isAccepted ? 'confirmed' : ''} ${isDone || isDeclined || isCancelled ? 'closed' : ''} ${S.fresh.has(r.id) ? 'fresh' : ''}" style="margin-bottom:14px">
        <div class="row between" style="flex-wrap:wrap">
          <span class="chip ${chipClass}">${t(`rescue.${r.status}`)}</span>
          <span class="muted xs">${r.createdAt ? hhmm(r.createdAt) : ''}</span>
        </div>
        <div style="margin-top:10px">
          <div class="row" style="gap:10px;align-items:center">
            ${icon('phone', 16)}
            <a class="bold" href="${telLink(r.phone)}" style="font-size:17px;color:var(--text)">${r.phone}</a>
          </div>
          <div class="muted small row" style="gap:6px;margin-top:6px">
            ${icon('pin', 14)}
            <a href="${mapLink(r.lat, r.lng)}" target="_blank" rel="noopener noreferrer" style="color:var(--primary);text-decoration:underline">
              ${r.lat.toFixed(4)}, ${r.lng.toFixed(4)} (${t('cp.openMap')})
            </a>
          </div>
          ${r.notes ? html`<div class="help" style="margin-top:8px;font-style:italic">"${r.notes}"</div>` : ''}
        </div>
        ${isRequested ? html`
          <div class="row-wrap" style="margin-top:14px">
            <button class="btn btn-sm" data-act="accept" data-id="${r.id}">${icon('check', 16)} ${t('cp.accept')}</button>
            <button class="btn btn-danger-soft btn-sm" data-act="decline" data-id="${r.id}">${icon('x', 16)} ${t('cp.decline')}</button>
          </div>
        ` : ''}
        ${isAccepted ? html`
          <div class="row-wrap" style="margin-top:14px">
            <a class="btn btn-soft btn-sm" href="${telLink(r.phone)}">${icon('phone', 15)} ${t('cp.callCaller')}</a>
            <button class="btn btn-mint btn-sm" data-act="done" data-id="${r.id}">${icon('check', 16)} ${t('cp.markDone')}</button>
          </div>
        ` : ''}
      </article>`;
  }

  function draw() {
    const inc = incoming();
    const act = active();
    const hist = history();

    el.innerHTML = toHtml(html`
      <header class="staff-head"><div class="container in">
        <span class="brand-mark">${icon('shieldUser', 24)}</span>
        <div><b style="font-size:18px;color:var(--green)">${S.me.name}</b><div class="muted xs">${S.me.district} · ${t('cp.desk')}</div></div>
        <div class="top-actions">
          ${themeSwitch()}
          <button class="lang-toggle" data-lang-toggle type="button"><span class="${t('lang.code') === 'en' ? 'on' : ''}">EN</span><span class="${t('lang.code') === 'kn' ? 'on' : ''}">ಕನ್ನಡ</span></button>
          <button class="btn btn-soft btn-sm" data-act="logout">${icon('logout', 15)} ${t('cp.logout')}</button>
        </div>
      </div></header>
      <div class="container page" style="padding-bottom:50px">
        <div class="tabs" role="tablist" style="margin-bottom:18px">
          <button class="btn btn-sm ${S.tab === 'incoming' ? '' : 'btn-soft'}" role="tab" aria-selected="${String(S.tab === 'incoming')}" data-tab="incoming">
            ${t('cp.incoming')} ${inc.length ? html`<span class="chip chip-amber" style="margin-left:4px;padding:2px 6px;font-size:11px">${inc.length}</span>` : ''}
          </button>
          <button class="btn btn-sm ${S.tab === 'active' ? '' : 'btn-soft'}" role="tab" aria-selected="${String(S.tab === 'active')}" data-tab="active">
            ${t('cp.active')} ${act.length ? html`<span class="chip chip-green" style="margin-left:4px;padding:2px 6px;font-size:11px">${act.length}</span>` : ''}
          </button>
          <button class="btn btn-sm ${S.tab === 'history' ? '' : 'btn-soft'}" role="tab" aria-selected="${String(S.tab === 'history')}" data-tab="history">
            ${t('cp.history')}
          </button>
        </div>

        <section>
          ${S.tab === 'incoming' ? html`
            <div class="stack">
              ${inc.length ? inc.map(card) : html`<div class="card empty">${icon('bell', 40)}<h3>${t('cp.noneIncoming')}</h3></div>`}
            </div>
          ` : S.tab === 'active' ? html`
            <div class="stack">
              ${act.length ? act.map(card) : html`<div class="card empty">${icon('shield', 40)}<h3>${t('cp.noneActive')}</h3></div>`}
            </div>
          ` : html`
            <div class="stack">
              ${hist.length ? hist.map(card) : html`<div class="card empty">${icon('clock', 40)}<h3>${t('cp.noneHistory')}</h3></div>`}
            </div>
          `}
        </section>
      </div>`);
  }

  const pollRescues = async () => {
    if (!ctx.alive()) return;
    try {
      const list = await api('/api/catcher/rescues', A);
      list.forEach((r) => {
        if (!seenIds.has(r.id) && r.status === 'REQUESTED') {
          seenIds.add(r.id);
          S.fresh.add(r.id);
          beep();
          document.title = `(!) ${t('cp.newRequest')}`;
          setTimeout(() => {
            document.title = baseTitle;
            S.fresh.delete(r.id);
          }, 8000);
        }
      });
      S.rescues = list;
      draw();
    } catch (e) {
      if (e.status === 401 || e.status === 403) {
        tokens.clear('catcher');
        ctx.rerender();
      }
    }
  };

  timer = setInterval(pollRescues, 6000);

  el.addEventListener('click', async (e) => {
    const tabBtn = e.target.closest('[data-tab]');
    if (tabBtn) {
      S.tab = tabBtn.dataset.tab;
      draw();
      return;
    }

    const b = e.target.closest('[data-act]');
    if (!b) return;
    const act = b.dataset.act;
    const id = b.dataset.id;

    if (act === 'logout') {
      tokens.clear('catcher');
      ctx.rerender();
      return;
    }

    if (act === 'accept') {
      b.disabled = true;
      try {
        await api(`/api/catcher/rescues/${encodeURIComponent(id)}/accept`, { method: 'POST', ...A });
        toast(t('rescue.ACCEPTED'), 'ok');
        S.tab = 'active';
        await pollRescues();
      } catch (ex) {
        b.disabled = false;
        toast(errorText(ex), 'err');
      }
      return;
    }

    if (act === 'decline') {
      const ok = await confirmDialog(t('cp.decline'), t('cp.declineAsk'), t('cp.declineYes'));
      if (!ok) return;
      b.disabled = true;
      try {
        await api(`/api/catcher/rescues/${encodeURIComponent(id)}/decline`, { method: 'POST', ...A });
        toast(t('rescue.DECLINED'), 'ok');
        await pollRescues();
      } catch (ex) {
        b.disabled = false;
        toast(errorText(ex), 'err');
      }
      return;
    }

    if (act === 'done') {
      const ok = await confirmDialog(t('cp.markDone'), t('cp.markDoneAsk'), t('cp.markDoneYes'));
      if (!ok) return;
      b.disabled = true;
      try {
        await api(`/api/catcher/rescues/${encodeURIComponent(id)}/done`, { method: 'POST', ...A });
        toast(t('rescue.DONE'), 'ok');
        S.tab = 'history';
        await pollRescues();
      } catch (ex) {
        b.disabled = false;
        toast(errorText(ex), 'err');
      }
      return;
    }
  });

  draw();
}

function loginView(el, ctx) {
  let phone = '';
  let timer = null;
  ctx.onCleanup(() => clearInterval(timer));

  const stepPhone = () => html`
    <div class="mark">${icon('shieldUser', 30)}</div>
    <div class="center eyebrow" style="color:var(--green)">${t('cp.portal')}</div>
    <h1>${t('cp.signIn')}</h1>
    <p class="sub">${t('cp.signInSub')}</p>
    <form id="f1" class="stack" style="gap:14px" novalidate>
      <div class="field">
        <label for="phone">${t('login.phone')}</label>
        <div class="input-icon">
          ${icon('phone', 18)}
          <input class="input" id="phone" type="tel" inputmode="tel" autocomplete="tel" placeholder="90000 00001" value="${phone}" required>
        </div>
      </div>
      <p class="error-text hidden" id="err"></p>
      <button class="btn btn-lg btn-block" type="submit">${t('login.send')} ${icon('arrow', 18)}</button>
    </form>`;

  const stepCode = () => html`
    <div class="mark">${icon('shield', 30)}</div>
    <div class="center eyebrow" style="color:var(--green)">${t('cp.portal')}</div>
    <h1>${t('login.codeTitle')}</h1>
    <p class="sub">${t('login.codeSub', { phone })}</p>
    ${ctx.config.otpMode === 'DEMO' ? html`<p class="chip chip-amber" style="justify-self:center">${t('login.demoHint')}</p>` : ''}
    <form id="f2" class="stack" style="gap:14px" novalidate>
      <div class="field">
        <label for="code">${t('login.code')}</label>
        <input class="input" id="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" placeholder="••••••" style="text-align:center;font-size:24px;letter-spacing:.4em;font-weight:800" required>
      </div>
      <p class="error-text hidden" id="err"></p>
      <button class="btn btn-lg btn-block" type="submit">${t('login.verify')}</button>
    </form>
    <div class="row between">
      <button class="btn-link" id="change" type="button">${icon('back', 14)} ${t('login.change')}</button>
      <button class="btn-link" id="resend" type="button">${t('login.resend')}</button>
    </div>`;

  el.innerHTML = toHtml(html`
    <div style="min-height:80vh;display:grid;place-items:center;padding:20px 0">
      <div style="width:100%">
        <div class="auth-card" style="margin-top:0" id="card" data-reveal="zoom"></div>
        <p class="center small" style="margin-top:16px"><a class="btn-link" href="#/">${icon('back', 14)} ${t('common.home')}</a></p>
      </div>
    </div>`);

  const cardEl = $('#card', el);

  const show = (template) => {
    cardEl.innerHTML = toHtml(template);
    const err = (m) => {
      const e = $('#err', cardEl);
      if (e) {
        e.textContent = m;
        e.classList.remove('hidden');
      }
    };
    const f1 = $('#f1', cardEl);
    const f2 = $('#f2', cardEl);

    if (f1) {
      $('#phone', cardEl).focus();
      f1.addEventListener('submit', async (e) => {
        e.preventDefault();
        phone = $('#phone', cardEl).value.trim();
        if (!phone) {
          err(t('err.INVALID_PHONE'));
          return;
        }
        const b = f1.querySelector('button');
        b.disabled = true;
        try {
          await api('/api/catcher/login/send', { method: 'POST', body: { phone } });
          show(stepCode());
          startCooldown();
        } catch (ex) {
          err(errorText(ex));
          b.disabled = false;
        }
      });
    }

    if (f2) {
      $('#code', cardEl).focus();
      f2.addEventListener('submit', async (e) => {
        e.preventDefault();
        const code = $('#code', cardEl).value.trim();
        if (!/^\d{6}$/.test(code)) {
          err(t('login.codeBad'));
          return;
        }
        const b = f2.querySelector('button');
        b.disabled = true;
        try {
          const r = await api('/api/catcher/login/verify', { method: 'POST', body: { phone, code } });
          tokens.set('catcher', r.token);
          toast(t('login.welcome'), 'ok');
          ctx.rerender();
        } catch (ex) {
          err(errorText(ex));
          b.disabled = false;
        }
      });

      $('#change', cardEl).addEventListener('click', () => show(stepPhone()));
      $('#resend', cardEl).addEventListener('click', async () => {
        try {
          await api('/api/catcher/login/send', { method: 'POST', body: { phone } });
          toast(t('login.sent'), 'ok');
          startCooldown();
        } catch (ex) {
          err(errorText(ex));
        }
      });
    }
  };

  function startCooldown() {
    const b = $('#resend', cardEl);
    if (!b) return;
    let left = 30;
    b.disabled = true;
    clearInterval(timer);
    timer = setInterval(() => {
      left -= 1;
      const r = $('#resend', cardEl);
      if (!r) {
        clearInterval(timer);
        return;
      }
      if (left <= 0) {
        clearInterval(timer);
        r.disabled = false;
        r.textContent = t('login.resend');
      } else {
        r.textContent = `${t('login.resend')} (${left})`;
      }
    }, 1000);
  }

  show(stepPhone());
}
