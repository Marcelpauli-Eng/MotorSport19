// Guarda la propia aplicación (HTML, JS, CSS, imágenes) para poder abrirla sin
// conexión. Los datos de la API no pasan por aquí: los guarda la aplicación.
//
// Primero siempre la red, para no quedarse nunca con una versión vieja; la
// copia solo se usa cuando la red falla.
const CACHE = 'motorsport19-app';

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/')) {
    return;
  }

  // Cualquier ruta de Angular es el mismo index: se guarda una sola vez.
  const clave = e.request.mode === 'navigate' ? '/' : e.request;

  e.respondWith(
    fetch(e.request)
      .then((respuesta) => {
        if (respuesta.ok) {
          const copia = respuesta.clone();
          caches.open(CACHE).then((c) => c.put(clave, copia));
        }
        return respuesta;
      })
      .catch(() => caches.match(clave).then((guardada) => guardada ?? Response.error())),
  );
});
