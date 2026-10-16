# Seguridad y accesos

## Reglas de Firestore y Storage (oct 2026)

Las reglas viven en `firestore.rules` y `storage.rules` y se publican desde el repo
(`firebase deploy --only firestore:rules,storage`). Antes estaban solo en la consola y el
`firestore.rules` del repo era el del commit inicial (desactualizado; publicarlo habría roto
la app porque no permitía listar usuarios).

**Pruebas:** `cd rules-tests && npm test` (levanta emuladores; necesita Java 21, sirve el JBR
de Android Studio: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`). Cada flujo de
`FirebaseAuthRepository` tiene una prueba que debe pasar y cada hueco viejo una que debe fallar.
Contra las reglas viejas fallaban 13 ataques y 2 flujos legítimos.

### Qué estaba abierto
- Cualquiera editaba su propio `users/{uid}`: rol, `orgId`, permisos → OWNER de cualquier empresa.
- `organizations` e `inviteCodes` se podían listar (así se conseguía el `orgId` ajeno).
- `isApproved`/`isActive` no se validaban: un pendiente ya leía y escribía la empresa.
- Storage: cualquier cuenta de Firebase leía/subía/borraba todo, incluidos documentos de colaboradores.

### Cómo quedó
- `users/{uid}` nace solo en 4 formas (Google sin empresa, OWNER de empresa NUEVA, EMPLOYEE o
  RESELLER pendiente con empresa existente), siempre sin permisos ni sucursales.
- Uno mismo solo cambia `displayName` y `photoUrl`. Excepción: quien tiene `orgId == ''`
  (onboarding de Google) puede pasar a una de las formas de nacimiento.
- OWNER/ADMIN editan a la gente de su empresa; ADMIN no crea OWNERs ni toca a un OWNER.
  El permiso `MANAGE_USERS` NO alcanza en reglas (solo rol OWNER/ADMIN): si se le da a un
  EMPLOYEE, la app le muestra la pantalla pero Firestore rechaza.
- Quien tiene `MANAGE_EXTERNAL_CUSTOMERS` aprueba revendedores (solo `isApproved`).
- `companies/{id}/**`: miembro aprobado y activo (`isApproved`/`isActive` ausentes = true,
  el OWNER original no los trae). Revendedor pendiente: solo leer `branches` y crear su propia
  ficha en `externalCustomers` (`linkedUserId == uid`, `active == false`).
- Storage: rutas con empresa (`companies/{c}`, `purchaseInvoices/{c}`, `specialOrders/{c}`)
  solo personal de esa empresa; rutas viejas sin empresa (`products/`, `assets/`, `recipes/`)
  personal aprobado de cualquier empresa. Las imágenes se muestran por URL con token, que no
  pasa por reglas. Las reglas de Storage leen Firestore (`firestore.get`): eso exige que el
  agente de Storage (`service-412638080390@gcp-sa-firebasestorage.iam.gserviceaccount.com`)
  tenga `roles/firebaserules.firestoreServiceAgent`. La CLI con `--non-interactive` NO lo pidió;
  se dio a mano el 2026-10-08. Sin ese rol, toda lectura/subida por SDK queda denegada.

### Fase 4 (oct 2026): qué se puede tocar dentro de la empresa
Las reglas ven los permisos de la CUENTA; en un equipo eso es el techo y la app recorta a la
persona, así que las reglas nunca son más estrictas que la app.
- **Historial que no se borra** (`isHistory`/`isHistorySub`): sales (+lines), salesDaily,
  cashSessions (+movements), cashTransfers, cashAuditEvents, dailyCashCloses, spotCashChecks,
  customerDeposits, dtes, finishedMovements, creditAccounts, counters, ingredients/*/movements.
  Ni el dueño desde la app; los scripts admin y las Cloud Functions sí (Admin SDK). Si algún
  día la app necesita borrar uno de estos, hay que sacarlo de la lista (y es mala señal).
  Lo que la app sí borra sigue abierto: billetes contados de caja/cierre, creditMovements,
  notificaciones, líneas de pedidos/lotes/despacho, compras (DELETE_PURCHASES), etc.
- **Configuración con permiso**: cashRegisters y saleQuickGroups (MANAGE_POS_CONFIG),
  priceLists (MANAGE_PRICE_LISTS), fixedBudget (MANAGE_FIXED_BUDGET), jobPositions
  (MANAGE_EMPLOYEES). felProfiles: solo la Cloud Function. productPrices queda abierto: lo
  escriben el form de producto, la admin de listas y la compra de reventa.
- **Sucursal**: sales y cashSessions nuevas solo con `branchId` en las sucursales de la cuenta
  (OWNER o cuenta sin sucursales = todas). Solo al crear: el autocierre y el cierre del día
  actualizan cajas de cualquier sucursal.

### Pendiente
- Lecturas por sucursal (hoy cualquier cuenta lee toda la empresa): las listas filtran en
  memoria y una regla de lectura por sucursal rompería consultas sin `where(branchId)`.
- Permisos finos en escrituras con efectos cruzados (venta → stock, compra → precios): mapear
  cada efecto a su permiso es frágil; se dejó en la app.
- El revendedor APROBADO conserva acceso a toda la empresa (no hay ninguno hoy). Limitarlo a
  sus pedidos/precios necesita guardar su `customerId` en el perfil para poder filtrar listas.
- Roles ADMIN/MANAGER no se asignan desde la app: `functions/tools/set-user-role.js`.

## Cuenta de sucursal + PIN por colaborador (plan, oct 2026)

Decisión: el equipo (tablet/teléfono) entra con la cuenta de sucursal, cuya contraseña solo
sabe el dueño; la persona se identifica con PIN para cada acción. Dueños/administradores
siguen con cuenta propia. Detalle del diseño y fases en la memoria del proyecto
`project_branch_account_pin_access_oct2026`. Puntos fijos:
- El colaborador (`employees`) es la identidad de la persona; la cuenta de sucursal es el techo
  de lo que se puede hacer en ese equipo. Permisos efectivos = colaborador ∩ equipo.
- El PIN se verifica en servidor (Cloud Function) con bloqueo por intentos; nunca un hash
  legible por el cliente (4–6 dígitos se rompen al instante).
- Cada acción guarda cuenta del equipo + colaborador.

Auditoría de partida: `functions/tools/inspect-users-employees.js` (solo lectura).

### Fase 1 (hecha, oct 2026): datos, PIN en servidor y pantallas de administración
- `Employee.permissions` (lo que hace con su PIN), `pinStatus` (NONE/TEMPORARY/SET) y
  `pinUpdatedAt`. El formulario NO los escribe: ficha existente se guarda con `update()`,
  no `set()`, para no borrarlos. Permisos: `updateEmployeePermissions` (escritura parcial).
- Reglas: permisos del colaborador solo OWNER/ADMIN; `pinStatus`/`pinUpdatedAt` solo la
  función; `employeePins/` cerrada. Ojo: `{sub=**}` calza con cero segmentos, por eso las
  subcolecciones del colaborador van con `{sub}/{rest=**}` (con `**` la ficha quedaba abierta).
- `User.isDeviceAccount`: lo marca OWNER/ADMIN en la pantalla de permisos del usuario; nadie
  nace con él. Chip "Equipo" en la lista de usuarios.
- Callables (`functions/access/employeeAccess.js`): `empleadoFijarPin` (solo OWNER/ADMIN; el
  PIN nace TEMPORAL salvo que lo haya escrito el colaborador), `empleadoCambiarPin`,
  `empleadoVerificarPin` (5 fallos → 10 min de bloqueo). scrypt con sal.
  ¿Por qué solo OWNER/ADMIN asignan PIN? Quien pone el PIN de otro puede hacerse pasar por él.
- UI: pestaña "Acceso" en la ficha (`EmployeeAccessTab`), lista de permisos compartida
  (`PermissionChecklist`) entre usuarios y colaboradores.
### Fase 2 (hecha, oct 2026): la persona se identifica en el equipo
- `AuthViewModel` guarda la CUENTA en su estado interno y publica en `uiState.user` el
  usuario efectivo (`effectiveUser`, model/OperatorSession.kt): permisos = cuenta ∩
  colaborador, `displayName` = nombre de la persona, `id` = el de la cuenta. Por eso los
  ~230 chequeos de permisos no se tocaron. `uiState.accountUser` es la cuenta sin mezclar;
  `uiState.operator`, la persona. Sin persona, permisos vacíos.
- `OperatorLockScreen` se pinta encima del NavHost cuando `needsOperator` (cuenta de equipo
  sin persona): escoger nombre (primero los de las sucursales del equipo) → teclado de PIN →
  si el PIN era temporal, escoger uno propio dos veces (`empleadoCambiarPin`).
- Bloqueo: "Cambiar de persona" en el Home y 15 min sin tocar la pantalla. Una pantalla "de
  mirar" (`WatchedScreenIdleLock()`, hoy solo el detalle del lote, junto a `KeepScreenOn`) no
  se bloquea por inactividad: en su lugar, iniciar el lote pide el PIN de la persona
  identificada (StartShiftDialog, con "No soy yo"). Plazos en `ui/IdleLockPolicy.kt`. Toques detectados
  con pointerInput en pase Initial, sin consumir. La sesión de la persona vive solo en memoria.
- Abrir caja en un equipo: abre la persona identificada, sin lista para escoger.
### Fase 3 (hecha, oct 2026): el dinero queda a nombre de la persona
- `User.operatorEmployeeId`: en memoria (no se guarda en `users/`), lo pone `effectiveUser`.
  Regla: lo que se registre a nombre de alguien guarda la cuenta (`xxxByUserId`) Y este id.
- Ya lo guardan: `Sale.cashierEmployeeId` (venta del mostrador y cuentas del restaurante
  cobradas), `CashSession.closedByEmployeeId` (abrir ya traía `openedByEmployeeId`),
  `CashMovement.createdByEmployeeId` y `CreditMovement.createdByEmployeeId`.
- Resumen diario "por cajero": la llave es `cashierEmployeeId` si existe, si no la cuenta.
  Las ventas viejas siguen sumando a la cuenta (no se reescribe historia).
- "Mi caja" = `CashSession.isOperatedBy(user)`: misma cuenta Y, en equipo compartido, misma
  persona que abrió. Lo usan POSHome (Continuar / En uso) y la revalidación del cobro.
  Consecuencia buscada: la cajera del turno siguiente no vende en la gaveta de la anterior;
  la anterior cierra y se la entrega.
- Entrega de gaveta en equipo compartido: quien RECIBE escribe su PIN en el diálogo de
  cierre (`receiverMustConfirm`; no se pide si se la entrega a sí misma).
- Sin cambiar todavía (solo el nombre es de la persona): producción, despacho, compras,
  conteos de pan, cuadres sorpresa, activos, pagos y anticipos de pedidos. 

### Quién lo hizo — auditoría (oct 2026)
Regla: el nombre de autor sale SIEMPRE de `authState.user` (el efectivo): con PIN es el
colaborador; sin PIN, la cuenta. Nunca de `accountUser` ni de Firebase Auth. Revisado módulo por
módulo; lo que faltaba:
- Guardar: `Expense.createdBy*` (form y gastos que nacen de una compra), `StockMovement.createdBy*`
  (Kardex de insumos, los 7 caminos), `ProductionLot.startedBy*`/`completedBy*`
  (`updateLotStatus`/`completeLot` reciben `by`), merma/pan de más firman la nota con el nombre.
- Mostrar (ya se guardaba): pedido de cliente (lista + detalle + quién recibió cada pago),
  compras (lista + quién hizo cada abono), lotes de preparación, historial de activos, conteos de
  pan, Kardex de insumos, armó/inició/cerró en el detalle del lote.
- Arreglo de paso: editar un gasto borraba los campos que el form no muestra (set() sin arrastrar).
- Sin autor todavía: ediciones de catálogo (productos, recetas, insumos, precios, proveedores)
  y cambios de estado del pedido de cliente.

### Bienvenida (oct 2026)
Primera entrada con PIN del día → `WelcomeOverlay` encima del Home ("¡Bienvenida!"/"¡Bienvenido!"
según `Employee.gender`; neutro si no hay). El día se marca en la ficha (`lastWelcomeDay`, vía
`markFirstEntryOfDay`) para que no se repita al cambiar de tablet; consulta con tope de 1.5 s y,
sin red, se entra sin saludo.
- Caja ajena: quien tiene `MANAGE_DAILY_CASH_CLOSE` ve "Cerrar caja" (en vez de "En uso") en
  la caja que otra persona dejó abierta, y la cierra o la entrega.
- Tras entregar la gaveta en un equipo, el equipo pasa directo a quien la recibió (ya puso
  su PIN en el cierre); si su PIN era temporal, se bloquea para que escoja el suyo.

## Desactivar cuentas (oct 2026)
- Pantalla de permisos del usuario → "Desactivar cuenta" / "Reactivar cuenta" (solo OWNER/ADMIN,
  nunca a uno mismo ni a un OWNER). Escribe solo `isActive` (`setUserActive`).
- `User.canEnter` = aprobado Y activo: una cuenta desactivada cae en PendingApprovalScreen con
  el texto "Esta cuenta fue desactivada", y las reglas ya le niegan los datos al instante.
- `updateUserAccess` ya no reactiva por accidente: guardar permisos de alguien desactivado lo
  deja desactivado (solo aprobar a un pendiente lo activa).

## Datos de costo y revendedores (oct 2026)

Revisión al terminar la rentabilidad por sucursal (`propuesta-rentabilidad-por-sucursal.md`):
- **Reportes de costo** (Costo por producto, Costo estándar, Costo de reparto, Por sucursal) cuelgan
  de `VIEW_FINANCIAL_REPORT` en la ruta y en el Home. Ese permiso ahora es `isSensitive`: el
  "Habilitar todos" del grupo Reportes ya no lo regala. Congelar el costo pide además
  `MANAGE_PRICE_LISTS` (pantalla + regla).
- **Valor del pan = costo** desde que hay costo estándar, así que todo lo que lo muestra pide
  `VIEW_DISPATCH_VALUE`: se cerraron los huecos del formulario de reparto (valor de cada nota),
  conteo de pan (lista y formulario: valor de mermas/sobrantes) y cierre del día.
- **Prestaciones** del colaborador: mismo permiso que el salario (`VIEW_EMPLOYEE_SALARY`).
- **Revendedores**: la lista de precios ES su precio. Alta, edición, aprobación, inactivar y borrar
  piden `MANAGE_PRICE_LISTS` (oct 2026: 7 cuentas de tienda tenían `MANAGE_EXTERNAL_CUSTOMERS`).
  Con solo `MANAGE_EXTERNAL_CUSTOMERS` se ven, se corrigen sus datos de contacto y se manejan los
  clientes recurrentes. Regla `externalCustomerWriteOk()` con tests; el auto-registro del
  revendedor por la app sigue por su propia regla.
- **Lectura**: las reglas siguen dejando leer toda la empresa a cualquier miembro aprobado
  (incluidos `standardCosts`, que el despacho necesita leer). El recorte de lectura es de la app.
