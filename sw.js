/* 极简 service worker：只为满足 PWA 安装条件 + 装完之后离线可用。
   不联网、不转发、不上报；只缓存本页需要的几个文件。 */
var CACHE = 'ue-yintu-mobile-v0.1';
var FILES = ['./', './index.html', './manifest.webmanifest', './图标192.png', './图标512.png'];

self.addEventListener('install', function (e) {
  e.waitUntil(
    caches.open(CACHE).then(function (c) {
      // 逐个加，缺一个不影响安装
      return Promise.all(FILES.map(function (f) {
        return c.add(new Request(f, { cache: 'reload' })).catch(function () { });
      }));
    }).then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.map(function (k) {
        return k === CACHE ? null : caches.delete(k);
      }));
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (e) {
  if (e.request.method !== 'GET') return;
  e.respondWith(
    caches.match(e.request, { cacheName: CACHE }).then(function (hit) {
      return hit || fetch(e.request);
    }).catch(function () { return fetch(e.request); })
  );
});
