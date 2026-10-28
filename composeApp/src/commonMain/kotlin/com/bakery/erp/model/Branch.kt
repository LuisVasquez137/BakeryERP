package com.bakery.erp.model

data class Branch(
    val id: String = "",
    val orgId: String = "",
    val name: String = "",
    val location: String = "",
    val phone: String = "",
    val managerName: String = "",
    val notes: String? = null,
    val capabilities: List<BranchCapability> = emptyList(),
    val assignedProductIds: List<String> = emptyList(),
    /** Lista de precios usada cuando esta sucursal recibe un despacho. Vacío = usar
     *  la lista default WHOLESALE del sistema. Usualmente solo se setea en sucursales
     *  con [BranchCapability.CAN_SELL] que reciben producto de plantas productoras. */
    val defaultPriceListId: String = "",
    /**
     * Orden en que esta planta quiere ver los productos en el consolidado de
     * pedidos (`productId` en el orden deseado). Los productos que no estén en
     * la lista se muestran después, alfabéticamente. Vacío = todo alfabético.
     *
     * Vive en la sucursal (y no en el dispositivo) porque el orden refleja la
     * secuencia real de horneado de esa planta: todos los que la consultan
     * deben ver la misma lista.
     */
    val productSortOrder: List<String> = emptyList(),
    /**
     * Orden en que el panadero de esta planta quiere ver la orden de producción
     * (la secuencia real del horno). **Independiente de [productSortOrder]**:
     * revisar pedidos entrantes y hornear no tienen por qué llevar el mismo
     * orden, y forzar uno solo obligaría a que uno de los dos roles ceda.
     */
    val productionSortOrder: List<String> = emptyList(),
    /**
     * Orden en que esta planta quiere ver las **sucursales destino** al empacar
     * (`branchId` en el orden deseado). Las que no estén en la lista van después,
     * alfabéticamente. Vacío = todo alfabético, como siempre.
     *
     * Es de la planta y no del dispositivo por la misma razón que
     * [productSortOrder]: el orden que quiere quien cuenta el pan es el orden
     * físico en que arma las canastas o en que carga el camión, y eso es de la
     * planta. Guardarlo por dispositivo lo perdería al cambiar de tablet y
     * dejaría a dos personas empacando en órdenes distintos.
     *
     * Separado de [productSortOrder] y [productionSortOrder] porque ordena otra
     * cosa (sucursales, no productos) y lo decide otro rol.
     */
    val dispatchBranchOrder: List<String> = emptyList(),
    /**
     * Ruta fija del camión que sale de esta planta: `branchId` de las sucursales en el
     * orden en que se entregan. Decide el orden de las paradas al despachar un viaje y,
     * con el promedio de cada tramo, cuánto le falta al repartidor a cada sucursal.
     *
     * Separada de [dispatchBranchOrder] a propósito: el camión se carga al revés de como
     * se entrega (lo primero que se baja va atrás, a la mano), así que el orden en que se
     * arman las canastas y el de la ruta no tienen por qué coincidir.
     */
    val deliveryRouteOrder: List<String> = emptyList(),
    /**
     * Días de la semana (ISO 1=lunes…7=domingo) en que esta planta NO recibe
     * pedidos — su día de descanso. Vacío = trabaja todos los días. Solo
     * aplica cuando esta sucursal actúa como planta productora: bloquea la
     * creación de `ProductionOrder` hacia ella en esos días (ver
     * `ProductionOrderFormScreen`), para que la sucursal solicitante no pida
     * "para mañana" un sábado sin darse cuenta de que cae domingo.
     */
    val closedWeekdays: List<Int> = emptyList(),
    /**
     * Viaje único en que esta sucursal recibe TODO su pedido cuando la planta
     * reparte el día en dos turnos: `1` = solo el primer viaje, `2` = solo el
     * segundo, `0` (default) = recibe en los dos y su pedido se reparte.
     *
     * Existe porque hay sucursales que abren tarde: no tienen a quién recibirle
     * el pan del primer viaje, así que repartirles mitad y mitad deja pan en la
     * puerta. Es propiedad de la SUCURSAL y no del pedido — el horario de
     * apertura no cambia de un día para otro, y obligar a declararlo en cada
     * pedido sería pedirle a la encargada que se acuerde todos los días.
     *
     * Se guarda como número de viaje (y no como `shiftId`) para que coincida con
     * [ProductionShift.dispatchOrder]: la sucursal razona "solo el segundo
     * viaje", no "solo el turno de día", y así la config sobrevive a que la
     * planta renombre o recree sus turnos.
     *
     * Manda sobre el reparto automático y sobre el encargo declarado, pero NO
     * sobre [ProductionShift.excludedProductIds]: si el pan no se hornea en ese
     * viaje, no hay forma de mandárselo — el reparto avisa en vez de inventar.
     */
    val receivesOnlyInTrip: Int = 0,
    /** Coordenadas capturadas automáticamente (GPS) la primera vez que alguien
     *  crea un pedido para esta sucursal — ver ProductionOrderFormScreen /
     *  LocationRequester. Null = aún no capturadas. No se piden por formulario:
     *  en un SaaS nadie las llena a mano de forma confiable. Alimentan el
     *  ajuste de cantidades sugeridas por clima. Se pueden corregir a mano
     *  desde BranchFormScreen si la captura automática quedó mal. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /**
     * NIT con el que factura esta sucursal ([FelProfile.id]). Vacío = no factura. Lo escribe
     * solo la pantalla de Facturación electrónica (escritura parcial), pero va también en
     * `toMap()` porque `updateBranch` hace `set()`: sin eso, guardar la ficha de la sucursal
     * le borraría el NIT.
     */
    val felProfileId: String = "",
    /** Código de establecimiento que la SAT le dio a esta sucursal dentro de su NIT. */
    val felEstablishmentCode: String = "",
    /** El cobro sale con "Facturar" ya marcado. Facturar sigue siendo opcional por venta. */
    val invoiceByDefault: Boolean = false,
    /**
     * Qué parte del local (0-100) usa la tienda, en una sucursal que produce y vende. Parte los
     * gastos anotados como [CostCenter.SITE] (renta, luz, agua): sin esto la tienda de la
     * planta saldría sin renta y se vería mejor que las tiendas que sí pagan local.
     */
    val storeSharePercent: Double = 0.0,
    /**
     * Cuánto se puede regalar al día desde las cajas de esta sucursal, en quetzales a precio de
     * venta. Pasarse BLOQUEA el regalo. 0 = no se regala. Solo el dueño lo cambia. Ver `CounterRemoval`.
     */
    val dailyCourtesyQuota: Double = 0.0,
    /**
     * Panes de cortesía (del grupo Q0.50) que cada colaboradora puede tomar al día desde las
     * cajas de esta sucursal. Es por PERSONA: lo que ya tomó en otra sucursal también cuenta.
     * Pasarse BLOQUEA. 0 = no se da. Solo el dueño lo cambia. Ver `CounterRemoval`.
     */
    val dailyStaffBreadUnits: Double = 0.0,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
