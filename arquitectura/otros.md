# Notificaciones y activos fijos

> Detalle movido desde CLAUDE.md. Leer antes de tocar este módulo.

### Notificaciones (`AppNotification`)

`companies/{c}/notifications/{id}` es la **fuente de verdad**; el push es solo un canal de entrega. Si la Cloud Function falla o no está desplegada, el aviso sigue en la campana.

- Destinatario por **sucursal + permiso**, no por usuario: quién está de turno cambia a diario, "la gente de la planta X que maneja lotes" no. `isVisibleTo(user)` filtra en cliente.
- Al autor no se le notifica su propia acción.
- Tipos: `ORDER_RECEIVED` (pedido → planta), `ORDER_IN_LOT` (planta tomó el pedido → sucursal), `ORDER_IN_PRODUCTION` (`startLot` → sucursal), `PRODUCT_CANCELLED_MIN_BATCH`.
- `NotificationTarget` es un string simbólico que resuelve `AppNavigation` — evita acoplar `model/` con `navigation/` (mismo criterio que `AlertTarget`).
- Campana con badge en el TopAppBar de HomeScreen (se suscribe ahí porque siempre está en el backstack) + `NotificationsSheet`.
- `notifySafely` en `ProductionViewModel`: un fallo al notificar nunca tumba la operación que lo provocó.
- **Push**: `PushTokenProvider` (interfaz en `domain/`, impl por plataforma). Android = `AndroidPushTokenProvider` (FCM). Web = `JsPushTokenProvider` (SDK modular `firebase/messaging` vía externals + `firebase-messaging-sw.js` en `jsMain/resources/`, que webpack copia a la raíz). iOS = `NoOpPushTokenProvider` hasta tener APNs.
- Tokens en `companies/{c}/deviceTokens/{token}` — el id ES el token, así re-registrar el mismo aparato sobreescribe en vez de duplicar.
- `functions/index.js` (Node 22, 2ª gen, us-central1) envía y borra tokens muertos. Ya desplegada. **Requiere plan Blaze** (activo).
- Android: el canal `bakeos_pedidos` se crea en `BakeryApp.onCreate` — en Android 8+ un push a un canal inexistente se descarta en silencio. El id debe coincidir en tres lugares: `NotificationChannels.kt`, el `default_notification_channel_id` del manifest y `functions/index.js`.
- Tap sobre el push: `MainActivity` rescata `targetScreen`/`targetId` de los extras del intent y los deja en `PendingDeepLink`; `AppNavigation` los consume cuando ya hay sesión Y terminó la navegación inicial (si no, el `popUpTo(0)` del arranque se lleva la pantalla por delante).
- Web: `WEB_PUSH_VAPID_KEY` en `FirebaseWeb.kt` (vacía = push web apagado, sin romper nada). Requiere HTTPS. El clic no aterriza en el pedido — la web no tiene rutas en la URL.

### Activos Fijos
Inventario valorado por sucursal: maquinaria, mobiliario, vehículos, herramientas. Permisos: `VIEW_ASSETS`, `EDIT_ASSETS`, `MANAGE_ASSETS`, `ADD_ASSET_HISTORY`.

- `FixedAsset` plano por sucursal con identificación, adquisición, status (`AssetStatus`), depreciación lineal opcional (`usefulLifeMonths`, `residualValue`), mantenimiento preventivo (`nextMaintenanceDate`, `maintenanceIntervalDays`), contadores denormalizados.
- `AssetHistoryEntry` en subcolección. `AssetEventType`: PURCHASE/MAINTENANCE/REPAIR/UPGRADE/INSPECTION/TRANSFER/DISPOSAL/SALE/NOTE.
- Crear activo → inserta PURCHASE auto en historial.
- `saveHistoryEntry` side-effects: TRANSFER actualiza `branchId`; DISPOSAL→`status=DISPOSED`; SALE→`SOLD`; MAINTENANCE con `nextMaintenanceDate` actualiza el campo del activo.
- `recalcAndPersistCounters` después de cada add/edit/delete de evento.
- `deleteAsset` borrado lógico (conserva historial).
- `util/DepreciationCalculator.kt::calculateBookValue(...)` con `kotlinx.datetime`.
- Lista con KPIs (total / inversión / valor en libros / mantenimientos 30 d), filtros sucursal+categoría+búsqueda, badge naranja si `nextMaintenanceDate ≤ +7 días`.
- `AssetImageUploader` default `NoOp` en appModule; overrides `AndroidAssetImageUploader` y `JsAssetImageUploader`.
