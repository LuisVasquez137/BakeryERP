# Facturación electrónica (FEL / Digifact)

> Detalle movido desde CLAUDE.md. Leer antes de tocar este módulo.

### Facturación electrónica (FEL Guatemala, Digifact) — en construcción

Réplica en BakeOS de lo que ComandaYa ya factura (mismo certificador, mismo NIT, mismos
códigos de establecimiento). Sucursales con el NIT 59325216: **Bombero (1), Pacaja (2),
Democracia (3)**; Calvario y Salida tienen otro NIT (después). Decisiones: facturar es
opcional con switch "por defecto" por sucursal; si Digifact falla se cobra igual y la factura
queda PENDIENTE con reintento automático; la cuenta del restaurante agregada al carrito se
factura JUNTO con el pan en un solo DTE y BakeOS le avisa a ComandaYa (que no la vuelva a
facturar y descuente el crédito del paquete compartido de DTEs).

**Fase 1 (hecha, sin desplegar): servidor** — `functions/fel/`:
- `digifactClient.js`: portado de ComandaYa (`OrderApp/functions/fel`), con `fetch` nativo y
  firebase-admin modular. Login con NIT a 12 dígitos, certificar NUC, anular (CANCEL FEL, no
  NCRE: la NCRE no acepta CF), GetDocument, consulta de NIT. `serieYNumero` lee del XML
  certificado porque Digifact devuelve `serial`/`batch` al revés.
- `felCredenciales.js`: credenciales POR NIT en la colección de nivel superior
  `FelCredentials/{companyId}__{profileId}` — las reglas no la abren (la de `companies/**`
  deja leer todo a cualquier empleado). El token se cachea ahí mismo.
- `felEmisor.js`: `companies/{c}/felProfiles/{pid}` (datos SAT, sin secretos) + en la sucursal
  `felProfileId` y `felCodigoEstablecimiento`.
- `nucXmlBuilder.js`: XML NUC desde líneas genéricas **con descuento por línea**
  (`<Discounts>`; ComandaYa no los mandaba). Total = Qty×Price−Descuento, IVA sobre eso, todo al
  centavo y totales sumados de los items. `prorateDiscount` reparte el descuento del encabezado
  de la venta. Referencia interna `BKOS-…` (no choca con ComandaYa) en modo VALIDAR. Validado
  contra `NUCSchema.xsd` oficial; tests en `nucXmlBuilder.test.js` (`npm test`).
- `felFunctions.js`: `felCertificarVenta` (varias ventas de un cobro → un DTE;
  `dte_<huella>` determinista + candado en transacción; duplicado 9022 → recupera el DTE),
  `felConsultarNit`, `felAnularDte`, `felObtenerPdf`, `felGuardarPerfil` (solo OWNER; prueba el
  login antes de guardar), `felReintentarPendientes` (cada 5 min, cola `felPendientes`,
  espera 2^n min, tope 12). Estados: CERTIFICANDO / CERTIFICADO / PENDIENTE (transitorio) /
  RECHAZADO (contenido o configuración) / ANULADO. La venta lleva `dteId` + `invoiceStatus` +
  serie/número.
- `tools/fel-probar.js`: prueba de punta a punta contra Digifact con credenciales por env;
  `fel-probar-sabropan.js` la corre con los datos de emisor de Sabro Pan y `fel-login.js` solo
  prueba el login. **Verificado en PRODUCCIÓN el 2026-09-25**: certificó una FPEQ de Q1.90 y la
  anuló. No hay ambiente de pruebas para este NIT: certificar es una factura real. En
  PowerShell la contraseña va con `$env:DIGIFACT_PASSWORD = '...'` (comillas simples por el `$`).

**Fase 2 (hecha, sin probar en dispositivo): configuración en la app** — módulo
"Facturación" en Administración, **solo OWNER** (`RequireOwner` en la ruta y `role == OWNER` en
el Home; la función exige lo mismo). `FelSetupScreen` lista los NIT con sus sucursales y las que
no facturan; `FelProfileFormScreen` edita datos del RTU, tipo de contribuyente (PEQ → FPEQ,
GEN → FACT + régimen ISR), credenciales y sucursales (código de establecimiento + "Facturar por
defecto").
- `model/FelProfile.kt`: `isValidGuatemalaNit` (módulo 11, atrapa el error de dedo antes que
  Digifact) y `felAssignmentProblems` (código vacío, no numérico o **repetido** entre sucursales
  del mismo NIT — "01" y "1" son el mismo). Tests en `FelProfileTest`.
- `FirebaseFelRepository` (nonWasmMain) lee `felProfiles` de Firestore y **guarda solo por
  `felGuardarPerfil`** (GitLive `firebase-functions`, primera Cloud Function que llama la app):
  las credenciales no pasan nunca por Firestore del cliente. La contraseña del formulario vive en
  `remember`, no `rememberSaveable`, para no terminar en el Bundle del sistema.
- `felGuardarPerfil` **prueba las credenciales antes de guardarlas**: antes las guardaba y
  después probaba, y una contraseña mal tecleada reemplazaba a la que sí facturaba.
- Orden del guardado: perfil primero, sucursales después (`FelViewModel.save`). Si Digifact
  rechaza, las sucursales no se tocan.
- `Branch.felProfileId` / `felEstablishmentCode` (en Firestore `felCodigoEstablecimiento`, el
  nombre que lee `felEmisor.js`) / `invoiceByDefault`. Van en `toMap()` y `BranchFormScreen.build()`
  los arrastra: `updateBranch` hace `set()` y guardar la ficha borraría el NIT. La asignación se
  escribe parcial (`assignBranches`), y desmarcar una sucursal la deja sin NIT.

**Desplegadas** las seis funciones `fel*` en `bakery-erp-saas` (2026-09-25).

**Fase 3a (hecha, sin probar en dispositivo): facturar al cobrar.**
- `PaymentDialog` lleva la sección **"Facturar"** (`pos/InvoiceSection.kt`) solo si la sucursal
  tiene NIT (`POSViewModel.canInvoice`); sale marcada con `Branch.invoiceByDefault`. Consumidor
  final o NIT con consulta a la SAT (`felConsultarNit`); si la SAT no contesta, el nombre se
  escribe a mano en vez de dejar al cliente sin factura. **Desde Q2,500 no se acepta CF**
  (`FEL_CF_LIMIT`, `felReceptorProblem` en `model/FelInvoice.kt`, tests en `FelInvoiceTest`).
- La factura cubre el pan **y las cuentas del restaurante que ComandaYa no facturó** (ver
  "Fase 4 mínima" abajo).
- **La venta nace `invoiceStatus = SOLICITADA`** con el receptor adentro, en la misma escritura
  que la crea. Si la llamada a `felCertificarVenta` se pierde (tablet sin internet justo al
  cobrar), el servidor nunca se enteraría: `felReintentarPendientes` barre las SOLICITADA de
  más de 2 minutos por empresa (igualdad simple, sin índice de grupo) y las certifica con el
  receptor guardado en la venta.
- El acuse del cobro espera **hasta 15 s** (`INVOICE_WAIT_MS`) y muestra serie/número, o
  "pendiente — se completa sola". Esperar los 45 s que puede tardar Digifact trabaría la fila.
  `InvoiceProgress.saleId` evita que una respuesta tardía aparezca en el acuse de otra venta.
- Los campos de factura van en `Sale.toMap()`: `collectPendingSale` reescribe la venta con
  `set()` y sin ellos recibir una cuenta le borraría la factura.
- Historial y detalle de venta muestran `SaleInvoiceChip`.
- **Sin internet** también se factura: la venta sube SOLICITADA cuando vuelve la red y el barrido (o la
  misma caja) la certifica. Ver `pos-ventas.md`, "Factura sin internet".
- **Sin detalle** (switch en `InvoiceSection`): una sola línea "Servicio de alimentos y/o
  bebidas" por el total, el mismo texto que ComandaYa (`DESCRIPCION_SIN_DETALLE`). Viaja en la
  venta (`Sale.invoiceWithoutDetail`, `CheckoutPayment.invoiceWithoutDetail`) para que el
  barrido de SOLICITADA la respete; el DTE guarda `descripcionSinDetalle` y el ticket imprime
  esa línea en vez de los productos (`ReceiptInvoice.singleLineDescription`).
- **El ticket con factura no imprime "Recibido" ni "Vuelto"**: es el documento del cliente, no
  el del cobro. El ticket sin factura los sigue llevando.

**Fase 3b (hecha, sin probar impresa): ticket con la factura.** `ReceiptData.invoice`
(`ReceiptInvoice`, armado por `receiptInvoiceFrom` desde la respuesta de la función) lleva lo
que la SAT pide en la representación gráfica: emisor (manda sobre el nombre de la empresa en el
encabezado), tipo de documento, serie/número, autorización, fecha de certificación, receptor,
frases, certificador (`FEL_CERTIFIER_NAME`/`NIT`) y el QR.
- **URL del QR**: `felVerificationUrl`, formato de la SAT ("Reglas y Validaciones FEL" 5.6), el
  mismo que ComandaYa (`DatosCertificador.urlVerificacion`).
- **Térmica**: la impresora dibuja el QR sola con `GS ( k` (`util/QrCode.kt::escPosQr`). Si el
  byte bajo del largo diera 0x0A se agrega un `&`: `chunkAtLineBreaks` corta en 0x0A y partiría
  el comando entre dos envíos.
- **Hoja/PDF/web**: `qrSvg` arma un SVG con la matriz de `io.github.g0dkar:qrcode-kotlin`
  (`QRCodeProcessor`, solo la matriz — `QRCode.ofSquares()` trae el render de cada plataforma).
- Si la factura cubre menos que el cobro (cuenta del restaurante aparte), el ticket imprime
  "Monto facturado": el total impreso y el de la SAT tienen que poder cuadrarse.
- La factura se adjunta al ticket del cobro al certificarse (`certifyInvoice`); el acuse cambia
  el botón a "Imprimir factura". Tests en `ReceiptInvoicePrintTest` (ancho de cada línea, URL,
  QR presente).
- **Falta**: reimprimir la factura desde el historial de ventas.

**Fase 3c (hecha, sin probar en dispositivo): anular y volver a facturar.** Desde el detalle
de la venta, con `VOID_SALE` (`pos/InvoiceVoidDialogs.kt`). **Anular la factura no anula la
venta**: no devuelve dinero ni inventario, y el diálogo lo dice. Para eso está "Anular venta"
(`pos-ventas.md`), que anula la factura primero y después la venta.
- **"Anular y facturar"** en un paso y **"Facturar de nuevo"** para anuladas o rechazadas. El
  caso común de anular es el NIT equivocado; sin reemisión, esa venta quedaba sin factura porque
  el id del DTE sale de las ventas y la segunda chocaba con la primera.
- `Sale.invoiceVersion` (solo servidor): reemitir la sube, y con ella cambian `dteIdPara` y
  `referenciaInterna` (Digifact rechaza referencias repetidas). La versión 0 no altera los ids
  ni las referencias de las facturas ya emitidas. Reemitir marca las ventas SOLICITADA con el
  receptor nuevo ANTES de llamar a Digifact, así el barrido retoma la versión correcta.
- El servidor lee del DTE anterior qué ventas cubría: la app manda solo la venta abierta.
- **Anular pide la fecha de emisión EXACTA**: `fechaEmision` se guarda al certificar, y las
  facturas anteriores la sacan del `<IssuedDateTime>` del XML guardado (`fechaEmisionDelDte`).
  Antes se mandaba la de certificación, que llega segundos después.
- Anular y reemitir son dos llamadas separadas: si la anulación sale y la nueva falla, lo
  anulado ya es un hecho ante la SAT; la venta queda para "Facturar de nuevo".

**Desde el historial de ventas** (`SaleDetailScreen`):
- **Reimprimir** (ícono de impresora): cualquier venta; si está facturada sale la
  representación gráfica completa con QR, leída del DTE guardado (`FelRepository.getInvoice`)
  sin volver a llamar a Digifact. El ticket lo arma `saleReceiptData` (`pos/SaleReceipt.kt`),
  que el cobro del POS también usa: el reimpreso dice lo mismo que el original. La primera vez
  en el aparato abre el selector de térmica, igual que el cobro.
- **"Facturar esta venta"**: para el pan que salió sin factura (el cliente la pidió después, o
  se olvidó marcarla). `felCanInvoiceLater` decide: sin factura previa, COMPLETED, sucursal con
  NIT. Incluye cuentas del restaurante: la que ComandaYa facturó trae `EXTERNA` y no pasa. Pide
  `OPERATE_POS`, el mismo permiso que facturar al cobrar. Se emite con la fecha de hoy.

**Fase 4 mínima (hecha): el restaurante en la misma factura, sin avisarle nada a ComandaYa.**
Se descartó la fase 4 completa (BakeOS → ComandaYa y el saldo compartido de DTEs): dos
proyectos Firebase, despliegues en las dos apps, ~2 días para una sola empresa. Regla de
operación: **donde paga el cliente, ahí se factura** — en la mesa, ComandaYa; en el mostrador
junto con el pan, BakeOS.
- **El puente de ComandaYa ya mandaba `billed`/`dteId`** y reenvía la orden cuando la factura
  después de cobrarla; BakeOS los ignoraba. Ahora `ingestExternalSale` escribe
  `invoiceStatus = "EXTERNA"` + `externalDteId`, y el servidor deja fuera de la factura las
  cuentas EXTERNA (no rechaza el documento entero: facturarla en la mesa mientras está en el
  carrito dejaría sin factura también el pan, y cada reintento chocaría con ella).
- **Bug corregido en `ingestExternalSale`**: hacía `set()` sin merge, y cualquier reenvío (p. ej.
  facturar en ComandaYa una orden ya cobrada) devolvía a PENDING_COLLECTION una cuenta ya
  recibida en caja —reaparecía en el POS para cobrarse dos veces— y le borraba gaveta, cobro y
  factura. Ahora una cuenta COMPLETED no se reescribe: de un reenvío solo se acepta enterarse de
  que ComandaYa la facturó. Si BakeOS ya la había facturado queda `invoiceConflict = true` (chip
  rojo "Doble factura: anular una") en vez de pisar nada. Una cancelación posterior a recibirla
  solo se registra en el log: el dinero ya está en una gaveta.
- `externalDeliveryFee`: el domicilio no viaja como línea; `lineasDeVenta` lo agrega como
  "Servicio a domicilio", o la factura salía por menos de lo cobrado. Si el detalle no cuadra
  con el total de las ventas queda un warning en el log.
- La cuenta del restaurante queda SOLICITADA al recibirse en el cobro
  (`CheckoutPayment.invoiceReceptorId/Name` → `collectPendingSale`), igual que el pan.
- **Operación decidida (2026-09-27)**: en Sabreopan todo se marca "Pagado" en ComandaYa y **se
  cobra y factura en BakeOS**. Para que nadie facture allá por error basta con dejar en 0 el
  `dteCreditsBalance` del restaurante: ComandaYa ya bloquea la facturación sin saldo.
- **Cuenta adicional** (`functions/externalAddendum.js`, tests en `externalAddendum.test.js`): si
  ComandaYa reenvía con más monto una cuenta ya recibida en caja (reabrieron la orden para un
  postre), entra una venta `{externalId}_ad{n}` PENDING_COLLECTION con solo lo nuevo, mesa
  "… · adicional". La original guarda `accountedTotal`/`accountedItems` (lista, no mapa: un
  nombre con punto rompería la clave; las cuentas previas lo reconstruyen de sus líneas) y un
  reenvío idéntico no crea otro. El monto manda sobre las líneas: descuento o renglón de
  "Ajuste" para que cuadre. Si el total BAJÓ se loguea como devolución a revisar. Suma al
  resumen del día sin contar transacción (`applied.transactions = 0`).
- **Contador de DTEs de ComandaYa**: cada DTE de producción de la empresa conectada se avisa al
  endpoint `registrarDteBakeos` de ComandaYa (en su `sabreopanBridge.js`, id de restaurante
  literal), que suma `FelProfiles.dtesConsumidos` del NIT —el que refleja el portal de Partners
  de Digifact— sin tocar `dteCreditsBalance` (saldo del SaaS). Idempotente por uuid
  (`Restaurants/{r}/bakeosDtes/{uuid}`). Autentica con la misma clave del puente
  (`COMANDAYA_INGEST_KEY` = `BAKEOS_INGEST_KEY` de ComandaYa). `comandayaReportado = false` se
  escribe junto con la factura certificada y `felReintentarPendientes` reintenta (anuladas
  incluidas: también gastaron su DTE). Solo certificaciones cuentan; las anulaciones no suman.
  `tools/fel-backfill-comandaya.js` marca los DTEs emitidos antes de esto.
- **Lo que no se hizo**: las cuentas corrompidas por el bug de `set()` antes del arreglo no se
  pueden detectar después: el reenvío borró la gaveta que las recibió., Fase 4 (puente con ComandaYa y crédito compartido; y arreglar que
`ingestExternalSale` hace `set` sin merge y reabre una venta ya recibida en caja).
