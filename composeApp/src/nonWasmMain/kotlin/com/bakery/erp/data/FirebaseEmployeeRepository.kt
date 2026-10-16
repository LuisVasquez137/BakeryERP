package com.bakery.erp.data

import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.EmployeeRepository.PrivatePart
import com.bakery.erp.model.CostCenter
import com.bakery.erp.model.Employee
import com.bakery.erp.model.EmployeePinStatus
import com.bakery.erp.model.EmployeeStatus
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FirebaseEmployeeRepository(
    private val firestore: FirebaseFirestore
) : EmployeeRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/employees")

    private fun privateDoc(companyId: String, employeeId: String, part: PrivatePart) =
        col(companyId).document(employeeId).collection("private").document(
            when (part) {
                PrivatePart.PROFILE -> "profile"
                PrivatePart.PAY -> "pay"
            }
        )

    override suspend fun getEmployees(companyId: String, privateParts: Set<PrivatePart>): List<Employee> {
        val visible = col(companyId).get().documents
            .map { it.toPublicRead() }
            .filter { it.employee.status != EmployeeStatus.FIRED && it.employee.status != EmployeeStatus.RESIGNED }
            .sortedBy { it.employee.name }
        if (privateParts.isEmpty()) return visible.map { it.employee }
        return coroutineScope {
            visible.map { async { withPrivate(companyId, it, privateParts) } }.awaitAll()
        }
    }

    override suspend fun getEmployee(
        companyId: String, employeeId: String, privateParts: Set<PrivatePart>
    ): Employee? {
        val read = runCatching { col(companyId).document(employeeId).get().toPublicRead() }.getOrNull()
            ?: return null
        return if (privateParts.isEmpty()) read.employee else withPrivate(companyId, read, privateParts)
    }

    /**
     * Encima de la ficha pública pone lo de las partes privadas. Mientras dure la fase 1 la
     * ficha pública todavía trae esos campos (las versiones viejas de la app los leen y los
     * escriben ahí); se usa la parte privada si es al menos igual de reciente, o si la pública
     * ya no los trae. Así, lo que una app vieja corrija no queda tapado por una copia vieja.
     * Una parte que no se puede leer (sin permiso, o sin crear) deja lo de la pública.
     */
    private suspend fun withPrivate(companyId: String, read: PublicRead, parts: Set<PrivatePart>): Employee =
        coroutineScope {
            // null = no se pudo leer (sin permiso, sin red): esa parte NO cuenta como cargada.
            val pending = parts.associateWith { part ->
                async { runCatching { privateDoc(companyId, read.employee.id, part).get() }.getOrNull() }
            }
            val results = pending.mapValues { it.value.await() }
            val snaps = results.mapValues { (_, snap) -> snap?.takeIf { it.exists } }
            var e = read.employee.copy(
                privatePartsLoaded = results.filterValues { it != null }.keys.map { it.name }.toSet()
            )
            snaps[PrivatePart.PROFILE]?.let { snap ->
                if (!read.hasLegacyProfile || snap.readMillis("updatedAt") >= read.updatedAt) e = e.withProfileFrom(snap)
            }
            snaps[PrivatePart.PAY]?.let { snap ->
                if (!read.hasLegacyPay || snap.readMillis("updatedAt") >= read.updatedAt) e = e.withPayFrom(snap)
            }
            e
        }

    override suspend fun saveEmployee(
        companyId: String, employee: Employee, privateParts: Set<PrivatePart>
    ): String {
        val docRef = if (employee.id.isEmpty()) col(companyId).document
                     else col(companyId).document(employee.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = employee.copy(
            id = docRef.id,
            active = employee.status == EmployeeStatus.ACTIVE,
            updatedAt = now,
            createdAt = if (employee.createdAt == 0L) now else employee.createdAt
        )
        // Ficha existente: update, no set. El mapa no lleva los campos de acceso
        // (permisos, estado del PIN) y un set() los borraría; además las reglas
        // rechazan que el formulario los toque. Tampoco lleva lo privado.
        if (employee.id.isEmpty()) docRef.set(final.toPublicMap()) else docRef.update(final.toPublicMap())
        // Cada parte privada es entera de este formulario: set() con su propio updatedAt.
        privateParts.forEach { part ->
            privateDoc(companyId, docRef.id, part).set(final.privateMap(part) + ("updatedAt" to now.toDouble()))
        }
        // FASE 1 (transición): copia de lo privado en la ficha pública, para que las versiones
        // viejas de la app lo sigan viendo igual. En la fase 2 las reglas dejan de aceptar estos
        // campos ahí (esta escritura falla sin consecuencias) y se quita este bloque. Ver
        // arquitectura/seguridad-accesos.md, "Ficha privada".
        if (privateParts.isNotEmpty()) {
            runCatching {
                docRef.update(privateParts.fold(emptyMap<String, Any?>()) { acc, part -> acc + final.privateMap(part) })
            }
        }
        return docRef.id
    }

    override suspend fun updateEmployeePermissions(companyId: String, employeeId: String, permissions: List<String>) {
        col(companyId).document(employeeId).update(
            firestoreSafeOf(
                "permissions" to ArrayList(permissions.distinct()),
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun markFirstEntryOfDay(companyId: String, employeeId: String, day: String): Boolean {
        val ref = col(companyId).document(employeeId)
        val last = runCatching { ref.get().get<String?>("lastWelcomeDay") }.getOrNull()
        if (last == day) return false
        ref.update(firestoreSafeOf("lastWelcomeDay" to day))
        return true
    }

    override suspend fun updateEmployeeContact(
        companyId: String,
        employeeId: String,
        name: String,
        phone: String,
        phoneAux: String,
        email: String
    ) {
        col(companyId).document(employeeId).update(
            firestoreSafeOf(
                "name" to name,
                "phone" to phone,
                "phoneAux" to phoneAux,
                "email" to email,
                "updatedAt" to Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    override suspend fun deleteEmployee(companyId: String, employeeId: String) {
        col(companyId).document(employeeId).update(mapOf(
            "active" to false,
            "status" to EmployeeStatus.FIRED.name
        ))
    }

    private fun Employee.privateMap(part: PrivatePart): Map<String, Any?> = when (part) {
        PrivatePart.PROFILE -> firestoreSafeOf(
            "dpi" to dpi,
            "nit" to nit,
            "maritalStatus" to maritalStatus,
            "address" to address,
            "emergencyContact" to emergencyContact,
            "emergencyPhone" to emergencyPhone,
            "notes" to notes
        )
        PrivatePart.PAY -> firestoreSafeOf(
            "paymentType" to paymentType,
            "bank" to bank,
            "bankAccount" to bankAccount,
            "baseSalary" to baseSalary,
            "monthlyBenefits" to monthlyBenefits
        )
    }

    private fun Employee.toPublicMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "name" to name,
        "employeeCode" to employeeCode,
        "photoUrl" to photoUrl,
        "birthDate" to birthDate,
        "gender" to gender,
        "phone" to phone,
        "phoneAux" to phoneAux,
        "email" to email,
        "branchId" to branchId,
        "branchName" to branchName,
        "costCenter" to costCenter,
        "department" to department,
        "positionId" to positionId,
        "positionTitle" to positionTitle,
        "supervisorId" to supervisorId,
        "supervisorName" to supervisorName,
        "contractType" to contractType,
        "shiftId" to shiftId,
        "shiftName" to shiftName,
        "workDays" to workDays.joinToString(","),
        "startTime" to startTime,
        "endTime" to endTime,
        "splitShift" to splitShift,
        "secondStartTime" to secondStartTime,
        "secondEndTime" to secondEndTime,
        "status" to status.name,
        "hireDate" to hireDate,
        "endDate" to endDate,
        "active" to active,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "userId" to userId
    )

    /** La ficha pública y lo que hace falta para decidir contra las partes privadas. */
    private class PublicRead(
        val employee: Employee,
        val updatedAt: Long,
        val hasLegacyProfile: Boolean,
        val hasLegacyPay: Boolean
    )

    private fun DocumentSnapshot.toPublicRead(): PublicRead {
        val e = toEmployee()
        fun str(field: String) = runCatching { get<String?>(field) }.getOrNull()
        return PublicRead(
            employee = e,
            updatedAt = e.updatedAt,
            hasLegacyProfile = listOf("dpi", "nit", "address", "emergencyContact", "emergencyPhone", "maritalStatus", "notes")
                .any { str(it) != null },
            hasLegacyPay = str("bank") != null || str("bankAccount") != null || str("paymentType") != null ||
                runCatching { get<Double?>("baseSalary") }.getOrNull() != null
        )
    }

    private fun Employee.withProfileFrom(snap: DocumentSnapshot): Employee {
        fun str(field: String) = runCatching { snap.get<String?>(field) }.getOrNull() ?: ""
        return copy(
            dpi = str("dpi"),
            nit = str("nit"),
            maritalStatus = str("maritalStatus"),
            address = str("address"),
            emergencyContact = str("emergencyContact"),
            emergencyPhone = str("emergencyPhone"),
            notes = str("notes")
        )
    }

    private fun Employee.withPayFrom(snap: DocumentSnapshot): Employee {
        fun str(field: String) = runCatching { snap.get<String?>(field) }.getOrNull() ?: ""
        fun num(field: String) = runCatching { snap.get<Double?>(field) }.getOrNull()
            ?: runCatching { snap.get<Long?>(field)?.toDouble() }.getOrNull() ?: 0.0
        return copy(
            paymentType = str("paymentType"),
            bank = str("bank"),
            bankAccount = str("bankAccount"),
            baseSalary = num("baseSalary"),
            monthlyBenefits = num("monthlyBenefits")
        )
    }

    /** Lee la ficha pública. Los campos privados salen de ahí solo mientras dure la fase 1. */
    private fun DocumentSnapshot.toEmployee(): Employee {
        val statusStr = runCatching { get<String?>("status") }.getOrNull()
        val status = statusStr?.let { runCatching { EmployeeStatus.valueOf(it) }.getOrNull() }
            ?: if (runCatching { get<Boolean?>("active") }.getOrNull() != false) EmployeeStatus.ACTIVE
               else EmployeeStatus.FIRED
        val workDaysStr = runCatching { get<String?>("workDays") }.getOrNull() ?: ""
        val workDays = if (workDaysStr.isBlank()) emptyList()
                       else workDaysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        return Employee(
            id = id,
            name = get<String?>("name") ?: "",
            employeeCode = get<String?>("employeeCode") ?: "",
            photoUrl = get<String?>("photoUrl") ?: "",
            dpi = get<String?>("dpi") ?: "",
            nit = get<String?>("nit") ?: "",
            birthDate = readMillis("birthDate"),
            gender = get<String?>("gender") ?: "",
            maritalStatus = get<String?>("maritalStatus") ?: "",
            phone = get<String?>("phone") ?: "",
            phoneAux = get<String?>("phoneAux") ?: "",
            email = get<String?>("email") ?: "",
            address = get<String?>("address") ?: "",
            emergencyContact = get<String?>("emergencyContact") ?: "",
            emergencyPhone = get<String?>("emergencyPhone") ?: "",
            branchId = get<String?>("branchId") ?: "",
            branchName = get<String?>("branchName") ?: "",
            costCenter = CostCenter.normalize(runCatching { get<String?>("costCenter") }.getOrNull()),
            department = get<String?>("department") ?: "",
            positionId = get<String?>("positionId") ?: "",
            positionTitle = get<String?>("positionTitle") ?: (get<String?>("position") ?: ""),
            supervisorId = get<String?>("supervisorId") ?: "",
            supervisorName = get<String?>("supervisorName") ?: "",
            contractType = get<String?>("contractType") ?: "",
            shiftId = get<String?>("shiftId") ?: "",
            shiftName = get<String?>("shiftName") ?: "",
            workDays = workDays,
            startTime = get<String?>("startTime") ?: "",
            endTime = get<String?>("endTime") ?: "",
            splitShift = runCatching { get<Boolean?>("splitShift") }.getOrNull() ?: false,
            secondStartTime = runCatching { get<String?>("secondStartTime") }.getOrNull() ?: "",
            secondEndTime = runCatching { get<String?>("secondEndTime") }.getOrNull() ?: "",
            paymentType = get<String?>("paymentType") ?: "",
            bank = get<String?>("bank") ?: "",
            bankAccount = get<String?>("bankAccount") ?: "",
            baseSalary = runCatching { get<Double?>("baseSalary") }.getOrNull() ?: 0.0,
            monthlyBenefits = runCatching { get<Double?>("monthlyBenefits") }.getOrNull()
                ?: runCatching { get<Long?>("monthlyBenefits")?.toDouble() }.getOrNull() ?: 0.0,
            status = status,
            hireDate = readMillis("hireDate"),
            endDate = readMillis("endDate"),
            notes = get<String?>("notes") ?: "",
            active = status == EmployeeStatus.ACTIVE,
            createdAt = readMillis("createdAt"),
            updatedAt = readMillis("updatedAt"),
            userId = get<String?>("userId") ?: "",
            permissions = runCatching { get<List<String>?>("permissions") }.getOrNull() ?: emptyList(),
            pinStatus = EmployeePinStatus.fromStored(runCatching { get<String?>("pinStatus") }.getOrNull()),
            pinUpdatedAt = readMillis("pinUpdatedAt")
        )
    }

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
