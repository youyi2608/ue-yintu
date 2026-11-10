/* 极简 service worker：
   1) 只为满足 PWA 安装条件 + 装完之后离线可用（缓存本页要的几个文件）
   2) 顺手接住系统分享进来的图（share_target → POST ./share） */
var CACHE = 'ue-yintu-mobile-v0.2';
var SHARE_CACHE = 'ue-share';
var SHARE_KEY = new URL('./__shared_image', self.location).href;
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
        if (k !== CACHE && k !== SHARE_CACHE) return caches.delete(k);
        return null;
      }));
    }).then(function () { return self.clients.claim(); })
  );
});

/* ---------- 系统分享进来的图 ---------- */
function handleShare(req) {
  var back = new URL('./index.html?shared=1', self.location).href;
  return req.formData().then(function (fd) {
    var f = fd.get('image');
    if (f && f.size) {
      var headers = { 'Content-Type': f.type || 'application/octet-stream' };
      try { headers['X-Name'] = encodeURIComponent(f.name || '分享的图.png'); } catch (e) { }
      return caches.open(SHARE_CACHE).then(function (c) {
        return c.put(SHARE_KEY, new Response(f, { headers: headers })).catch(function () { });
      }).catch(function () { });
    }
  }).catch(function () { }).then(function () {
    // 成不成功都回首页，页面那边再给一句话
    return Response.redirect(back, 303);
  });
}

self.addEventListener('fetch', function (e) {
  var req = e.request;

  if (req.method === 'POST') {
    try {
      var u = new URL(req.url);
      if (u.origin === self.location.origin && u.pathname === new URL('./share', self.location).pathname) {
        e.respondWith(handleShare(req));
      }
    } catch (err) { }
    return;
  }
  if (req.method !== 'GET') return;

  e.respondWith(
    caches.match(req, { cacheName: CACHE }).then(function (hit) {
      return hit || fetch(req);
    }).catch(function () { return fetch(req); })
  );
});
