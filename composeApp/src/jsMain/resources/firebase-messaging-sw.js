/**
 * Service Worker de push para BakeOS web.
 *
 * Es lo que permite que llegue el aviso con la pestaña cerrada: el navegador lo
 * mantiene vivo aparte de la página. Corre en su propio contexto — no ve nada
 * del código Kotlin ni de la config de la app, por eso la configuración de
 * Firebase se repite acá.
 *
 * Se sirve desde la raíz (`/firebase-messaging-sw.js`) porque el alcance de un
 * Service Worker no puede subir de su propia carpeta. Vive en
 * `jsMain/resources/`, que webpack copia tal cual a la raíz del sitio.
 *
 * Se usa el SDK "compat" por CDN a propósito: los Service Workers no pasan por
 * el bundler de Kotlin/JS, así que no hay forma de importar el SDK modular.
 * La versión debe coincidir con la que trae el SDK de GitLive (10.12.2).
 */
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: 'AIzaSyCC1nIxA5kWBGcrogka3APFhqbs6j2oNhM',
  authDomain: 'bakery-erp-saas.firebaseapp.com',
  projectId: 'bakery-erp-saas',
  storageBucket: 'bakery-erp-saas.firebasestorage.app',
  messagingSenderId: '412638080390',
  appId: '1:412638080390:web:796d1dcec9d4bfac305007',
});

const messaging = firebase.messaging();

/**
 * Aviso recibido con la pestaña cerrada o en segundo plano.
 *
 * Se pinta a mano en vez de dejar que el navegador lo haga solo para poder
 * adjuntar el destino en `data`: sin eso, al hacer clic no sabríamos a qué
 * pedido llevar.
 */
messaging.onBackgroundMessage((payload) => {
  const notification = payload.notification || {};
  const data = payload.data || {};
  self.registration.showNotification(notification.title || 'BakeOS', {
    body: notification.body || '',
    icon: '/favicon.ico',
    badge: '/favicon.ico',
    // Un aviso por pedido: si llegan dos del mismo, el segundo reemplaza al
    // primero en vez de apilar notificaciones repetidas.
    tag: data.notificationId || undefined,
    data: data,
  });
});

/**
 * Clic sobre el aviso: enfoca la pestaña de BakeOS si ya está abierta, y si no
 * la abre.
 *
 * Nota: la app web es una sola página con navegación en memoria (sin rutas en
 * la URL), así que no se puede aterrizar directo en el pedido como en Android.
 * Se abre la app y el aviso queda esperando en la campana.
 */
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then((clientList) => {
        for (const client of clientList) {
          if ('focus' in client) return client.focus();
        }
        if (self.clients.openWindow) return self.clients.openWindow('/');
      })
  );
});
