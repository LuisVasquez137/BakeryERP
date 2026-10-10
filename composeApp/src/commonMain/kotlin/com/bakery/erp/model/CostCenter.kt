package com.bakery.erp.model

/**
 * A quién le corresponde un gasto, aparte de qué caja lo pagó.
 *
 * El dinero de todas las sucursales cae en un saco común y las facturas se pagan de la caja
 * general, así que "de dónde salió" ([ExpensePaidFrom]) no dice nada de quién gana y quién
 * pierde. Para el resultado por sucursal cada gasto necesita un dueño: la renta de Pacajá se
 * paga con dinero de otra tienda y aun así es de Pacajá.
 *
 * Casi todo es de una sucursal, y ese caso no se guarda como valor propio: [BRANCH] (vacío)
 * significa "la sucursal del documento", que es lo que ya decía `branchId` antes de este
 * campo. Así los documentos viejos se leen bien sin migrar nada. Los otros dos son los gastos
 * que no son de ninguna sucursal:
 *
 * - [GENERAL]: contador, sueldo del dueño, vehículo, publicidad. **No se reparten** entre
 *   sucursales: repartirlos crea perdedores falsos (una tienda chica "pierde" por la parte del
 *   contador que le tocó, y cerrarla no quita al contador). El reporte los muestra aparte y
 *   cada tienda muestra cuánto aporta para cubrirlos.
 * - [DELIVERY]: sueldo del piloto y gastos del camión, que lleva pan y repostería a las
 *   tiendas. Decisión del dueño: se reparte entre las tiendas que reciben, por entregas.
 *
 * Con [GENERAL] o [DELIVERY], `branchId` del documento sigue siendo la sucursal donde se anotó
 * (inventario, caja y permisos siguen colgando de ahí); lo único que cambia es a qué renglón
 * del resultado va el monto. Ver [costCenterReportKey].
 */
object CostCenter {
    const val BRANCH = ""
    const val GENERAL = "GENERAL"
    const val DELIVERY = "DELIVERY"

    /** Los que no son de ninguna sucursal, en el orden en que se ofrecen. */
    val SHARED = listOf(GENERAL, DELIVERY)

    fun isShared(value: String): Boolean = value in SHARED

    fun label(value: String) = when (value) {
        GENERAL -> "Generales"
        DELIVERY -> "Reparto"
        else -> "Sucursal"
    }

    /** Qué entra en cada uno, para decirlo junto a la opción. */
    fun hint(value: String) = when (value) {
        GENERAL -> "Contador, sueldo del dueño, vehículo, publicidad. No se le carga a ninguna sucursal."
        DELIVERY -> "Piloto y camión que lleva el pan a las tiendas. Se reparte entre las tiendas según las entregas."
        else -> ""
    }

    /** Valor guardado → uno conocido. Lo que no se reconoce cae a la sucursal. */
    fun normalize(value: String?): String = value?.takeIf { it in SHARED } ?: BRANCH
}

/**
 * Clave con la que un gasto se agrupa en los reportes por sucursal: la sucursal, o un renglón
 * propio para los compartidos. El prefijo evita chocar con un id de sucursal real.
 */
fun costCenterReportKey(costCenter: String, branchId: String): String =
    if (CostCenter.isShared(costCenter)) "cc:$costCenter" else branchId

/** Nombre del renglón para [costCenterReportKey]. */
fun costCenterReportName(costCenter: String, branchName: String): String =
    if (CostCenter.isShared(costCenter)) CostCenter.label(costCenter) else branchName
