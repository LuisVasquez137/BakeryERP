package com.bakery.erp.model

/**
 * Un NIT emisor de facturas electrónicas (FEL Guatemala). Vive en
 * `companies/{c}/felProfiles/{id}` y lo escribe **solo** la Cloud Function `felGuardarPerfil`:
 * junto con los datos SAT viajan las credenciales de Digifact, que la app nunca guarda ni lee.
 *
 * No todas las sucursales facturan con el mismo NIT, por eso la identidad fiscal es un perfil
 * y cada sucursal apunta al suyo ([Branch.felProfileId]) con su propio código de
 * establecimiento — mismo modelo que ComandaYa.
 */
data class FelProfile(
    val id: String = "",
    val nit: String = "",
    /** Razón social tal como sale en el RTU ("VÁSQUEZ,FLORES,,LUIS,FERNANDO"). */
    val nombreEmisor: String = "",
    val nombreComercial: String = "",
    val correo: String = "",
    val direccion: String = "",
    val municipio: String = "",
    val departamento: String = "",
    val codigoPostal: String = "01001",
    val afiliacionIVA: FelVatAffiliation = FelVatAffiliation.GEN,
    val regimenISR: String = "OPCIONAL",
    val active: Boolean = true,
    /** Ya hay credenciales de Digifact guardadas (la contraseña nunca llega a la app). */
    val hasCredentials: Boolean = false,
    val environment: FelEnvironment = FelEnvironment.TEST,
    val updatedAt: Long = 0L
)

/**
 * Afiliación al IVA. No es un dato decorativo: decide el **tipo de documento**. El pequeño
 * contribuyente emite FPEQ sin desglose de IVA y el régimen general FACT con IVA; emitir el
 * que no corresponde lo rechaza la SAT.
 */
enum class FelVatAffiliation(val label: String, val detail: String, val docType: String) {
    PEQ("Pequeño contribuyente", "Emite FPEQ (5% sobre ingresos, sin desglose de IVA)", "FPEQ"),
    GEN("Régimen general", "Emite FACT con IVA del 12%", "FACT");

    companion object {
        fun fromStored(value: String?): FelVatAffiliation =
            if (value.equals("PEQ", ignoreCase = true) || value.equals("PEC", ignoreCase = true)) PEQ else GEN
    }
}

enum class FelEnvironment(val label: String) {
    TEST("Pruebas"),
    PRODUCCION("Producción");

    companion object {
        fun fromStored(value: String?): FelEnvironment =
            if (value.equals("PRODUCCION", ignoreCase = true)) PRODUCCION else TEST
    }
}

/** Credenciales tecleadas en el formulario. Se mandan a la función y se descartan. */
data class FelCredentialsInput(
    val user: String,
    val password: String,
    val environment: FelEnvironment
)

/** Qué sucursal factura con qué perfil y con qué código de establecimiento. */
data class FelBranchAssignment(
    val branchId: String,
    val establishmentCode: String,
    val invoiceByDefault: Boolean
)

/** "5932521-6" / "59325216 " → "59325216". La K del dígito verificador va en mayúscula. */
fun cleanNit(nit: String): String = nit.filter { it.isDigit() || it == 'k' || it == 'K' }.uppercase()

/**
 * Dígito verificador del NIT de Guatemala (módulo 11). Atrapa el error de dedo antes de que
 * Digifact lo rechace — y, peor, antes de que se guarde un NIT ajeno que sí es válido.
 */
fun isValidGuatemalaNit(nit: String): Boolean {
    val clean = cleanNit(nit)
    if (clean.length < 2) return false
    val body = clean.dropLast(1)
    if (!body.all { it.isDigit() }) return false
    val sum = body.mapIndexed { i, c -> (c - '0') * (body.length + 1 - i) }.sum()
    val expected = (11 - sum % 11) % 11
    val check = if (expected == 10) 'K' else ('0' + expected)
    return clean.last() == check
}

/** "59325216" → "5932521-6", la forma en que se lee en el RTU. */
fun formatNit(nit: String): String {
    val clean = cleanNit(nit)
    return if (clean.length < 2) clean else "${clean.dropLast(1)}-${clean.last()}"
}

/**
 * Lo que impide guardar las sucursales de un NIT, en texto para quien configura. Vacío = bien.
 *
 * El código repetido es el error caro: dos sucursales con el mismo establecimiento emiten
 * facturas que ante la SAT salen de un solo lugar, y el reporte por establecimiento deja de
 * cuadrar sin que ninguna factura sea rechazada.
 */
fun felAssignmentProblems(
    assignments: List<FelBranchAssignment>,
    branchNameOf: (String) -> String
): List<String> {
    val problems = mutableListOf<String>()
    assignments.forEach { a ->
        val code = a.establishmentCode.trim()
        when {
            code.isEmpty() -> problems += "${branchNameOf(a.branchId)}: falta el código de establecimiento."
            !code.all { it.isDigit() } || (code.toIntOrNull() ?: 0) == 0 ->
                problems += "${branchNameOf(a.branchId)}: el código de establecimiento es un número (1, 2, 3…)."
        }
    }
    assignments
        .filter { it.establishmentCode.trim().toIntOrNull() != null }
        .groupBy { it.establishmentCode.trim().toInt() }
        .filterValues { it.size > 1 }
        .forEach { (code, dup) ->
            problems += "El establecimiento $code está en ${dup.joinToString(" y ") { branchNameOf(it.branchId) }}: " +
                "cada sucursal lleva el suyo."
        }
    return problems
}
