package com.bakery.erp.data

import com.bakery.erp.domain.EmployeeAccessRepository
import com.bakery.erp.domain.VerifiedEmployee
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable

class FirebaseEmployeeAccessRepository : EmployeeAccessRepository {

    // Misma región que functions/access/employeeAccess.js.
    private val functions by lazy { Firebase.functions("us-central1") }

    override suspend fun setPin(companyId: String, employeeId: String, pin: String, typedByEmployee: Boolean) {
        functions.httpsCallable("empleadoFijarPin")
            .invoke(SetPinRequest(companyId, employeeId, pin, typedByEmployee))
    }

    override suspend fun changePin(companyId: String, employeeId: String, currentPin: String, newPin: String) {
        functions.httpsCallable("empleadoCambiarPin")
            .invoke(ChangePinRequest(companyId, employeeId, currentPin, newPin))
    }

    override suspend fun verifyPin(companyId: String, employeeId: String, pin: String): VerifiedEmployee {
        val r = functions.httpsCallable("empleadoVerificarPin")
            .invoke(VerifyPinRequest(companyId, employeeId, pin))
            .data<VerifyPinResponse>()
        return VerifiedEmployee(
            employeeId = r.employeeId,
            name = r.name,
            branchId = r.branchId,
            photoUrl = r.photoUrl,
            permissions = r.permissions,
            mustChangePin = r.debeCambiar
        )
    }

    @Serializable
    private data class SetPinRequest(
        val companyId: String,
        val employeeId: String,
        val pin: String,
        val escritoPorColaborador: Boolean
    )

    @Serializable
    private data class ChangePinRequest(
        val companyId: String,
        val employeeId: String,
        val pinActual: String,
        val pinNuevo: String
    )

    @Serializable
    private data class VerifyPinRequest(val companyId: String, val employeeId: String, val pin: String)

    @Serializable
    private data class VerifyPinResponse(
        val employeeId: String = "",
        val name: String = "",
        val branchId: String = "",
        val photoUrl: String = "",
        val permissions: List<String> = emptyList(),
        val debeCambiar: Boolean = false
    )
}
