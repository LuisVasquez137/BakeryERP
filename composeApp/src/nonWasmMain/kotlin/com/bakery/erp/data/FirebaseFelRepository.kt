package com.bakery.erp.data

import com.bakery.erp.domain.FelRepository
import com.bakery.erp.model.FelBranchAssignment
import com.bakery.erp.model.FelCertifyResult
import com.bakery.erp.model.FelInvoiceRequest
import com.bakery.erp.model.FelCredentialsInput
import com.bakery.erp.model.FelEnvironment
import com.bakery.erp.model.FelProfile
import com.bakery.erp.model.FelVatAffiliation
import com.bakery.erp.model.cleanNit
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable
import kotlin.time.Clock

class FirebaseFelRepository(
    private val firestore: FirebaseFirestore
) : FelRepository {

    // Misma región que functions/fel/felFunctions.js.
    private val functions by lazy { Firebase.functions("us-central1") }

    private fun profiles(companyId: String) =
        firestore.collection("companies/$companyId/felProfiles")

    override suspend fun getProfiles(companyId: String): List<FelProfile> =
        profiles(companyId).get().documents.map { it.toProfile() }

    override suspend fun saveProfile(
        companyId: String,
        profile: FelProfile,
        credentials: FelCredentialsInput?
    ): String {
        val request = SaveProfileRequest(
            companyId = companyId,
            profileId = profile.id.ifBlank { null },
            perfil = ProfilePayload(
                nit = cleanNit(profile.nit),
                nombreEmisor = profile.nombreEmisor.trim(),
                nombreComercial = profile.nombreComercial.trim(),
                correo = profile.correo.trim(),
                direccion = profile.direccion.trim(),
                municipio = profile.municipio.trim(),
                departamento = profile.departamento.trim(),
                codigoPostal = profile.codigoPostal.trim(),
                regimenISR = profile.regimenISR,
                afiliacionIVA = profile.afiliacionIVA.name,
                active = profile.active
            ),
            credenciales = credentials?.let {
                CredentialsPayload(usuario = it.user.trim(), password = it.password, ambiente = it.environment.name)
            }
        )
        val result = functions.httpsCallable("felGuardarPerfil").invoke(request)
        return result.data<SaveProfileResponse>().profileId
    }

    override suspend fun assignBranches(
        companyId: String,
        profileId: String,
        assignments: List<FelBranchAssignment>,
        releasedBranchIds: List<String>
    ) {
        val branches = firestore.collection("companies/$companyId/branches")
        val now = Clock.System.now().toEpochMilliseconds()
        assignments.forEach { a ->
            branches.document(a.branchId).update(
                firestoreSafeOf(
                    "felProfileId" to profileId,
                    "felCodigoEstablecimiento" to a.establishmentCode.trim(),
                    "invoiceByDefault" to a.invoiceByDefault,
                    "updatedAt" to now
                )
            )
        }
        releasedBranchIds.forEach { id ->
            branches.document(id).update(
                firestoreSafeOf(
                    "felProfileId" to "",
                    "felCodigoEstablecimiento" to "",
                    "invoiceByDefault" to false,
                    "updatedAt" to now
                )
            )
        }
    }

    override suspend fun certifySales(
        companyId: String,
        saleIds: List<String>,
        request: FelInvoiceRequest,
        reissue: Boolean
    ): FelCertifyResult {
        val result = functions.httpsCallable("felCertificarVenta").invoke(
            CertifyRequest(
                companyId = companyId,
                saleIds = saleIds,
                receptor = ReceptorPayload(id = request.receptorId, nombre = request.receptorName),
                reemitir = reissue
            )
        )
        val r = result.data<CertifyResponse>()
        return FelCertifyResult(
            dteId = r.dteId, status = r.estado, message = r.mensaje,
            serie = r.serie, numero = r.numero, uuid = r.uuid, docType = r.docType,
            certifiedAt = r.fechaCertificacion.toLong(), total = r.granTotal, vat = r.totalIVA,
            receptorId = r.receptorId, receptorName = r.receptorNombre,
            emisorNit = r.emisorNit, emisorName = r.emisorNombre,
            emisorTradeName = r.emisorNombreComercial, emisorAddress = r.emisorDireccion,
            phrases = r.frases, environment = r.ambiente
        )
    }

    override suspend fun voidInvoice(companyId: String, dteId: String, reason: String) {
        functions.httpsCallable("felAnularDte").invoke(VoidRequest(companyId, dteId, reason.trim()))
    }

    override suspend fun lookupNit(companyId: String, branchId: String, nit: String): String? {
        val result = functions.httpsCallable("felConsultarNit")
            .invoke(NitLookupRequest(companyId, branchId, cleanNit(nit)))
        val r = result.data<NitLookupResponse>()
        return r.nombre.takeIf { r.encontrado && it.isNotBlank() }
    }

    private fun DocumentSnapshot.toProfile() = FelProfile(
        id = id,
        nit = get<String?>("nit") ?: "",
        nombreEmisor = get<String?>("nombreEmisor") ?: "",
        nombreComercial = get<String?>("nombreComercial") ?: "",
        correo = get<String?>("correo") ?: "",
        direccion = get<String?>("direccion") ?: "",
        municipio = get<String?>("municipio") ?: "",
        departamento = get<String?>("departamento") ?: "",
        codigoPostal = get<String?>("codigoPostal") ?: "01001",
        afiliacionIVA = FelVatAffiliation.fromStored(get<String?>("afiliacionIVA")),
        regimenISR = get<String?>("regimenISR") ?: "OPCIONAL",
        active = runCatching { get<Boolean?>("active") }.getOrNull() ?: true,
        hasCredentials = runCatching { get<Boolean?>("tieneCredenciales") }.getOrNull() ?: false,
        environment = FelEnvironment.fromStored(get<String?>("ambienteApi")),
        // La función escribe Date.now(): entero en Android, Double en JS.
        updatedAt = runCatching { get<Long?>("updatedAt") }.getOrNull()
            ?: runCatching { get<Double?>("updatedAt")?.toLong() }.getOrNull() ?: 0L
    )
}

// Lo que recibe felGuardarPerfil. Los nombres son los del servidor, en español.

@Serializable
private data class SaveProfileRequest(
    val companyId: String,
    val profileId: String? = null,
    val perfil: ProfilePayload,
    val credenciales: CredentialsPayload? = null
)

@Serializable
private data class ProfilePayload(
    val nit: String,
    val nombreEmisor: String,
    val nombreComercial: String,
    val correo: String,
    val direccion: String,
    val municipio: String,
    val departamento: String,
    val codigoPostal: String,
    val regimenISR: String,
    val afiliacionIVA: String,
    val active: Boolean
)

@Serializable
private data class CredentialsPayload(
    val usuario: String,
    val password: String,
    val ambiente: String
)

@Serializable
private data class SaveProfileResponse(val profileId: String)

@Serializable
private data class CertifyRequest(
    val companyId: String,
    val saleIds: List<String>,
    val receptor: ReceptorPayload,
    val reemitir: Boolean = false
)

@Serializable
private data class VoidRequest(val companyId: String, val dteId: String, val motivo: String)

@Serializable
private data class ReceptorPayload(val id: String, val nombre: String)

// Todos con default: la respuesta de una factura PENDIENTE trae la mayoría vacíos, y un campo
// que falte no puede tumbar el cobro.
@Serializable
private data class CertifyResponse(
    val dteId: String = "",
    val estado: String = "",
    val mensaje: String = "",
    val serie: String = "",
    val numero: String = "",
    val uuid: String = "",
    val docType: String = "",
    val fechaCertificacion: Double = 0.0,
    val granTotal: Double = 0.0,
    val totalIVA: Double = 0.0,
    val receptorId: String = "CF",
    val receptorNombre: String = "CONSUMIDOR FINAL",
    val emisorNit: String = "",
    val emisorNombre: String = "",
    val emisorNombreComercial: String = "",
    val emisorDireccion: String = "",
    val frases: List<String> = emptyList(),
    val ambiente: String = ""
)

@Serializable
private data class NitLookupRequest(val companyId: String, val branchId: String, val nit: String)

@Serializable
private data class NitLookupResponse(
    val encontrado: Boolean = false,
    val nit: String = "",
    val nombre: String = ""
)
