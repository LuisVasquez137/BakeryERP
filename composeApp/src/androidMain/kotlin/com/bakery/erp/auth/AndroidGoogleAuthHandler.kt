package com.bakery.erp.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.bakery.erp.data.CurrentActivityHolder
import com.bakery.erp.domain.GoogleAuthHandler
import com.bakery.erp.domain.GoogleSignInCancelledException
import com.bakery.erp.domain.GoogleSignInException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

class AndroidGoogleAuthHandler(private val context: Context) : GoogleAuthHandler {

    private val webClientId = "412638080390-10uq96sisqntdn3fc5u64rhsj79hi5mi.apps.googleusercontent.com"

    override suspend fun getIdToken(): String {
        val credentialManager = CredentialManager.create(context)

        // 1er intento: bottom sheet de One Tap con todas las cuentas del dispositivo.
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        return try {
            requestToken(credentialManager, googleIdOption)
        } catch (e: NoCredentialException) {
            // Sin credencial disponible: o no hay cuenta Google utilizable, o One Tap está en
            // cooldown por descartes previos. El flujo "Sign in with Google" fuerza el selector
            // completo y no está sujeto a ese cooldown.
            try {
                requestToken(
                    credentialManager,
                    GetSignInWithGoogleOption.Builder(webClientId).build()
                )
            } catch (cancel: GetCredentialCancellationException) {
                throw GoogleSignInCancelledException()
            } catch (fallback: GetCredentialException) {
                throw GoogleSignInException(
                    message = "No hay ninguna cuenta de Google disponible en este dispositivo. " +
                        "Agrega tu cuenta en Ajustes › Cuentas, actualiza Google Play Services " +
                        "y vuelve a intentarlo.",
                    diagnostic = "${fallback::class.java.simpleName}: ${fallback.message}",
                    cause = fallback
                )
            }
        }
    }

    private suspend fun requestToken(
        credentialManager: CredentialManager,
        option: androidx.credentials.CredentialOption
    ): String {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        // El selector de cuentas necesita una Activity: con el contexto de la aplicación
        // funciona en Android 14+ (lo abre el sistema) pero en equipos más viejos falla con
        // "Failed to launch the selector UI".
        val uiContext = CurrentActivityHolder.activity ?: context

        val result = try {
            credentialManager.getCredential(request = request, context = uiContext)
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInCancelledException()
        } catch (e: NoCredentialException) {
            throw e   // lo maneja getIdToken() con el fallback
        } catch (e: GetCredentialException) {
            throw GoogleSignInException(
                message = "No se pudo conectar con Google en este dispositivo. " +
                    "Verifica que Google Play Services esté actualizado y que tengas conexión.",
                diagnostic = "${e::class.java.simpleName}: ${e.message}",
                cause = e
            )
        } catch (e: Exception) {
            throw GoogleSignInException(
                message = "Error inesperado al conectar con Google.",
                diagnostic = "${e::class.java.simpleName}: ${e.message}",
                cause = e
            )
        }

        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw GoogleSignInException(
                message = "Google devolvió una credencial no compatible.",
                diagnostic = "credential type=${credential.type}"
            )
        }

        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw GoogleSignInException(
                message = "No se pudo leer la credencial de Google.",
                diagnostic = "GoogleIdTokenParsingException: ${e.message}",
                cause = e
            )
        }
    }
}
