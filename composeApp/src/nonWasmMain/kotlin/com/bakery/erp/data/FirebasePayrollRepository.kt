package com.bakery.erp.data

import com.bakery.erp.domain.PayrollRepository
import com.bakery.erp.domain.report.PayrollSummary
import com.bakery.erp.model.PayrollRecord
import com.bakery.erp.model.PayrollStatus
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FirebasePayrollRepository(
    private val firestore: FirebaseFirestore
) : PayrollRepository {

    private fun col(companyId: String, employeeId: String) =
        firestore.collection("companies/$companyId/employees/$employeeId/payroll")

    override suspend fun getPayrollHistory(companyId: String, employeeId: String): List<PayrollRecord> =
        col(companyId, employeeId).get().documents
            .map { it.toRecord() }
            .sortedByDescending { it.periodStart }

    override suspend fun getPayrollRecord(
        companyId: String, employeeId: String, payrollId: String
    ): PayrollRecord? =
        runCatching { col(companyId, employeeId).document(payrollId).get().toRecord() }.getOrNull()

    override suspend fun savePayroll(
        companyId: String, employeeId: String, record: PayrollRecord
    ): PayrollRecord {
        val docRef = if (record.id.isEmpty()) col(companyId, employeeId).document
                     else col(companyId, employeeId).document(record.id)
        val now = Clock.System.now().toEpochMilliseconds()
        val final = record.copy(
            id = docRef.id,
            employeeId = employeeId,
            updatedAt = now,
            createdAt = if (record.createdAt == 0L) now else record.createdAt
        )
        docRef.set(final.toMap())
        return final
    }

    override suspend fun deletePayroll(
        companyId: String, employeeId: String, payrollId: String
    ) {
        col(companyId, employeeId).document(payrollId).delete()
    }

    override suspend fun getPayrollForPeriod(
        companyId: String, fromMillis: Long, untilMillis: Long
    ): List<PayrollRecord> = coroutineScope {
        // Sin collectionGroup (evita requerir índice nuevo): iteramos empleados y
        // cargamos su subcolección en paralelo. OK para panaderías de ~20 empleados.
        val employees = firestore.collection("companies/$companyId/employees").get().documents
        employees.map { emp ->
            async { col(companyId, emp.id).get().documents.map { it.toRecord() } }
        }.awaitAll()
            .flatten()
            .filter {
                it.paymentDate in fromMillis until untilMillis &&
                    (it.status == PayrollStatus.APPROVED || it.status == PayrollStatus.PAID)
            }
    }

    override suspend fun aggregatePayrollForPeriod(
        companyId: String, fromMillis: Long, untilMillis: Long
    ): PayrollSummary {
        val records = getPayrollForPeriod(companyId, fromMillis, untilMillis)
        if (records.isEmpty()) return PayrollSummary()
        return PayrollSummary(
            recordsCount = records.size,
            totalGross = records.sumOf { it.totalGross },
            totalNet = records.sumOf { it.totalNet },
            totalEmployerCost = records.sumOf { it.totalGross + it.igssEmployer }
        )
    }

    private fun PayrollRecord.toMap(): Map<String, Any?> = firestoreSafeOf(
        "id" to id,
        "employeeId" to employeeId,
        "employeeName" to employeeName,
        "periodStart" to periodStart,
        "periodEnd" to periodEnd,
        "baseSalary" to baseSalary,
        "overtimePay" to overtimePay,
        "tips" to tips,
        "advances" to advances,
        "bonuses" to bonuses,
        "igssEmployee" to igssEmployee,
        "igssEmployer" to igssEmployer,
        "isr" to isr,
        "otherDeductions" to otherDeductions,
        "vacationDays" to vacationDays,
        "vacationPay" to vacationPay,
        "severancePay" to severancePay,
        "totalGross" to totalGross,
        "totalNet" to totalNet,
        "paymentDate" to paymentDate,
        "paymentMethod" to paymentMethod,
        "status" to status.name,
        "notes" to notes,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toRecord(): PayrollRecord {
        val statusStr = runCatching { get<String?>("status") }.getOrNull()
        val status = statusStr?.let { runCatching { PayrollStatus.valueOf(it) }.getOrNull() }
            ?: PayrollStatus.DRAFT
        fun d(field: String) = runCatching { get<Double?>(field) }.getOrNull() ?: 0.0
        return PayrollRecord(
            id = id,
            employeeId = get<String?>("employeeId") ?: "",
            employeeName = get<String?>("employeeName") ?: "",
            periodStart = readMillis("periodStart"),
            periodEnd = readMillis("periodEnd"),
            baseSalary = d("baseSalary"),
            overtimePay = d("overtimePay"),
            tips = d("tips"),
            advances = d("advances"),
            bonuses = d("bonuses"),
            igssEmployee = d("igssEmployee"),
            igssEmployer = d("igssEmployer"),
            isr = d("isr"),
            otherDeductions = d("otherDeductions"),
            vacationDays = d("vacationDays"),
            vacationPay = d("vacationPay"),
            severancePay = d("severancePay"),
            totalGross = d("totalGross"),
            totalNet = d("totalNet"),
            paymentDate = readMillis("paymentDate"),
            paymentMethod = get<String?>("paymentMethod") ?: "",
            status = status,
            notes = get<String?>("notes") ?: "",
            createdAt = readMillis("createdAt"),
            updatedAt = readMillis("updatedAt")
        )
    }

    private fun DocumentSnapshot.readMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
