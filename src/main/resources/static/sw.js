// 설치형 웹앱 서비스워커: 화면 파일만 캐시해 인터넷이 끊겨도 화면이 열리게 한다.
// API 응답(/api/) · 업로드 · 입력값은 절대 캐시하지 않는다 (개인정보 최소). 화면 파일을 바꾸면 VERSION 을 올린다
var VERSION = 'wageback-v2-19';
var FILES = ['./', 'index.html', 'css/app.css', 'js/app.js', 'js/vendor/qrcode.js', 'fonts/noran-sans.woff2', 'favicon.svg',
  'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/icon-maskable-512.png',
  'i18n/languages.json', 'i18n/ko.json', 'i18n/en.json', 'i18n/vi.json', 'data/offices.json', 'data/guides.json', 'data/deadlines.json'];

self.addEventListener('install', function (e) {
  // 브라우저에 남은 예전 파일을 담지 않게 서버에서 새로 받는다
  e.waitUntil(caches.open(VERSION).then(function (c) {
    return c.addAll(FILES.map(function (f) { return new Request(f, { cache: 'reload' }); }));
  }).then(function () { return self.skipWaiting(); }));
});
self.addEventListener('activate', function (e) {
  e.waitUntil(caches.keys().then(function (keys) {
    return Promise.all(keys.filter(function (k) { return k !== VERSION; }).map(function (k) { return caches.delete(k); }));
  }).then(function () { return self.clients.claim(); }));
});
// 화면 파일: 먼저 서버에서 새로 받고(받으면 캐시를 바꿈), 안 되면 캐시. 다른 요청은 손대지 않는다
self.addEventListener('fetch', function (e) {
  var url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.indexOf('/api/') === 0) return;
  // 브라우저 HTTP 캐시에 남은 예전 파일 대신 서버에 바뀌었는지 물어본다 (화면 이동 요청은 그대로)
  var req = e.request.mode === 'navigate' ? e.request : new Request(e.request, { cache: 'no-cache' });
  e.respondWith(fetch(req).then(function (res) {
    if (res.ok) {
      var copy = res.clone();
      caches.open(VERSION).then(function (c) { c.put(e.request, copy); });
    }
    return res;
  }).catch(function () {
    return caches.match(e.request, { ignoreSearch: true }).then(function (hit) { return hit || caches.match('index.html'); });
  }));
});
