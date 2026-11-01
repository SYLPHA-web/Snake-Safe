import { store } from './util.js';

export class ApiError extends Error {
  constructor(code, message, status) { super(message); this.code = code; this.status = status; }
}

const KEYS = { user: 'ss-token', staff: 'ss-staff-token', admin: 'ss-admin-token' };
export const tokens = {
  get: (kind) => store.get(KEYS[kind]),
  set: (kind, v) => store.set(KEYS[kind], v),
  clear: (kind) => store.del(KEYS[kind]),
};

/** auth: 'user' | 'staff' | 'admin'. body: object (sent as JSON) or FormData. */
export async function api(path, { method = 'GET', body, auth, signal } = {}) {
  const headers = {};
  let payload;
  if (body instanceof FormData) payload = body;
  else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
  const token = auth ? tokens.get(auth) : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(path, { method, headers, body: payload, signal });
  } catch (e) {
    if (e.name === 'AbortError') throw e;
    throw new ApiError('OFFLINE', 'No connection.', 0);
  }
  if (res.status === 204) return null;
  const text = await res.text();
  let data = null;
  if (text) { try { data = JSON.parse(text); } catch { data = text; } }
  if (!res.ok) {
    if (res.status === 401 && auth) { tokens.clear(auth); window.dispatchEvent(new CustomEvent('authexpired', { detail: auth })); }
    throw new ApiError(data?.error || 'ERROR', data?.message || 'Something went wrong.', res.status);
  }
  return data;
}

export async function download(path, filename, auth) {
  const token = auth ? tokens.get(auth) : null;
  const res = await fetch(path, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!res.ok) throw new ApiError('ERROR', 'Download failed.', res.status);
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url; a.download = filename; document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}
