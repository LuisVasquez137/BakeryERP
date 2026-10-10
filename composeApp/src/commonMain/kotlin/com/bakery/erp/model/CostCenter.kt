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
 *
 * **Plantas con tienda.** Cada planta tiene su mostrador, y lo de uno no es de la otra: el
 * panadero es costo del pan (entra a la tasa por kilo) y la cajera es gasto de la tienda. En
 * esas sucursales ([splitsStore]) [BRANCH] significa **la planta** —lo que ya estaba anotado
 * ahí casi todo era producción, así que no se migra nada—, [STORE] es la tienda y [SITE] es
 * lo que comparten (renta, luz, agua del local), que se parte con
 * [Branch.storeSharePercent]. En una tienda sin planta los tres son lo mismo. Ver [plantShare].
 */
object CostCenter {
    const val BRANCH = ""
    const val GENERAL = "GENERAL"
    const val DELIVERY = "DELIVERY"
    /** La tienda de una sucursal que también produce. */
    const val STORE = "STORE"
    /** Lo que comparten la planta y su tienda: se parte con [Branch.storeSharePercent]. */
    const val SITE = "SITE"

    /** Los que no son de ninguna sucursal, en el orden en que se ofrecen. */
    val SHARED = listOf(GENERAL, DELIVERY)

    fun isShared(value: String): Boolean = value in SHARED

    /** Las de dentro de una sucursal que produce y vende, en el orden en que se ofrecen. */
    val SPLIT = listOf(BRANCH, STORE, SITE)

    fun label(value: String) = when (value) {
        GENERAL -> "Generales"
        DELIVERY -> "Reparto"
        STORE -> "Tienda"
        SITE -> "Local compartido"
        else -> "Sucursal"
    }

    /** Qué entra en cada uno, para decirlo junto a la opción. */
    fun hint(value: String) = when (value) {
        GENERAL -> "Contador, sueldo del dueño, vehículo, publicidad. No se le carga a ninguna sucursal."
        DELIVERY -> "Piloto y camión que lleva el pan a las tiendas. Se reparte entre las tiendas según las entregas."
        STORE -> "Cajeras, bolsas, rollo de la caja: lo del mostrador. No entra al costo del pan."
        SITE -> "Renta, luz y agua del local. Se parte entre la planta y su tienda con el porcentaje de la sucursal."
        else -> ""
    }

    /** Valor guardado → uno conocido. Lo que no se reconoce cae a la sucursal. */
    fun normalize(value: String?): String = value?.takeIf { it in SHARED || it == STORE || it == SITE } ?: BRANCH
}

/** Produce y vende: una planta con su tienda, cuyos gastos hay que partir. */
val Branch.splitsStore: Boolean
    get() = BranchCapability.CAN_PRODUCE in capabilities && BranchCapability.CAN_SELL in capabilities

/**
 * Qué fracción (0..1) de un monto anotado en [branch] con [costCenter] es de su **planta**; el
 * resto es de su tienda. Generales y Reparto no son de ninguna de las dos: 0.
 */
fun plantShare(costCenter: String, branch: Branch?): Double {
    if (CostCenter.isShared(costCenter) || branch == null) return 0.0
    if (BranchCapability.CAN_PRODUCE !in branch.capabilities) return 0.0
    if (BranchCapability.CAN_SELL !in branch.capabilities) return 1.0
    return when (costCenter) {
        CostCenter.STORE -> 0.0
        CostCenter.SITE -> 1.0 - branch.storeSharePercent.coerceIn(0.0, 100.0) / 100.0
        else -> 1.0
    }
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
