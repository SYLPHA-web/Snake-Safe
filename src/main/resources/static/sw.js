// Service worker: keeps the app shell, first aid and snake data available offline.
// Hospital stock and alerts are never cached: stale stock could send someone to an empty hospital.
const VERSION = 'v3';
const CACHE = `snakesafe-${VERSION}`;
const SHELL = [
  '/', '/index.html', '/css/app.css', '/img/logo.svg', '/icons/icon-192.png', '/icons/icon-512.png',
  '/vendor/gsap.min.js', '/vendor/ScrollTrigger.min.js', '/fonts/plus-jakarta-sans.woff2', '/fonts/noto-sans-kannada.woff2',
  '/js/app.js', '/js/util.js', '/js/icons.js', '/js/api.js', '/js/geo.js', '/js/motion.js', '/js/ui.js', '/js/nav.js', '/js/i18n.js', '/js/chat.js',
  ...['home', 'hospitals', 'hospital', 'snakes', 'snake', 'firstaid', 'catchers', 'catcher', 'rescue', 'identify', 'emergency', 'login', 'profile', 'staff', 'admin', 'notfound'].map((p) => `/js/pages/${p}.js`),
];
const OFFLINE_DATA = [/^\/api\/config$/, /^\/api\/first-aid$/, /^\/api\/snakes(\/\d+)?$/];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => Promise.allSettled(SHELL.map((u) => c.add(u)))).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== location.origin) return;

  if (url.pathname.startsWith('/api/')) {
    if (OFFLINE_DATA.some((re) => re.test(url.pathname))) e.respondWith(networkFirst(req));
    return; // everything else goes straight to the network
  }
  if (url.pathname.startsWith('/ws/')) return;

  if (req.mode === 'navigate') { e.respondWith(fetch(req).catch(() => caches.match('/index.html'))); return; }
  e.respondWith(staleWhileRevalidate(req));
});

async function networkFirst(req) {
  const cache = await caches.open(CACHE);
  try {
    const res = await fetch(req);
    if (res.ok) cache.put(req, res.clone());
    return res;
  } catch {
    const hit = await cache.match(req);
    return hit || new Response(JSON.stringify({ error: 'OFFLINE', message: 'No connection.' }), { status: 503, headers: { 'Content-Type': 'application/json' } });
  }
}

async function staleWhileRevalidate(req) {
  const cache = await caches.open(CACHE);
  const hit = await cache.match(req);
  const fresh = fetch(req).then((res) => { if (res.ok) cache.put(req, res.clone()); return res; }).catch(() => null);
  return hit || (await fresh) || new Response('', { status: 504 });
}
