# Precios, despacho, reparto e inventario de tienda

> Detalle movido desde CLAUDE.md. Leer antes de tocar este módulo.

### Listas de Precios — cascada
`PriceList` con `type` (RETAIL|WHOLESALE|CUSTOM) y `isDefault` por tipo. `Branch.defaultPriceListId` opcional; si no, default del tipo WHOLESALE.

**Cascada `resolveWholesalePrice`**:
1. `ProductPrice(productId, listId)` con `unitPrice>0` → override.
2. `priceFactor` (0..1+, ej. 0.80) × `product.salePrice` → calculado.
3. Nada → `unitPrice=0`, `source=NONE`. **Bloquea cierre del despacho.**

`ResolvedPrice.source` (PRODUCT_OVERRIDE|LIST_FACTOR|NONE) se serializa como `DispatchNoteLine.priceSource` (snapshot inmutable — auditoría).

**Seed**: primer ingreso a `PriceListAdminScreen` crea "Cliente final" (RETAIL default) y "Sucursales" (WHOLESALE default) vía `ensureSeedLists(companyId)`.

**Sync salePrice ↔ lista RETAIL default**: en tab "Precios" del form de producto, fila de RETAIL default es readonly y refleja el `salePrice` de tab General. `salePrice` es única fuente de verdad para retail; se persiste también como `ProductPrice` en RETAIL default.

**Override vacío**: pasa el listId a `priceListIdsToReset` y el VM borra el doc `ProductPrice` — vuelve a aplicar factor o "sin precio".

Permisos: `MANAGE_PRICE_LISTS`, `VIEW_DISPATCH_VALUE` para ver montos.

### Despacho (`DispatchNote`) — flujo post-producción

Al `completeLot` se crean **`DispatchNote` DRAFT por cada `sourceOrderId`** (`createDispatchNotesForLot`, idempotente).

**Ciclo:**
```
DRAFT → READY (empaque cerró) → IN_TRANSIT (salió a reparto)
       → ARRIVED (acuse de llegada) → DELIVERED / DELIVERED_WITH_DISCREPANCY
       o CANCELLED (solo DRAFT/READY)
```

**Recepción en 2 pasos** (refleja flujo real):
1. **`acknowledgeArrival`**: IN_TRANSIT→ARRIVED. La encargada verifica TIPOS de pan; libera al repartidor. Sin stock todavía.
2. **`confirmReception`**: ARRIVED→DELIVERED. La encargada cuenta con calma, ajusta cantidades y razones (FALTANTE/DAÑADO/SOBRANTE). Dispara `applyMovement` RECEPCION a `FinishedStockRepository`.

Acepta `confirmReception` también desde IN_TRANSIT (flujo rápido).

**Cerrar el conteo se acusa con diálogo, y ése es el que navega.** Al cerrarlo la nota pasa
a DELIVERED y `DispatchNoteReceiveScreen` vuelve a dibujar **el mismo formulario de conteo**
(su rama `else` cubre todo lo que no es IN_TRANSIT), así que quien contó no tenía señal de
que el ingreso hubiera quedado registrado — el único aviso era `lastReceptionSummary`, que
ninguna pantalla consumía. Ahora `DispatchUiState.receptionResult` lleva unidades, productos
y discrepancias hasta un `AlertDialog` sin descarte por fuera, y su botón es el que hace
`onNavigateBack()`. El acuse de llegada (paso 1) sigue saliendo solo con `isSaved`: ahí no
hay nada que reportar más allá de "el repartidor se puede ir", y por eso la condición es
`isSaved && receptionResult == null`.

**Pre-llenado**:
- Empaque: `packedQuantity = requestedQuantity`. Si difiere → `packedReason` obligatorio.
- Recepción: `receivedQuantity = packedQuantity`. Si difiere → `discrepancyType` + `receivedReason`.

**Bloqueos**:
- DRAFT→READY bloqueado si alguna línea tiene `priceSource=NONE`. Los pedidos especiales llevan `SPECIAL_ORDER` y quedan exentos (ver "Pedidos especiales").
- `cancelNote` bloqueado en IN_TRANSIT o terminales; `restoreNote` solo desde CANCELLED.
- Discrepancia NO devuelve stock a planta (queda como pérdida en tránsito en Kardex).

**Cancelar una nota no la borra, y se puede deshacer.**

Cancelar era un botón a un toque sin confirmación, y la nota cancelada no aparecía en
ninguna de las cinco pestañas: se leía como borrada, sin forma de ver qué se canceló ni de
volver atrás. Y el pan cancelado —que ya estaba horneado— desaparecía del sistema entero.

- **`cancelNote`** guarda `statusBeforeCancel`, `cancelledAt/By`, `cancelReason` (opcional) y
  `plantAbsorbedAtCancel`, y **suma `packedQuantity` al `FinishedProductStock` de la planta**
  como `AJUSTE`: ese pan existe y se queda físicamente ahí. No hay doble conteo con
  `absorbLeftoverIntoPlant` — aquél ingresa lo **no** empacado (`requested − packed`) y éste lo
  empacado. Tampoco con el cierre de empaque: `observePackingViewForBranchDateShift` y
  `closePackingForBranchDateShift` filtran `status == DRAFT`, así que la cancelada ya salió.
- **`restoreNote`** devuelve la nota a `statusBeforeCancel` (READY → "Listas", DRAFT → la matriz
  de empaque) y **descuenta de la planta** lo que la cancelación le sumó. Notas canceladas antes
  de esto no traen el campo: vuelven a DRAFT y no mueven inventario, porque tampoco lo movieron
  al cancelarse — para eso existe `plantAbsorbedAtCancel` como hecho guardado y no como algo
  deducido del estado.
- **Un solo helper para los dos sentidos** (`applyPlantAbsorption`, con `sign` ±1): un reverso
  escrito aparte termina usando otra cantidad y deja pan fantasma en la planta. Agrupa por
  `productId` porque una nota puede repetirlo (especiales consolidados) y `applyMovement`
  lee-modifica-escribe el stock — dos llamadas seguidas sobre el mismo doc se pisan.
- **El encabezado se escribe ANTES de mover stock**, igual que `receivePurchase`: si el
  movimiento falla a media aplicación, un reintento choca con la guarda de estado en vez de
  sumar el pan dos veces.
- **La pestaña "Canceladas" solo aparece cuando el día tiene alguna** — mismo criterio que
  Facturas/Compras rápidas en Compras. Con ella el `TabRow` fijo pasa a `ScrollableTabRow`: seis
  pestañas repartidas a lo fijo dejan "Pendiente conteo" ilegible en teléfono.
- El botón de restaurar vive en el **detalle** y no en la lista: mueve inventario, y quien lo
  toca tiene que estar viendo qué pan y cuánto. Los dos diálogos dicen el número de unidades y
  a qué sucursal le pega antes de confirmar.

**Self-dispatch** (`markAsLocalDispatch`): si `originBranchId==destinationBranchId` y READY, botón único **"Marcar como recibida en mostrador"** salta a DELIVERED sin reparto. Suma a `FinishedProductStock` de la planta.

**Permiso `VIEW_DISPATCH_VALUE`**: oculta `unitWholesalePrice` y `totalValueWholesale` en todas las pantallas (solo OWNER por defecto).

**Scroll en las pantallas de lista larga**: la lista de productos va en un `LazyColumn` con `Modifier.weight(1f)` dentro de la Column de la pantalla, con encabezado y botón de acción fijos arriba y abajo. Puesta en una Column plana, una nota con muchos productos empuja las últimas filas y el botón fuera de pantalla sin scroll que las alcance — le pasó al checklist de llegada de `DispatchNoteReceiveScreen`, que dejaba a la encargada sin poder firmar el acuse.

**Pantallas**: `DispatchNoteListScreen` (tabs por status, filtro mes, listener vivo), `DispatchNotePackScreen` (input por producto, razón si difiere, bloquea si precio NONE), `DispatchNoteReceiveScreen` (UI alterna por status), `DispatchNoteDetailScreen` (read-only con timeline).

**Agrupado por masa en empaque/recepción/detalle**: `DispatchNoteLine` no trae `recipeId` (viene de `ProductionOrderLine`, que tampoco lo tiene), así que en vez de snapshotearlo se resuelve en cliente reutilizando `ProductionUiState.recipeRefByProductId` (`ProductionViewModel.loadRecipeRefByProductIdIfNeeded`, ya cacheado) + `groupByMasa` de `presentation/production/MasaGrouping.kt` — ambos son `internal`, visibles igual desde `presentation/dispatch` porque `internal` en Kotlin es de módulo, no de paquete. `DispatchNotePackScreen`, las dos secciones de `DispatchNoteReceiveScreen` (`ArrivalChecklistSection`, `CountSection`), `DispatchNoteDetailScreen` (`LinesSection`) **y `PackingMatrixScreen.kt`** (`PackingMatrixTable` para tablet, `PackingBranchTabs` para móvil — la pestaña "Por empacar", que es la que se abre por defecto al entrar a Despacho, así que es la que más importa) reciben `productionViewModel` como parámetro nuevo solo para esto — mismo patrón que ya usaba `ProductWeightsScreen`. **Ojo**: "Despacho" tiene 4 pantallas con lista de productos, no 3 — la matriz `PackingMatrixContent` (pestaña "Por empacar" por defecto) es la que más se usa y es fácil pasarla por alto porque vive en un archivo separado (`PackingMatrixScreen.kt`) y no en `DispatchNote*Screen.kt`.

### Reparto — el tablero del día (oct 2026)

**Despacho es solo de la planta** (`DISPATCH_PACK`): la matriz de empaque de siempre y
"Cerrar turno". Sus pestañas (Listas, En tránsito, Pendiente conteo, Recibidas) se fueron:
**Reparto es el tablero de todos** (`DeliveryBoardScreen`, permisos
`MANAGE_DELIVERY_RUNS`/`DRIVE_DELIVERY`/`RECEIVE_DISPATCH`/`VIEW_PRODUCTION`). La sucursal
firma la llegada y cuenta desde ahí; las pantallas de recepción y detalle no cambiaron, solo
desde dónde se llega. Diseño aprobado con prototipo antes de construir.

- **Una tarjeta por sucursal y viaje**, agrupadas por viaje (`tripOfNote`: el pan de noche de
  Pacajá aparece en el 2º viaje, que es cuando sale). Etapas (`boardStage`): En empaque
  (barra que crece por panes) · Lista para salir · En camino (carretera mínima, camión blanco,
  pin) · Llegó-falta contar · Recibida/cancelada (plegadas al final).
- **Solo late lo que te toca** (`BoardViewer.isMyTurn`): a la planta lo listo sin camión, a la
  sucursal lo que falta contar. El dueño lo ve en **modo supervisión**: tiene todos los
  permisos y si le latiera todo lo que puede hacer, no le serviría para ver lo atrasado.
- **Excepción de paleta aprobada por el dueño:** el chip "Llegó · falta contar" lleva letra
  BLANCA sobre `Accent` (2.6:1, contra la regla de abajo). Acotada a esta pantalla, en
  `styleOf` de `DeliveryBoardScreen`; el resto de la app sigue con `OnAccent`.
- **Avance de empaque en vivo**: `DispatchNote.plannedUnits/packedUnits`, caché recalculada
  desde las líneas (`refreshPackedUnits`) en cada marca/corrección. Recalcular y no sumar:
  varias pantallas reescriben el encabezado entero y un contador se desfasaría para siempre.
- **Estimado de llegada sin GPS**: `deliveryLegStats/{origen}_{destino}` guarda lo que suele
  tardar cada tramo; se actualiza al firmar cada llegada (`recordLegSample`, pesa 25% la
  última, descarta firmas de más de 4 h). Con menos de 3 muestras no se promete un minuto
  ("Salió hace X min") pero el camión igual avanza, con 15 min por parada de supuesto.
  Nunca llega al pin solo: llega cuando la sucursal firma.
- **Ruta fija por planta** (`Branch.deliveryRouteOrder`, ícono de ruta en la barra): orden de
  las paradas al despachar. Separada de `dispatchBranchOrder` porque el camión se carga al
  revés de como entrega.
- **Hoja de reparto en un toque**: "Despachar Nº viaje" en el encabezado del viaje abre una
  hoja con todo lo listo ya marcado en orden de ruta y el repartidor/vehículo del último viaje
  igual (`DeliveryRun.trip`). "Salir" = `saveRun` + `startRun`; el "Iniciar reparto" aparte se
  olvidaba y dejaba la hoja pendiente con el camión en la calle.
- La planta toca una tarjeta en empaque → la matriz abre ya en esa sucursal, turno y día
  (`DispatchViewModel.focusPacking`).

#### La cadena de cada entrega (`model/UnitChain.kt`, `NoteChainSheet`)

El dueño (y quien maneja la planta que la mandó) toca una entrega en el tablero y ve, por
producto, **pedido → empacado → contado**, con el eslabón donde empezó a faltar
(`ChainLine.firstBreak`: producción, empaque, o camino/recepción) y quién estaba en cada paso
(empacó, llevó, firmó la llegada, contó). La encargada de sucursal no la ve: saber quién perdió
su pan genera roces que no ayudan a encontrarlo.

- **Motivos de lista fija** (`PackingReason`, `ReceptionReason`, selector `ReasonPicker` con
  "Otro" + detalle obligatorio). En dos semanas de datos reales el empaque decía "no se iso",
  "nose iso", "f" y "no salio" para lo mismo: con texto libre no se podía sumar nada. Se
  guardan como texto legible para que las notas viejas se lean igual.
- **El traspaso entre sucursales dice a quién**: "Se tomó de Calvario" / "Se pasó a Democracia"
  (antes "reasignadas a otra sucursal"). No cuenta como pérdida (`isTransfer`).
- Un empaque corto se atribuye a **producción** si dice "No alcanzó la producción" o si el turno
  horneó menos de lo planeado de ese pan (con conteo confirmado); si no, a empaque.
- El horno no sabe para quién es cada pan: el dato del turno es contexto, no se reparte.
- **Canastas** (`DispatchNote.basketsSent/basketsReceived`): al cerrar el turno de empaque
  se anota cuántas van a cada sucursal (obligatorio, solo lo que viaja); al firmar la llegada
  la sucursal ve "Salieron 6" y contesta "Llegaron las 6" o cuántas — sin prellenar, para que
  no se firme sin contar. Cada canasta va a una sola sucursal aunque lleve varios panes, así
  que `basketCheck` separa el tramo camino/recepción en **Camión** (faltó canasta) o
  **Dentro de la canasta** (empaque o conteo). Notas anteriores: sin dato, no se afirma nada.
- Pendiente: reporte "Dónde se pierde el pan" por período (eslabón, sucursal, producto, persona).

### Reparto (`DeliveryRun`)
Agrupa N `DispatchNote` para repartidor + vehículo + día.

**Status**: PENDING→IN_PROGRESS→COMPLETED/CANCELLED.

- **`saveRun`**: calcula diff `previousNoteIds` vs `newNoteIds`; añadidas → `dispatchRepository.markInTransit(noteId, runId)`; quitadas → `unassignFromRun`. Centraliza la transición.
- **Auto-cierre** (`tryAutoCompleteRun`): cuando todas las paradas están en "driver done" (ARRIVED|DELIVERED|DELIVERED_WITH_DISCREPANCY|CANCELLED), pasa a COMPLETED. Disparado desde `confirmReception` Y `acknowledgeArrival`.
- **Cancel bloqueado** si alguna parada está ARRIVED en adelante.
- **Vehículo = `FixedAsset`** con categoría que contenga "Vehículo"/"Vehiculo"/"Transporte", `active=true`, `status=ACTIVE`. Si solo hay uno, auto-preselección. Guarda `vehicleAssetId` + `vehicleInfo` denormalizado.
- Repartidor (`DRIVE_DELIVERY`) ve solo sus runs; supervisor (`MANAGE_DELIVERY_RUNS`) ve todas las de sus sucursales accesibles.

### Inventario de Tienda (`FinishedProductStock` + `FinishedStockMovement`)

Plano `companies/{c}/finishedStocks/{branchId}_{productId}` con `currentStock` + `avgUnitCost` (promedio ponderado).

**Recalculo `avgUnitCost` en `applyMovement`**:
- Ingreso (qty>0): `avg = (stockPrev × avgPrev + qty × unitPrice) / (stockPrev + qty)`.
- Salida (qty<0): stock baja, avg se mantiene.

**Tipos**: RECEPCION (auto), VENTA, MERMA, AJUSTE, TRANSFERENCIA.

**La lista no sale solo de los documentos de stock** (`model/StoreInventory.kt::buildStoreInventoryRows`,
tests en `StoreInventoryTest`). Un `FinishedProductStock` nace con el primer movimiento, así que
un producto de reventa recién dado de alta no aparecía en ninguna sucursal —ni en 0— y no había
cómo saber si la factura había ingresado. Ahora cada producto de reventa activo sale en cada
sucursal (0 = "sin movimientos todavía"), con filtro Pan propio / Reventa. El producto
respaldado por insumo (huevo por docena) muestra el `BranchStock` del insumo ÷
`ingredientUnitsPerSale` y **no ofrece Ajustar**: un ajuste acá escribiría un terminado paralelo
que la venta no descuenta. `FinishedStockUiState.stocks` lleva además registros sintéticos en 0
para la reventa, porque el formulario de ajuste elige producto de esa lista (conteo inicial de
lo que ya estaba en el estante). `functions/tools/inspect-resale-stock.js` es un diagnóstico de
solo lectura de compras de reventa vs. `finishedStocks`.

Form `FinishedStockAdjustScreen` con 4 opciones (Merma, Ajuste menor/mayor, Venta directa), valida no negativo, razón obligatoria, preview "Nuevo stock: X".

KPIs ocultan valor de inventario sin `VIEW_DISPATCH_VALUE`. Permiso: `MANAGE_FINISHED_STOCK`.
