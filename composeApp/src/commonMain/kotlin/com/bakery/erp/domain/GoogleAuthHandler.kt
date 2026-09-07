package com.bakery.erp.domain

interface GoogleAuthHandler {
    val isAvailable: Boolean get() = true

    /**
     * true cuando la plataforma completa el sign-in por su cuenta (web: signInWithPopup ya deja
     * al usuario autenticado en Firebase) y por lo tanto `getIdToken()` devuelve null en el caso
     * de éxito. En Android/iOS un null significa "no se obtuvo token" — es decir, un fallo.
     */
    val completesSignInItself: Boolean get() = false

    /** Lanza [GoogleSignInCancelledException] o [GoogleSignInException] si algo falla. */
    suspend fun getIdToken(): String?
}

/** El usuario cerró el selector de cuentas. No es un error: la UI solo deja de cargar. */
class GoogleSignInCancelledException : Exception("Inicio de sesión con Google cancelado")

/** Fallo real obteniendo la credencial de Google. [message] es apto para mostrar al usuario. */
class GoogleSignInException(
    message: String,
    val diagnostic: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)
