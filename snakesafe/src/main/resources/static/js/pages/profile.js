import { html, toHtml, $ } from '../util.js';
import { api, tokens } from '../api.js';
import { icon } from '../icons.js';
import { t, setLang, getLang } from '../i18n.js';
import { openModal, confirmDialog, toast, errorText, telLink } from '../ui.js';

export const chrome = 'full';
export const title = 'nav.profile';

export default async function profile(el, ctx) {
  if (!tokens.get('user')) { ctx.navigate('/login?next=/profile'); return; }
  const A = { auth: 'user' };
  const [me, members, contacts] = await Promise.all([api('/api/profile', A), api('/api/profile/members', A), api('/api/profile/contacts', A)]);
  const S = { me, members, contacts };

  const draw = () => {
    el.innerHTML = toHtml(html`
      <div class="split" style="align-items:start">
        <section class="card" data-reveal>
          <div class="row" style="gap:14px"><span class="avatar" style="width:56px;height:56px">${icon('user', 28)}</span><div><h1 style="font-size:24px">${S.me.name || t('profile.noName')}</h1><div class="muted">${S.me.phone}</div></div></div>
          <form id="pf" class="stack" style="margin-top:20px;gap:14px" novalidate>
            <div class="field"><label for="pn">${t('flow.name')}</label><input class="input" id="pn" maxlength="100" value="${S.me.name || ''}"></div>
            <div class="field"><label for="pl">${t('profile.language')}</label><select class="select" id="pl"><option value="en" ${S.me.language === 'en' ? 'selected' : ''}>English</option><option value="kn" ${S.me.language === 'kn' ? 'selected' : ''}>ಕನ್ನಡ</option></select></div>
            <button class="btn" type="submit">${icon('check', 16)} ${t('common.save')}</button>
          </form>
          <div class="row-wrap" style="margin-top:20px">
            <a class="btn btn-red" href="#/emergency">${icon('sos', 16)} ${t('home.startShort2')}</a>
            <button class="btn btn-ghost" data-act="logout">${icon('logout', 16)} ${t('profile.logout')}</button>
          </div>
        </section>

        <div class="stack">
          <section class="card" data-reveal="right">
            <div class="row between"><h3 style="font-size:19px">${icon('users', 18)} ${t('profile.family')}</h3><button class="btn btn-sm" data-act="addMember">${icon('plus', 14)} ${t('common.add')}</button></div>
            <p class="muted small" style="margin:6px 0 12px">${t('profile.familySub')}</p>
            <div class="stack" style="gap:10px">${S.members.length ? S.members.map((m) => html`<div class="list-item"><div class="grow"><b>${m.name}</b><div class="muted xs">${[m.relation, m.age != null ? t('profile.years', { n: m.age }) : '', m.allergies ? `${t('profile.allergies')}: ${m.allergies}` : ''].filter(Boolean).join(' · ')}</div></div>
              <button class="icon-btn" data-act="editMember" data-id="${m.id}" aria-label="${t('common.edit')}">${icon('edit', 15)}</button><button class="icon-btn danger" data-act="delMember" data-id="${m.id}" aria-label="${t('common.delete')}">${icon('trash', 15)}</button></div>`) : html`<p class="muted small">${t('profile.noFamily')}</p>`}</div>
          </section>
          <section class="card" data-reveal="right">
            <div class="row between"><h3 style="font-size:19px">${icon('phone', 18)} ${t('profile.contacts')}</h3><button class="btn btn-sm" data-act="addContact">${icon('plus', 14)} ${t('common.add')}</button></div>
            <p class="muted small" style="margin:6px 0 12px">${t('profile.contactsSub')}</p>
            <div class="stack" style="gap:10px">${S.contacts.length ? S.contacts.map((c) => html`<div class="list-item"><div class="grow"><b>${c.name}</b><div class="muted xs">${c.phone}</div></div>
              <a class="icon-btn" href="${telLink(c.phone)}" aria-label="${t('common.call')}">${icon('phone', 15)}</a><button class="icon-btn" data-act="editContact" data-id="${c.id}" aria-label="${t('common.edit')}">${icon('edit', 15)}</button><button class="icon-btn danger" data-act="delContact" data-id="${c.id}" aria-label="${t('common.delete')}">${icon('trash', 15)}</button></div>`) : html`<p class="muted small">${t('profile.noContacts')}</p>`}</div>
          </section>
        </div>
      </div>`);
    ctx.motion(el);
  };
  draw();

  async function reload() { [S.members, S.contacts] = await Promise.all([api('/api/profile/members', A), api('/api/profile/contacts', A)]); draw(); }

  function memberForm(m = {}) {
    const mod = openModal(html`<h3>${m.id ? t('profile.editMember') : t('profile.addMember')}</h3>
      <form id="mf" class="stack" style="gap:12px" novalidate>
        <div class="field"><label>${t('flow.name')}</label><input class="input" name="name" maxlength="100" required value="${m.name || ''}"></div>
        <div class="grid-2" style="gap:12px"><div class="field"><label>${t('flow.age')}</label><input class="input" name="age" type="number" min="0" max="120" value="${m.age ?? ''}"></div>
        <div class="field"><label>${t('profile.relation')}</label><input class="input" name="relation" maxlength="50" value="${m.relation || ''}"></div></div>
        <div class="field"><label>${t('profile.allergies')}</label><input class="input" name="allergies" maxlength="500" value="${m.allergies || ''}"></div>
        <div class="field"><label>${t('profile.notes')}</label><textarea class="textarea" name="notes" maxlength="500">${m.notes || ''}</textarea></div>
        <p class="error-text hidden" data-err></p>
        <div class="actions"><button type="button" class="btn btn-soft" data-close>${t('common.cancel')}</button><button class="btn" type="submit">${t('common.save')}</button></div>
      </form>`);
    mod.el.querySelector('#mf').addEventListener('submit', async (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      const body = { name: f.get('name').trim(), age: f.get('age') === '' ? null : +f.get('age'), relation: f.get('relation').trim(), allergies: f.get('allergies').trim(), notes: f.get('notes').trim() };
      if (!body.name) { const er = mod.el.querySelector('[data-err]'); er.textContent = t('common.required'); er.classList.remove('hidden'); return; }
      try { await api(m.id ? `/api/profile/members/${m.id}` : '/api/profile/members', { method: m.id ? 'PUT' : 'POST', body, ...A }); mod.close(); reload(); } catch (ex) { const er = mod.el.querySelector('[data-err]'); er.textContent = errorText(ex); er.classList.remove('hidden'); }
    });
  }

  function contactForm(c = {}) {
    const mod = openModal(html`<h3>${c.id ? t('profile.editContact') : t('profile.addContact')}</h3>
      <form id="cf" class="stack" style="gap:12px" novalidate>
        <div class="field"><label>${t('flow.name')}</label><input class="input" name="name" maxlength="100" required value="${c.name || ''}"></div>
        <div class="field"><label>${t('login.phone')}</label><input class="input" name="phone" type="tel" required value="${c.phone || ''}"></div>
        <p class="error-text hidden" data-err></p>
        <div class="actions"><button type="button" class="btn btn-soft" data-close>${t('common.cancel')}</button><button class="btn" type="submit">${t('common.save')}</button></div>
      </form>`);
    mod.el.querySelector('#cf').addEventListener('submit', async (e) => {
      e.preventDefault();
      const f = new FormData(e.target); const er = mod.el.querySelector('[data-err]');
      const body = { name: f.get('name').trim(), phone: f.get('phone').trim() };
      if (!body.name || !body.phone) { er.textContent = t('common.required'); er.classList.remove('hidden'); return; }
      try { await api(c.id ? `/api/profile/contacts/${c.id}` : '/api/profile/contacts', { method: c.id ? 'PUT' : 'POST', body, ...A }); mod.close(); reload(); } catch (ex) { er.textContent = errorText(ex); er.classList.remove('hidden'); }
    });
  }

  el.addEventListener('submit', async (e) => {
    if (e.target.id !== 'pf') return;
    e.preventDefault();
    try {
      S.me = await api('/api/profile', { method: 'PUT', body: { name: $('#pn', el).value.trim(), language: $('#pl', el).value }, ...A });
      toast(t('common.saved'), 'ok');
      if (S.me.language !== getLang()) setLang(S.me.language); else draw();
    } catch (ex) { toast(errorText(ex), 'err'); }
  });

  el.addEventListener('click', async (e) => {
    const b = e.target.closest('[data-act]'); if (!b) return;
    const id = +b.dataset.id;
    const a = b.dataset.act;
    if (a === 'logout') { tokens.clear('user'); toast(t('profile.loggedOut')); ctx.navigate('/'); }
    else if (a === 'addMember') memberForm();
    else if (a === 'editMember') memberForm(S.members.find((m) => m.id === id));
    else if (a === 'addContact') contactForm();
    else if (a === 'editContact') contactForm(S.contacts.find((c) => c.id === id));
    else if (a === 'delMember' || a === 'delContact') {
      if (!(await confirmDialog(t('common.delete'), t('profile.deleteAsk'), t('common.delete')))) return;
      try { await api(`/api/profile/${a === 'delMember' ? 'members' : 'contacts'}/${id}`, { method: 'DELETE', ...A }); reload(); } catch (ex) { toast(errorText(ex), 'err'); }
    }
  });
}
