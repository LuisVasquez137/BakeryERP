# POS, control de cajas, créditos y anticipos

> Detalle movido desde CLAUDE.md. Leer antes de tocar este módulo.

### POS: venta por grupo de precio ("Pan de Q0.50")

La mayor parte del pan cuesta Q0.50 (francés, sheca, pirujo…). Con cola en el mostrador, que la
cajera elija el tipo de cada pan es lento **y además da un dato malo**: con prisa toca lo primero
que ve. Una `POSCategory` con `sellAsGroup = true` se cobra como un solo botón: se teclea cuántos
panes, no cuáles, a `groupUnitPrice`. `groupLabel` es el nombre en ticket/carrito/reportes
(`groupDisplayName`) porque el `name` es el del chip y puede ser corto ("0.50").

**La venta NO descuenta ningún producto; lo reparte el conteo de cierre (`BreadCount`).** La
línea va con `productId = null` + `quickGroupId` (campos que ya existían del diseño viejo) y el
encabezado de la venta denormaliza `groupIds`/`groupUnits` en arreglos paralelos, para que el
conteo lea solo encabezados. Al cerrar, `allocateGroupSales` (`model/GroupSaleAllocation.kt`,
tests en `GroupSaleAllocationTest`) reparte lo vendido de cada grupo **en proporción al faltante**
de cada pan (mayor residuo, nunca pasa el faltante), escribe un `VENTA` por producto y solo el
resto como `MERMA`. El reparto por tipo sale del conteo físico, que es un hecho, en vez de lo que
tocó la cajera — por eso es más exacto que obligar a elegir.

- **Depende de que la tienda cuente todos los días.** Confirmado con el dueño. La ventana de ventas
  va del `closedAt` del conteo cerrado anterior de la sucursal hasta el cierre de éste
  (`groupSalesWindow`): un día sin contar se acumula al siguiente, no se pierde.
- **Antes de esto, todo faltante del conteo era merma** (`difference < 0`). Ahora la merma real es
  `BreadCountLine.effectiveWaste` (`wasteQuantity`, null en conteos viejos → cae al faltante
  entero), y `aggregateMermasForMonth` y la razón obligatoria del formulario usan eso: la
  encargada no tiene que justificar 150 francés que se vendieron.
- **Vendido de más que faltante** (`unexplainedGroupUnits`): entró pan sin registrar o el conteo
  está mal. Se avisa y no se inventa movimiento.
- VENTA y MERMA del mismo pan van **en serie**: `applyMovement` lee-modifica-escribe.
- **Solo entran productos cuyo `salePrice` = `groupUnitPrice`**: el precio del grupo nunca
  contradice el catálogo.
- Los panes de un grupo **no salen sueltos en "Todos"** del POS (invitaría a buscar el tipo, que es
  justo lo que se evita), y el POS arranca con el grupo seleccionado. En el resumen diario de
  ventas entran con clave `groupSaleKey` (`grp_<id>`) para que el reporte no los pierda.

**"Todos" va ordenado por lo más vendido** (unidades de los últimos 30 días de `salesDaily`,
en caché por sucursal y día en `POSViewModel`) y cada grupo de precio sale ahí como una ficha
más (`GroupTile`), que pregunta cuántos.

### Control de cajas: horarios de cuadre y bitácora (Fase A, 2026-09-29)

El dueño quiere detectar a quien cuenta dinero fuera de hora: ver cuánto se lleva vendido es
contar sin autorización. Decisiones tomadas con él:

- **La barra del POS solo dice cuántas ventas** ("45 ventas"), nunca el monto. Lo vendido se ve
  al cierre. En vez del usuario (la tablet de cada caja tiene UN solo usuario para todas las
  cajeras) muestra el colaborador que abrió.
- **El cierre NO es a ciegas**: muestra cuánto debería haber, porque las cajeras cuadran entre
  ellas en el cambio de turno y necesitan saber si cuadró. Lo que se controla es CUÁNDO.
- **Horarios de cuadre por caja, no por sucursal** (`CashRegister.closeSchedules` "HH:mm" +
  `closeToleranceMinutes`, editables en el formulario de la caja): en una sucursal cada caja
  cuadra a su hora. Sin horarios = sin control. `isWithinCloseSchedule` (con ventana que cruza
  medianoche) y `closeScheduleLabel` en `model/CashAudit.kt`, tests en `CashAuditTest`.
- **Entrar a la pantalla de cierre ya queda en la bitácora** (`enterCloseScreen`), se cierre o
  no — la pantalla muestra lo esperado. Fuera de horario, además: push al dueño
  (`NotificationType.CASH_CLOSE_OUT_OF_SCHEDULE`, `requiredPermission = VIEW_CASH_AUDIT`, tap →
  `NotificationTarget.CASH_AUDIT` con la sucursal) y un diálogo "¿Seguro que harás el cierre en
  un horario que no es el habitual?" con "Volver".
- **Quién**: colaboradores (`Employee`), no usuarios de la app. `EmployeePickerField` al abrir
  ("¿Quién abre la caja?", obligatorio) y al cerrar ("¿Quién recibe la caja?", con "Nadie: es el
  cierre del día"). `CashSession.openedByEmployee*/receivedByEmployee*/closedByUser*`; van en
  `toMap()` porque `closeSession` reescribe la sesión.
- **Bitácora** (`companies/{c}/cashAuditEvents`, solo se agrega; `presentation/cashaudit/`):
  módulo "Bitácora de cajas" en Administración con permiso `VIEW_CASH_AUDIT`, por sucursal y
  día, fuera de horario en rojo y filtrable. Eventos: OPEN, CLOSE_SCREEN, CLOSE (esperado,
  contado, diferencia, quién abrió → quién recibe).
- **Fase B (hecha): la caja es una cadena de turnos** (`model/CashHandover.kt`, tests en
  `CashHandoverTest`). Al cerrar: retiro opcional (permiso `WITHDRAW_AT_CLOSE`, no más de lo
  contado) y `shiftHandover` calcula lo que se entrega — efectivo contado menos retirado, y los
  vouchers de tarjeta y transferencias ACUMULADOS (recibidos + cobrados en el turno): pasan de
  turno a turno como efectivo y se recogen al final del día. `recordHandover` (escritura parcial)
  lo guarda en la sesión cerrada (`closingWithdrawal`, `carriedOut*`, `handoverRecorded`).
  - **Si alguien recibe**, `closeSession` abre SOLO el siguiente turno de la misma caja con
    `openingBalance` = efectivo entregado, `carriedInCard/Transfer`, `previousSessionId` y el
    colaborador que recibe; la pantalla va directo al POS de ese turno (`onHandedOver`).
  - **"Nadie" (cierre del día)**: lo que queda es para mañana. La primera apertura de esa caja
    muestra "Se dejaron Q X" (`loadOpeningExpected` → `expectedOpeningCash` del último cierre con
    entrega) y la diferencia en vivo; se guarda en `openingExpected` y la bitácora la anota
    ("Al abrir faltaban Q…"). El cierre automático de cajas olvidadas también registra entrega.
  - El detalle de un turno cerrado (`ShiftHandoverCard`) lista cada cobro con tarjeta y
    transferencia (hora, factura) — de qué turno es cada voucher — y qué entregó o retiró. Solo
    cerrado: abierto diría cuánto se lleva vendido.
  - El `DailyCashClose` sigue aparte: la cadena no lo usa todavía.
- **Pendiente (Fase C)**: depósitos al banco por tandas con foto de la boleta, que cuentan en el
  cuadre y no pasan al siguiente turno — el dueño quiere que todo el dinero termine en el banco y
  nadie retire.

### POS: diálogo de cobro (`PaymentDialog`)

- **En pantalla ancha (≥840dp, tablet horizontal) va en dos paneles**: izquierda "cómo paga"
  (método, le dieron, montos rápidos, billetes, vuelto) y derecha "del cliente" (factura
  ARRIBA, anticipo, descuento de colaborador) con el botón de cobrar al pie. En una columna de
  560dp la factura quedaba al fondo y había que desplazarse para llenarla. En teléfono sigue
  en una columna, con la factura justo después del método.
- **El NIT se consulta con el botón "Buscar"** (o la tecla buscar del teclado), no a cada tecla:
  buscar a medio escribir gastaba consultas y cambiaba el nombre mientras se tecleaba. Teclado
  `KeyboardType.Ascii` (fila de números + letras, por la K). Campo angosto con el botón al lado.
- **Acuse animado** (`SaleSuccessDialog.kt`): el ✓ entra con rebote y el vuelto cuenta hasta su
  valor. Imprimir muestra "Imprimiendo ticket…" con barra al menos `MIN_PRINTING_FEEDBACK_MS`
  (1.6 s: la térmica responde en medio segundo y un parpadeo no se leía) y después "Ticket
  impreso" (`POSUiState.printedAt`).

### POS: un cobro, varias ventas (cuenta del restaurante en el carrito) + "Último cobro"

Una cuenta de ComandaYa pendiente se puede **cobrar ahora** (lo de siempre: el dinero entra a
esta gaveta) o **agregar al carrito** para cobrarla junto con el pan: un solo pago y un solo
vuelto. Al confirmar, primero se recibe la cuenta (`collectPendingSale` con `CheckoutPayment`)
—si otra caja ya la recibió, se corta antes de mover inventario— y después se crea la venta de
panadería. Todas las ventas del cobro llevan el mismo `checkoutId`, `checkoutTotal`,
`tenderedAmount`, `changeAmount` y billetes, porque lo que se reclama es el cobro.

- Cobrada desde el carrito, la cuenta toma el **método del cobro** (se vacía el
  `paymentBreakdown` de ComandaYa): el cliente pagó en esta caja.
- Solo se ofrece sumar al carrito una cuenta **toda en efectivo**: si en el restaurante ya se
  pasó tarjeta, cobrarla otra vez le cobraría dos veces al cliente.
- Si otra caja recibe una cuenta que está en este carrito, el listener la saca y avisa.

**"Último cobro"** (ícono de reloj en la barra del POS): cuánto se cobró, cuánto dieron,
cuánto vuelto y **con qué billetes**, con flechas a los 10 anteriores. Los billetes salen de los
chips que la cajera toca en `PaymentDialog` (`billValues`/`billCounts`); si teclea el monto van
vacíos, a propósito: no se inventa con qué pagaron. Los cobros de este aparato viven en memoria;
si la app se reinició se rearman de las ventas de la sesión (`checkoutsFromSales`, test en
`CheckoutRecordTest`), sin el detalle de productos.

**Un producto está en una sola categoría** entre las que comparten sucursal
(`productCategoryConflicts`; vacío = todas). El formulario esconde los ya usados, con buscador y
un "Mover aquí" explícito que lo quita de la otra al guardar. `saveCategory` revalida contra lo
guardado: un producto en dos grupos dejaría sin saber a cuál repartirle la venta.

### Créditos — cuentas por cobrar y adelantos (reemplaza el cuaderno)

`CreditAccount` (`companies/{c}/creditAccounts`) es QUIÉN debe: persona, institución (el
colegio) o colaborador. La deuda es de la cuenta; una institución tiene **personas
autorizadas** (arreglos paralelos en el doc) y cada cargo anota cuál se llevó la mercadería.
Solo el administrador agrega personas: si llega alguien que no está en la lista no se le fía.
**Sin límite de crédito** (decisión del dueño).

`CreditMovement` (`companies/{c}/creditMovements`, plana con `accountId`) es el estado de
cuenta. `amount` con signo: + deuda (SALE, ADVANCE, ADJUSTMENT positivo), − pago (PAYMENT,
PAYROLL_DEDUCTION, ADJUSTMENT negativo). `CreditAccount.balance` es **caché**: todo lo que
escribe o borra un movimiento lo recalcula sumando la colección (`recalculateBalance`),
mismo criterio que `paidAmount` de compras. Nada se edita: los errores se corrigen con un
ajuste, así el estado de cuenta entregado sigue diciendo lo mismo. El saldo que traían del
cuaderno se carga como ajuste "Saldo anterior (cuaderno)".

- **Colaboradores no se capturan**: `CreditViewModel.load` crea la cuenta de cada colaborador
  activo con id fijo `emp_<employeeId>` (`ensureEmployeeAccount`).
- **Vender fiado** (`SalePaymentMethod.CREDIT`, permiso `SELL_ON_CREDIT`): el POS pide cuenta y,
  si es institución, quién se lo lleva. La venta mueve inventario igual; en la sesión suma a
  `totalCreditSales` y NO a `expectedCashNow`. El cargo se escribe después de la venta con
  `saleId`. No se ofrece con una cuenta del restaurante en el carrito.
- **Cobrar crédito en caja** (`COLLECT_CREDIT_PAYMENT`): abono PAYMENT + si es efectivo un
  `CashMovement` DEPOSIT ligado por `sourceCreditMovementId`; queda en "Último cobro".
- **Adelanto desde la gaveta** (`GIVE_EMPLOYEE_ADVANCE`): ADVANCE en la cuenta del colaborador +
  WITHDRAWAL de la caja, validado contra el efectivo recién leído. Los movimientos de caja con
  `sourceCreditMovementId` no se corrigen desde "Movimientos de caja" (`canAct`): se corrigen en
  la cuenta. Los adelantos ya NO se anotan en el historial del colaborador (la opción se quitó;
  los viejos se siguen viendo).
- **Planilla**: el campo "Créditos y adelantos" (`PayrollRecord.advances`) se propone con el
  saldo de la cuenta en planillas nuevas. Al quedar **PAID** escribe un PAYROLL_DEDUCTION con id
  fijo `payroll_<payrollId>` (`syncPayrollCreditDeduction`): corregirla reescribe el mismo
  renglón, volverla a borrador o borrarla lo quita. `savePayroll` ahora devuelve el registro.
- Administración (`MANAGE_CREDIT_ACCOUNTS`, módulo "Créditos" en Ventas): cuentas, pagos por
  transferencia, ajustes y adelantos de caja general. La ficha del colaborador tiene la pestaña
  "Créditos" con el mismo `CreditAccountPanel`.
- **Personas autorizadas**: se editan (mismo id) y se eliminan. Eliminarlas es seguro: cada cargo
  guarda `personName` como snapshot.
- **Antigüedad (FIFO)**: `openCharges` aplica cada pago / ajuste negativo al cargo MÁS VIEJO
  (un pago de más queda a favor y se come los cargos siguientes). `recalculateBalance` guarda los
  cargos abiertos en la cuenta (`openChargeDates/Amounts`), así el reporte
  (`CreditAgingReportScreen`, tramos 0–30/31–60/61–90/+90 vía `creditAgingOf`) y el aviso del
  Inicio (`AlertTarget.CREDIT_OVERDUE`, CRITICAL con saldo de +90) leen una sola colección.
  Cuentas con saldo anteriores al cálculo se rehacen una vez al cargar (`recalculate`).
- **Impresión**: `util/CreditDocuments.kt` arma estado de cuenta y reporte (HTML hoja + ESC/POS
  58/80) desde las mismas funciones que la pantalla. `DocumentPrintButton` (en
  `ProductionOrderPrintButton.kt`) es el botón genérico de imprimir; la orden de producción lo usa.
- **Descuento de colaborador al pagar en efectivo**: el % es **del producto**
  (`Product.employeeDiscountPercent`, pestaña Precios — mayormente empacados y reventa) y la
  persona solo tiene **derecho sí/no** (`CreditAccount.cashDiscountEligible`, en su cuenta; una
  versión anterior guardaba `cashDiscountPercent` por persona y se lee como "con derecho"). En el
  cobro con Efectivo aparece "¿Es un colaborador? Descuento de Q X" solo si algún producto del
  carrito lleva %. `POSViewModel.employeeDiscountAmount` → `employeeCashDiscount` suma línea por
  línea (medio centavo sube); el pan de grupo y el restaurante no descuentan. Va a
  `Sale.discount` + `discountAccountId/Label`. Lo fiado, con tarjeta o transferencia: sin descuento.

### Anticipos de clientes (`CustomerDeposit`) — reemplazan el papel de "ya pagó"

El cliente deja un encargo (pan, un pastel, comida del restaurante) con anticipo o pagado
completo, y lo recoge otro día en **cualquier caja o sucursal**. El anticipo es dinero que entra
a una gaveta hoy pero **todavía no es venta** (si cancela, se devuelve):

- **El diálogo de recibir es pantalla completa** (`ReceiveDepositDialog`): un AlertDialog de alto
  fijo dejaba los campos de abajo tapados por el teclado. Encargo **del catálogo** (productos con
  cantidad, `CustomerDepositItem` en arreglos paralelos, total a precio de hoy como referencia) o
  **del restaurante** (texto: los platillos viven en ComandaYa). Entrega con **calendario + reloj**
  → `deliveryAt` (epoch millis), base de los recordatorios; `deliveryDayLabel` la muestra como
  "Hoy 4:30 PM" y los pendientes se ordenan por entrega. Botones "Solo guardar" y "Guardar e
  imprimir" (sin impresora se guarda igual y se reimprime después).
- **En pantalla ancha (≥720dp, tablet horizontal) va en dos columnas que se desplazan por
  separado** (cliente + encargo | entrega + anticipo) y los botones suben a la barra de arriba:
  con el teclado ocupando media pantalla, una columna a lo ancho dejaba ver solo el campo que se
  llenaba. Nombre y teléfono en una línea desde 480dp. Secciones en tarjetas (`FormSection`).
- **No se acepta una entrega pasada** (`depositDeliveryProblem`): el calendario no ofrece días
  anteriores (`SelectableDates`) y hoy a una hora ya pasada se marca en rojo y no deja guardar;
  `receiveDeposit` lo revalida (el diálogo pudo quedar abierto mientras la hora pasaba). La fecha
  se lee "Miércoles 30/09/2026" (`longDateLabel`).
- **Sucursal de retiro** (`pickupBranchId/Name`): por defecto la de la caja; se elige entre las
  sucursales activas con `CAN_SELL`, y un pedido ligado trae la suya. El comprobante dice
  "Anticipo en: X" y "Recoge en: Y" (en negrita), y la lista de pendientes "Dejó en X · recoge
  en Y" cuando difieren. Vacío en anticipos viejos = la misma donde se dejó
  (`effectivePickupBranchName`).
- **Recibir** (menú de la caja abierta → "Anticipo de cliente"): `companies/{c}/customerDeposits`
  con folio correlativo `ANT-0042` asignado en transacción (`counters/customerDeposits`). El
  efectivo entra como `CashMovement` DEPOSIT con `sourceDepositId` — el cuadre de ESA caja lo
  explica — y **no suma a ventas**. Tarjeta/transferencia no tocan la gaveta (mismo criterio que
  el abono de crédito). Se imprime el comprobante (`util/CustomerDepositPrint.kt`, por
  `DocumentPrinter`): folio grande y "no es factura".
- **Aplicar** (cobro → "Aplicar anticipo de cliente", busca por folio/nombre/teléfono con
  `CustomerDeposit.matches`): la venta se registra por el TOTAL y el anticipo va en el desglose
  como `SalePaymentMethod.CUSTOMER_DEPOSIT`, que `incrementSaleTotals` suma a `totalSales` y a
  nada de la gaveta; hoy solo se cobra el saldo. Se reparte entre las ventas del cobro con
  `planDepositApplication` (restaurante primero, después el pan). Puede ser parcial: el resto
  queda abierto. No se combina con fiado.
- **Orden de escritura**: el anticipo se reserva (`apply`, en transacción contra el saldo recién
  leído) ANTES de escribir ventas; si el cobro se corta, `release` devuelve solo lo que no llegó
  a ninguna venta. `createSale` ahora suma a la caja **por partes** (`effectivePaymentBreakdown`),
  igual que `collectPendingSale`.
- **Devolver** ("Anticipos pendientes" → Devolver): sale en efectivo de la caja abierta, validado
  contra `expectedCashNow` recién leído; `WITHDRAWAL` con `sourceDepositId`. Los movimientos con
  `sourceDepositId` no se corrigen desde "Movimientos de caja".
- **La factura sale al entregar, por el total** de la venta. El comprobante del anticipo no es
  factura.
- Restaurante: el encargo se anota en ComandaYa (la cocina lo necesita), el anticipo en BakeOS; al
  entregar, la orden llega como cuenta pendiente, se agrega al carrito y se le aplica el anticipo.
  Regla de operación: anotar el folio en la nota de la orden de ComandaYa.
- El ticket de la venta muestra "Anticipo ANT-0042 −Q" y "Pagado hoy". Lógica pura con tests en
  `CustomerDepositTest` y `CustomerDepositPrintTest`.
- **Ligado a un pedido de cliente** (`CustomerDeposit.customerOrderId/Label`): al recibirlo, "Ligar
  a un pedido" prellena cliente, encargo, entrega y el saldo (`acceptsDeposit`, `depositLabel`,
  `depositDescription` en `model/CustomerOrderDeposit.kt`, tests en `CustomerOrderDepositTest`) y
  registra un `CustomerOrderPayment` ADVANCE con `depositId/depositFolio` — el pedido recalcula
  abonado y saldo. Al entregar y aplicarlo en el cobro, `completeOrderOnDelivery` registra el
  saldo pagado hoy como BALANCE (con `saleId`), liga la venta de pan (`linkedCustomerOrderId`) y
  marca el pedido DELIVERED si ya no debe. Devolverlo registra un pago negativo. Todo esto es no
  crítico: si falla, el dinero ya quedó bien y se avisa.
- **El pedido ya no acepta "Anticipo recibido" tecleado**: era el papel en la app, un número sin
  dinero en ninguna gaveta. El formulario conserva lo que el pedido traía y el detalle lista los
  pagos con su folio. `recordPayment` convierte el anticipo escrito a mano de un pedido viejo en
  un pago "anotado a mano" antes del primer pago real (`legacyAdvanceToMigrate`), porque el
  recálculo suma pagos y lo borraría.

### Venta a revendedor (oct 2026)

Paso 2 de `arquitectura/propuesta-rentabilidad-por-sucursal.md`. Los revendedores compran en las
tiendas (no en la planta), casi siempre de contado, a un precio menor que el público pero mayor
que el de las sucursales. Antes la caja solo cobraba `salePrice`: no había forma de cobrarles bien.

- **Un modo de la caja, no una pantalla aparte.** "Venta a revendedor" (menú ⋮ en teléfono,
  "Revendedor" en tablet) elige al cliente (`ExternalCustomer` tipo RESELLER, activo y aprobado) y
  `POSUiState.resellerSale` guarda sus precios ya resueltos para todo el catálogo. Fichas, grupo,
  diálogo de cantidad y carrito muestran ESE precio. Un aviso naranja fijo arriba del catálogo lo
  recuerda y tiene "Volver a mostrador".
- **Misma cascada que el pedido del revendedor**: `domain/ListPriceResolution.kt::resolveListPrices`
  (precio fijo del producto en la lista → porcentaje de la lista × `salePrice` → sin precio),
  redondeado a centavos. `ResellerViewModel` la usa también: lo que ve en su pedido es lo que paga.
- **Sin precio no se cobra.** Un producto sin precio en la lista no entra al carrito (avisa que hay
  que agregarlo en Listas de precios): caer al precio público cobraría de más. Si el revendedor no
  tiene lista asignada, no se entra al modo.
- **Grupo "Pan Q0.50"**: `groupListPrice` — tiene precio solo si TODOS sus panes (los que de verdad
  entran: `salePrice == groupUnitPrice`) cuestan lo mismo en la lista; si no, null y se cobran uno
  por uno. Sin miembros, cae al porcentaje de la lista.
- **Entrar con carrito lleno lo re-precia** (`repriceCart`) en vez de obligar a vaciarlo: la cajera
  se entera tarde de que es revendedor. Lo que no tiene precio se saca y se avisa. Salir lo regresa
  al precio público.
- **De contado y sin descuento de colaborador**: el diálogo de cobro no ofrece crédito ni descuento
  y `confirmSale` lo vuelve a validar. Decisión del dueño: fiar a revendedores es raro.
- **La venta guarda** `resellerId/resellerName/resellerPriceListName` (snapshot) y cada línea su
  `priceListId` + `priceSource` (PRODUCT_OVERRIDE / LIST_FACTOR). El ticket lo dice junto al método
  de pago ("Efectivo · Revendedor: X"), igual en la reimpresión.
- **Al cobrar, la caja vuelve sola a mostrador**: quedarse en modo revendedor le cobraría barato al
  siguiente de la fila.
- Tests: `ListPriceResolutionTest`.

### Alta de revendedores sin app (oct 2026)

Antes un revendedor solo existía si se registraba en la app (`ResellerSetupScreen` → aprobar). La
mayoría no tiene la app, así que **Revendedores** (Home, `ResellerApprovalScreen`) ahora tiene
"Agregar revendedor" y un lápiz en cada tarjeta que abren `ExternalCustomerFormScreen`.
- Alta desde aquí = `registrationStatus` ACTIVE y sin `linkedUserId`: entra directo a la caja.
- Para un revendedor el formulario **exige lista de precios** (sin lista la caja no entra al modo
  revendedor) y pide cómo compra: sucursal y hora si retira, referencia si se le entrega.
- La pantalla solo muestra tipo RESELLER; los clientes recurrentes siguen en Pedidos de clientes.
- Inactivar (switch "Activo") lo saca de la caja sin borrar su historial.
- Los 4 de prueba se borraron con `functions/tools/delete-test-resellers.js` (respaldo en JSON);
  sus 5 pedidos de prueba quedan, guardan el nombre como copia.
