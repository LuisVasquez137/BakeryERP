package com.bakery.erp.domain

import com.bakery.erp.model.BankDeposit

/** Depósitos al banco desde las cajas. Ver [BankDeposit]. */
interface BankDepositRepository {
    /** Crea el depósito (PENDING) y devuelve el guardado con id. */
    suspend fun create(companyId: String, deposit: BankDeposit): BankDeposit

    suspend fun get(companyId: String, depositId: String): BankDeposit?

    /** Sin boleta todavía, de cualquier turno de la caja: bloquean el cierre. */
    suspend fun getPendingForRegister(companyId: String, registerId: String): List<BankDeposit>

    /** Los del turno (a medio turno y el del cierre), para el cuadre. */
    suspend fun getForSession(companyId: String, sessionId: String): List<BankDeposit>

    /** Ata el movimiento de caja que sacó el dinero. Escritura parcial. */
    suspend fun setMovement(companyId: String, depositId: String, movementId: String)

    /** No llegó a salir de la gaveta. Escritura parcial. */
    suspend fun cancel(companyId: String, depositId: String, reason: String)

    /** Boleta adjunta. Escritura parcial. */
    suspend fun attachSlip(
        companyId: String, depositId: String, photoUrl: String, slipNumber: String, slipAmount: Double,
        userName: String, employeeId: String
    )

    /** Se deja sin boleta con motivo. Escritura parcial. */
    suspend fun markNoSlip(companyId: String, depositId: String, reason: String, userName: String)
}

/**
 * Sube la foto de la boleta y devuelve el downloadUrl. Ruta
 * `companies/{c}/bankDeposits/{depositId}.jpg` (las reglas de Storage ya cubren `companies/`).
 * Implementación por plataforma, igual que [PurchaseInvoiceImageUploader].
 */
interface BankSlipImageUploader {
    suspend fun uploadSlipPhoto(bytes: ByteArray, companyId: String, depositId: String): String
}

/** Sin storage: no hay dónde guardar la boleta, y sin foto no se puede adjuntar. */
class NoOpBankSlipImageUploader : BankSlipImageUploader {
    override suspend fun uploadSlipPhoto(bytes: ByteArray, companyId: String, depositId: String): String = ""
}
