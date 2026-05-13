package com.bakery.erp.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.bakery.erp.domain.GoogleAuthHandler
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

class AndroidGoogleAuthHandler(private val context: Context) : GoogleAuthHandler {

    private val webClientId = "412638080390-10uq96sisqntdn3fc5u64rhsj79hi5mi.apps.googleusercontent.com"

    override suspend fun getIdToken(): String? = try {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = CredentialManager.create(context).getCredential(
            request = request,
            context = context
        )

        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            null
        }
    } catch (e: GetCredentialCancellationException) {
        null
    } catch (e: GoogleIdTokenParsingException) {
        null
    } catch (e: Exception) {
        null
    }
}
