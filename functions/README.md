# Push de BakeOS

La función `sendNotificationPush` **ya está desplegada** en `bakery-erp-saas`
(us-central1, Node 22, 2ª gen). Se dispara sola cada vez que se crea un documento
en `companies/{companyId}/notifications/`.

## Estado por plataforma

| Plataforma | Estado | Qué falta |
|---|---|---|
| Android | ✅ Listo | Nada. |
| Web | ⚠️ Falta pegar la clave VAPID | Ver abajo. |
| iOS | ❌ Sin push | Certificado APNs de la cuenta de Apple Developer + implementar `PushTokenProvider` para iOS. |

Sin push, los avisos igual llegan a la campana de la app. Lo único que se pierde
es que suene con la app cerrada.

## Activar el push web

Falta un solo dato, que solo se puede sacar de la consola:

1. Firebase Console → Configuración del proyecto → **Cloud Messaging** →
   "Certificados push web" → **Generar par de claves**.
2. Copiar la clave (empieza con `B...`, ~87 caracteres) y pegarla en
   `composeApp/src/jsMain/kotlin/com/bakery/erp/FirebaseWeb.kt`:

   ```kotlin
   const val WEB_PUSH_VAPID_KEY = "BFx...."
   ```

3. Recompilar la web (`./gradlew composeApp:jsBrowserDistribution`) y publicar.

Es la clave **pública**: no es un secreto y puede vivir en el repo, igual que el
`apiKey`.

Mientras esté vacía, `JsPushTokenProvider` devuelve null, no registra token y la
web funciona con la campana in-app. No rompe nada.

### Requisitos del navegador

- **HTTPS obligatorio** (o `localhost`). Un sitio en `http://` no puede registrar
  Service Workers, así que no habrá push.
- El archivo `firebase-messaging-sw.js` debe quedar servido en la **raíz** del
  sitio. Ya sale ahí solo: vive en `jsMain/resources/` y webpack lo copia junto a
  `index.html`.
- Al hacer clic en el aviso, la web enfoca o abre la pestaña pero **no aterriza en
  el pedido** como en Android: la app es una sola página con navegación en
  memoria, sin rutas en la URL. El aviso queda en la campana.

## Volver a desplegar la función

```bash
cd functions && npm install
cd .. && firebase deploy --only functions
```

## Verificar

1. Abre la app en dos aparatos con usuarios de sucursales distintas.
2. Manda un pedido desde la sucursal a la planta.
3. En el aparato de la planta debe sonar el push y subir el número de la campana.

Si no llega el push pero sí sube la campana, el problema está en esta función:

```bash
firebase functions:log --only sendNotificationPush
```

## Costo

Cloud Functions requiere el plan **Blaze** (ya activo). Cobra por uso con una capa
gratuita mensual de 2 M de invocaciones; para una panadería con unas pocas
sucursales el costo real esperado es prácticamente cero.

Hay una política de limpieza activa que borra las imágenes de contenedor de más de
1 día, para que no se acumulen en Artifact Registry y generen un cobro fijo.

## Cómo está armado

```
La app escribe  ──>  companies/{c}/notifications/{id}   <──  la campana lee en vivo
                              │
                              └──> sendNotificationPush ──> FCM ──> el teléfono suena
```

La colección de Firestore es la fuente de verdad y el push es solo un canal de
entrega. Por eso un fallo acá nunca hace perder un aviso.

Los destinatarios se resuelven por **sucursal + permiso**, no por lista de
usuarios: quién está de turno cambia todos los días, pero "la gente de la planta
Calvario que maneja lotes" es estable. La función replica esa misma lógica que
usa la app (`AppNotification.isVisibleTo`) — si cambias una, cambia la otra.
