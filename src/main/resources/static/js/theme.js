(function () {
  var t = null;
  try { t = localStorage.getItem('ss-theme'); } catch (e) { /* storage blocked */ }
  if (t !== 'dark' && t !== 'light') {
    t = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  document.documentElement.setAttribute('data-theme', t);
  var m = document.querySelector('meta[name="theme-color"]');
  if (m) m.setAttribute('content', t === 'dark' ? '#0a121e' : '#003d1f');
})();
