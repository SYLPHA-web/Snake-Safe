// Header with React-Bits-style pill nav (circle fill + label swap, driven by GSAP), mobile bottom nav, flow header, footer.
import { html, toHtml, $, $$, session, store } from './util.js';
import { icon } from './icons.js';
import { t, getLang, setLang } from './i18n.js';
import { gsap, motionOn } from './motion.js';
import { telLink } from './ui.js';

/** Sliding light/dark switch. Reused by the staff and admin headers. */
export function themeSwitch() {
  const dark = document.documentElement.dataset.theme === 'dark';
  return html`<button class="theme-switch" type="button" role="switch" aria-checked="${String(dark)}" aria-label="${t('theme.toggle')}" title="${t('theme.toggle')}" data-theme-toggle>
    <span class="ts-ico ts-sun">${icon('sun', 14)}</span><span class="ts-ico ts-moon">${icon('moon', 14)}</span>
    <span class="ts-knob">${icon('sun', 16, 'k-sun')}${icon('moon', 16, 'k-moon')}</span></button>`;
}

export function setTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  store.set('ss-theme', theme);
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', theme === 'dark' ? '#0a121e' : '#003d1f');
  $$('.theme-switch').forEach((b) => b.setAttribute('aria-checked', String(theme === 'dark')));
}

const ITEMS = [
  ['/', 'nav.home'],
  ['/identify', 'nav.identify'],
  ['/snakes', 'nav.snakes'],
  ['/catchers', 'nav.catchers'],
  ['/first-aid', 'nav.firstAid'],
  ['/hospitals', 'nav.hospitals'],
];

let cfg = null;
let mode = 'full';
let route = '/';
let flow = { step: 1, total: 4 };
let installEvent = null;
window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); installEvent = e; $$('[data-install]').forEach((b) => b.classList.remove('hidden')); });

const top = () => $('#chrome-top');
const bottom = () => $('#chrome-bottom');
const foot = () => $('#chrome-foot');

export function initChrome(config) { cfg = config; }

export function setChrome(m, path) {
  mode = m; route = path || route;
  document.body.dataset.chrome = m;
  render();
}

export function setFlowStep(step, total = 4) {
  flow = { step, total };
  const prog = $('.flow-progress span');
  if (prog) prog.style.width = `${(step / total) * 100}%`;
  const label = $('.flow-step');
  if (label) label.innerHTML = `<i>${step}</i><span class="t">${t('flow.step', { n: step, total })}</span>`;
}

export function refreshChrome() { render(); }

function isActive(p) {
  if (p === '/') return route === '/';
  return route === p || route.startsWith(`${p}/`);
}

function brandHtml() {
  return `<a class="brand" href="#/" aria-label="${cfg.name}">
    <span class="brand-mark">${toHtml(icon('pulse', 22))}</span>
    <span class="brand-text"><b>${cfg.name}<span>${t('brand.region')}</span></b><small>${t('brand.sub')}</small></span></a>`;
}

function langToggle() {
  const kn = getLang() === 'kn';
  return `<button class="lang-toggle" data-lang-toggle type="button" aria-label="${t('nav.language')}"><span class="${kn ? '' : 'on'}">EN</span><span class="${kn ? 'on' : ''}">ಕನ್ನಡ</span></button>`;
}

function render() {
  if (!cfg) return;
  const tb = top(); const bt = bottom(); const ft = foot();
  const active = !!session.get('ss-alert');
  const alertBar = active && mode === 'full' && !route.startsWith('/emergency')
    ? `<a class="alert-bar" href="#/emergency/alert/${session.get('ss-alert')}">${t('flow.activeAlert')} →</a>` : '';

  if (mode === 'bare') { tb.innerHTML = ''; bt.innerHTML = ''; ft.innerHTML = ''; return; }

  if (mode === 'flow') {
    tb.innerHTML = `<div class="flow-head"><div class="container flow-top">
      ${brandHtml()}
      <div class="flow-step"><i>${flow.step}</i><span class="t">${t('flow.step', { n: flow.step, total: flow.total })}</span></div>
      <div class="top-actions">
        ${toHtml(themeSwitch())}${langToggle()}
        <a class="btn btn-red btn-sm" href="${telLink(cfg.emergencyNumber)}">${toHtml(icon('phone', 15))} ${t('flow.call', { n: cfg.emergencyNumber })}</a>
        <a class="btn btn-soft btn-sm" href="#/" data-exit-flow>${toHtml(icon('x', 15))}<span class="t">${t('flow.exit')}</span></a>
      </div></div><div class="flow-progress"><span style="width:${(flow.step / flow.total) * 100}%"></span></div></div>`;
    bt.innerHTML = '';
  } else {
    tb.innerHTML = `${navigator.onLine ? '' : `<div class="offline-bar">${t('offline.bar')}</div>`}${alertBar}
      <header class="topbar"><div class="container topbar-in">
        ${brandHtml()}
        <nav class="pillnav" aria-label="Main"><ul>${ITEMS.map(([p, k]) => `
          <li><a class="pill${isActive(p) ? ' is-active' : ''}" href="#${p}"${isActive(p) ? ' aria-current="page"' : ''}>
            <span class="hover-circle" aria-hidden="true"></span>
            <span class="label-stack"><span class="pill-label">${t(k)}</span><span class="pill-label-hover" aria-hidden="true">${t(k)}</span></span>
          </a></li>`).join('')}</ul></nav>
        <div class="top-actions">
          ${toHtml(themeSwitch())}${langToggle()}
          <a class="sos-btn" href="#/emergency">${toHtml(icon('sos', 16))} ${t('nav.sos')}</a>
          <a class="avatar" href="#/profile" aria-label="${t('nav.profile')}">${toHtml(icon('user', 19))}</a>
        </div></div></header>`;
    const bi = [['/', 'home', 'nav.home'], ['/hospitals', 'hospital', 'nav.hospitals'], ['/emergency', 'sos', 'nav.emergency'], ['/first-aid', 'kit', 'nav.firstAid'], ['/profile', 'user', 'nav.profile']];
    bt.innerHTML = `<nav class="bottom-nav" aria-label="Main"><ul>${bi.map(([p, ic, k]) => `
      <li><a href="#${p}" class="${isActive(p) ? 'is-active' : ''}${p === '/emergency' ? ' sos' : ''}"><span class="ico">${toHtml(icon(ic, p === '/emergency' ? 26 : 20))}</span>${t(k)}</a></li>`).join('')}</ul></nav>`;
  }

  ft.innerHTML = footerHtml();
  setupPills();
}

function footerHtml() {
  return `<footer class="site-footer"><div class="container">
    <div class="foot-grid">
      <div>
        <div class="brand"><span class="brand-mark">${toHtml(icon('pulse', 22))}</span><span class="brand-text"><b>${cfg.name}</b></span></div>
        <p>${t('foot.about')}</p>
        <p class="bold" style="color:var(--red);margin-top:10px">${t('common.reminder')}</p>
      </div>
      <div>
        <h5>${t('foot.numbers')}</h5>
        <a class="call-tile" href="${telLink(cfg.emergencyNumber)}"><span><small>${t('foot.ambulance')}</small><b>${t('foot.dial', { n: cfg.emergencyNumber })}</b></span>${toHtml(icon('phone', 20))}</a>
        <a class="call-tile alt" href="${telLink(cfg.poisonHelpline)}"><span><small>${t('foot.poison')}</small><b>${cfg.poisonHelpline}</b></span>${toHtml(icon('phone', 20))}</a>
      </div>
      <div>
        <h5>${t('foot.explore')}</h5>
        <div class="foot-links">
          <a href="#/first-aid">${t('nav.firstAid')}</a><a href="#/hospitals">${t('nav.hospitals')}</a><a href="#/snakes">${t('nav.snakes')}</a>
          <a href="#/catchers">${t('nav.catchers')}</a><a href="#/identify">${t('nav.identify')}</a><a href="#/staff">${t('foot.staff')}</a>
          <button class="btn-link ${installEvent ? '' : 'hidden'}" data-install type="button">${toHtml(icon('download', 15))} ${t('foot.install')}</button>
        </div>
      </div>
    </div>
    <div class="foot-bottom"><span>© ${cfg.name}</span><span>${t('common.reminder')}</span></div>
  </div></footer>`;
}

// ----- pill hover effect (from the React Bits "PillNav" idea) -----
const tweens = new WeakMap();

function setupPills() {
  if (!motionOn || window.matchMedia('(hover: none)').matches) return;
  $$('.pillnav .pill:not(.is-active)').forEach((pill) => {
    const circle = pill.querySelector('.hover-circle');
    const label = pill.querySelector('.pill-label');
    const hover = pill.querySelector('.pill-label-hover');
    const { width: w, height: h } = pill.getBoundingClientRect();
    if (!w) return;
    const R = ((w * w) / 4 + h * h) / (2 * h);
    const D = Math.ceil(2 * R) + 2;
    const delta = Math.ceil(R - Math.sqrt(Math.max(0, R * R - (w * w) / 4))) + 1;
    circle.style.width = `${D}px`;
    circle.style.height = `${D}px`;
    circle.style.bottom = `-${delta}px`;
    gsap.set(circle, { xPercent: -50, scale: 0, transformOrigin: `50% ${D - delta}px` });
    gsap.set(label, { y: 0 });
    gsap.set(hover, { y: h + 12, opacity: 0 });
    const tl = gsap.timeline({ paused: true });
    tl.to(circle, { scale: 1.2, xPercent: -50, duration: 0.5, ease: 'power3.out' }, 0);
    tl.to(label, { y: -(h + 8), duration: 0.5, ease: 'power3.out' }, 0);
    tl.to(hover, { y: 0, opacity: 1, duration: 0.5, ease: 'power3.out' }, 0);
    tweens.set(pill, tl);
  });
}

document.addEventListener('mouseover', (e) => {
  const pill = e.target.closest && e.target.closest('.pill');
  if (!pill || (e.relatedTarget && pill.contains(e.relatedTarget))) return;
  const tl = tweens.get(pill);
  if (tl) tl.tweenTo(tl.duration(), { duration: 0.3, ease: 'power3.out', overwrite: 'auto' });
});
document.addEventListener('mouseout', (e) => {
  const pill = e.target.closest && e.target.closest('.pill');
  if (!pill || (e.relatedTarget && pill.contains(e.relatedTarget))) return;
  const tl = tweens.get(pill);
  if (tl) tl.tweenTo(0, { duration: 0.2, ease: 'power3.out', overwrite: 'auto' });
});

let rt;
window.addEventListener('resize', () => { clearTimeout(rt); rt = setTimeout(setupPills, 150); });
if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => setupPills());

// global chrome clicks
document.addEventListener('click', async (e) => {
  if (e.target.closest('[data-theme-toggle]')) { setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'); return; }
  if (e.target.closest('[data-lang-toggle]')) { setLang(getLang() === 'kn' ? 'en' : 'kn'); return; }
  if (e.target.closest('[data-install]') && installEvent) { installEvent.prompt(); await installEvent.userChoice; installEvent = null; $$('[data-install]').forEach((b) => b.classList.add('hidden')); }
});
window.addEventListener('online', render);
window.addEventListener('offline', render);
