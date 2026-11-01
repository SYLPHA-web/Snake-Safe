import { session } from './util.js';

let last = null;
try { const s = session.get('ss-loc'); if (s) last = JSON.parse(s); } catch { last = null; }

export const lastLocation = () => last;

/** Resolves {lat, lng}; rejects with Error whose .code is 'denied' | 'unavailable' | 'timeout' | 'unsupported'. */
export function getLocation({ timeout = 9000, maxAge = 120000 } = {}) {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) { const e = new Error('unsupported'); e.code = 'unsupported'; reject(e); return; }
    navigator.geolocation.getCurrentPosition(
      (p) => {
        last = { lat: +p.coords.latitude.toFixed(6), lng: +p.coords.longitude.toFixed(6) };
        session.set('ss-loc', JSON.stringify(last));
        resolve(last);
      },
      (err) => {
        const e = new Error(err.message);
        e.code = err.code === 1 ? 'denied' : err.code === 3 ? 'timeout' : 'unavailable';
        reject(e);
      },
      { enableHighAccuracy: true, timeout, maximumAge: maxAge },
    );
  });
}

/** Location only if the user already allowed it, so we never pop up a permission prompt unasked. */
export async function quietLocation() {
  if (last) return last;
  try {
    const st = await navigator.permissions?.query({ name: 'geolocation' });
    if (st?.state === 'granted') return await getLocation({ timeout: 6000 });
  } catch { /* permissions API missing */ }
  return null;
}
