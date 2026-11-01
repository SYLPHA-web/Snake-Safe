import { $, toHtml } from './util.js';
import { api } from './api.js';
import { t, onLangChange, initLang } from './i18n.js';
import { initChrome, setChrome, refreshChrome } from './nav.js';
import { initMotion, killTriggers, pageIn, motionOn } from './motion.js';
import { errorBlock, toast } from './ui.js';
import { initChat } from './chat.js';

const ROUTES = [
  [/^\/$/, 'home'],
  [/^\/hospitals$/, 'hospitals'],
  [/^\/hospitals\/(\d+)$/, 'hospital'],
  [/^\/snakes$/, 'snakes'],
  [/^\/snakes\/(\d+)$/, 'snake'],
  [/^\/first-aid$/, 'firstaid'],
  [/^\/catchers$/, 'catchers'],
  [/^\/catchers\/(\d+)$/, 'catcher'],
  [/^\/rescues\/([\w-]+)$/, 'rescue'],
  [/^\/identify$/, 'identify'],
  [/^\/emergency$/, 'emergency'],
  [/^\/emergency\/alert\/([\w-]+)$/, 'emergency'],
  [/^\/login$/, 'login'],
  [/^\/profile$/, 'profile'],
  [/^\/staff$/, 'staff'],
  [/^\/admin$/, 'admin'],
];

const DEFAULT_CONFIG = { name: 'SnakeSafe', logo: '/img/logo.svg', tagline: '', emergencyNumber: '108', poisonHelpline: '1800-116-117', verifiedHours: 2, defaultLanguage: 'en', otpMode: 'DEMO', cancelWindowSeconds: 5, noReplyMinutes: 2 };

let config = DEFAULT_CONFIG;
let current = null; // { id, cleanups, onLang, path, query }
let counter = 0;

export const navigate = (path) => { location.hash = `#${path}`; };

function parseHash() {
  const raw = location.hash.slice(1) || '/';
  const [path, qs] = raw.split('?');
  return { path: path || '/', query: new URLSearchParams(qs || '') };
}

async function render() {
  if (current) { current.cleanups.splice(0).forEach((f) => { try { f(); } catch { /* ignore */ } }); }
  killTriggers();
  const id = ++counter;
  const { path, query } = parseHash();
  const view = $('#view');

  let name = 'notfound'; let params = [];
  for (const [re, n] of ROUTES) { const m = path.match(re); if (m) { name = n; params = m.slice(1); break; } }

  const ctx = {
    id, path, query, params, config,
    cleanups: [],
    onLang: null,
    alive: () => id === counter,
    onCleanup(fn) { this.cleanups.push(fn); },
    navigate,
    rerender: () => render(),
    motion: (el) => initMotion(el),
  };
  current = ctx;

  const el = document.createElement('div');
  el.className = 'container page';
  view.replaceChildren(el);
  document.title = `${config.name}`;

  let mod;
  try { mod = await import(`./pages/${name}.js`); } catch (e) { console.error(e); el.innerHTML = '<p class="error-text">Could not load this page.</p>'; return; }
  if (!ctx.alive()) return;
  setChrome(mod.chrome || 'full', path);
  window.scrollTo({ top: ctx.keepScroll || 0, behavior: 'instant' });

  try {
    await mod.default(el, ctx);
  } catch (e) {
    if (!ctx.alive()) return;
    console.error(e);
    el.innerHTML = toHtml(errorBlock(e));
  }
  if (!ctx.alive()) return;
  initMotion(el);
  pageIn(el);
  const title = mod.title ? `${t(mod.title)} · ${config.name}` : config.name;
  document.title = title;
}

document.addEventListener('click', (e) => {
  if (e.target.closest('[data-act="retry"]')) render();
});

window.addEventListener('hashchange', render);
window.addEventListener('authexpired', (e) => {
  if (e.detail === 'user') { toast(t('err.UNAUTHORIZED'), 'err'); navigate('/login'); }
});
window.addEventListener('load', () => { if (motionOn) window.ScrollTrigger.refresh(); });

onLangChange(() => {
  refreshChrome();
  if (!current) return;
  if (current.onLang) { current.onLang(); return; }
  const keep = window.scrollY;
  current.keepScroll = keep;
  render().then(() => window.scrollTo({ top: keep, behavior: 'instant' }));
});

async function boot() {
  try { config = { ...DEFAULT_CONFIG, ...(await api('/api/config')) }; } catch { config = DEFAULT_CONFIG; }
  initLang(config.defaultLanguage);
  initChrome(config);
  initChat(config);
  const fav = document.querySelector('link[rel="icon"]');
  if (fav && config.logo) fav.href = config.logo;
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
  await render();
}

boot();
