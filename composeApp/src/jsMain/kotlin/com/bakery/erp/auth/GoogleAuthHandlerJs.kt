package com.bakery.erp.auth

import com.bakery.erp.domain.GoogleAuthHandler
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.auth.externals.GoogleAuthProvider
import dev.gitlive.firebase.auth.externals.signInWithPopup
import dev.gitlive.firebase.auth.js
import kotlinx.coroutines.await

class GoogleAuthHandlerJs : GoogleAuthHandler {
    override val isAvailable = true

    /** signInWithPopup ya autentica contra Firebase; el null de getIdToken() es el caso de éxito. */
    override val completesSignInItself = true

    override suspend fun getIdToken(): String? {
        // signInWithPopup handles the full OAuth flow and signs the user into Firebase.
        // The authState flow in FirebaseAuthRepository picks up the auth change automatically.
        signInWithPopup(Firebase.auth.js, GoogleAuthProvider()).await()
        return null
    }
}
