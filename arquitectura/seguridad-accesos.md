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

### Pendiente
- Los permisos finos (cobrar, anular, editar recetas) y el alcance por sucursal siguen solo en
  la app. Fase 4 del plan de PIN.
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
- Bloqueo: "Cambiar de persona" en el Home y 10 min sin tocar la pantalla
  (`OPERATOR_IDLE_LOCK_MS` en AppNavigation; toques detectados con pointerInput en pase
  Initial, sin consumir). La sesión de la persona vive solo en memoria.
- Abrir caja en un equipo: abre la persona identificada, sin lista para escoger.
- Pendiente (fase 3): guardar `employeeId` en cada registro (hoy los `xxxByUserId` son la
  cuenta del equipo; solo el nombre ya es de la persona), `Sale.cashierId`, "mi caja" en
  POSHome compara cuentas, quien RECIBE la caja confirma con su PIN.
