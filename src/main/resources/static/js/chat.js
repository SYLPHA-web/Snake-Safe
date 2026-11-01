import { html, toHtml, $ } from './util.js';
import { api } from './api.js';
import { icon } from './icons.js';
import { t, getLang, onLangChange } from './i18n.js';
import { telLink, errorText } from './ui.js';

const MAX_HISTORY = 10;

export function initChat(cfg) {
  const root = $('#chat-root');
  const history = [];
  let open = false; let busy = false;

  root.innerHTML = '<button class="chat-fab" type="button" data-chat-open></button><section class="chat-panel hidden" role="dialog" aria-label="Chat"></section>';
  const fab = $('.chat-fab', root); const panel = $('.chat-panel', root);

  function drawShell() {
    fab.setAttribute('aria-label', t('chat.open'));
    fab.innerHTML = toHtml(icon(open ? 'x' : 'chat', 26));
    const log = $('.chat-log', panel);
    const keep = log ? log.innerHTML : null;
    panel.innerHTML = toHtml(html`
      <div class="chat-head"><div><h4>${t('chat.title')}</h4><small>${t('chat.sub')}</small></div><button class="icon-btn" type="button" data-chat-close aria-label="${t('common.close')}" style="background:rgba(255,255,255,.18);color:#fff;box-shadow:none">${icon('x', 16)}</button></div>
      <a class="chat-call" href="${telLink(cfg.emergencyNumber)}"><span>${t('chat.bite')}</span><b>${icon('phone', 14)} ${t('flow.call', { n: cfg.emergencyNumber })}</b></a>
      <div class="chat-log" aria-live="polite"></div>
      <form class="chat-form" novalidate><input class="input" name="m" maxlength="${cfg.chatMax || 500}" placeholder="${t('chat.placeholder')}" aria-label="${t('chat.placeholder')}" autocomplete="off"><button class="btn" type="submit" aria-label="${t('chat.send')}">${icon('send', 16)}</button></form>`);
    const newLog = $('.chat-log', panel);
    if (keep) newLog.innerHTML = keep; else greet();
  }

  const log = () => $('.chat-log', panel);
  function add(text, who, note) {
    const d = document.createElement('div');
    d.className = `msg ${who}`;
    d.textContent = text;
    if (note) { const n = document.createElement('div'); n.className = 'xs muted'; n.style.marginTop = '6px'; n.textContent = note; d.append(n); }
    log().append(d);
    log().scrollTop = log().scrollHeight;
    return d;
  }

  function greet() {
    add(t('chat.hello'), 'bot');
    const s = document.createElement('div');
    s.className = 'chat-sugg';
    ['chat.s1', 'chat.s2', 'chat.s3'].forEach((k) => { const b = document.createElement('button'); b.type = 'button'; b.textContent = t(k); b.dataset.sugg = t(k); s.append(b); });
    log().append(s);
  }

  async function send(text) {
    if (busy || !text.trim()) return;
    busy = true;
    $('.chat-sugg', panel)?.remove();
    add(text, 'me');
    const typing = document.createElement('div');
    typing.className = 'msg bot'; typing.innerHTML = '<span class="typing"><i></i><i></i><i></i></span>';
    log().append(typing); log().scrollTop = log().scrollHeight;
    try {
      const r = await api('/api/chat', { method: 'POST', body: { message: text.trim(), language: getLang(), history: history.slice(-MAX_HISTORY) } });
      typing.remove();
      add(r.reply, 'bot', r.fallback ? t('chat.quick') : '');
      history.push({ role: 'user', content: text.trim() }, { role: 'assistant', content: r.reply });
    } catch (e) {
      typing.remove();
      add(e.code === 'RATE_LIMITED' ? t('err.RATE_LIMITED') : `${errorText(e)} ${t('chat.fallbackCall', { n: cfg.emergencyNumber })}`, 'bot');
    } finally { busy = false; const i = $('input', panel); if (i) i.focus(); }
  }

  function toggle(force) {
    open = force ?? !open;
    panel.classList.toggle('hidden', !open);
    fab.innerHTML = toHtml(icon(open ? 'x' : 'chat', 26));
    fab.setAttribute('aria-expanded', String(open));
    if (open) setTimeout(() => $('input', panel)?.focus(), 50);
  }

  root.addEventListener('click', (e) => {
    if (e.target.closest('.chat-fab')) toggle();
    else if (e.target.closest('[data-chat-close]')) toggle(false);
    else if (e.target.closest('[data-sugg]')) send(e.target.closest('[data-sugg]').dataset.sugg);
  });
  root.addEventListener('submit', (e) => {
    e.preventDefault();
    const i = $('input', panel); const v = i.value; i.value = '';
    send(v);
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && open && !document.querySelector('.modal-back')) toggle(false); });
  onLangChange(() => { drawShell(); });
  drawShell();
}
