// Scroll-reveal and scrub animations with GSAP + ScrollTrigger (served from /vendor, so they also work offline).
const g = window.gsap;
const ST = window.ScrollTrigger;
const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const ok = !!(g && ST) && !reduced;
if (ok) g.registerPlugin(ST);

export const motionOn = ok;
export { g as gsap };

export function killTriggers() {
  if (!ok) return;
  ST.getAll().forEach((t) => t.kill());
}

const FROM = {
  up: { y: 34 },
  fade: {},
  left: { x: -40 },
  right: { x: 40 },
  zoom: { scale: 0.92, y: 16 },
};

/** Wires up every data-reveal / data-stagger / data-count / data-ring / data-scrub in `root`. */
export function initMotion(root) {
  if (!ok) { finishInstantly(root); return; }

  root.querySelectorAll('[data-reveal]').forEach((el) => {
    const kind = FROM[el.dataset.reveal] || FROM.up;
    g.set(el, { opacity: 0, ...kind });
    ST.create({
      trigger: el, start: 'top 90%', once: true,
      onEnter: () => g.to(el, { opacity: 1, x: 0, y: 0, scale: 1, duration: 0.8, ease: 'power3.out', delay: +(el.dataset.delay || 0), clearProps: 'transform' }),
    });
  });

  root.querySelectorAll('[data-stagger]').forEach((box) => {
    const kids = [...box.children];
    const kind = FROM[box.dataset.stagger] || FROM.up;
    g.set(kids, { opacity: 0, ...kind });
    ST.create({
      trigger: box, start: 'top 88%', once: true,
      onEnter: () => g.to(kids, { opacity: 1, x: 0, y: 0, scale: 1, duration: 0.7, ease: 'power3.out', stagger: 0.09, clearProps: 'transform' }),
    });
  });

  root.querySelectorAll('[data-count]').forEach((el) => {
    const end = +el.dataset.count;
    const o = { v: 0 };
    el.textContent = '0';
    ST.create({
      trigger: el, start: 'top 95%', once: true,
      onEnter: () => g.to(o, { v: end, duration: 1.3, ease: 'power2.out', onUpdate: () => { el.textContent = Math.round(o.v); }, onComplete: () => { el.textContent = end; } }),
    });
  });

  root.querySelectorAll('[data-ring]').forEach((c) => {
    const frac = Math.max(0, Math.min(1, +c.dataset.ring));
    const len = c.getTotalLength ? c.getTotalLength() : 2 * Math.PI * c.r.baseVal.value;
    c.style.strokeDasharray = len;
    c.style.strokeDashoffset = len;
    ST.create({
      trigger: c, start: 'top 95%', once: true,
      onEnter: () => g.to(c, { strokeDashoffset: len * (1 - frac), duration: 1.4, ease: 'power3.out' }),
    });
  });

  root.querySelectorAll('[data-bar]').forEach((b) => {
    g.set(b, { scaleX: 0 });
    ST.create({ trigger: b, start: 'top 95%', once: true, onEnter: () => g.to(b, { scaleX: +b.dataset.bar, duration: 1.1, ease: 'power3.out' }) });
  });

  root.querySelectorAll('[data-parallax]').forEach((el) => {
    g.to(el, { y: +el.dataset.parallax, ease: 'none', scrollTrigger: { trigger: el, start: 'top bottom', end: 'bottom top', scrub: true } });
  });

  // Scroll-scrubbed progress line that lights up each step in turn
  root.querySelectorAll('[data-scrub-steps]').forEach((box) => {
    const fill = box.querySelector('.how-line span');
    const steps = [...box.querySelectorAll('.how-step')];
    ST.create({
      trigger: box, start: 'top 75%', end: 'bottom 55%', scrub: 0.4,
      onUpdate: (self) => {
        if (fill) g.set(fill, { scaleX: self.progress });
        steps.forEach((s, i) => s.classList.toggle('on', self.progress >= i / (steps.length - 1) - 0.02));
      },
    });
  });

  ST.refresh();
}

function finishInstantly(root) {
  root.querySelectorAll('[data-count]').forEach((el) => { el.textContent = el.dataset.count; });
  root.querySelectorAll('[data-ring]').forEach((c) => {
    const len = c.getTotalLength ? c.getTotalLength() : 2 * Math.PI * c.r.baseVal.value;
    c.style.strokeDasharray = len;
    c.style.strokeDashoffset = len * (1 - Math.max(0, Math.min(1, +c.dataset.ring)));
  });
  root.querySelectorAll('[data-bar]').forEach((b) => { b.style.transform = `scaleX(${b.dataset.bar})`; });
  root.querySelectorAll('.how-step').forEach((s) => s.classList.add('on'));
  root.querySelectorAll('.how-line span').forEach((s) => { s.style.transform = 'scaleX(1)'; });
}

export function pageIn(el) {
  if (!ok) return;
  g.fromTo(el, { opacity: 0, y: 14 }, { opacity: 1, y: 0, duration: 0.4, ease: 'power2.out', clearProps: 'transform,opacity' });
}
