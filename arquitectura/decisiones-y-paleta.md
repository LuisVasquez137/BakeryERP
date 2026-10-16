# Paleta y decisiones de diseño

> Detalle movido desde CLAUDE.md. Leer antes de tocar este módulo.

## Paleta

`ui/theme/AppTheme.kt::BrandColors` es la **única** fuente de verdad. Ninguna pantalla vuelve a
declarar un `Color(0xFF...)` de marca: había 464 literales repartidos en 70 archivos y el mismo
estado salía de dos colores distintos en dos listas.

| Token | Hex | Para qué |
|---|---|---|
| `Primary` / `PrimaryDark` / `PrimaryContainer` | `#005B8D` / `#00486F` / `#DCEEFB` | Helvetia Blue — estructura, TopAppBar, FABs, identidad |
| `Accent` | `#FE7F03` | Naranja de marca, muestreado del croissant del logo. **Siempre relleno**, nunca texto |
| `OnAccent` | `#00344F` | Azul marino — la tinta que va **sobre** `Accent` (5.1:1) |
| `AccentDeep` | `#A85200` | Naranja oscuro: relleno con letra **blanca** (5.4:1), y el único naranja admisible como texto/icono sobre blanco (5.4:1) |
| `AccentSoft` | `#FFF3E0` | Relleno pálido de chip; lleva `Primary` encima (6.7:1) |
| `Ivory` / `Sand` | `#FDFBF7` / `#F5EFE6` | Fondo cálido y cards beige |
| `TextSlate` / `MutedSlate` / `Outline` | `#1A2B38` / `#5E6E7A` / `#C8A882` | Texto y bordes |

### Dos reglas que no se negocian

**1. El naranja claro nunca es texto.** `Accent` (`#FE7F03`) sobre blanco da **2.6:1**; el mínimo
legible es 4.5:1. El acento dorado anterior (`#D9A441`) se usaba justo así en decenas de sitios y
en una tablet enharinada bajo la luz de una panadería ese texto simplemente no está. Si el naranja
tiene que decir algo con letras, se rellena (`Accent` + `OnAccent`, o `AccentDeep` + blanco).
`AccentDeep` sí puede ser tinta sobre blanco — es el naranja oscurecido a propósito para eso.

**2. El naranja significa estado, no decoración.** Azul = estructura e identidad; naranja =
"esto necesita tus ojos / está en curso" (pendiente, por recibir, vencido, precio faltante, stock
bajo, mantenimiento próximo, sobreventa); verde = hecho; rojo = roto. Un FAB, un switch o un chip
de sucursal no son alertas y por eso no van en naranja — si todo fuera naranja, el naranja
dejaría de avisar.

### Chips: `ui/theme/BrandChip.kt`

`BrandChip(text, tone, emphasis)` es el chip de estado de toda la app. Antes había ~20
`StatusChip`/`Badge`/`Pill` privados, cada uno con su `Surface` y sus literales — la razón
estructural de que el color quedara disparejo. Se declara **qué significa** (`ChipTone`:
NEUTRAL / INFO / PROGRESS / ATTENTION / SUCCESS / DANGER / BRAND) y la paleta decide cómo se ve.

`chipColors(tone, emphasis)` es lógica pura y `BrandChipContrastTest` verifica que **ningún par
baje de 4.5:1**. No se relaja a 3:1 (el umbral de "texto grande") a propósito: los chips van en
`labelSmall` (11sp), que no califica por más negrita que lleve.

Ya migrados: `effectiveOrderDisplay` + `StatusChip` de pedidos (lista y detalle), despacho,
reparto, `PurchaseStatusPill` y el estado de pedidos de cliente. Los badges que quedan con
implementación propia (activos, empleados, productos, recetas, insumos, preparaciones) usan el
patrón "color al 15% de fondo + color pleno de texto", que es legible; migrarlos es limpieza
pendiente, no un bug.

### Encabezado degradado: `ui/BrandTopAppBar.kt` (oct 2026)
Todas las pantallas usan `BrandTopAppBar` (misma firma que `TopAppBar`). Si piden el azul de marca
se pinta degradado `PrimaryDark → Primary → HeaderBright` con un brillo `ChartSky` leve en la
esquina derecha; cualquier otro color se respeta. Los tonos son oscuros a propósito: el título y
los iconos son blancos y un celeste claro los dejaría sin contraste. Pantalla nueva = usar
`BrandTopAppBar`, nunca `TopAppBar` directo; el tono se ajusta solo en `BrandHeaderBrush`.

### Color por sección en el lanzador del Home

`ModuleSection` lleva `ink` + `container` y **cada `ModuleItem` los hereda** — el color vive en la
sección, no en el módulo (antes cada una de las 26 fichas repetía su hex y los cuatro valores eran
variantes de azul, así que la rejilla se leía como un bloque uniforme y había que leer cada
etiqueta para encontrar "Compras").

| Sección | Tinta / contenedor | Por qué |
|---|---|---|
| Operaciones | `AccentDeep` / `AccentSoft` | Es donde pasa el día |
| Inventario | `Primary` / `PrimaryContainer` | El azul de estructura |
| Ventas | `#0D7A8C` / `#D4EFF3` | El teal que la app ya usa en categorías del POS |
| Administración | `MutedSlate` / `Sand` | Se toca una vez al mes; que se apague es correcto |

**Cuatro tonos y no uno por módulo (estilo Odoo).** Odoo puede permitirse 15 hues porque sus
módulos se venden por separado y no tienen relación entre sí; acá recetas → producción → despacho
→ reparto son **una sola cadena**, y pintarlos de colores distintos afirmaría lo contrario. Por
debajo de cuatro hues sigue leyéndose como herramienta; de ocho en adelante es app de consumo.

**El color por sección vive SOLO en este lanzador.** Dentro de las pantallas el icono sigue
diciendo estado (naranja = necesita tus ojos). Si los iconos se colorearan decorativamente esa
señal muere — es el mismo error que el banner de CRITICAL en dorado.

HomeScreen: saludo dinámico, gráfica de ventas (mock), carrusel de accesos, grid de módulos por sección (Operaciones / Inventario / Ventas / Administración).

---

## Notas y Decisiones de Diseño (el "porqué")

### Masa Concha NO va dentro de la receta de Gusanito
La Masa Concha se prepara por separado (otro horario) y sirve para varios panes. La asociación "este pan lleva concha" pertenece al **producto terminado** (`Product.decorations`), no a la receta de masa.

### Yields no tienen cantidad ni unidad
La cantidad varía día a día según tamaño que decide el panadero. El yield solo dice *qué tipos* pueden salir de esa masa.

### `stageCount` / `yieldCount` en doc raíz de Recipe
Contadores denormalizados para mostrar badges en lista sin cargar subcolecciones. Actualizados al guardar.

### `components` a nivel de receta y no bajo stage
Firestore no recomienda >2 niveles de subcolección. `stageId` resuelve pertenencia.

### Ingredient sin `currentStock`
Stock es siempre por sucursal — global no tiene sentido en multi-sucursal. Antes de la primera compra, el insumo aparece "Sin stock registrado".

### Recepción del despacho en 2 pasos (ARRIVED → DELIVERED)
Flujo real: repartidor llega, encargada verifica TIPOS y firma acuse para liberar al repartidor; cuenta unidad por unidad después con calma. Combinarlos forzaría (a) repartidor esperando 20 min o (b) encargada confirmando a ciegas. Separar hace explícito que la integridad del transporte se transfiere en el acuse y el conteo correcto vive con la encargada después.

### `priceFactor` es ratio (0..1+), no descuento absoluto
Un factor escala automáticamente al subir precios al cliente final; un descuento fijo se desactualiza solo. Permite extender a "factor sobre costo + margen" cuando llegue el módulo de costos sin romper lo existente.

### Snapshot `priceSource` en línea del despacho
Auditoría: una nota de agosto debe seguir mostrando su precio histórico aunque el factor cambie en septiembre. Snapshot inmutable evita versionado complejo de listas.

### `cancelRun` bloquea cuando hay ARRIVED en adelante
ARRIVED ya significa que el repartidor entregó físicamente y firmaron acuse. Cancelar "borraría" un evento real. La hoja queda histórica; ajustes vía Kardex.

### Self-dispatch: el conteo del empaque ES la recepción
Planta que vende en mostrador no debe pasar por reparto (no hay vehículo). Forzar el flujo completo añade 3-4 taps.

**Al cerrar el empaque, la nota de la propia planta salta sola a DELIVERED** (`DispatchRepository.closeNoteAfterPacking`: `markReady` y, si `originBranchId == destinationBranchId`, `markAsLocalDispatch`). Antes quedaba en "Listas" esperando un reparto que nunca iba a existir — estancada ahí para siempre, y el pan que ya estaba físicamente en la tienda no entraba a su inventario. No hay transporte de por medio que pueda cambiar la cantidad, así que pedir un segundo conteo era pedir el mismo número dos veces.

La regla vive **solo** en `closeNoteAfterPacking`, que usan los dos caminos de cierre: el turno completo (`closePackingForBranchDateShift`, que cuenta las locales aparte en `PackingCloseResult.deliveredLocally` porque no aparecen en "Listas") y la nota suelta (`DispatchViewModel.confirmPacking`). El botón manual "Marcar como recibida en mostrador" sigue existiendo para las notas que ya quedaron en READY antes de esto.

No hay doble conteo con `absorbLeftoverIntoPlant`: ése ingresa lo **no empacado** (`requested − packed`) como AJUSTE y esto ingresa lo empacado como RECEPCION.

`getNotesForRunForm` **excluye las notas de la propia planta**: ofrecerlas como parada de una hoja de reparto las mandaba a un viaje que no existe y las dejaba IN_TRANSIT esperando un acuse que nadie firma, trabando además el auto-cierre de la hoja (`tryAutoCompleteRun` exige todas las paradas entregadas). Siguen visibles si ya están en ESA hoja, para poder quitarlas.

### Vehículos son Activos Fijos
Coherencia: el vehículo requiere mantenimiento, depreciación, garantía. Vincular permite atribuir combustible, kilometraje, viajes por vehículo. Evita duplicar registro.

### Faltantes en recepción NO regresan al stock de la planta
La planta no almacena terminado (produce y despacha el mismo día). Devolver implicaría inventario de terminado en planta. El faltante queda como pérdida en tránsito en Kardex — pérdida real, no retorno.

### Esponja decidida por el panadero (no la encargada)
La encargada solo consolida pedidos. El panadero decide por línea (un pan puede llevar esponja y otro no) en el momento de producción, no antes. Por eso la UI vive en el detalle del lote, no en el form del lote.

### Persistencia de esponja solo en botones del lote
Persistir en cada keystroke saturaría Firestore. `editableLotLines` en memoria + persistencia al "Iniciar producción" o "Completar". Trade-off: si el panadero navega fuera sin tocar esos botones, pierde cambios.

---
