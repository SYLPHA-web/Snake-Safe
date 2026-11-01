import { html, toHtml } from '../util.js';
import { icon } from '../icons.js';
import { t } from '../i18n.js';
import { telLink } from '../ui.js';

export const chrome = 'full';

export default async function notFound(el, ctx) {
  el.innerHTML = toHtml(html`<div class="card empty" data-reveal>${icon('search', 44)}<h1 style="font-size:28px;margin-bottom:8px">${t('nf.title')}</h1><p>${t('nf.text')}</p>
    <div class="row-wrap" style="justify-content:center;margin-top:20px"><a class="btn" href="#/">${icon('home', 16)} ${t('common.home')}</a><a class="btn btn-red" href="${telLink(ctx.config.emergencyNumber)}">${icon('phone', 16)} ${t('common.call108')}</a></div></div>`);
}
