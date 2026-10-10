# Notificaciones, activos fijos y asistencia

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

### Asistencia: selfie al marcar en el kiosko (oct 2026)

Problema: en el kiosko (`ShiftClockInScreen`) cualquiera tocaba el nombre de otro y le marcaba; un PIN se presta igual. La huella de `BiometricClockButton` (código muerto, sin uso) **no identifica a nadie**: `BiometricPrompt` solo dice "coincidió alguna huella de este equipo", no de quién.

- **Solo se marca la ENTRADA** (decisión del dueño: lo que interesa es la hora de llegada; sin salida ni foto de salida). Si ya marcó hoy, la ficha dice "Ya marcaste tu entrada hoy a las HH:MM". Las marcas viejas con salida se siguen mostrando en el historial.
- **Turno mixto** (`Employee.splitShift` + `secondStartTime/secondEndTime`, interruptor en la ficha): dos entradas al día, una marca (`AttendanceRecord.shiftNumber` 1|2) por turno, cada una con su tardanza contra su propia hora. La regla vive en `model/AttendanceEntry.kt::nextEntry` (con test): la 2ª se habilita al terminar el 1º turno (o 1 h antes del 2º si no hay salida del 1º) para que un doble toque no la registre de una vez; si no marcó en la mañana y ya pasó esa hora, la marca cuenta como del 2º turno. El VM la vuelve a evaluar con las marcas y la hora del momento de marcar. "Presentes" del historial cuenta días, no marcas.
- Tardanza: la pantalla final dice "Has llegado X minutos tarde" + invitación a llegar más temprano para evitar penalizaciones (7 s en pantalla; a tiempo, 4 s y "¡gracias por tu puntualidad!").
- Flujo: LISTA → ficha → MARCAR ENTRADA → paso **SELFIE** → éxito. Círculo chico al centro (≤200 dp o un tercio del alto: tablet horizontal, y para que no dé pena), la foto se dispara sola.
- `SelfieCamera` (expect, `ui/SelfieCamera.kt`). Android = CameraX + ML Kit Face Detection unbundled (`SelfieCamera.android.kt`): una sola cara, ojos abiertos → cerrados → abiertos = `LIVE`; sin parpadeo en 2.5 s = `FACE`; detector caído = `PHOTO`. Recorte cuadrado a la cara, 360 px, ~25 KB.
- **No bloquea nunca**: a los 15 s (o sin cámara/permiso) aparece "Marcar sin foto" → `SKIPPED`. En web/iOS `isSelfieCameraAvailable = false` y se marca directo como `SKIPPED`. El historial lo señala; la idea es disuadir, no rechazar a alguien a las 5 a.m.
- La marca se escribe primero y la foto se sube después (`EmployeeViewModel.uploadSelfie` → `attachSelfiePhoto`, escritura parcial). Check ≠ SKIPPED con URL vacía = "Foto no subida" (sin red, por ejemplo).
- Campos nuevos en `attendance`: `clockIn/OutSelfieCheck`, `clockIn/OutPhotoUrl`. `verificationMethod` = `selfie` | `sin_foto` | `manual`.
- Storage: `attendanceSelfies/{c}/{employeeId}/{recordId}_{in|out}.jpg`, prefijo **de primer nivel** a propósito: la regla de ciclo de vida del bucket (`functions/tools/setup-attendance-selfie-lifecycle.js`) borra ese prefijo a los **60 días** (`ATTENDANCE_SELFIE_RETENTION_DAYS`). Pasado el plazo la UI muestra "Foto vencida" sin pedir la URL. Regla de Storage propia con tope de 1 MB.
- **Trampa**: declarar `CAMERA` en el manifest hace que `ACTION_IMAGE_CAPTURE` lance `SecurityException` si el permiso no está concedido. `ImagePickerButton` (foto de factura, etc.) ahora pide el permiso antes de abrir la cámara del sistema.
- Velocidad: al principio tardaba porque el parpadeo (~150 ms, 1-2 cuadros) se perdía con umbrales estrictos (abierto >0.6, cerrado <0.3) y se esperaba el límite de 6 s; además `minFaceSize 0.25` no veía caras a distancia normal en horizontal, y un cuadro sin cara reiniciaba la cuenta. Ahora: umbrales 0.5/0.4, límite 2.5 s, `minFaceSize 0.15`, 700 ms de gracia si se pierde la cara.
- CameraX 1.4.0 no trae `awaitInstance`; `getInstance` devuelve `ListenableFuture` y Guava solo estaba en runtime → se declara `guava:32.1.3-android` (la misma versión que ya viajaba, sin peso extra).
- Fase 2 posible: comparar la selfie contra la foto de perfil (embedding TFLite) y dejar la marca en "revisar", sin bloquear.
