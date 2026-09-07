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
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
