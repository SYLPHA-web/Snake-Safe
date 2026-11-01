import { html, toHtml, $ } from '../util.js';
import { api, tokens } from '../api.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { errorText, toast } from '../ui.js';

export const chrome = 'full';
export const title = 'login.title';

export default async function login(el, ctx) {
  const next = ctx.query.get('next') || '/profile';
  if (tokens.get('user')) { ctx.navigate('/profile'); return; }
  let phone = '';
  let timer = null;
  ctx.onCleanup(() => clearInterval(timer));

  const stepPhone = () => html`
    <div class="mark">${icon('phone', 28)}</div>
    <h1>${t('login.title')}</h1><p class="sub">${t('login.sub')}</p>
    <form id="f1" class="stack" style="gap:14px" novalidate>
      <div class="field"><label for="phone">${t('login.phone')}</label><div class="input-icon">${icon('phone', 18)}<input class="input" id="phone" type="tel" inputmode="tel" autocomplete="tel" placeholder="98765 43210" value="${phone}" required></div></div>
      <p class="error-text hidden" id="err"></p>
      <button class="btn btn-lg btn-block" type="submit">${t('login.send')} ${icon('arrow', 18)}</button>
    </form>
    <p class="center muted small">${t('login.guest')}</p>
    <a class="btn btn-red btn-block" href="#/emergency">${icon('sos', 16)} ${t('home.startShort2')}</a>`;

  const stepCode = () => html`
    <div class="mark">${icon('shield', 28)}</div>
    <h1>${t('login.codeTitle')}</h1><p class="sub">${t('login.codeSub', { phone })}</p>
    ${ctx.config.otpMode === 'DEMO' ? html`<p class="chip chip-amber" style="justify-self:center">${t('login.demoHint')}</p>` : ''}
    <form id="f2" class="stack" style="gap:14px" novalidate>
      <div class="field"><label for="code">${t('login.code')}</label><input class="input" id="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" placeholder="••••••" style="text-align:center;font-size:24px;letter-spacing:.4em;font-weight:800" required></div>
      <p class="error-text hidden" id="err"></p>
      <button class="btn btn-lg btn-block" type="submit">${t('login.verify')}</button>
    </form>
    <div class="row between"><button class="btn-link" id="change" type="button">${icon('back', 14)} ${t('login.change')}</button><button class="btn-link" id="resend" type="button">${t('login.resend')}</button></div>`;

  const card = document.createElement('div');
  card.className = 'auth-card';
  card.setAttribute('data-reveal', 'zoom');
  el.append(card);

  const show = (h) => {
    card.innerHTML = toHtml(h);
    const err = (m) => { const e = $('#err', card); if (e) { e.textContent = m; e.classList.remove('hidden'); } };
    const f1 = $('#f1', card); const f2 = $('#f2', card);
    if (f1) {
      $('#phone', card).focus();
      f1.addEventListener('submit', async (e) => {
        e.preventDefault();
        phone = $('#phone', card).value.trim();
        if (!phone) { err(t('err.INVALID_PHONE')); return; }
        const b = f1.querySelector('button'); b.disabled = true;
        try { await api('/api/auth/otp/send', { method: 'POST', body: { phone } }); show(stepCode()); startCooldown(); } catch (ex) { err(errorText(ex)); b.disabled = false; }
      });
    }
    if (f2) {
      $('#code', card).focus();
      f2.addEventListener('submit', async (e) => {
        e.preventDefault();
        const code = $('#code', card).value.trim();
        if (!/^\d{6}$/.test(code)) { err(t('login.codeBad')); return; }
        const b = f2.querySelector('button'); b.disabled = true;
        try {
          const r = await api('/api/auth/otp/verify', { method: 'POST', body: { phone, code } });
          tokens.set('user', r.token);
          toast(t('login.welcome'), 'ok');
          ctx.navigate(next);
        } catch (ex) { err(errorText(ex)); b.disabled = false; }
      });
      $('#change', card).addEventListener('click', () => show(stepPhone()));
      $('#resend', card).addEventListener('click', async () => {
        try { await api('/api/auth/otp/send', { method: 'POST', body: { phone } }); toast(t('login.sent'), 'ok'); startCooldown(); } catch (ex) { err(errorText(ex)); }
      });
    }
  };

  function startCooldown() {
    const b = $('#resend', card); if (!b) return;
    let left = 30; b.disabled = true;
    clearInterval(timer);
    timer = setInterval(() => {
      left -= 1; const r = $('#resend', card);
      if (!r) { clearInterval(timer); return; }
      if (left <= 0) { clearInterval(timer); r.disabled = false; r.textContent = t('login.resend'); } else r.textContent = `${t('login.resend')} (${left})`;
    }, 1000);
  }

  show(stepPhone());
}
