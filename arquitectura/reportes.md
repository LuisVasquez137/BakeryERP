# Reportes y dashboard

> Detalle movido desde CLAUDE.md. Leer antes de tocar este módulo.

## Reportes y Dashboard

Pieza nueva (Fase 1 de reportería). Tres reportes formales + HomeScreen con datos vivos + banner de alertas.

### Permisos (`Permission.kt`, grupo "Reportes")
`VIEW_OPERATIONS_REPORT`, `VIEW_SALES_REPORT`, `VIEW_PRODUCTION_REPORT`, `VIEW_FINANCIAL_REPORT`, `VIEW_INVENTORY_REPORT`. OWNER los hereda implícito. Los permisos de monto (`VIEW_SALES_VALUE`, `VIEW_DISPATCH_VALUE`) siguen controlando si los $ se muestran dentro de cada reporte.

### Agregaciones (cliente, no servidor)
Firestore (y especialmente JS) no tiene group-by. Patrón: cada repo expone `aggregateXxxInRange(...)` que devuelve un `data class XxxSummary` ya agrupado. Viven en `domain/report/ReportSummaries.kt`:

- `SaleRepository.aggregateSalesInRange(companyId, branchIds?, from, until): SalesSummary`
- `ProductionRepository.aggregateProductionInRange(companyId, branchIds?, from, until): ProductionSummary` + `getLotsForBranchInRange(...)`
- `DispatchRepository.aggregateDispatchInRange(companyId, from, until): DispatchSummary` — `inTransitLoss = packedQty − receivedQty` cuando hay SHORT/DAMAGED
- `ExpenseRepository.getExpensesForPeriod(period)` + `aggregateExpensesForPeriod(...): ExpenseSummary` (period = "YYYY-MM"); corta `fromPurchases` vs `standalone`
- `PurchaseRepository.aggregatePurchasesInRange(companyId, from, until): PurchaseSummary` — filtra en memoria (mismo motivo que `getPurchases`: `date` puede ser `Timestamp` o `Long`)
- `PayrollRepository.getPayrollForPeriod(from, until)` + `aggregatePayrollForPeriod(...): PayrollSummary` — itera empleados (no `collectionGroup`) para evitar requerir índice nuevo. APPROVED+PAID solamente.
- `FinishedStockRepository.aggregateMovementsInRange(branchId, from, until): StockMovementSummary`
- `BreadCountRepository.aggregateMermasForMonth(branchId, yearMonth): MermaSummary`

### Reportes (`presentation/reports/`)
- **SalesReport**: día/semana/mes + sucursal. KPIs (ingresos, tickets, ticket promedio, unidades), barras por día, horizontales por método de pago, top productos, cajeros, histograma horario.
- **ProductionReport**: día/semana/mes + planta. KPIs (lotes completados, unidades, % eficiencia, merma en tránsito), paired bars planeado vs producido, por turno, por producto (badge de variación %), discrepancias por sucursal destino.
- **FinancialReport**: mensual. KPIs (ingresos, egresos, utilidad operativa, margen %, comprado, salió de caja). P&L card. Ingresos = `SalesSummary.totalRevenue`. Egresos = `ExpenseSummary.total + PayrollSummary.totalEmployerCost`. Utilidad = ingresos − egresos − planilla − mermas. Compras y flujo de caja en secciones aparte (ver abajo). Por sucursal y mermas por razón.

**Las compras entran como FLUJO DE CAJA, no como egreso del P&L.** El reporte no las conocía:
egresos eran gastos + planilla, así que la harina y el azúcar —la mayor salida de efectivo de
una panadería— no aparecían en ningún lado. Restarlas de la utilidad habría sido peor: son un
activo hasta que se venden, y un mes en el que se llena la bodega saldría en pérdida y el
siguiente en ganancia inventada. Por eso viven en dos secciones propias ("Compras del mes" y
"Salida de dinero del mes") y la utilidad operativa queda igual, con una nota que dice cuánto
se compró de inventario y por qué no está ahí.

**El doble conteo es el modo de fallo real, y se evita con dos cortes:**
- `Purchase.inventoryAmount` / `expensedAmount` denormalizan qué parte del total entró a
  inventario (INGREDIENT + RESALE_PRODUCT) y qué parte ya se gastó (SUPPLY + SERVICE, que
  además generaron su propio `Expense`). Van en el encabezado y no se calculan leyendo las
  líneas porque el reporte recorre un mes entero — mismo criterio que `totalAmount`.
- `ExpenseSummary.fromPurchases` / `.standalone` separan los gastos que nacieron de una compra
  de los capturados aparte. El flujo de caja resta `purchases.paidTotal + expenses.standalone`,
  así que el cloro no se resta dos veces.

`Purchase.amountSplit()` deduce el reparto del encabezado cuando los campos faltan: **sin
`lineKinds` es materia prima por definición** (era lo único que el módulo registraba), un solo
grupo de tipos va entero a su lado, y una compra MIXTA anterior al campo cae en
`unclassified` — que la pantalla muestra como "Sin desglosar" en vez de repartirlo a ojo.
Adivinar ahí sería mentir justo en el número que la sección existe para dar.
`functions/tools/backfill-purchases.js` los rellena desde las líneas y resuelve ese caso.

**`paidTotal` es lo abonado de las compras FECHADAS en el mes**, no el efectivo exacto que
salió: un abono de hoy a una factura de agosto cuenta en agosto. Se acepta a sabiendas —
lo contrario obliga a abrir la subcolección de abonos de cada compra— y la pantalla lo dice
en vez de dar a entender que es un flujo de caja exacto. Una compra `PENDING_RECEIPT` no
cuenta: no entró a la bodega ni generó gasto todavía.

Lógica pura en `domain/report/PurchaseAggregation.kt`, con tests en `PurchaseAggregationTest`.

- **Pedido vs. recibido** (`presentation/reports/branchsupply/`, permiso `VIEW_BRANCH_SUPPLY_REPORT`): por sucursal y producto, agrupado por masa — pedido → asignado por turno → empacado → recibido → vendido. Una sucursal a la vez (`BranchFilterRow(allowAll = false)`): sumar varias mezcla el faltante de una con el sobrante de otra. Permiso propio y no `VIEW_PRODUCTION_REPORT` porque ése es de la planta; la encargada de sucursal ve solo lo suyo.
  - **No hay colección nueva**: la nota de despacho es el puente, porque es lo único que sabe cuánto de un pedido le tocó a cada turno. `buildBranchSupplyReport` (`model/BranchSupplyReport.kt`, tests en `BranchSupplyReportTest`) es lógica pura; `loadBranchSupplyData` hace las lecturas (notas por `getNotesForOrder` + `getNote`, no la colección entera).
  - Enviado solo cuenta notas fuera de DRAFT; recibido y diferencia solo las ya contadas (DELIVERED*). Lo que salió y no se contó se muestra "sin contar" en vez de un 0 que se lea como faltante. Canceladas van aparte.
  - "Los turnos no suman lo pedido" se marca cuando el pedido ya tiene todas sus notas y no cuadran. Si falta la nota del segundo turno (su lote no arrancó; `expectedShiftsForOrder` = 2 para un pedido sin turno a planta de 2 turnos activos) la diferencia es "sin lote todavía", no un reparto roto.
  - **Una pestaña por planta productora** (`PlantSupply`, un `buildBranchSupplyReport` por planta): cada una despacha con sus propios turnos y juntas mezclaban columnas de turnos ajenos y alargaban la lista. KPIs y tabla siguen a la pestaña elegida; sin pestañas si la sucursal le pidió a una sola.
  - Vendido sale de `SalesSummary.unitsByProduct` (nuevo: `topProducts` se corta en 20). Los especiales no tienen ventas.
  - La misma tabla (`BranchSupplyTable`) sale como sección **"Seguimiento"** en el detalle del pedido (`ProductionViewModel.loadOrderSupply`), en cuanto existen notas.

Helpers compartidos en `presentation/reports/shared/ReportComponents.kt`: `ReportPeriodSelector`, `PeriodNavigator`, `BranchFilterRow`, `KpiTile`, `ReportSection`, `VerticalBars`, `PairedBars`, `HorizontalBars`, `EmptyDataNote`.

### HomeScreen renovado
- `HomeViewModel` combina sales/production/stock/dispatch/cash flows. KPIs vivos, gráfica de ventas reales de los últimos 7 días, banner de alertas filtrado por permisos del user.
- Sección "Reportes" — chips a Ventas / Producción / Financiero, visibles solo según `VIEW_*_REPORT`.
- Refresh manual vía `HomeViewModel.refresh(user)`; `ensureLoadedFor(user)` carga una vez al login.

### Banner de alertas (`presentation/home/AlertsBanner.kt`)
Banner rotatorio cada 6s entre alertas. Tap → navega a módulo correspondiente. Pager dots si hay >1. Color de borde por severity. Si `alerts.isEmpty()` no se renderiza.

**Modelos**: `domain/report/HomeAlert.kt` con `AlertSeverity` (INFO/WARNING/CRITICAL) y `AlertTarget` (enum que mapea a `Screen` desde HomeScreen — evita acoplar `domain/` con `navigation/`).

**Tipos de alerta** (cada uno gateado por permiso, computados in-memory en HomeViewModel):

| Alerta | Datos fuente | Permiso |
|---|---|---|
| Stock bajo | `BranchStock < ingredient.stockAlert` | `VIEW_INGREDIENTS` |
| Mantenimiento próximo | `FixedAsset.nextMaintenanceDate ≤ +7d` | `VIEW_ASSETS` |
| Despacho sin acuse | `DispatchNote.status==IN_TRANSIT && now − packedAt > 12h` | `MANAGE_DELIVERY_RUNS` o `DISPATCH_PACK` |
| Lote estancado | `ProductionLot.status==IN_PROGRESS && productionDate < hoy` | `MANAGE_PRODUCTION_LOTS` |
| Pedido tardío PENDING | `ProductionOrder.isLateOrder && status==PENDING` | `MANAGE_PRODUCTION_LOTS` |
| Caja abierta vencida | `CashSession.status==OPEN && now − openedAt > 14h` | `SPOT_CHECK_CASH` o `MANAGE_DAILY_CASH_CLOSE` |
| Precio sin definir | `DispatchNote.hasPriceMissing` en DRAFT/READY | `MANAGE_PRICE_LISTS` |

No hay colección `alerts/` en Firestore — son proyecciones derivadas. Evita sincronización y stale data.

---

## Costo por producto (oct 2026)

Paso 3 de `arquitectura/propuesta-rentabilidad-por-sucursal.md`. Home → "Costo por producto" (permiso `VIEW_FINANCIAL_REPORT`: el costo es dato financiero). Cálculo puro en `domain/MaterialCost.kt` (pruebas en `MaterialCostTest`), pantalla en `presentation/reports/productcost/`.

- **Todo en Q por gramo.** La receta es % del panadero (razón entre pesos) y el producto declara peso en crudo: el gramo es la única unidad común. Masa: `Σ(% × Q/g) ÷ Σ%`, la misma proporción de la receta escalada.
- **Precio del insumo** (`ingredientGramPrices`): promedio ponderado por cantidad de las líneas INGREDIENT de las compras de los últimos 90 días; si no hubo compra, la presentación predeterminada. Sin ninguno, o con unidad que no pasa a gramos ("Bote"), queda FUERA del mapa — no se inventa.
- **Producto** (`productMaterialCost`) = peso en crudo × Q/g de su receta (por `yields`) + `doughFillings` × Q/g de esa otra masa + `decorations` × Q/g (preparación: costo del lote ÷ peso; o insumo comprado). Solo productos propios activos; reventa y productos ligados a insumo quedan fuera (su costo es el de compra).
- **Huecos visibles, no escondidos**: sin receta / sin peso en crudo / receta vacía → sin costo (pestaña Pendientes). Insumo sin precio → cuenta Q0, el costo sale pero marcado "corto" y nombra el insumo (pestaña Insumos ordena por productos afectados).
- **Lecturas**: `getRecipesWithYields` no trae componentes → `getRecipe` solo de las recetas que producen algo o son masa de relleno; `getLines` solo de compras del periodo cuyo `itemIds` trae materia prima.
- Esponja/prefermento no se costea aparte: son los mismos insumos de la masa.
- Datos al 10 oct 2026: pan 54/60 con receta y 55 con peso; repostería y pastelería sin peso en crudo y con recetas "de relleno" (solo harina+agua) → carga de datos pendiente antes del paso 4.

## Costo estándar (oct 2026)

Paso 4 de `arquitectura/propuesta-rentabilidad-por-sucursal.md`. Home → "Costo estándar" (ver: `VIEW_FINANCIAL_REPORT`; congelar: `MANAGE_PRICE_LISTS`, también en `firestore.rules`). Cálculo puro en `domain/StandardCost.kt` (tests `StandardCostTest`), pantalla y VM en `presentation/reports/productcost/`.

- **Costo de un mes = datos del mes ANTERIOR, congelado.** El dueño elige el mes (actual o siguiente), revisa y congela. Todos los despachos del mes usan el mismo número; la diferencia contra lo real queda en la planta.
- **Gastos de planta** (`plantExpenses`): los `Expense` del mes × `plantShare` (sin tienda, con su parte de local compartido, sin Generales/Reparto). La materia prima no está aquí: entra a inventario, no a gastos.
- **Planilla** (`plantPayroll`): por colaborador cargado a la planta, su nómina registrada del mes (bruto) o, si no hay, su salario base, **más las prestaciones al mes** que el dueño fija en la ficha (`Employee.monthlyBenefits`: bonificación, aguinaldo y bono 14 repartidos, alimentación). **Sin IGSS patronal** (decisión del dueño). Ojo: si un mes se registra nómina con bonificaciones, las prestaciones se suman igual. La nómina casi no se registra; sin el salario base el pan saldría regalado.
- **Masa** (`plantDough`): líneas de lotes no cancelados del mes, producido (o planeado) × (peso en crudo + rellenos). Líneas sin peso se cuentan y se avisan: su masa falta y la tasa sale más alta. Menos de 50 kg en el mes = sin tasa (`MIN_DOUGH_GRAMS_FOR_RATE`).
- **Producto** = materia prima (`MaterialCostLoader`, el mismo cálculo de "Costo por producto") + tasa de su planta × gramos de masa. Su planta = la que más masa le sacó en el mes, o la única que lo tiene asignado. Sin materia prima, sin planta o con planta sin tasa → sin costo, y se despacha con la lista.
- **Persistencia**: `standardCostPeriods/{YYYY-MM}` (plantas como arreglos paralelos) y `standardCosts/{YYYY-MM}_{productId}` plano. Volver a congelar reescribe y borra los productos que salieron; el encabezado se escribe al final.
- **Despacho**: `resolveWholesalePrice` mira primero `standardCosts/{mes actual}_{producto}` (`PriceSource.STANDARD_COST`); si no hay, sigue la cascada de la lista "Sucursales". Las notas ya creadas guardaron su precio y no cambian.
- Pendiente: repostería sin peso en crudo → su planta no tiene tasa y sus productos van con lista; varianza real vs estándar es parte del reporte del paso 6.
